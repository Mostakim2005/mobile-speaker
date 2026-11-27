package com.example.audiobtbridge.latency

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyManagerTest {

    @Test
    fun `test low latency mode returns small buffer and 96kbps`() {
        val manager = LatencyManager()
        val config = manager.getConfigForMode(LatencyMode.LOW_LATENCY)
        assertEquals(2, config.bufferSize)
        assertEquals(96000, config.bitrate)
        assertEquals(5, config.complexity)
    }

    @Test
    fun `test balance mode returns medium buffer and 196kbps`() {
        val manager = LatencyManager()
        val config = manager.getConfigForMode(LatencyMode.BALANCE)
        assertEquals(4, config.bufferSize)
        assertEquals(196000, config.bitrate)
        assertEquals(8, config.complexity)
    }

    @Test
    fun `test high quality and best quality modes scale buffer and bitrate`() {
        val manager = LatencyManager()
        val high = manager.getConfigForMode(LatencyMode.HIGH_QUALITY)
        val best = manager.getConfigForMode(LatencyMode.BEST_QUALITY)
        assertEquals(6, high.bufferSize)
        assertEquals(256000, high.bitrate)
        assertEquals(8, best.bufferSize)
        assertEquals(320000, best.bitrate)
    }
}
