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

    private var lastSeekTimestampMs: Long = 0L

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
                delay(200) // 5Hz evaluation loop
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
        val nowMs = System.currentTimeMillis()

        if (room.isHost && room.controlMode == "HOST_ONLY") {
            // Host follows room playback state during seek buffering gate or pause
            if (!room.isPlaying && exo.playWhenReady) {
                exo.playWhenReady = false
            } else if (room.isPlaying && !exo.playWhenReady && !isUserSeeking) {
                exo.playWhenReady = true
            }

            _telemetry.value = SyncTelemetry(
                rttMs = rtt,
                clockOffsetMs = offset,
                driftMs = 0L,
                playbackSpeed = 1.0f,
                status = if (exo.playbackState == Player.STATE_BUFFERING) SyncStatus.BUFFERING else SyncStatus.IN_SYNC,
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
                // Room is paused or in seek buffering gate: Hard sync to exact pause position if drift > 250ms
                val pauseDriftMs = ((targetPosSec - currentPosSec) * 1000).toLong()
                if (Math.abs(pauseDriftMs) > 250 && targetPosSec >= 0 && (nowMs - lastSeekTimestampMs > 1200L)) {
                    lastSeekTimestampMs = nowMs
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
            } else if (absDrift <= 700) {
                // Case 2: Smooth imperceptible speed adjustment (80ms - 700ms)
                val adjustment = (driftMs.toFloat() / 10000.0f).coerceIn(-0.04f, 0.04f)
                newSpeed = 1.0f + adjustment
                status = if (driftMs > 0) SyncStatus.SPEEDING_UP else SyncStatus.SLOWING_DOWN
            } else {
                // Case 3: Macro drift (> 700ms) -> Seek only if not recently sought
                if (nowMs - lastSeekTimestampMs > 2200L) {
                    lastSeekTimestampMs = nowMs
                    exo.seekTo((targetPosSec * 1000).toLong())
                    newSpeed = 1.0f
                    status = SyncStatus.SEEKING
                } else {
                    // Within cooldown window: ramp speed without interrupting the buffer
                    val adjustment = if (driftMs > 0) 0.05f else -0.05f
                    newSpeed = 1.0f + adjustment
                    status = if (driftMs > 0) SyncStatus.SPEEDING_UP else SyncStatus.SLOWING_DOWN
                }
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
