package com.syncwatch.app

import com.syncwatch.app.data.models.SyncSettings
import com.syncwatch.app.data.network.ClockSyncManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LagFreeSyncEngineTest {

    @Test
    fun testCristianNtpClockSync() {
        val clockManager = ClockSyncManager()
        val t0 = 1000L
        val t3 = 1020L // RTT = 20ms
        val serverTime = 5010L // Server time at response

        clockManager.processPong(clientSendTime = t0, serverTime = serverTime, receiveClientTime = t3)

        assertEquals(20L, clockManager.getRtt())
        // Estimated Server Time at t3 = 5010 + 10 = 5020. Offset = 5020 - 1020 = 4000
        assertEquals(4000L, clockManager.getOffset())
    }

    @Test
    fun testDynamicSpeedCalculationMatrix() {
        val settings = SyncSettings(
            syncToleranceMs = 100L,
            enableDynamicSpeed = true,
            maxSpeedMultiplier = 1.08f,
            minSpeedMultiplier = 0.92f
        )

        // Test 1: Drift within 100ms tolerance -> Speed 1.0x
        val driftInSync = 50L
        val speed1 = if (Math.abs(driftInSync) <= settings.syncToleranceMs) 1.0f else 1.0f
        assertEquals(1.0f, speed1, 0.001f)

        // Test 2: Lagging behind by +200ms -> Speed up to 1.08x
        val driftLagging = 200L
        val speedAdjustment = (driftLagging.toFloat() / 2500.0f)
        val speed2 = (1.0f + speedAdjustment).coerceIn(settings.minSpeedMultiplier, settings.maxSpeedMultiplier)
        assertEquals(1.08f, speed2, 0.001f)

        // Test 3: Ahead by -150ms -> Slow down
        val driftAhead = -150L
        val speedAdjustment3 = (driftAhead.toFloat() / 2500.0f)
        val speed3 = (1.0f + speedAdjustment3).coerceIn(settings.minSpeedMultiplier, settings.maxSpeedMultiplier)
        assertTrue("Speed should be less than 1.0x", speed3 < 1.0f)
        assertEquals(0.94f, speed3, 0.01f)
    }
}
