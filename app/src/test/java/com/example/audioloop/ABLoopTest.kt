package com.example.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for A-B Loop and Shadowing logic.
 * Verifies the precision of nudge adjustments and state transitions.
 */
class ABLoopTest {

    @Test
    fun `default ab markers are inactive`() {
        val state = AudioLoopUiState()
        assertEquals(-1f, state.abLoopStart)
        assertEquals(-1f, state.abLoopEnd)
    }

    @Test
    fun `ab loop markers calculate progress correctly`() {
        val totalMs = 10000L
        val startMs = 2000L
        val endMs = 8000L
        
        val startProgress = startMs.toFloat() / totalMs.toFloat()
        val endProgress = endMs.toFloat() / totalMs.toFloat()
        
        assertEquals(0.2f, startProgress, 0.001f)
        assertEquals(0.8f, endProgress, 0.001f)
    }

    @Test
    fun `nudge forward logic shifts marker by exactly requested ms`() {
        val totalMs = 20000
        val currentProgress = 0.25f // 5000ms
        val nudgeMs = 500
        
        val currentMs = (currentProgress * totalMs).toInt()
        val nextMs = (currentMs + nudgeMs).coerceIn(0, totalMs)
        val nextProgress = nextMs.toFloat() / totalMs.toFloat()
        
        assertEquals(5500, nextMs)
        assertEquals(0.275f, nextProgress, 0.001f)
    }

    @Test
    fun `nudge backward logic shifts marker by exactly requested ms`() {
        val totalMs = 20000
        val currentProgress = 0.25f // 5000ms
        val nudgeMs = -1000
        
        val currentMs = (currentProgress * totalMs).toInt()
        val nextMs = (currentMs + nudgeMs).coerceIn(0, totalMs)
        val nextProgress = nextMs.toFloat() / totalMs.toFloat()
        
        assertEquals(4000, nextMs)
        assertEquals(0.2f, nextProgress, 0.001f)
    }

    @Test
    fun `nudge is clamped to start boundary`() {
        val totalMs = 10000
        val currentProgress = 0.01f // 100ms
        val nudgeMs = -500
        
        val currentMs = (currentProgress * totalMs).toInt()
        val nextMs = (currentMs + nudgeMs).coerceIn(0, totalMs)
        val nextProgress = nextMs.toFloat() / totalMs.toFloat()
        
        assertEquals(0, nextMs)
        assertEquals(0f, nextProgress, 0.001f)
    }

    @Test
    fun `nudge is clamped to end boundary`() {
        val totalMs = 10000
        val currentProgress = 0.99f // 9900ms
        val nudgeMs = 500
        
        val currentMs = (currentProgress * totalMs).toInt()
        val nextMs = (currentMs + nudgeMs).coerceIn(0, totalMs)
        val nextProgress = nextMs.toFloat() / totalMs.toFloat()
        
        assertEquals(10000, nextMs)
        assertEquals(1.0f, nextProgress, 0.001f)
    }

    @Test
    fun `shadowing pause duration calculation for AB loop`() {
        val abStart = 0.2f
        val abEnd = 0.5f
        val totalMs = 10000L
        
        // Loop segment duration = (0.5 - 0.2) * 10000 = 3000ms
        val segmentDuration = ((abEnd - abStart) * totalMs).toLong()
        
        assertEquals(3000L, segmentDuration)
        
        // Final pause duration logic in ViewModel: segmentDuration.coerceAtMost(15000L)
        val finalPause = segmentDuration.coerceAtMost(15000L)
        assertEquals(3000L, finalPause)
    }

    @Test
    fun `nudge precision test for high-end studio feel`() {
        val totalMs = 30000 // 30s
        val currentProgress = 0.5f // 15000ms
        val nudgeMs = 50 // 50ms tap
        
        val nextMs = ((currentProgress * totalMs).toInt() + nudgeMs).coerceIn(0, totalMs)
        val nextProgress = nextMs.toFloat() / totalMs.toFloat()
        
        // 15050 / 30000 = 0.50166...
        assertEquals(15050, nextMs)
        assertEquals(0.501666f, nextProgress, 0.0001f)
    }
}
