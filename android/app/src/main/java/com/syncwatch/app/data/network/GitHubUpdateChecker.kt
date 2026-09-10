package com.syncwatch.app.data.network

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.syncwatch.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val releaseUrl: String,
    val releaseNotes: String = ""
)

object GitHubUpdateChecker {
    private const val GITHUB_REPO = "haarryyyyyyy/streamtogether"
    val CURRENT_VERSION: String
        get() = "v${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersion(): String = CURRENT_VERSION

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "StreamTogether-Android")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateInfo(
                        hasUpdate = false,
                        latestVersion = CURRENT_VERSION,
                        releaseUrl = "https://github.com/$GITHUB_REPO/releases"
                    )
                }

                val bodyStr = response.body?.string() ?: ""
                val jsonObj = JsonParser.parseString(bodyStr).asJsonObject
                val tag = jsonObj.get("tag_name")?.asString ?: CURRENT_VERSION
                val htmlUrl = jsonObj.get("html_url")?.asString ?: "https://github.com/$GITHUB_REPO/releases"
                val body = jsonObj.get("body")?.asString ?: ""

                val cleanLatest = tag.trim().removePrefix("v").removePrefix("V")
                val cleanCurrent = CURRENT_VERSION.trim().removePrefix("v").removePrefix("V")

                val isNewer = isVersionNewer(cleanLatest, cleanCurrent)

                UpdateInfo(
                    hasUpdate = isNewer,
                    latestVersion = tag,
                    releaseUrl = htmlUrl,
                    releaseNotes = body
                )
            }
        } catch (e: Exception) {
            UpdateInfo(
                hasUpdate = false,
                latestVersion = CURRENT_VERSION,
                releaseUrl = "https://github.com/$GITHUB_REPO/releases"
            )
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}