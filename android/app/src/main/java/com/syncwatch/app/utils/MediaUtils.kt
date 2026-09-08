package com.syncwatch.app.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.net.URLDecoder

object MediaUtils {

    /**
     * Extracts a clean, human-readable movie title from a stream URL.
     * E.g. "https://domain.com/streams/Avengers_Endgame_2019_1080p.mp4?auth=xyz"
     * -> "Avengers Endgame 2019"
     */
    fun extractTitleFromUrl(url: String): String {
        if (url.isBlank()) return "Movie Stream"

        try {
            // Remove query parameters
            val cleanUrl = url.split("?").firstOrNull() ?: url
            val decoded = URLDecoder.decode(cleanUrl, "UTF-8")
            val lastSegment = decoded.substringAfterLast("/").trim()

            if (lastSegment.isBlank()) return "Live Stream"

            // Remove file extension (.mp4, .m3u8, .mkv, .ts, .mov, .webm, .avi, etc.)
            val nameWithoutExt = lastSegment.replace(Regex("\\.(mp4|m3u8|mkv|webm|avi|mov|ts|mpd|flv)$", RegexOption.IGNORE_CASE), "")

            // Replace separators with spaces
            var formatted = nameWithoutExt.replace(Regex("[_\\-\\.]+"), " ").trim()

            // Remove common release junk tags (e.g. 1080p, 720p, 4k, BluRay, x264, HDR, WEB-DL)
            formatted = formatted.replace(Regex("(?i)\\b(1080p|720p|480p|2160p|4k|hdr|bluray|web-dl|webrip|x264|x265|hevc|aac|dts)\\b"), "").trim()

            // Clean up multi-spaces
            formatted = formatted.replace(Regex("\\s+"), " ").trim()

            // Capitalize words
            val capitalized = formatted.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            return if (capitalized.isNotBlank()) capitalized else "Movie Stream"
        } catch (e: Exception) {
            return "Movie Stream"
        }
    }

    /**
     * Retrieves the file display name from an Android content:// URI.
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        val rawName = result ?: "Local Video"
        return rawName.replace(Regex("\\.(mp4|mkv|webm|avi|mov|ts)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_\\-]+"), " ").trim()
    }
}
