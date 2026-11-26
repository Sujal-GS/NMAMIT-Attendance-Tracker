package com.nmamit.attendance.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nmamit.attendance.data.model.*
import com.nmamit.attendance.data.repository.AttendanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class DashboardTab(val title: String, val badge: String) {
    CALENDAR("Calendar", "Day Log"),
    SUBJECT_PLANNER("Subject Planner", "85% Goal"),
    ACTIVITY_MATRIX("Activity Matrix", "Streaks"),
    IA_MARKS("IA Marks", "CIE")
}

data class SimDelta(
    val extraAttended: Int = 0,
    val extraConducted: Int = 0
)

data class AbsentRecordItem(
    val dateStr: String,
    val period: String,
    val hours: Int,
    val teacher: String
)

data class DayBarStat(
    val label: String,
    val attended: Int,
    val conducted: Int,
    val percentage: Int? // null if 0 conducted
)

data class DashboardUiState(
    val activeTab: DashboardTab = DashboardTab.CALENDAR,
    val studentInfo: StudentInfo? = null,
    val isDemo: Boolean = false,
    val sessionExpired: Boolean = false,

    // Summary & Subject Planner
    val subjects: List<SubjectAttendanceDto> = emptyList(),
    val targetPercent: Int = 85,
    val searchQuery: String = "",
    val perSubjectSim: Map<String, SimDelta> = emptyMap(),
    val isSummaryLoading: Boolean = false,
    val summaryError: String? = null,

    // What-If Sandbox Simulator
    val simScope: String = "AGGREGATE",
    val simExtraAttend: Int = 0,
    val simExtraBunk: Int = 0,

    // Calendar & Daily Timetable
    val calYear: Int = LocalDate.now().year,
    val calMonth: Int = LocalDate.now().monthValue,
    val selectedDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val monthDataMap: Map<String, Map<String, DayAttendanceSummary>> = emptyMap(),
    val selectedDayClasses: List<DailyClassRecord> = emptyList(),
    val isCalendarLoading: Boolean = false,
    val isDailyLoading: Boolean = false,

    // Activity Matrix (Heatmap & Smart Insights)
    val semesterData: Map<String, DayAttendanceSummary> = emptyMap(),
    val heatmapSubjectFilter: String = "ALL",
    val isHeatmapLoading: Boolean = false,
    val streaks: Triple<Int, Int, Int> = Triple(0, 0, 0),
    val weeklyPattern: List<DayBarStat> = emptyList(),
    val monthlyTrend: List<DayBarStat> = emptyList(),

    // IA Marks (CIE)
    val iaMarks: List<IAMarkItem> = emptyList(),
    val isIaLoading: Boolean = false,
    val iaError: String? = null,

    // Modals
    val showWrappedModal: Boolean = false,
    val absentModalSubject: SubjectAttendanceDto? = null,
    val absentModalRecords: List<AbsentRecordItem> = emptyList()
)

class DashboardViewModel(
    private val repository: AttendanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var insightsJob: Job? = null

    init {
        viewModelScope.launch {
            repository.studentInfoFlow.collect { info ->
                _uiState.value = _uiState.value.copy(studentInfo = info)
            }
        }
        viewModelScope.launch {
            repository.isDemoFlow.collect { demo ->
                _uiState.value = _uiState.value.copy(isDemo = demo)
            }
        }
    }

    fun selectTab(tab: DashboardTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun loadAllInitialData() {
        val now = LocalDate.now()
        _uiState.value = _uiState.value.copy(
            sessionExpired = false,
            calYear = now.year,
            calMonth = now.monthValue,
            selectedDate = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        )
        loadAttendance()
        loadCalendarMonth(now.year, now.monthValue)
        loadDailyClasses(now.format(DateTimeFormatter.ISO_LOCAL_DATE))
        loadIaMarks()
        loadSemesterHeatmap()
    }

    fun syncAllData() {
        val state = _uiState.value
        repository.clearCache()
        _uiState.value = state.copy(
            monthDataMap = emptyMap(),
            semesterData = emptyMap(),
            perSubjectSim = emptyMap(),
            sessionExpired = false
        )
        loadAttendance()
        loadCalendarMonth(state.calYear, state.calMonth, forceRefresh = true)
        loadDailyClasses(state.selectedDate)
        loadIaMarks()
        loadSemesterHeatmap(forceRefresh = true)
    }

    fun loadAttendance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSummaryLoading = true,
                summaryError = null,
                sessionExpired = false
            )
            when (val result = repository.getAttendanceSummary()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        subjects = result.data,
                        isSummaryLoading = false,
                        sessionExpired = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSummaryLoading = false,
                        summaryError = result.message,
                        sessionExpired = result.sessionExpired
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Calendar & Daily Breakdown
    // -----------------------------------------------------------------------
    fun prevMonth() {
        val state = _uiState.value
        val (newY, newM) = if (state.calMonth == 1) state.calYear - 1 to 12 else state.calYear to state.calMonth - 1
        val newDate = LocalDate.of(newY, newM, 1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        _uiState.value = state.copy(calYear = newY, calMonth = newM, selectedDate = newDate)
        loadCalendarMonth(newY, newM)
        loadDailyClasses(newDate)
    }

    fun nextMonth() {
        val state = _uiState.value
        val (newY, newM) = if (state.calMonth == 12) state.calYear + 1 to 1 else state.calYear to state.calMonth + 1
        val newDate = LocalDate.of(newY, newM, 1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        _uiState.value = state.copy(calYear = newY, calMonth = newM, selectedDate = newDate)
        loadCalendarMonth(newY, newM)
        loadDailyClasses(newDate)
    }

    fun goToday() {
        val now = LocalDate.now()
        val dateStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        _uiState.value = _uiState.value.copy(calYear = now.year, calMonth = now.monthValue, selectedDate = dateStr)
        loadCalendarMonth(now.year, now.monthValue)
        loadDailyClasses(dateStr)
    }

    fun selectDate(dateStr: String) {
        _uiState.value = _uiState.value.copy(selectedDate = dateStr)
        loadDailyClasses(dateStr)
    }

    fun jumpToDateInCalendar(dateStr: String) {
        val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            activeTab = DashboardTab.CALENDAR,
            calYear = parsed.year,
            calMonth = parsed.monthValue,
            selectedDate = dateStr,
            absentModalSubject = null
        )
        loadCalendarMonth(parsed.year, parsed.monthValue)
        loadDailyClasses(dateStr)
    }

    fun loadCalendarMonth(year: Int, month: Int, forceRefresh: Boolean = false) {
        val cacheKey = "%04d-%02d".format(year, month)
        val existing = _uiState.value.monthDataMap[cacheKey]
        if (!forceRefresh && existing != null) {
            updateSelectedDayFromCache(existing, _uiState.value.selectedDate)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCalendarLoading = true)
            when (val res = repository.getMonthAttendance(year, month)) {
                is Resource.Success -> {
                    val updatedMonths = _uiState.value.monthDataMap.toMutableMap()
                    updatedMonths[cacheKey] = res.data
                    val updatedSemester = _uiState.value.semesterData.toMutableMap()
                    updatedSemester.putAll(res.data)
                    _uiState.value = _uiState.value.copy(
                        monthDataMap = updatedMonths,
                        semesterData = updatedSemester,
                        isCalendarLoading = false
                    )
                    updateSelectedDayFromCache(res.data, _uiState.value.selectedDate)
                    scheduleInsightsRecompute()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCalendarLoading = false,
                        sessionExpired = res.sessionExpired || _uiState.value.sessionExpired
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    private fun updateSelectedDayFromCache(monthMap: Map<String, DayAttendanceSummary>, dateStr: String) {
        val cachedClasses = monthMap[dateStr]?.classes
        if (cachedClasses != null) {
            _uiState.value = _uiState.value.copy(selectedDayClasses = cachedClasses, isDailyLoading = false)
        }
    }

    fun loadDailyClasses(dateStr: String) {
        val ym = dateStr.take(7)
        val cached = _uiState.value.monthDataMap[ym]?.get(dateStr)?.classes
            ?: _uiState.value.semesterData[dateStr]?.classes
        if (cached != null) {
            _uiState.value = _uiState.value.copy(selectedDayClasses = cached, isDailyLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDailyLoading = true)
            when (val res = repository.getDailyAttendance(dateStr)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        selectedDayClasses = res.data,
                        isDailyLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        selectedDayClasses = emptyList(),
                        isDailyLoading = false,
                        sessionExpired = res.sessionExpired || _uiState.value.sessionExpired
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Subject Planner, Target & What-If Simulator
    // -----------------------------------------------------------------------
    fun setTargetPercent(target: Int) {
        _uiState.value = _uiState.value.copy(targetPercent = target)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun simulateSubject(subCode: String, addAttended: Int, addConducted: Int) {
        val currentMap = _uiState.value.perSubjectSim.toMutableMap()
        val prev = currentMap[subCode] ?: SimDelta()
        currentMap[subCode] = SimDelta(
            extraAttended = prev.extraAttended + addAttended,
            extraConducted = prev.extraConducted + addConducted
        )
        _uiState.value = _uiState.value.copy(perSubjectSim = currentMap)
    }

    fun resetSubjectSimulation(subCode: String) {
        val currentMap = _uiState.value.perSubjectSim.toMutableMap()
        currentMap.remove(subCode)
        _uiState.value = _uiState.value.copy(perSubjectSim = currentMap)
    }

    fun setSimScope(scope: String) {
        _uiState.value = _uiState.value.copy(simScope = scope)
    }

    fun adjustSimAttend(delta: Int) {
        val next = (_uiState.value.simExtraAttend + delta).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(simExtraAttend = next)
    }

    fun adjustSimBunk(delta: Int) {
        val next = (_uiState.value.simExtraBunk + delta).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(simExtraBunk = next)
    }

    fun applySimPreset(attend: Int, bunk: Int) {
        _uiState.value = _uiState.value.copy(simExtraAttend = attend, simExtraBunk = bunk)
    }

    fun resetSimulator() {
        _uiState.value = _uiState.value.copy(simExtraAttend = 0, simExtraBunk = 0)
    }

    // -----------------------------------------------------------------------
    // Activity Matrix (Heatmap & Smart Insights)
    // -----------------------------------------------------------------------
    fun setHeatmapSubjectFilter(subCode: String) {
        _uiState.value = _uiState.value.copy(heatmapSubjectFilter = subCode)
        scheduleInsightsRecompute()
    }

    fun loadSemesterHeatmap(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHeatmapLoading = true)
            val today = LocalDate.now()
            val startYear = if (today.monthValue < 8) today.year - 1 else today.year

            val monthsToFetch = mutableListOf<Pair<Int, Int>>()
            var y = startYear
            var m = 8
            while (y < today.year || (y == today.year && m <= today.monthValue)) {
                val key = "%04d-%02d".format(y, m)
                if (forceRefresh || !_uiState.value.monthDataMap.containsKey(key)) {
                    monthsToFetch.add(y to m)
                }
                m++
                if (m > 12) {
                    m = 1
                    y++
                }
            }

            val accumulatedMonths = mutableMapOf<String, Map<String, DayAttendanceSummary>>()
            val accumulatedSemester = mutableMapOf<String, DayAttendanceSummary>()

            // Fetch in chunks of 2 to avoid overwhelming the portal, then publish in one atomic state update
            for (chunk in monthsToFetch.chunked(2)) {
                val results = chunk.map { (yr, mo) ->
                    async {
                        Triple(yr, mo, repository.getMonthAttendance(yr, mo))
                    }
                }.awaitAll()

                for ((yr, mo, res) in results) {
                    if (res is Resource.Success) {
                        val key = "%04d-%02d".format(yr, mo)
                        accumulatedMonths[key] = res.data
                        accumulatedSemester.putAll(res.data)
                    }
                }
            }

            if (accumulatedMonths.isNotEmpty()) {
                val updatedMonths = _uiState.value.monthDataMap.toMutableMap().apply { putAll(accumulatedMonths) }
                val updatedSemester = _uiState.value.semesterData.toMutableMap().apply { putAll(accumulatedSemester) }
                _uiState.value = _uiState.value.copy(
                    monthDataMap = updatedMonths,
                    semesterData = updatedSemester,
                    isHeatmapLoading = false
                )
                scheduleInsightsRecompute()
            } else {
                _uiState.value = _uiState.value.copy(isHeatmapLoading = false)
            }
        }
    }

    private fun scheduleInsightsRecompute() {
        val snapshotData = _uiState.value.semesterData
        val snapshotFilter = _uiState.value.heatmapSubjectFilter
        insightsJob?.cancel()
        insightsJob = viewModelScope.launch {
            val (streaks, weekly, monthly) = withContext(Dispatchers.Default) {
                Triple(
                    computeStreaksFromData(snapshotData, snapshotFilter),
                    computeWeeklyPatternFromData(snapshotData),
                    computeMonthlyTrendFromData(snapshotData)
                )
            }
            _uiState.value = _uiState.value.copy(
                streaks = streaks,
                weeklyPattern = weekly,
                monthlyTrend = monthly
            )
        }
    }

    // -----------------------------------------------------------------------
    // IA Marks (CIE)
    // -----------------------------------------------------------------------
    fun loadIaMarks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIaLoading = true, iaError = null)
            when (val res = repository.getIaMarks()) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        iaMarks = res.data,
                        isIaLoading = false
                    )
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isIaLoading = false,
                        iaError = res.message,
                        sessionExpired = res.sessionExpired || _uiState.value.sessionExpired
                    )
                }
                Resource.Loading -> {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Modals: Wrapped & Absent Dates
    // -----------------------------------------------------------------------
    fun setWrappedModalOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(showWrappedModal = open)
    }

    fun openAbsentModal(subject: SubjectAttendanceDto) {
        val cleanTarget = subject.subCode.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        val allDays = mutableMapOf<String, DayAttendanceSummary>()
        allDays.putAll(_uiState.value.semesterData)
        _uiState.value.monthDataMap.values.forEach { month ->
            month.forEach { (dateStr, info) ->
                if (!allDays.containsKey(dateStr)) allDays[dateStr] = info
            }
        }

        val records = mutableListOf<AbsentRecordItem>()
        for ((dateStr, dayInfo) in allDays) {
            dayInfo.classes?.forEach { cls ->
                val cCode = (cls.subCode ?: "").replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val isMatch = cCode == cleanTarget || cls.subCode?.trim() == subject.subCode.trim()
                if (isMatch && !cls.isPresent) {
                    records.add(
                        AbsentRecordItem(
                            dateStr = dateStr,
                            period = cls.period ?: "1",
                            hours = cls.hoursCount,
                            teacher = cls.teacherName.orEmpty()
                        )
                    )
                }
            }
        }
        records.sortByDescending { it.dateStr }
        _uiState.value = _uiState.value.copy(
            absentModalSubject = subject,
            absentModalRecords = records
        )
    }

    fun closeAbsentModal() {
        _uiState.value = _uiState.value.copy(
            absentModalSubject = null,
            absentModalRecords = emptyList()
        )
    }

    // -----------------------------------------------------------------------
    // Computed Helpers for Heatmap / Insights / Wrapped
    // -----------------------------------------------------------------------
    fun getFilteredDayStats(dateStr: String, filterSubCode: String): Pair<Int, Int> {
        return getFilteredDayStatsFromData(_uiState.value.semesterData, dateStr, filterSubCode)
    }

    private fun getFilteredDayStatsFromData(
        semesterData: Map<String, DayAttendanceSummary>,
        dateStr: String,
        filterSubCode: String
    ): Pair<Int, Int> {
        val raw = semesterData[dateStr] ?: return 0 to 0
        if (filterSubCode == "ALL") {
            return raw.attended to raw.conducted
        }
        var att = 0
        var cond = 0
        raw.classes.orEmpty()
            .filter { (it.subCode ?: "") == filterSubCode }
            .forEach { cls ->
                val h = cls.hoursCount
                cond += h
                if (cls.isPresent) att += h
            }
        return att to cond
    }

    private fun computeStreaksFromData(
        semesterData: Map<String, DayAttendanceSummary>,
        filterSubCode: String = "ALL"
    ): Triple<Int, Int, Int> {
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val sortedDates = semesterData.keys.filter { it <= todayStr }.sorted()

        var bestStreak = 0
        var tempStreak = 0
        var flawlessDays = 0

        for (dateStr in sortedDates) {
            val (att, cond) = getFilteredDayStatsFromData(semesterData, dateStr, filterSubCode)
            if (cond > 0) {
                if (att == cond) flawlessDays++
                if (att.toDouble() / cond >= 0.75) {
                    tempStreak++
                    if (tempStreak > bestStreak) bestStreak = tempStreak
                } else {
                    tempStreak = 0
                }
            }
        }

        var activeStreak = 0
        for (dateStr in sortedDates.reversed()) {
            val (att, cond) = getFilteredDayStatsFromData(semesterData, dateStr, filterSubCode)
            if (cond > 0) {
                if (att.toDouble() / cond >= 0.75) activeStreak++ else break
            }
        }
        return Triple(activeStreak, bestStreak, flawlessDays)
    }

    private fun computeWeeklyPatternFromData(
        semesterData: Map<String, DayAttendanceSummary>
    ): List<DayBarStat> {
        val days = listOf(
            DayOfWeek.MONDAY to "Mon",
            DayOfWeek.TUESDAY to "Tue",
            DayOfWeek.WEDNESDAY to "Wed",
            DayOfWeek.THURSDAY to "Thu",
            DayOfWeek.FRIDAY to "Fri",
            DayOfWeek.SATURDAY to "Sat"
        )
        val totals = days.associate { it.first to intArrayOf(0, 0) }
        for ((dateStr, info) in semesterData) {
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val arr = totals[date.dayOfWeek] ?: continue
            if (info.conducted > 0) {
                arr[0] += info.attended
                arr[1] += info.conducted
            }
        }
        return days.map { (dow, label) ->
            val arr = totals[dow]!!
            val pct = if (arr[1] > 0) ((arr[0].toDouble() / arr[1]) * 100.0).roundToInt() else null
            DayBarStat(label = label, attended = arr[0], conducted = arr[1], percentage = pct)
        }
    }

    private fun computeMonthlyTrendFromData(
        semesterData: Map<String, DayAttendanceSummary>
    ): List<DayBarStat> {
        val monthMap = sortedMapOf<String, IntArray>()
        for ((dateStr, info) in semesterData) {
            val ym = dateStr.take(7)
            val arr = monthMap.getOrPut(ym) { intArrayOf(0, 0) }
            arr[0] += info.attended
            arr[1] += info.conducted
        }
        val active = monthMap.entries.filter { it.value[1] > 0 }
        val toShow = (if (active.isNotEmpty()) active else monthMap.entries.toList()).takeLast(6)
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return toShow.map { (ym, arr) ->
            val mIdx = ym.substring(5, 7).toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
            val pct = if (arr[1] > 0) ((arr[0].toDouble() / arr[1]) * 100.0).roundToInt() else null
            DayBarStat(label = monthNames[mIdx], attended = arr[0], conducted = arr[1], percentage = pct)
        }
    }

    fun resetState() {
        _uiState.value = DashboardUiState()
    }
}
