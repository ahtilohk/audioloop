package com.example.audioloop

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for AudioLoopUiState data class and state transitions.
 * Verifies that after the major refactoring, state fields are correct.
 */
class UiStateTest {

    // ── Default State Tests ──
    @Test
    fun `default state has General category`() {
        val state = AudioLoopUiState()
        assertEquals("General", state.currentCategory)
    }

    @Test
    fun `default state has one category`() {
        val state = AudioLoopUiState()
        assertEquals(1, state.categories.size)
        assertEquals("General", state.categories.first())
    }

    @Test
    fun `default state is not playing`() {
        val state = AudioLoopUiState()
        assertEquals("", state.playingFileName)
        assertFalse(state.isPaused)
    }

    @Test
    fun `default state has no search visible`() {
        val state = AudioLoopUiState()
        assertFalse(state.isSearchVisible)
        assertEquals("", state.searchQuery)
    }

    @Test
    fun `default state has no active playlist`() {
        val state = AudioLoopUiState()
        assertNull(state.viewingPlaylistId)
        assertFalse(state.showPlaylistView)
        assertNull(state.editingPlaylist)
    }

    @Test
    fun `default state has no backup sheet open`() {
        val state = AudioLoopUiState()
        assertFalse(state.showBackupSheet)
        assertFalse(state.isBackupSignedIn)
    }

    @Test
    fun `default state has no trim dialog open`() {
        val state = AudioLoopUiState()
        assertFalse(state.showTrimDialog)
        assertNull(state.recordingToTrim)
    }

    @Test
    fun `default sleep timer is disabled`() {
        val state = AudioLoopUiState()
        assertEquals(0, state.selectedSleepMinutes)
        assertEquals(0L, state.sleepTimerRemainingMs)
    }

    @Test
    fun `default loop mode is one`() {
        val state = AudioLoopUiState()
        assertEquals(1, state.loopMode)
    }

    @Test
    fun `default playback speed is 1`() {
        val state = AudioLoopUiState()
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
    }

    @Test
    fun `default theme is SLATE`() {
        val state = AudioLoopUiState()
        assertEquals(com.example.audioloop.ui.theme.AppTheme.SLATE, state.currentTheme)
    }

    // ── State Transition Tests (via copy) ──
    @Test
    fun `opening search sets isSearchVisible`() {
        val state = AudioLoopUiState()
        val newState = state.copy(isSearchVisible = true)
        assertTrue(newState.isSearchVisible)
    }

    @Test
    fun `setting search query updates searchQuery`() {
        val state = AudioLoopUiState()
        val newState = state.copy(searchQuery = "hello")
        assertEquals("hello", newState.searchQuery)
    }

    @Test
    fun `starting playback updates playingFileName`() {
        val state = AudioLoopUiState()
        val newState = state.copy(playingFileName = "recording1.m4a")
        assertEquals("recording1.m4a", newState.playingFileName)
    }

    @Test
    fun `pausing sets isPaused to true`() {
        val state = AudioLoopUiState(playingFileName = "file.m4a")
        val newState = state.copy(isPaused = true)
        assertTrue(newState.isPaused)
    }

    @Test
    fun `stopping clears playingFileName`() {
        val state = AudioLoopUiState(playingFileName = "file.m4a", isPaused = false)
        val newState = state.copy(playingFileName = "", isPaused = false)
        assertEquals("", newState.playingFileName)
        assertFalse(newState.isPaused)
    }

    @Test
    fun `viewing playlist sets viewingPlaylistId`() {
        val state = AudioLoopUiState()
        val newState = state.copy(viewingPlaylistId = "pl_001", showPlaylistView = true)
        assertEquals("pl_001", newState.viewingPlaylistId)
        assertTrue(newState.showPlaylistView)
    }

    @Test
    fun `closing playlist view clears viewingPlaylistId`() {
        val state = AudioLoopUiState(viewingPlaylistId = "pl_001", showPlaylistView = true)
        val newState = state.copy(viewingPlaylistId = null, showPlaylistView = false)
        assertNull(newState.viewingPlaylistId)
        assertFalse(newState.showPlaylistView)
    }

    @Test
    fun `editing playlist sets editingPlaylist`() {
        val playlist = Playlist(
            id = "new_001",
            name = "Test",
            files = emptyList(),
            createdAt = System.currentTimeMillis()
        )
        val state = AudioLoopUiState()
        val newState = state.copy(editingPlaylist = playlist)
        assertNotNull(newState.editingPlaylist)
        assertEquals("new_001", newState.editingPlaylist!!.id)
    }

    @Test
    fun `clearing editingPlaylist closes editor`() {
        val playlist = Playlist("id", "Name", emptyList(), 0L)
        val state = AudioLoopUiState(editingPlaylist = playlist)
        val newState = state.copy(editingPlaylist = null)
        assertNull(newState.editingPlaylist)
    }

    @Test
    fun `opening backup sheet sets showBackupSheet`() {
        val state = AudioLoopUiState()
        val newState = state.copy(showBackupSheet = true)
        assertTrue(newState.showBackupSheet)
    }

    @Test
    fun `selection mode adds files`() {
        val state = AudioLoopUiState()
        val newState = state.copy(
            isSelectionMode = true,
            selectedFiles = setOf("file1.m4a", "file2.m4a")
        )
        assertTrue(newState.isSelectionMode)
        assertEquals(2, newState.selectedFiles.size)
    }

    @Test
    fun `clearing selection mode empties selectedFiles`() {
        val state = AudioLoopUiState(
            isSelectionMode = true,
            selectedFiles = setOf("file1.m4a")
        )
        val newState = state.copy(isSelectionMode = false, selectedFiles = emptySet())
        assertFalse(newState.isSelectionMode)
        assertTrue(newState.selectedFiles.isEmpty())
    }
}
