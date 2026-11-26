package com.nmamit.attendance.data.remote

import com.nmamit.attendance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkDownloadUrl: String
)

object AppUpdateChecker {

    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/Sujal-GS/NMAMIT-Attendance-Tracker/releases/latest"
    const val RELEASES_PAGE_URL =
        "https://github.com/Sujal-GS/NMAMIT-Attendance-Tracker/releases"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NMAMITAttendanceTracker/$currentVersion")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null

                val json = JSONObject(body)
                if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
                    return@withContext null
                }

                val tagName = json.optString("tag_name", "").trim()
                if (tagName.isBlank()) return@withContext null

                val remoteNormalized = normalizeVersion(tagName)
                val currentNormalized = normalizeVersion(currentVersion)
                if (!isNewerVersion(remoteNormalized, currentNormalized)) {
                    return@withContext null
                }

                val releaseUrl = json.optString("html_url", RELEASES_PAGE_URL)
                    .ifBlank { RELEASES_PAGE_URL }
                val releaseTitle = json.optString("name", "")
                    .ifBlank { "NMAMIT Attendance Tracker $tagName" }
                val releaseNotes = json.optString("body", "").trim()

                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                AppUpdateInfo(
                    currentVersion = currentNormalized,
                    latestVersion = remoteNormalized,
                    tagName = tagName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    releaseUrl = releaseUrl,
                    apkDownloadUrl = apkUrl ?: releaseUrl
                )
            }
        }.getOrNull()
    }

    internal fun normalizeVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
    }

    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split('.').map { it.toIntOrNull() ?: 0 }
        if (remoteParts.isEmpty() || remoteParts.all { it == 0 }) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
