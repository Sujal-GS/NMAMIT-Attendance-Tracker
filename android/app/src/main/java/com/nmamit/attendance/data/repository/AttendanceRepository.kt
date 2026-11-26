package com.nmamit.attendance.data.repository

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nmamit.attendance.data.local.SessionManager
import com.nmamit.attendance.data.model.*
import com.nmamit.attendance.data.remote.AttendanceApiService
import com.nmamit.attendance.data.remote.PortalCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.ConcurrentHashMap

class AttendanceRepository(
    private val apiService: AttendanceApiService,
    private val cookieJar: PortalCookieJar,
    private val sessionManager: SessionManager
) {
    private val gson = Gson()
    private val reloginMutex = Mutex()
    @Volatile
    private var lastReloginAtMs: Long = 0L
    private val dailyCache = ConcurrentHashMap<String, List<DailyClassRecord>>()

    val sessionIdFlow: Flow<String?> = sessionManager.sessionIdFlow
    val studentInfoFlow: Flow<StudentInfo?> = sessionManager.studentInfoFlow
    val isDemoFlow: Flow<Boolean> = sessionManager.isDemoFlow

    companion object {
        private val UNIV_REGEX = Regex("^[a-zA-Z0-9_-]{1,10}$")
        private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        private val EXPIRY_PHRASES = listOf(
            "session expired",
            "session timed out",
            "session timeout",
            "please login",
            "invalid session",
            "not logged in",
            "signin.php",
            "window.location=\"index.html\"",
            "window.location='index.html'",
            "session destroyed",
            "login again"
        )
    }

    suspend fun hasSavedSession(): Boolean = withContext(Dispatchers.IO) {
        sessionManager.hasSavedSession()
    }

    fun clearCache() {
        dailyCache.clear()
    }

    private fun sanitizeUniv(raw: String): String {
        val trimmed = raw.trim()
        return if (UNIV_REGEX.matches(trimmed)) trimmed else "049"
    }

    private fun sanitizeRegno(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
        return if (cleaned.length in 3..30) cleaned else null
    }

    private fun sanitizePassword(raw: String): String? {
        return if (raw.length in 1..128 && raw.none { it.code < 32 }) raw else null
    }

    private fun isPortalSessionExpired(
        json: JsonObject?,
        rawText: String,
        httpCode: Int = 200,
        locationHeader: String? = null
    ): Boolean {
        if (httpCode == 401 || httpCode == 301 || httpCode == 302) return true
        val loc = locationHeader?.lowercase().orEmpty()
        if (loc.contains("index.html") || loc.contains("signin") || loc.contains("login")) return true
        val lowerRaw = rawText.lowercase()
        if (EXPIRY_PHRASES.any { lowerRaw.contains(it) }) return true
        if (json != null) {
            val msg = (json.str("msg") ?: json.str("message") ?: "").lowercase()
            if (EXPIRY_PHRASES.any { msg.contains(it) }) return true
        }
        return false
    }

    private fun parseJsonObjectSafe(rawText: String): JsonObject? {
        val trimmed = rawText.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            val el = JsonParser.parseString(trimmed)
            if (el.isJsonObject) el.asJsonObject else null
        }.getOrNull()
    }

    private enum class ReloginResult {
        SUCCESS,
        INVALID_CREDENTIALS,
        NETWORK_ERROR
    }

    private suspend fun ensureActivePortalCookie(): ReloginResult {
        if (sessionManager.isDemoSession()) return ReloginResult.SUCCESS
        if (cookieJar.hasSessionCookie()) return ReloginResult.SUCCESS
        return attemptSilentRelogin(forceRefresh = false)
    }

    private suspend fun attemptSilentRelogin(forceRefresh: Boolean = true): ReloginResult = reloginMutex.withLock {
        if (sessionManager.isDemoSession()) return ReloginResult.SUCCESS
        val now = System.currentTimeMillis()
        if (!forceRefresh && cookieJar.hasSessionCookie()) return ReloginResult.SUCCESS
        if (forceRefresh && cookieJar.hasSessionCookie() && (now - lastReloginAtMs) < 4_000L) {
            return ReloginResult.SUCCESS
        }
        val creds = sessionManager.getSavedCredentials() ?: return ReloginResult.INVALID_CREDENTIALS
        if (creds.isDemo) return ReloginResult.SUCCESS

        return try {
            cookieJar.clear()
            val captchaResp = apiService.getCaptcha()
            if (!captchaResp.isSuccessful) {
                return ReloginResult.NETWORK_ERROR
            }
            val captchaTextRaw = captchaResp.body()?.string().orEmpty()
            val captchaJson = parseJsonObjectSafe(captchaTextRaw)
            val freshCaptcha = captchaJson?.str("captcha")?.take(10)?.ifBlank { null } ?: "123456"

            val signInResp = apiService.signIn(
                regno = creds.loginId,
                passwd = creds.password,
                captcha = freshCaptcha
            )
            val signInRaw = signInResp.body()?.string().orEmpty()
            val signInJson = parseJsonObjectSafe(signInRaw)
            if (!signInResp.isSuccessful || signInJson == null) {
                return ReloginResult.NETWORK_ERROR
            }
            val errorCode = signInJson.intOrNull("error_code") ?: -1
            if (errorCode == 0 && cookieJar.hasSessionCookie()) {
                lastReloginAtMs = System.currentTimeMillis()
                ReloginResult.SUCCESS
            } else if (errorCode != 0) {
                ReloginResult.INVALID_CREDENTIALS
            } else {
                ReloginResult.NETWORK_ERROR
            }
        } catch (_: Exception) {
            ReloginResult.NETWORK_ERROR
        }
    }

    suspend fun fetchCaptcha(univcode: String = "049"): Resource<CaptchaResponse> =
        withContext(Dispatchers.IO) {
            try {
                cookieJar.clear()
                val response = apiService.getCaptcha()
                if (response.isSuccessful) {
                    val raw = response.body()?.string().orEmpty()
                    val json = parseJsonObjectSafe(raw)
                    val captcha = json?.str("captcha")?.take(10)?.ifBlank { null }
                        ?: (100000..999999).random().toString()
                    Resource.Success(
                        CaptchaResponse(
                            success = true,
                            captcha = captcha,
                            captchaToken = "cookie_bound"
                        )
                    )
                } else {
                    Resource.Error("Portal returned HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Resource.Error("Unable to reach university portal. Check your internet connection.")
            }
        }

    suspend fun login(
        regno: String,
        passwd: String,
        captcha: String,
        captchaToken: String = "",
        univcode: String = "049",
        isDemo: Boolean = false
    ): Resource<LoginResponse> = withContext(Dispatchers.IO) {
        val cleanUniv = sanitizeUniv(univcode)

        if (isDemo) {
            dailyCache.clear()
            cookieJar.clear()
            val demoStudent = DemoDataProvider.getDemoStudentInfo(regno)
            val sessionToken = "demo_${System.currentTimeMillis()}"
            val demoRegno = demoStudent.regno ?: "NNM24CS1234"
            sessionManager.saveSession(
                sessionId = sessionToken,
                loginId = demoRegno,
                regno = demoRegno,
                plainPassword = "",
                univcode = cleanUniv,
                studentInfo = demoStudent,
                isDemo = true
            )
            return@withContext Resource.Success(
                LoginResponse(
                    success = true,
                    sessionId = sessionToken,
                    studentInfo = demoStudent,
                    isDemo = true
                )
            )
        }

        val cleanRegno = sanitizeRegno(regno)
            ?: return@withContext Resource.Error("Valid registered mobile number or USN (3–30 chars) is required.")
        val cleanPasswd = sanitizePassword(passwd)
            ?: return@withContext Resource.Error("Invalid password format.")

        try {
            dailyCache.clear()
            var finalCaptcha = captcha.trim().take(10)

            // Ensure a valid PHPSESSID cookie exists for this login attempt
            if (!cookieJar.hasSessionCookie()) {
                val capResp = apiService.getCaptcha()
                val capRaw = capResp.body()?.string().orEmpty()
                val capJson = parseJsonObjectSafe(capRaw)
                finalCaptcha = capJson?.str("captcha")?.take(10)?.ifBlank { null } ?: "123456"
            }

            val signInResp = apiService.signIn(
                regno = cleanRegno,
                passwd = cleanPasswd,
                captcha = finalCaptcha.ifBlank { "123456" }
            )

            val rawText = signInResp.body()?.string().orEmpty()
            val json = parseJsonObjectSafe(rawText)

            if (!signInResp.isSuccessful || json == null) {
                return@withContext Resource.Error("Unexpected university portal response (HTTP ${signInResp.code()}).")
            }

            val errorCode = json.intOrNull("error_code") ?: -1
            if (errorCode != 0) {
                val msg = json.str("msg")?.takeIf { it.isNotBlank() }
                    ?: "Invalid mobile number, password, or captcha."
                return@withContext Resource.Error(msg)
            }

            // Fetch full student profile from src/profile.php
            var resolvedRegno = cleanRegno
            var resolvedUniv = cleanUniv
            var studentInfo: StudentInfo? = null

            runCatching {
                val profResp = apiService.getProfile()
                val profRaw = profResp.body()?.string().orEmpty()
                val profJson = parseJsonObjectSafe(profRaw)
                if (profJson != null && (profJson.str("status") == "success" || !profJson.str("strRegno").isNullOrBlank())) {
                    resolvedRegno = profJson.str("strRegno")?.ifBlank { null } ?: cleanRegno
                    val profUniv = profJson.str("funivcode")
                    if (!profUniv.isNullOrBlank() && UNIV_REGEX.matches(profUniv.trim())) {
                        resolvedUniv = profUniv.trim()
                    }
                    studentInfo = StudentInfo(
                        regno = resolvedRegno,
                        name = profJson.str("fname")?.ifBlank { null } ?: "Student User",
                        degree = profJson.str("degree") ?: profJson.str("fdegree") ?: "Engineering",
                        branch = profJson.str("college")?.ifBlank { null } ?: "NMAM Institute of Technology",
                        semester = profJson.str("strSemester") ?: profJson.str("fexamname") ?: "Academic Year",
                        photo = profJson.str("photo") ?: profJson.str("photopath") ?: ""
                    )
                }
            }

            if (studentInfo == null) {
                studentInfo = StudentInfo(
                    regno = resolvedRegno,
                    name = "Student User",
                    degree = "Engineering",
                    branch = "Undergraduate Program",
                    semester = "Academic Year"
                )
            }

            val sessionToken = "portal_${System.currentTimeMillis()}"
            sessionManager.saveSession(
                sessionId = sessionToken,
                loginId = cleanRegno,
                regno = resolvedRegno,
                plainPassword = cleanPasswd,
                univcode = resolvedUniv,
                studentInfo = studentInfo,
                isDemo = false
            )

            Resource.Success(
                LoginResponse(
                    success = true,
                    sessionId = sessionToken,
                    studentInfo = studentInfo,
                    isDemo = false
                )
            )
        } catch (e: Exception) {
            Resource.Error("Could not connect to university portal. Check your internet connection.")
        }
    }

    suspend fun getAttendanceSummary(date: String? = null): Resource<List<SubjectAttendanceDto>> =
        withContext(Dispatchers.IO) {
            if (sessionManager.isDemoSession()) {
                return@withContext Resource.Success(DemoDataProvider.getDemoAttendanceSummary())
            }

            val queryDate = if (date != null && DATE_REGEX.matches(date)) date else LocalDate.now().toString()
            val univcode = sessionManager.getUnivCode()

            try {
                when (ensureActivePortalCookie()) {
                    ReloginResult.SUCCESS -> Unit
                    ReloginResult.INVALID_CREDENTIALS -> {
                        sessionManager.clearSession()
                        return@withContext Resource.Error("Session expired. Please sign in again.", sessionExpired = true)
                    }
                    ReloginResult.NETWORK_ERROR -> {
                        return@withContext Resource.Error("Unable to reach university portal. Check your internet and tap Sync.")
                    }
                }

                var resp = apiService.getAttendanceSummary(univcode, queryDate)
                var rawText = resp.body()?.string().orEmpty()
                var json = parseJsonObjectSafe(rawText)

                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                    when (attemptSilentRelogin()) {
                        ReloginResult.SUCCESS -> {
                            resp = apiService.getAttendanceSummary(univcode, queryDate)
                            rawText = resp.body()?.string().orEmpty()
                            json = parseJsonObjectSafe(rawText)
                        }
                        ReloginResult.INVALID_CREDENTIALS -> {
                            sessionManager.clearSession()
                            return@withContext Resource.Error("University portal session expired. Please sign in again.", sessionExpired = true)
                        }
                        ReloginResult.NETWORK_ERROR -> {
                            return@withContext Resource.Error("Could not reconnect to university portal. Check your connection.")
                        }
                    }
                }

                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                    sessionManager.clearSession()
                    return@withContext Resource.Error("University portal session expired. Please sign in again.", sessionExpired = true)
                }

                if (!resp.isSuccessful || json == null) {
                    return@withContext Resource.Error("Unable to read attendance records from student portal.")
                }

                val dataArray = json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                val list = mutableListOf<SubjectAttendanceDto>()
                dataArray?.forEach { element ->
                    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    val cond = obj.str("conducted") ?: obj.str("ftotalclass") ?: obj.str("total") ?: "0"
                    val att = obj.str("attended") ?: obj.str("fpresentclass") ?: obj.str("present") ?: "0"
                    val code = obj.str("fsubcode") ?: obj.str("subcode") ?: obj.str("code") ?: ""
                    val name = obj.str("fsubname") ?: obj.str("subname") ?: obj.str("name") ?: ""
                    if (code.isNotBlank() || name.isNotBlank()) {
                        list.add(
                            SubjectAttendanceDto(
                                subCode = code,
                                subName = name,
                                conducted = cond,
                                attended = att,
                                totalClass = cond,
                                presentClass = att
                            )
                        )
                    }
                }
                Resource.Success(list)
            } catch (e: Exception) {
                Resource.Error("Failed to retrieve attendance summary from student portal.")
            }
        }

    suspend fun getDailyAttendance(date: String): Resource<List<DailyClassRecord>> =
        withContext(Dispatchers.IO) {
            if (!DATE_REGEX.matches(date)) {
                return@withContext Resource.Error("Invalid date format.")
            }

            if (sessionManager.isDemoSession()) {
                return@withContext Resource.Success(DemoDataProvider.getDemoDailyAttendance(date))
            }

            dailyCache[date]?.let { cached ->
                return@withContext Resource.Success(cached)
            }

            val univcode = sessionManager.getUnivCode()
            val regno = sessionManager.getRegno()
            if (regno.isBlank()) {
                sessionManager.clearSession()
                return@withContext Resource.Error("Session expired. Please sign in again.", sessionExpired = true)
            }

            try {
                when (ensureActivePortalCookie()) {
                    ReloginResult.SUCCESS -> Unit
                    ReloginResult.INVALID_CREDENTIALS -> {
                        sessionManager.clearSession()
                        return@withContext Resource.Error("Session expired. Please sign in again.", sessionExpired = true)
                    }
                    ReloginResult.NETWORK_ERROR -> {
                        return@withContext Resource.Error("Could not reach university portal. Check your internet connection.")
                    }
                }

                var resp = apiService.getDailyAttendance(univcode, date, regno)
                var rawText = resp.body()?.string().orEmpty()
                var json = parseJsonObjectSafe(rawText)

                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                    when (attemptSilentRelogin()) {
                        ReloginResult.SUCCESS -> {
                            resp = apiService.getDailyAttendance(univcode, date, regno)
                            rawText = resp.body()?.string().orEmpty()
                            json = parseJsonObjectSafe(rawText)
                        }
                        ReloginResult.INVALID_CREDENTIALS -> {
                            sessionManager.clearSession()
                            return@withContext Resource.Error("Session expired. Please sign in again.", sessionExpired = true)
                        }
                        ReloginResult.NETWORK_ERROR -> {
                            return@withContext Resource.Error("Could not reconnect to university portal.")
                        }
                    }
                }

                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                    sessionManager.clearSession()
                    return@withContext Resource.Error("Session expired. Please sign in again.", sessionExpired = true)
                }

                if (!resp.isSuccessful || json == null) {
                    return@withContext Resource.Error("Unable to parse timetable data from student portal.")
                }

                val classes = parseDailyClassesFromJson(json)
                dailyCache[date] = classes
                Resource.Success(classes)
            } catch (e: Exception) {
                Resource.Error("Failed to retrieve daily attendance records.")
            }
        }

    suspend fun getMonthAttendance(year: Int, month: Int): Resource<Map<String, DayAttendanceSummary>> =
        withContext(Dispatchers.IO) {
            if (year !in 2020..2035 || month !in 1..12) {
                return@withContext Resource.Error("Invalid month/year range.")
            }

            val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
            val todayStr = LocalDate.now().toString()
            val results = ConcurrentHashMap<String, DayAttendanceSummary>()
            val activeDatesToFetch = mutableListOf<String>()

            for (d in 1..daysInMonth) {
                val dateStr = "%04d-%02d-%02d".format(year, month, d)
                val dayOfWeek = LocalDate.of(year, month, d).dayOfWeek
                if (dayOfWeek == DayOfWeek.SUNDAY || dateStr > todayStr) {
                    results[dateStr] = DayAttendanceSummary(conducted = 0, attended = 0, classes = emptyList())
                } else {
                    activeDatesToFetch.add(dateStr)
                }
            }

            if (sessionManager.isDemoSession()) {
                for (dateStr in activeDatesToFetch) {
                    val classes = DemoDataProvider.getDemoDailyAttendance(dateStr)
                    results[dateStr] = summarizeDay(classes)
                }
                return@withContext Resource.Success(results.toMap())
            }

            val univcode = sessionManager.getUnivCode()
            val regno = sessionManager.getRegno()
            if (regno.isBlank()) {
                sessionManager.clearSession()
                return@withContext Resource.Error("Session expired.", sessionExpired = true)
            }

            when (ensureActivePortalCookie()) {
                ReloginResult.SUCCESS -> Unit
                ReloginResult.INVALID_CREDENTIALS -> {
                    sessionManager.clearSession()
                    return@withContext Resource.Error("Session expired.", sessionExpired = true)
                }
                ReloginResult.NETWORK_ERROR -> {
                    return@withContext Resource.Error("Unable to reach university portal. Check your internet connection.")
                }
            }

            var sessionExpired = false
            var reloggedOnce = false

            // Batch fetch in chunks of 6 to match server.js concurrency & avoid overwhelming portal
            for (chunk in activeDatesToFetch.chunked(6)) {
                if (sessionExpired) break

                val uncached = mutableListOf<String>()
                for (dateStr in chunk) {
                    val cached = dailyCache[dateStr]
                    if (cached != null) {
                        results[dateStr] = summarizeDay(cached)
                    } else {
                        uncached.add(dateStr)
                    }
                }
                if (uncached.isEmpty()) continue

                coroutineScope {
                    uncached.map { dateStr ->
                        async {
                            if (sessionExpired) return@async
                            try {
                                var resp = apiService.getDailyAttendance(univcode, dateStr, regno)
                                var rawText = resp.body()?.string().orEmpty()
                                var json = parseJsonObjectSafe(rawText)

                                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"]) && !reloggedOnce) {
                                    reloggedOnce = true
                                    when (attemptSilentRelogin()) {
                                        ReloginResult.SUCCESS -> {
                                            resp = apiService.getDailyAttendance(univcode, dateStr, regno)
                                            rawText = resp.body()?.string().orEmpty()
                                            json = parseJsonObjectSafe(rawText)
                                        }
                                        ReloginResult.INVALID_CREDENTIALS -> {
                                            sessionExpired = true
                                            return@async
                                        }
                                        ReloginResult.NETWORK_ERROR -> {
                                            results[dateStr] = DayAttendanceSummary(0, 0, emptyList(), error = true)
                                            return@async
                                        }
                                    }
                                }

                                if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                                    sessionExpired = true
                                    return@async
                                }

                                val classes = if (json != null) parseDailyClassesFromJson(json) else emptyList()
                                dailyCache[dateStr] = classes
                                results[dateStr] = summarizeDay(classes)
                            } catch (_: Exception) {
                                results[dateStr] = DayAttendanceSummary(0, 0, emptyList(), error = true)
                            }
                        }
                    }.awaitAll()
                }
            }

            if (sessionExpired) {
                sessionManager.clearSession()
                return@withContext Resource.Error("University portal session expired. Please sign in again.", sessionExpired = true)
            }

            Resource.Success(results.toMap())
        }

    suspend fun getIaMarks(): Resource<List<IAMarkItem>> = withContext(Dispatchers.IO) {
        if (sessionManager.isDemoSession()) {
            return@withContext Resource.Success(DemoDataProvider.getDemoIaMarks())
        }

        val univcode = sessionManager.getUnivCode()
        try {
            when (ensureActivePortalCookie()) {
                ReloginResult.SUCCESS -> Unit
                ReloginResult.INVALID_CREDENTIALS -> {
                    sessionManager.clearSession()
                    return@withContext Resource.Error("Session expired.", sessionExpired = true)
                }
                ReloginResult.NETWORK_ERROR -> {
                    return@withContext Resource.Error("Unable to reach university portal. Check your internet connection.")
                }
            }

            var resp = apiService.getIaMarksNew(univcode)
            var rawText = resp.body()?.string().orEmpty()
            var json = parseJsonObjectSafe(rawText)

            if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                when (attemptSilentRelogin()) {
                    ReloginResult.SUCCESS -> {
                        resp = apiService.getIaMarksNew(univcode)
                        rawText = resp.body()?.string().orEmpty()
                        json = parseJsonObjectSafe(rawText)
                    }
                    ReloginResult.INVALID_CREDENTIALS -> {
                        sessionManager.clearSession()
                        return@withContext Resource.Error("Session expired.", sessionExpired = true)
                    }
                    ReloginResult.NETWORK_ERROR -> {
                        return@withContext Resource.Error("Could not reconnect to university portal.")
                    }
                }
            }

            if (isPortalSessionExpired(json, rawText, resp.code(), resp.headers()["Location"])) {
                sessionManager.clearSession()
                return@withContext Resource.Error("Session expired.", sessionExpired = true)
            }

            var items = extractIaItemsFromJson(json)

            // Fallback to elementWiseMarks if viewIaMarksNew returned nothing
            if (items.isEmpty()) {
                runCatching {
                    val resp2 = apiService.getElementWiseMarks(univcode)
                    val raw2 = resp2.body()?.string().orEmpty()
                    val json2 = parseJsonObjectSafe(raw2)
                    if (!isPortalSessionExpired(json2, raw2)) {
                        items = extractIaItemsFromJson(json2)
                    }
                }
            }

            Resource.Success(items)
        } catch (e: Exception) {
            Resource.Error("Failed to retrieve IA marks from student portal.")
        }
    }

    private fun extractIaItemsFromJson(json: JsonObject?): List<IAMarkItem> {
        val dataEl = json?.get("data") ?: return emptyList()
        if (dataEl.isJsonArray) {
            return parseIaArray(dataEl.asJsonArray)
        }
        if (dataEl.isJsonObject) {
            val marksEl = dataEl.asJsonObject.get("marks") ?: return emptyList()
            if (marksEl.isJsonPrimitive && marksEl.asJsonPrimitive.isString) {
                val html = marksEl.asString.take(250_000)
                return parseIaHtmlTable(html)
            }
            if (marksEl.isJsonArray) {
                return parseIaArray(marksEl.asJsonArray)
            }
        }
        return emptyList()
    }

    private fun parseIaArray(array: com.google.gson.JsonArray): List<IAMarkItem> {
        return array.mapNotNull { el ->
            runCatching { gson.fromJson(el, IAMarkItem::class.java) }.getOrNull()
        }
    }

    private fun parseDailyClassesFromJson(json: JsonObject): List<DailyClassRecord> {
        val errCode = json.intOrNull("error_code") ?: 0
        if (errCode != 0) return emptyList()
        val dataArray = json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return dataArray.mapNotNull { el ->
            runCatching { gson.fromJson(el, DailyClassRecord::class.java) }.getOrNull()
        }
    }

    private fun summarizeDay(classes: List<DailyClassRecord>): DayAttendanceSummary {
        var conducted = 0
        var attended = 0
        for (cls in classes) {
            val h = cls.hoursCount
            conducted += h
            if (cls.isPresent) attended += h
        }
        return DayAttendanceSummary(conducted = conducted, attended = attended, classes = classes)
    }

    private fun parseIaHtmlTable(html: String): List<IAMarkItem> {
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("<t[dh][^>]*>(.*?)</t[dh]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val tagStripRegex = Regex("<[^>]+>")

        val rows = rowRegex.findAll(html).map { trMatch ->
            cellRegex.findAll(trMatch.groupValues[1]).map { cellMatch ->
                cellMatch.groupValues[1]
                    .replace(tagStripRegex, "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .trim()
            }.toList()
        }.toList()

        if (rows.isEmpty()) return emptyList()

        var headerIndex = -1
        var headers = emptyList<String>()
        for (i in rows.indices) {
            val cells = rows[i]
            val hasSub = cells.any { it.contains(Regex("sub.*code|subject", RegexOption.IGNORE_CASE)) }
            val hasMarks = cells.any { it.contains(Regex("mse|marks|la-|final", RegexOption.IGNORE_CASE)) }
            if (hasSub || (cells.size >= 4 && hasMarks)) {
                headerIndex = i
                headers = cells
                break
            }
        }
        if (headerIndex == -1) {
            headerIndex = if (rows.size > 1) 1 else 0
            headers = rows.getOrNull(headerIndex) ?: emptyList()
        }

        val result = mutableListOf<IAMarkItem>()
        for (i in (headerIndex + 1) until rows.size) {
            val cells = rows[i]
            if (cells.size < 3) continue
            val map = mutableMapOf<String, String>()
            headers.forEachIndexed { idx, h ->
                map[h.lowercase()] = cells.getOrElse(idx) { "" }
            }
            fun findVal(pattern: String): String {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                return map.entries.firstOrNull { regex.containsMatchIn(it.key) }?.value.orEmpty()
            }
            val subCode = findVal("sub.*code").ifBlank { cells.getOrElse(2) { "" } }
            val subName = findVal("sub.*name").ifBlank { cells.getOrElse(3) { "" } }
            var finalMarks = findVal("final")
            if (finalMarks.isBlank()) {
                finalMarks = cells.lastOrNull { it.matches(Regex("^\\d+(\\.\\d+)?$")) }.orEmpty()
            }
            if (subCode.isNotBlank() || subName.isNotBlank()) {
                result.add(
                    IAMarkItem(
                        slNo = findVal("sl.*no|^no$"),
                        sem = findVal("^sem$"),
                        subcode = subCode,
                        subname = subName,
                        mse1 = findVal("mse.?1"),
                        mse2 = findVal("mse.?2"),
                        la1 = findVal("la.?1"),
                        la2 = findVal("la.?2"),
                        la3 = findVal("la.?3"),
                        record = findVal("record"),
                        conduction = findVal("conduction"),
                        mseLab = findVal("mse.*lab"),
                        finalMarks = finalMarks,
                        eligibility = findVal("elig")
                    )
                )
            }
        }
        return result
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            dailyCache.clear()
            cookieJar.clear()
            sessionManager.clearSession()
        }
    }
}

private fun JsonObject.str(key: String): String? {
    val el: JsonElement = get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    return runCatching { el.asString }.getOrNull()
}

private fun JsonObject.intOrNull(key: String): Int? {
    val el: JsonElement = get(key) ?: return null
    if (el.isJsonNull || !el.isJsonPrimitive) return null
    return runCatching { el.asString.trim().toIntOrNull() }.getOrNull()
}
