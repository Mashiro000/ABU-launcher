package com.limi.tvdesktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHealthMonitorTest {
    @Test
    fun bufferedVideoWithoutFirstFrameSwitchesEngine() {
        val monitor = PlaybackHealthMonitor(startedAtMs = 1_000L)
        assertEquals(
            PlaybackHealthMonitor.Decision.SWITCH_ENGINE,
            monitor.sample(0L, 5_000L, isBuffering = false, hasVideo = true, atMs = 10_000L),
        )
    }

    @Test
    fun emptyBufferIsClassifiedAsSlowNetwork() {
        val monitor = PlaybackHealthMonitor(startedAtMs = 1_000L)
        assertEquals(
            PlaybackHealthMonitor.Decision.WAITING_FOR_NETWORK,
            monitor.sample(0L, 0L, isBuffering = true, hasVideo = true, atMs = 22_000L),
        )
    }

    @Test
    fun playbackProgressRemainsHealthy() {
        val monitor = PlaybackHealthMonitor(startedAtMs = 1_000L)
        monitor.onFirstFrame(2_000L)
        assertEquals(
            PlaybackHealthMonitor.Decision.HEALTHY,
            monitor.sample(4_000L, 12_000L, isBuffering = false, hasVideo = true, atMs = 8_000L),
        )
    }
}
