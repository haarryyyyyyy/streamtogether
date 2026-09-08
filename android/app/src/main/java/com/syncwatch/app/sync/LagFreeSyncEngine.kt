package com.syncwatch.app.sync

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.syncwatch.app.data.models.RoomState
import com.syncwatch.app.data.models.SyncSettings
import com.syncwatch.app.data.models.SyncStatus
import com.syncwatch.app.data.models.SyncTelemetry
import com.syncwatch.app.data.network.ClockSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LagFreeSyncEngine(
    private val clockSyncManager: ClockSyncManager
) {
    private var player: Player? = null
    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _telemetry = MutableStateFlow(SyncTelemetry())
    val telemetry: StateFlow<SyncTelemetry> = _telemetry

    var settings: SyncSettings = SyncSettings()
    var isUserSeeking: Boolean = false
    @Volatile
    var isApplyingSync: Boolean = false

    fun attachPlayer(exoPlayer: Player) {
        this.player = exoPlayer
    }

    fun detachPlayer() {
        this.player = null
        stopSyncLoop()
    }

    fun startSyncLoop(roomStateFlow: StateFlow<RoomState>) {
        stopSyncLoop()
        syncJob = scope.launch {
            while (true) {
                val exo = player
                if (exo != null && !isUserSeeking) {
                    evaluateAndApplySync(exo, roomStateFlow.value)
                }
                delay(250) // 4Hz evaluation loop
            }
        }
    }

    fun stopSyncLoop() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun evaluateAndApplySync(exo: Player, room: RoomState) {
        if (room.roomId.isEmpty()) return

        val serverNow = clockSyncManager.currentServerTimeMs()
        val rtt = clockSyncManager.getRtt()
        val offset = clockSyncManager.getOffset()
        val currentPosSec = exo.currentPosition / 1000.0

        if (room.isHost && room.controlMode == "HOST_ONLY") {
            // Host is playback authority in HOST_ONLY mode
            _telemetry.value = SyncTelemetry(
                rttMs = rtt,
                clockOffsetMs = offset,
                driftMs = 0L,
                playbackSpeed = 1.0f,
                status = SyncStatus.IN_SYNC,
                targetPositionSec = currentPosSec,
                currentPositionSec = currentPosSec
            )
            return
        }

        isApplyingSync = true
        try {
            // Calculate expected target position (in seconds)
            val targetPosSec = if (room.isPlaying && room.anchorServerTime > 0) {
                val elapsedSec = (serverNow - room.anchorServerTime) / 1000.0
                Math.max(0.0, room.targetPositionSec + elapsedSec)
            } else {
                room.targetPositionSec
            }

            // If player is actively buffering frames after seek or network load, wait for STATE_READY
            if (exo.playbackState == Player.STATE_BUFFERING) {
                _telemetry.value = SyncTelemetry(
                    rttMs = rtt,
                    clockOffsetMs = offset,
                    driftMs = 0L,
                    playbackSpeed = 1.0f,
                    status = SyncStatus.BUFFERING,
                    targetPositionSec = targetPosSec,
                    currentPositionSec = currentPosSec
                )
                return
            }

            // Synchronize Play / Pause state
            if (room.isPlaying && !exo.playWhenReady) {
                exo.playWhenReady = true
            } else if (!room.isPlaying && exo.playWhenReady) {
                exo.playWhenReady = false
            }

            if (!room.isPlaying) {
                // Room is paused: Hard sync to exact pause position if drift > 250ms
                val pauseDriftMs = ((targetPosSec - currentPosSec) * 1000).toLong()
                if (Math.abs(pauseDriftMs) > 250 && targetPosSec > 0) {
                    exo.seekTo((targetPosSec * 1000).toLong())
                }
                _telemetry.value = SyncTelemetry(
                    rttMs = rtt,
                    clockOffsetMs = offset,
                    driftMs = pauseDriftMs,
                    playbackSpeed = 1.0f,
                    status = SyncStatus.IN_SYNC,
                    targetPositionSec = targetPosSec,
                    currentPositionSec = currentPosSec
                )
                return
            }

            // Current ExoPlayer playback position
            val driftMs = ((targetPosSec - currentPosSec) * 1000).toLong()
            val absDrift = Math.abs(driftMs)

            var newSpeed = 1.0f
            var status = SyncStatus.IN_SYNC

            if (absDrift <= 80) {
                // Case 1: In Sync (< 80ms)
                newSpeed = 1.0f
                status = SyncStatus.IN_SYNC
            } else if (absDrift <= 600) {
                // Case 2: Smooth imperceptible speed adjustment (80ms - 600ms)
                val adjustment = (driftMs.toFloat() / 10000.0f).coerceIn(-0.03f, 0.03f)
                newSpeed = 1.0f + adjustment
                status = if (driftMs > 0) SyncStatus.SPEEDING_UP else SyncStatus.SLOWING_DOWN
            } else {
                // Case 3: Macro drift / Seek (> 600ms) -> Instant Seek
                exo.seekTo((targetPosSec * 1000).toLong())
                newSpeed = 1.0f
                status = SyncStatus.SEEKING
            }

            // Apply playback speed to ExoPlayer
            if (Math.abs(exo.playbackParameters.speed - newSpeed) > 0.005f) {
                exo.playbackParameters = PlaybackParameters(newSpeed)
            }

            _telemetry.value = SyncTelemetry(
                rttMs = rtt,
                clockOffsetMs = offset,
                driftMs = driftMs,
                playbackSpeed = newSpeed,
                status = status,
                targetPositionSec = targetPosSec,
                currentPositionSec = currentPosSec
            )
        } finally {
            isApplyingSync = false
        }
    }
}
