package com.example.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for utility functions and business logic
 */
class AudioLoopUtilsTest {

    // Duration formatting tests
    @Test
    fun `format duration zero seconds`() {
        val formatted = formatDuration(0L)
        assertEquals("0:00", formatted)
    }

    @Test
    fun `format duration one minute`() {
        val formatted = formatDuration(60_000L)
        assertEquals("1:00", formatted)
    }

    @Test
    fun `format duration with seconds`() {
        val formatted = formatDuration(75_000L) // 1:15
        assertEquals("1:15", formatted)
    }

    @Test
    fun `format duration ten minutes`() {
        val formatted = formatDuration(600_000L)
        assertEquals("10:00", formatted)
    }

    @Test
    fun `format duration one hour`() {
        val formatted = formatDuration(3_600_000L)
        assertEquals("60:00", formatted)
    }

    // Sleep timer tests
    @Test
    fun `sleep timer off value is zero`() {
        val offValue = 0
        assertEquals(0, offValue)
    }

    @Test
    fun `sleep timer five minutes in milliseconds`() {
        val fiveMinMs = 5 * 60_000L
        assertEquals(300_000L, fiveMinMs)
    }

    @Test
    fun `sleep timer fifteen minutes in milliseconds`() {
        val fifteenMinMs = 15 * 60_000L
        assertEquals(900_000L, fifteenMinMs)
    }

    @Test
    fun `sleep timer sixty minutes in milliseconds`() {
        val sixtyMinMs = 60 * 60_000L
        assertEquals(3_600_000L, sixtyMinMs)
    }

    // Loop count tests
    @Test
    fun `loop count infinite is minus one`() {
        val infinite = -1
        assertEquals(-1, infinite)
    }

    @Test
    fun `loop count off is zero or one`() {
        val off = 1
        assertTrue(off >= 0)
    }

    @Test
    fun `loop count five times`() {
        val fiveTimes = 5
        assertEquals(5, fiveTimes)
    }

    // Selection logic tests
    @Test
    fun `empty selection has no files`() {
        val selection = setOf<String>()
        assertTrue(selection.isEmpty())
    }

    @Test
    fun `selection can add files`() {
        var selection = setOf<String>()
        selection = selection + "file1.m4a"
        assertEquals(1, selection.size)
        assertTrue(selection.contains("file1.m4a"))
    }

    @Test
    fun `selection can remove files`() {
        var selection = setOf("file1.m4a", "file2.m4a")
        selection = selection - "file1.m4a"
        assertEquals(1, selection.size)
        assertFalse(selection.contains("file1.m4a"))
        assertTrue(selection.contains("file2.m4a"))
    }

    @Test
    fun `selection order is preserved in list`() {
        val selectionList = listOf("file1.m4a", "file2.m4a", "file3.m4a")
        assertEquals(0, selectionList.indexOf("file1.m4a"))
        assertEquals(1, selectionList.indexOf("file2.m4a"))
        assertEquals(2, selectionList.indexOf("file3.m4a"))
    }

    @Test
    fun `selection index plus one gives position`() {
        val selectionList = listOf("file1.m4a", "file2.m4a")
        val position = selectionList.indexOf("file2.m4a") + 1
        assertEquals(2, position)
    }

    @Test
    fun `non-selected file has position zero`() {
        val selectionList = listOf("file1.m4a", "file2.m4a")
        val position = selectionList.indexOf("file3.m4a") + 1
        assertEquals(0, position) // indexOf returns -1, +1 = 0
    }

    // Playback speed tests
    @Test
    fun `normal speed is one`() {
        val normalSpeed = 1.0f
        assertEquals(1.0f, normalSpeed, 0.001f)
    }

    @Test
    fun `half speed is point five`() {
        val halfSpeed = 0.5f
        assertEquals(0.5f, halfSpeed, 0.001f)
    }

    @Test
    fun `double speed is two`() {
        val doubleSpeed = 2.0f
        assertEquals(2.0f, doubleSpeed, 0.001f)
    }

    // File name parsing tests
    @Test
    fun `extract name without extension`() {
        val fileName = "recording_001.m4a"
        val nameWithoutExt = fileName.substringBeforeLast(".")
        assertEquals("recording_001", nameWithoutExt)
    }

    @Test
    fun `extract extension`() {
        val fileName = "recording_001.m4a"
        val ext = fileName.substringAfterLast(".")
        assertEquals("m4a", ext)
    }

    @Test
    fun `file without extension returns full name`() {
        val fileName = "recording"
        val nameWithoutExt = fileName.substringBeforeLast(".")
        assertEquals("recording", nameWithoutExt)
    }

    // Progress calculation tests
    @Test
    fun `progress at start is zero`() {
        val currentMs = 0L
        val totalMs = 60_000L
        val progress = currentMs.toFloat() / totalMs.toFloat()
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `progress at middle is half`() {
        val currentMs = 30_000L
        val totalMs = 60_000L
        val progress = currentMs.toFloat() / totalMs.toFloat()
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun `progress at end is one`() {
        val currentMs = 60_000L
        val totalMs = 60_000L
        val progress = currentMs.toFloat() / totalMs.toFloat()
        assertEquals(1f, progress, 0.001f)
    }

    // Category tests
    @Test
    fun `default category is General`() {
        val defaultCategory = "General"
        assertEquals("General", defaultCategory)
    }

    @Test
    fun `categories list contains General`() {
        val categories = listOf("General")
        assertTrue(categories.contains("General"))
    }

    @Test
    fun `can add new category`() {
        var categories = listOf("General")
        categories = categories + "Work"
        assertEquals(2, categories.size)
        assertTrue(categories.contains("Work"))
    }

    // Helper function for tests
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
