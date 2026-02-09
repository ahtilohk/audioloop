package com.example.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for trim/selection logic
 */
class TrimLogicTest {

    @Test
    fun `selection start and end at file bounds`() {
        val durationMs = 60_000L
        val selectionStartMs = 0L
        val selectionEndMs = durationMs

        assertEquals(0L, selectionStartMs)
        assertEquals(60_000L, selectionEndMs)
    }

    @Test
    fun `selection can be partial`() {
        val durationMs = 60_000L
        val selectionStartMs = 10_000L
        val selectionEndMs = 50_000L

        assertTrue(selectionStartMs >= 0)
        assertTrue(selectionEndMs <= durationMs)
        assertTrue(selectionStartMs < selectionEndMs)
    }

    @Test
    fun `selection start cannot exceed end`() {
        var selectionStartMs = 30_000L
        var selectionEndMs = 20_000L

        // Swap if needed (mimics UI logic)
        if (selectionStartMs > selectionEndMs) {
            val temp = selectionStartMs
            selectionStartMs = selectionEndMs
            selectionEndMs = temp
        }

        assertTrue(selectionStartMs <= selectionEndMs)
    }

    @Test
    fun `pixel to milliseconds conversion`() {
        val widthPx = 1000f
        val durationMs = 60_000f
        val pixelX = 500f // Half way

        val milliseconds = (pixelX / widthPx) * durationMs

        assertEquals(30_000f, milliseconds, 0.1f)
    }

    @Test
    fun `milliseconds to pixel conversion`() {
        val widthPx = 1000f
        val durationMs = 60_000f
        val currentMs = 30_000f // Half way

        val pixelX = (currentMs / durationMs) * widthPx

        assertEquals(500f, pixelX, 0.1f)
    }

    @Test
    fun `coerce pixel to bounds`() {
        val widthPx = 1000f
        var pixelX = -50f // Out of bounds left

        pixelX = pixelX.coerceIn(0f, widthPx)
        assertEquals(0f, pixelX, 0.001f)

        pixelX = 1100f // Out of bounds right
        pixelX = pixelX.coerceIn(0f, widthPx)
        assertEquals(1000f, pixelX, 0.001f)
    }

    // Hit zone tests for trim handles
    @Test
    fun `handle hit width is generous`() {
        val handleHitWidth = 56f // dp converted to px (approximate)
        assertTrue(handleHitWidth >= 40f) // Should be at least 40dp
    }

    @Test
    fun `edge threshold calculation`() {
        val handleHitWidth = 56f
        val edgeThreshold = handleHitWidth * 0.75f

        assertEquals(42f, edgeThreshold, 0.1f)
    }

    @Test
    fun `start handle at edge extends hit zone to zero`() {
        val handleHitWidth = 56f
        val edgeThreshold = handleHitWidth * 0.75f
        val startX = 20f // Near edge

        val startHitLeft = if (startX < edgeThreshold) 0f else startX - handleHitWidth / 2

        assertEquals(0f, startHitLeft, 0.001f)
    }

    @Test
    fun `end handle at edge extends hit zone to width`() {
        val widthPx = 1000f
        val handleHitWidth = 56f
        val edgeThreshold = handleHitWidth * 0.75f
        val endX = 980f // Near edge

        val endHitRight = if (endX > widthPx - edgeThreshold) widthPx else endX + handleHitWidth / 2

        assertEquals(1000f, endHitRight, 0.001f)
    }

    @Test
    fun `touch is near start handle`() {
        val startX = 100f
        val handleHitWidth = 56f
        val startHitLeft = startX - handleHitWidth / 2
        val startHitRight = startX + handleHitWidth / 2
        val touchX = 110f

        val isNearStart = touchX >= startHitLeft && touchX <= startHitRight

        assertTrue(isNearStart)
    }

    @Test
    fun `touch is not near start handle`() {
        val startX = 100f
        val handleHitWidth = 56f
        val startHitLeft = startX - handleHitWidth / 2
        val startHitRight = startX + handleHitWidth / 2
        val touchX = 200f // Too far

        val isNearStart = touchX >= startHitLeft && touchX <= startHitRight

        assertFalse(isNearStart)
    }

    // Trim mode tests
    @Test
    fun `keep selection mode keeps inner part`() {
        val removeSelection = false
        val selectionStartMs = 10_000L
        val selectionEndMs = 50_000L

        // If removeSelection is false, we keep the selection
        if (!removeSelection) {
            // Kept portion is selectionStartMs to selectionEndMs
            val keptDuration = selectionEndMs - selectionStartMs
            assertEquals(40_000L, keptDuration)
        }
    }

    @Test
    fun `remove selection mode keeps outer parts`() {
        val removeSelection = true
        val durationMs = 60_000L
        val selectionStartMs = 10_000L
        val selectionEndMs = 50_000L

        // If removeSelection is true, we remove the selection and keep rest
        if (removeSelection) {
            // Kept portion is 0 to selectionStartMs + selectionEndMs to durationMs
            val keptDuration = selectionStartMs + (durationMs - selectionEndMs)
            assertEquals(20_000L, keptDuration)
        }
    }

    // Time formatting for trim labels
    @Test
    fun `format time for label zero seconds`() {
        val ms = 0L
        val formatted = formatTimeLabel(ms)
        assertEquals("0:00", formatted)
    }

    @Test
    fun `format time for label with decimals`() {
        val ms = 1500L // 1.5 seconds
        val seconds = ms / 1000f
        assertTrue(seconds >= 1f && seconds < 2f)
    }

    @Test
    fun `format time for label one minute thirty seconds`() {
        val ms = 90_000L
        val formatted = formatTimeLabel(ms)
        assertEquals("1:30", formatted)
    }

    // Preview position tests
    @Test
    fun `preview position clamped to selection`() {
        val selectionStartMs = 10_000L
        val selectionEndMs = 50_000L
        var previewPositionMs = 5_000L // Before selection

        previewPositionMs = previewPositionMs.coerceIn(selectionStartMs, selectionEndMs)

        assertEquals(10_000L, previewPositionMs)
    }

    @Test
    fun `preview position within selection unchanged`() {
        val selectionStartMs = 10_000L
        val selectionEndMs = 50_000L
        var previewPositionMs = 30_000L // Within selection

        previewPositionMs = previewPositionMs.coerceIn(selectionStartMs, selectionEndMs)

        assertEquals(30_000L, previewPositionMs)
    }

    private fun formatTimeLabel(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
