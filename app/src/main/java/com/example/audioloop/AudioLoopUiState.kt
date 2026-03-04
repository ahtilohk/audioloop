package com.example.audioloop

import com.example.audioloop.ui.theme.AppTheme

/**
 * Single source of truth for all UI state in AudioLoop.
 * Replaces the 40+ mutableStateOf fields scattered across MainActivity.
 */
data class AudioLoopUiState(
    // Playback
    val playingFileName: String = "",
    val isPaused: Boolean = false,
    val currentProgress: Float = 0f,
    val currentTimeString: String = "00:00",
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val loopMode: Int = 1, // 0=off, 1=one, -1=inf

    // Listen & Repeat (shadowing)
    val isShadowingMode: Boolean = false,
    val shadowPauseSeconds: Int = 0, // 0 = auto
    val shadowCountdownText: String = "",

    // Category & file list
    val currentCategory: String = "General",
    val categories: List<String> = listOf("General"),
    val savedItems: List<RecordingItem> = emptyList(),
    val searchQuery: String = "",

    // Sleep timer
    val sleepTimerRemainingMs: Long = 0L,
    val selectedSleepMinutes: Int = 0,

    // Theme
    val currentTheme: AppTheme = AppTheme.SLATE,

    // Practice Coach
    val practiceWeeklyMinutes: Float = 0f,
    val practiceWeeklyGoal: Int = 120,
    val practiceStreak: Int = 0,
    val practiceTodayMinutes: Float = 0f,
    val practiceWeeklySessions: Int = 0,
    val practiceWeeklyEdits: Int = 0,
    val practiceGoalProgress: Float = 0f,
    val practiceRecommendation: CoachEngine.Recommendation = CoachEngine.Recommendation("", "", "", 0),
    val showPracticeStats: Boolean = false,
    val isSmartCoachExpanded: Boolean = false,
    val currentSessionElapsedMs: Long = 0L,

    // Playlists
    val playlists: List<Playlist> = emptyList(),
    val currentlyPlayingPlaylistId: String? = null,
    val currentPlaylistIteration: Int = 1,

    // Backup & Restore
    val isBackupSignedIn: Boolean = false,
    val backupEmail: String = "",
    val backupProgress: String = "",
    val isBackupRunning: Boolean = false,
    val backupList: List<BackupInfo> = emptyList(),

    // Storage
    val usePublicStorage: Boolean = true,

    // Snackbar messages (replaces Toast)
    val snackbarMessage: SnackbarMessage? = null
)

/**
 * Structured snackbar message that replaces Toast.
 * Supports action button (Undo, Retry) for better UX.
 */
data class SnackbarMessage(
    val text: String,
    val actionLabel: String? = null,
    val isError: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short
)

enum class SnackbarDuration { Short, Long, Indefinite }
