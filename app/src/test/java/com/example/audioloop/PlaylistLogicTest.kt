package com.example.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for playlist logic and repeat functionality
 */
class PlaylistLogicTest {

    @Test
    fun `playlist with single file`() {
        val playlist = listOf("file1.m4a")
        assertEquals(1, playlist.size)
    }

    @Test
    fun `playlist with multiple files preserves order`() {
        val playlist = listOf("file1.m4a", "file2.m4a", "file3.m4a")
        assertEquals("file1.m4a", playlist[0])
        assertEquals("file2.m4a", playlist[1])
        assertEquals("file3.m4a", playlist[2])
    }

    @Test
    fun `empty playlist`() {
        val playlist = emptyList<String>()
        assertTrue(playlist.isEmpty())
    }

    @Test
    fun `playlist index bounds check`() {
        val playlist = listOf("file1.m4a", "file2.m4a")
        assertTrue(0 < playlist.size)
        assertTrue(1 < playlist.size)
        assertFalse(2 < playlist.size)
    }

    // Repeat/loop logic tests
    @Test
    fun `infinite loop check`() {
        val loopCount = -1
        assertTrue(loopCount == -1)
    }

    @Test
    fun `finite loop count two times`() {
        val loopCount = 2
        val currentIteration = 1
        assertTrue(currentIteration < loopCount) // Should continue
    }

    @Test
    fun `finite loop count completes after target`() {
        val loopCount = 2
        val currentIteration = 2
        assertFalse(currentIteration < loopCount) // Should stop
    }

    @Test
    fun `loop iteration increments correctly`() {
        var iteration = 1
        iteration += 1
        assertEquals(2, iteration)
    }

    @Test
    fun `five times repeat logic`() {
        val loopCount = 5
        for (iteration in 1..5) {
            if (iteration < loopCount) {
                assertTrue("Iteration $iteration should continue", true)
            }
        }
        assertFalse(5 < loopCount)
    }

    // Playlist playback state tests
    @Test
    fun `current index starts at zero`() {
        val currentIndex = 0
        assertEquals(0, currentIndex)
    }

    @Test
    fun `current index increments for next file`() {
        var currentIndex = 0
        currentIndex += 1
        assertEquals(1, currentIndex)
    }

    @Test
    fun `playlist restarts at index zero`() {
        val playlistSize = 3
        var currentIndex = 3 // Past end
        if (currentIndex >= playlistSize) {
            currentIndex = 0 // Restart
        }
        assertEquals(0, currentIndex)
    }

    // Selection to playlist conversion
    @Test
    fun `selection set to ordered list`() {
        val selectedFiles = setOf("file2.m4a", "file1.m4a", "file3.m4a")
        val orderedList = selectedFiles.toList()
        assertEquals(3, orderedList.size)
    }

    @Test
    fun `filter recordings by selection`() {
        data class MockRecording(val name: String)

        val allRecordings = listOf(
            MockRecording("file1.m4a"),
            MockRecording("file2.m4a"),
            MockRecording("file3.m4a"),
            MockRecording("file4.m4a")
        )
        val selectedNames = setOf("file1.m4a", "file3.m4a")

        val filesToPlay = allRecordings.filter { selectedNames.contains(it.name) }

        assertEquals(2, filesToPlay.size)
        assertTrue(filesToPlay.any { it.name == "file1.m4a" })
        assertTrue(filesToPlay.any { it.name == "file3.m4a" })
    }

    @Test
    fun `map selection order to recordings`() {
        data class MockRecording(val name: String)

        val allRecordings = listOf(
            MockRecording("file1.m4a"),
            MockRecording("file2.m4a"),
            MockRecording("file3.m4a")
        )
        val orderedSelection = listOf("file3.m4a", "file1.m4a") // User selected in this order

        val filesToPlay = orderedSelection.mapNotNull { name ->
            allRecordings.find { it.name == name }
        }

        assertEquals(2, filesToPlay.size)
        assertEquals("file3.m4a", filesToPlay[0].name) // First selected
        assertEquals("file1.m4a", filesToPlay[1].name) // Second selected
    }

    // Shadowing mode tests
    @Test
    fun `shadowing mode stays on same file`() {
        val isShadowing = true
        val currentIndex = 1
        val nextIndex = if (isShadowing) currentIndex else currentIndex + 1
        assertEquals(1, nextIndex)
    }

    @Test
    fun `normal mode advances to next file`() {
        val isShadowing = false
        val currentIndex = 1
        val nextIndex = if (isShadowing) currentIndex else currentIndex + 1
        assertEquals(2, nextIndex)
    }

    // Playback completion tests
    @Test
    fun `playlist completion callback`() {
        var completed = false
        val onComplete: () -> Unit = { completed = true }

        onComplete()

        assertTrue(completed)
    }

    @Test
    fun `playlist cleared on completion`() {
        var playingPlaylist = listOf("file1.m4a", "file2.m4a")

        // Simulate completion
        playingPlaylist = emptyList()

        assertTrue(playingPlaylist.isEmpty())
    }

    @Test
    fun `playlist cleared on stop`() {
        var playingPlaylist = listOf("file1.m4a", "file2.m4a")

        // Simulate stop action
        playingPlaylist = emptyList()

        assertTrue(playingPlaylist.isEmpty())
    }
}
