package com.syncwatch.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syncwatch.app.data.models.RecentRoom
import java.util.UUID

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "watchtogether_prefs"
        private const val KEY_GUEST_ID = "guest_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_RECENT_ROOMS = "recent_rooms"
        
        // Default embedded production server endpoint (Cloudflare SSL Tunnel)
        const val DEFAULT_SERVER_URL = "wss://legacy-space-packages-projection.trycloudflare.com"
    }

    /**
     * Retrieves existing anonymous Guest ID or creates and persists a new one.
     */
    fun getOrCreateGuestId(): String {
        var guestId = prefs.getString(KEY_GUEST_ID, null)
        if (guestId.isNullOrEmpty()) {
            val randomPart = UUID.randomUUID().toString().replace("-", "").take(8)
            guestId = "gst_$randomPart"
            prefs.edit().putString(KEY_GUEST_ID, guestId).apply()
        }
        return guestId
    }

    /**
     * Stored display name.
     */
    fun getDisplayName(): String {
        return prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
    }

    fun saveDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    /**
     * Stored Server URL.
     */
    fun getServerUrl(): String {
        val saved = prefs.getString(KEY_SERVER_URL, null)
        return if (saved.isNullOrEmpty() || saved.contains("10.0.2.2") || saved.contains("localhost") || saved.contains("duckdns")) {
            DEFAULT_SERVER_URL
        } else {
            saved
        }
    }

    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    /**
     * Converts WebSocket URL (ws:// or wss://) to HTTP base URL (http:// or https://)
     */
    fun getHttpBaseUrl(): String {
        val serverUrl = getServerUrl().trim()
        val normalized = when {
            serverUrl.startsWith("ws://", ignoreCase = true) -> serverUrl.replaceFirst("ws://", "http://", ignoreCase = true)
            serverUrl.startsWith("wss://", ignoreCase = true) -> serverUrl.replaceFirst("wss://", "https://", ignoreCase = true)
            serverUrl.startsWith("http://", ignoreCase = true) || serverUrl.startsWith("https://", ignoreCase = true) -> serverUrl
            else -> "http://$serverUrl"
        }
        return normalized.trimEnd('/')
    }

    /**
     * Recently joined rooms history (stored locally).
     */
    fun getRecentRooms(): List<RecentRoom> {
        val json = prefs.getString(KEY_RECENT_ROOMS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<RecentRoom>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentRoom(roomCode: String, mediaTitle: String) {
        val currentList = getRecentRooms().filterNot { it.roomCode.equals(roomCode, ignoreCase = true) }.toMutableList()
        currentList.add(
            0,
            RecentRoom(
                roomCode = roomCode.uppercase().trim(),
                mediaTitle = mediaTitle,
                lastJoinedTimestamp = System.currentTimeMillis()
            )
        )
        // Keep only last 10 rooms
        val trimmed = currentList.take(10)
        val json = gson.toJson(trimmed)
        prefs.edit().putString(KEY_RECENT_ROOMS, json).apply()
    }

    fun removeRecentRoom(roomCode: String) {
        val filtered = getRecentRooms().filterNot { it.roomCode.equals(roomCode, ignoreCase = true) }
        prefs.edit().putString(KEY_RECENT_ROOMS, gson.toJson(filtered)).apply()
    }

    fun clearRecentRooms() {
        prefs.edit().remove(KEY_RECENT_ROOMS).apply()
    }
}
