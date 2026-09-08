package com.syncwatch.app.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ClockSyncManager {

    private var serverOffsetMs: Long = 0L
    private var lastRttMs: Long = 0L

    private val _rttState = MutableStateFlow(0L)
    val rttState: StateFlow<Long> = _rttState

    private val _offsetState = MutableStateFlow(0L)
    val offsetState: StateFlow<Long> = _offsetState

    /**
     * Called when NTP_PONG is received from server.
     * Cristian's Algorithm:
     * RTT = t3 (receive client time) - t0 (send client time)
     * Estimated Server Time at t3 = serverTime + RTT / 2
     * Clock Offset O = Estimated Server Time - t3 = serverTime - (t3 + t0) / 2
     */
    fun processPong(clientSendTime: Long, serverTime: Long, receiveClientTime: Long = System.currentTimeMillis()) {
        val rtt = Math.max(1L, receiveClientTime - clientSendTime)
        val estimatedServerTimeAtReceive = serverTime + (rtt / 2L)
        val offset = estimatedServerTimeAtReceive - receiveClientTime

        // Exponential Moving Average filter to smooth network jitter
        serverOffsetMs = if (serverOffsetMs == 0L) offset else ((serverOffsetMs * 0.7) + (offset * 0.3)).toLong()
        lastRttMs = rtt

        _rttState.value = lastRttMs
        _offsetState.value = serverOffsetMs
    }

    /**
     * Converts local device timestamp to synchronized Server Reference Time
     */
    fun currentServerTimeMs(): Long {
        return System.currentTimeMillis() + serverOffsetMs
    }

    fun getRtt(): Long = lastRttMs
    fun getOffset(): Long = serverOffsetMs
}
