package com.syncwatch.app.data.models

data class RoomState(
    val roomId: String = "",
    val pin: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val isHost: Boolean = false,
    val mediaUrl: String = "",
    val mediaTitle: String = "No Media Loaded",
    val isPlaying: Boolean = false,
    val targetPositionSec: Double = 0.0,
    val anchorServerTime: Long = 0L,
    val controlMode: String = "HOST_ONLY", // "HOST_ONLY" or "SHARED"
    val isLocked: Boolean = false,
    val participants: List<Participant> = emptyList(),
    val peerCount: Int = 1
)

data class Participant(
    val guestId: String,
    val displayName: String,
    val isHost: Boolean = false,
    val isBuffering: Boolean = false,
    val isOnline: Boolean = true,
    val joinedAt: Long = 0L
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String = "",
    val sender: String = "",
    val isHost: Boolean = false,
    val isSystem: Boolean = false,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class RecentRoom(
    val roomCode: String,
    val mediaTitle: String,
    val lastJoinedTimestamp: Long
)

data class MediaCatalogItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String,
    val durationSec: Int,
    val license: String
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    IN_SYNC
}
