package com.syncwatch.app.data.models

sealed class SyncPacket {
    data class NtpPing(
        val type: String = "NTP_PING",
        val clientTime: Long
    )

    data class CreateRoom(
        val type: String = "CREATE_ROOM",
        val guestId: String,
        val displayName: String,
        val mediaUrl: String,
        val mediaTitle: String
    )

    data class JoinRoom(
        val type: String = "JOIN_ROOM",
        val roomCode: String,
        val guestId: String,
        val displayName: String
    )

    data class ActionPlay(
        val type: String = "ACTION_PLAY",
        val positionSec: Double
    )

    data class ActionPause(
        val type: String = "ACTION_PAUSE",
        val positionSec: Double
    )

    data class ActionSeek(
        val type: String = "ACTION_SEEK",
        val positionSec: Double
    )

    data class ChangeMedia(
        val type: String = "CHANGE_MEDIA",
        val mediaUrl: String,
        val mediaTitle: String
    )

    data class SetControlMode(
        val type: String = "SET_CONTROL_MODE",
        val mode: String // "HOST_ONLY" or "SHARED"
    )

    data class SetRoomLock(
        val type: String = "SET_ROOM_LOCK",
        val isLocked: Boolean
    )

    data class KickParticipant(
        val type: String = "KICK_PARTICIPANT",
        val targetGuestId: String
    )

    data class BufferingState(
        val type: String = "BUFFERING_STATE",
        val isBuffering: Boolean
    )

    data class SendChat(
        val type: String = "SEND_CHAT",
        val text: String
    )
}
