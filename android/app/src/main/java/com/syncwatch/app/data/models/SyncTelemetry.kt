package com.syncwatch.app.data.models

enum class SyncStatus {
    IN_SYNC,
    SPEEDING_UP,
    SLOWING_DOWN,
    SEEKING,
    BUFFERING
}

data class SyncTelemetry(
    val rttMs: Long = 0L,
    val clockOffsetMs: Long = 0L,
    val driftMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val status: SyncStatus = SyncStatus.IN_SYNC,
    val targetPositionSec: Double = 0.0,
    val currentPositionSec: Double = 0.0
)

data class SyncSettings(
    val syncToleranceMs: Long = 100L,        // Below this, no speed change
    val enableDynamicSpeed: Boolean = true,  // True = lag-free speed adjustment, False = force hard seek
    val maxSpeedMultiplier: Float = 1.08f,   // Max speed up factor
    val minSpeedMultiplier: Float = 0.92f    // Max slow down factor
)
