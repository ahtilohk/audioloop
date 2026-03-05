package com.example.audioloop.ui

import org.junit.Test
import org.junit.Assert.assertEquals

class UtilsTest {
    @Test
    fun `formatDuration formats correctly`() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("01:00", formatDuration(60000L))
        assertEquals("01:15", formatDuration(75000L))
    }

    @Test
    fun `formatSessionTime formats correctly`() {
        assertEquals("00:00:00", formatSessionTime(0L))
        assertEquals("00:01:00", formatSessionTime(60000L))
        assertEquals("01:00:00", formatSessionTime(3600000L))
    }
}
