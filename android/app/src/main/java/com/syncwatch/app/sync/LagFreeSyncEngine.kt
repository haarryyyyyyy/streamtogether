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
                if (exo != null) {
                    evaluateAndApplySync(exo, roomStateFlow.value)
                }
                delay(250) // High-frequency 4Hz evaluation loop
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

        // Calculate expected target position (in seconds)
        val targetPosSec = if (room.isPlaying && room.anchorServerTime > 0) {
            val elapsedSec = (serverNow - room.anchorServerTime) / 1000.0
            Math.max(0.0, room.targetPositionSec + elapsedSec)
        } else {
            room.targetPositionSec
        }

        // Handle Play/Pause synchronization state
        if (room.isPlaying && !exo.isPlaying && exo.playbackState == Player.STATE_READY) {
            exo.play()
        } else if (!room.isPlaying && exo.isPlaying) {
            exo.pause()
        }

        if (!room.isPlaying) {
            // Room is paused: Hard sync to exact pause position if drift is noticeable
            val currentPosSec = exo.currentPosition / 1000.0
            val pauseDriftMs = ((targetPosSec - currentPosSec) * 1000).toLong()
            if (Math.abs(pauseDriftMs) > 300) {
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
        val currentPosSec = exo.currentPosition / 1000.0
        val driftMs = ((targetPosSec - currentPosSec) * 1000).toLong()

        var newSpeed = 1.0f
        var status = SyncStatus.IN_SYNC

        val absDrift = Math.abs(driftMs)

        if (absDrift <= settings.syncToleranceMs) {
            // Case 1: In Sync! No adjustment needed.
            newSpeed = 1.0f
            status = SyncStatus.IN_SYNC
        } else if (settings.enableDynamicSpeed && absDrift <= 1500) {
            // Case 2: LAG-FREE DYNAMIC ADJUSTMENT (50ms - 1500ms drift)
            // Gently adjust playback speed proportional to drift
            // E.g., 200ms behind -> speed = 1.0 + (200 / 2500) = 1.08x
            val adjustment = (driftMs.toFloat() / 2500.0f)
            newSpeed = (1.0f + adjustment).coerceIn(settings.minSpeedMultiplier, settings.maxSpeedMultiplier)
            status = if (driftMs > 0) SyncStatus.SPEEDING_UP else SyncStatus.SLOWING_DOWN
        } else {
            // Case 3: Large Drift (> 1500ms) -> Hard Seek to sync point
            exo.seekTo((targetPosSec * 1000).toLong())
            newSpeed = 1.0f
            status = SyncStatus.SEEKING
        }

        // Apply updated playback speed to ExoPlayer if modified
        if (Math.abs(exo.playbackParameters.speed - newSpeed) > 0.01f) {
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
    }
}
