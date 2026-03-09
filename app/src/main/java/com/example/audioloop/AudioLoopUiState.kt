package com.example.audioloop

import com.android.billingclient.api.ProductDetails
import com.example.audioloop.ui.theme.AppTheme
import java.io.File

enum class SortMode { NAME_ASC, NAME_DESC, DATE_DESC, DATE_ASC, LENGTH_DESC, LENGTH_ASC }
enum class ThemeMode { AUTO, LIGHT, DARK }

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
    val abLoopStart: Float = -1f, // 0..1 phase
    val abLoopEnd: Float = -1f,

    // Listen & Repeat (shadowing)
    val isShadowingMode: Boolean = false,
    val shadowPauseSeconds: Int = 0, // 0 = auto
    val shadowCountdownText: String = "",

    // Category & file list
    val currentCategory: String = "General",
    val categories: List<String> = listOf("General"),
    val savedItems: List<RecordingItem> = emptyList(),
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.DATE_DESC,
    val searchCategory: String? = null,
    val filteredItems: List<RecordingItem> = emptyList(),

    // Sleep timer
    val sleepTimerRemainingMs: Long = 0L,
    val selectedSleepMinutes: Int = 0,

    // Theme & Language
    val currentTheme: AppTheme = AppTheme.OCEAN,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val appLanguage: String = "en",

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
    val isSmartCoachEnabled: Boolean = true,
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

    // Snackbar messages (replaces Toast)
    val snackbarMessage: SnackbarMessage? = null,

    // --- Navigation & App UI States ---
    val isSearchVisible: Boolean = false,
    val isRecording: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val settingsOpen: Boolean = false,
    val showCategorySheet: Boolean = false,
    val isLoading: Boolean = false,

    // --- Dialog Visibility ---
    val showRenameDialog: Boolean = false,
    val showMoveDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showTrimDialog: Boolean = false,
    val showNoteDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showPlaylistSheet: Boolean = false,
    val showBackupSheet: Boolean = false,
    val showPlaylistView: Boolean = false,
    val showPrivacyPolicyDialog: Boolean = false,
    val showExportSegmentDialog: Boolean = false,

    // --- Contextual Items ---
    val itemToModify: RecordingItem? = null,
    val recordingToDelete: RecordingItem? = null,
    val recordingToTrim: RecordingItem? = null,
    val recordingToNote: RecordingItem? = null,
    val recordingToInfo: RecordingItem? = null,
    val editingPlaylist: Playlist? = null,
    val viewingPlaylistId: String? = null,
    val exportSegmentParams: ExportParams? = null,
    
    // --- Billing / Pro ---
    val isProUser: Boolean = false,
    val billingProducts: List<ProductDetails> = emptyList(),
    val showUpgradeSheet: Boolean = false,
    
    // --- Onboarding ---
    val showOnboarding: Boolean = false,
    val onboardingStep: Int = 1,
    
    // --- App Lifecycle ---
    val isReady: Boolean = false
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

data class ExportParams(
    val startMs: Long,
    val endMs: Long,
    val fadeInMs: Long,
    val fadeOutMs: Long,
    val normalize: Boolean
)
