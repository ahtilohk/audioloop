package com.example.audioloop.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.Playlist
import com.example.audioloop.R
import com.example.audioloop.ui.*
import com.example.audioloop.ui.theme.AppColorPalette
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNavigationBar(currentTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        val tabs = listOf(
            Triple("library", stringResource(R.string.nav_library), AppIcons.QueueMusic),
            Triple("record", stringResource(R.string.nav_record), AppIcons.Mic),
            Triple("coach", stringResource(R.string.nav_coach), AppIcons.School),
            Triple("settings", stringResource(R.string.nav_settings), AppIcons.Settings)
        )
        tabs.forEach { (route, label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 10.sp) },
                selected = currentTab == route,
                onClick = { onTabSelected(route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun AppNavigationRail(currentTab: String, onTabSelected: (String) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
        header = {
            Spacer(Modifier.height(12.dp))
        },
    ) {
        val tabs = listOf(
            Triple("library", stringResource(R.string.nav_library), AppIcons.QueueMusic),
            Triple("record", stringResource(R.string.nav_record), AppIcons.Mic),
            Triple("coach", stringResource(R.string.nav_coach), AppIcons.School),
            Triple("settings", stringResource(R.string.nav_settings), AppIcons.Settings)
        )
        tabs.forEach { (route, label, icon) ->
            NavigationRailItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 10.sp) },
                selected = currentTab == route,
                onClick = { onTabSelected(route) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun MainOverlays(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    themeColors: AppColorPalette,
    onBackupSignIn: () -> Unit
) {
    val context = LocalContext.current
    // Dialogs
    if (uiState.showRenameDialog && uiState.itemToModify != null) {
        RenameDialog(
            currentName = uiState.itemToModify!!.name,
            onDismiss = { viewModel.closeRenameDialog() },
            onConfirm = { newName -> viewModel.renameFile(uiState.itemToModify!!, newName); viewModel.closeRenameDialog() },
            themeColors = themeColors
        )
    }

    if (uiState.showMoveDialog && uiState.itemToModify != null) {
        MoveFileDialog(
            categories = uiState.categories,
            onDismiss = { viewModel.closeMoveDialog() },
            onSelect = { targetCat -> viewModel.moveFile(uiState.itemToModify!!, targetCat); viewModel.closeMoveDialog() }
        )
    }

    if (uiState.showDeleteDialog && uiState.recordingToDelete != null) {
        DeleteConfirmDialog(
            title = stringResource(R.string.dialog_delete_file),
            text = stringResource(R.string.dialog_delete_confirm_format, uiState.recordingToDelete!!.name),
            onDismiss = { viewModel.closeDeleteDialog() },
            onConfirm = { viewModel.deleteFile(uiState.recordingToDelete!!); viewModel.closeDeleteDialog() }
        )
    }

    if (uiState.showNoteDialog && uiState.recordingToNote != null) {
        NoteEditDialog(
            currentNote = uiState.recordingToNote!!.note,
            fileName = uiState.recordingToNote!!.name,
            onDismiss = { viewModel.closeNoteDialog() },
            onConfirm = { note -> viewModel.saveNote(uiState.recordingToNote!!, note); viewModel.closeNoteDialog() },
            themeColors = themeColors
        )
    }

    if (uiState.showInfoDialog && uiState.recordingToInfo != null) {
        FileInfoDialog(
            item = uiState.recordingToInfo!!,
            onDismiss = { viewModel.closeInfoDialog() },
            themeColors = themeColors
        )
    }

    // Sheets (Slide-up overlays)
    if (uiState.showBackupSheet) {
        BackupRestoreSheet(
            isBackupSignedIn = uiState.isBackupSignedIn,
            backupEmail = uiState.backupEmail,
            backupProgress = uiState.backupProgress,
            isBackupRunning = uiState.isBackupRunning,
            onBackupSignIn = onBackupSignIn,
            onBackupSignOut = { viewModel.signOutBackup() },
            onBackupCreate = { viewModel.createBackup() },
            onBackupList = { viewModel.listBackups() },
            backupList = uiState.backupList,
            onRestoreFromBackup = { viewModel.restoreFromBackup(it) },
            onDeleteBackup = { viewModel.deleteBackup(it) },
            onClose = { viewModel.setShowBackupSheet(false) },
            themeColors = themeColors
        )
    }

    if (uiState.showPlaylistSheet) {
        PlaylistListSheet(
            playlists = uiState.playlists,
            formatDuration = { p ->
                val totalMs = p.files.sumOf { path ->
                    val name = path.substringAfter("/")
                    viewModel.getAllRecordings().find { it.name == name }?.durationMillis ?: 0L
                }
                val mins = (totalMs / 1000) / 60
                val secs = (totalMs / 1000) % 60
                if (mins > 0) context.getString(R.string.label_duration_min_sec, mins, secs) else context.getString(R.string.label_duration_sec, secs)
            },
            getCategoryForFile = { path -> 
                val general = context.getString(R.string.label_general)
                path.substringBefore("/", general) 
            },
            onCreateNew = {
                viewModel.openPlaylistEditor(Playlist(
                    id = "new_" + java.util.UUID.randomUUID().toString(),
                    name = "",
                    files = emptyList(),
                    createdAt = System.currentTimeMillis()
                ))
            },
            onEdit = { viewModel.openPlaylistEditor(it) },
            onView = { viewModel.openPlaylistView(it.id) },
            onPlay = { 
                viewModel.playPlaylistFromPlaylist(it)
                viewModel.setShowPlaylistSheet(false)
                viewModel.openPlaylistView(it.id)
            },
            onPause = { viewModel.pausePlaying() },
            onDelete = { viewModel.deletePlaylist(it) },
            onClose = { viewModel.setShowPlaylistSheet(false) },
            themeColors = themeColors,
            currentlyPlayingPlaylistId = uiState.currentlyPlayingPlaylistId
        )
    }

    if (uiState.editingPlaylist != null) {
        PlaylistEditorScreen(
            playlist = uiState.editingPlaylist!!,
            allCategories = uiState.categories,
            getFilesForCategory = { cat -> viewModel.getAllRecordings().filter { it.file.parentFile?.name == cat } },
            getCategoryForFile = { path -> 
                val general = context.getString(R.string.label_general)
                path.substringBefore("/", general) 
            },
            resolveFileName = { path -> path.substringAfter("/") },
            resolveFileDuration = { path -> 
                val name = path.substringAfter("/")
                viewModel.getAllRecordings().find { it.name == name }?.durationString ?: context.getString(R.string.label_duration_sec, 0)
            },
            onSave = { 
                viewModel.savePlaylist(it)
                viewModel.openPlaylistEditor(null)
            },
            onClose = { viewModel.openPlaylistEditor(null) },
            themeColors = themeColors
        )
    }

    if (uiState.showPlaylistView && uiState.viewingPlaylistId != null) {
        PlaylistViewScreen(
            playlist = uiState.playlists.find { it.id == uiState.viewingPlaylistId } ?: Playlist(id="", name="", files=emptyList(), createdAt=0L),
            playingFileName = uiState.playingFileName,
            currentIteration = uiState.currentPlaylistIteration,
            isPaused = uiState.isPaused,
            allRecordings = viewModel.getAllRecordings(),
            onBack = { 
                viewModel.closePlaylistView()
                viewModel.setShowPlaylistSheet(true)
            },
            onPause = { viewModel.pausePlaying() },
            onResume = { viewModel.resumePlaying() },
            onStop = { 
                viewModel.stopPlaying()
                viewModel.closePlaylistView()
            },
            themeColors = themeColors
        )
    }

    if (uiState.showPracticeStats) {
        PracticeStatsScreen(
            stats = viewModel.practiceStats,
            coach = viewModel.coachEngine,
            themeColors = themeColors,
            onBack = { viewModel.setShowPracticeStats(false) },
            onStartRecommended = { 
                viewModel.setShowPracticeStats(false)
                viewModel.startRecommendedSession(it) 
            },
            onGoalChange = { viewModel.setPracticeGoal(it) }
        )
    }

    if (uiState.showTrimDialog && uiState.recordingToTrim != null) {
        TrimAudioScreen(
            file = uiState.recordingToTrim!!.file,
            uri = uiState.recordingToTrim!!.uri,
            durationMs = uiState.recordingToTrim!!.durationMillis,
            onDismiss = { viewModel.closeTrimDialog() },
            onConfirm = { start, end, replace, remove, fadeIn, fadeOut, norm, gain ->
                viewModel.trimAudioFile(uiState.recordingToTrim!!.file, start, end, replace, remove, fadeIn, fadeOut, norm, gain) {
                    viewModel.closeTrimDialog()
                }
            },
            themeColors = themeColors
        )
    }

    if (uiState.isSearchVisible) {
        SearchOverlay(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            categories = uiState.categories,
            selectedCategory = uiState.searchCategory,
            onCategorySelect = { viewModel.setSearchCategory(it) },
            onClose = { viewModel.setSearchVisible(false) },
            themeColors = themeColors
        )
    }
}
