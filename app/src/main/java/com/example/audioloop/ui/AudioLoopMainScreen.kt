package com.example.audioloop.ui

import com.example.audioloop.ui.RenameDialog
import com.example.audioloop.ui.MoveFileDialog
import com.example.audioloop.ui.DeleteConfirmDialog
import com.example.audioloop.ui.formatDuration
import com.example.audioloop.ui.formatSessionTime
import com.example.audioloop.RecordingItem
import com.example.audioloop.WaveformGenerator
import com.example.audioloop.AppIcons
import com.example.audioloop.Playlist
import com.example.audioloop.PlaylistManager
import com.example.audioloop.CoachEngine
import java.util.UUID
import kotlinx.coroutines.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.detectDragGestures
import android.media.MediaPlayer
import com.example.audioloop.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import java.io.File

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint

import androidx.compose.runtime.mutableFloatStateOf


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLoopMainScreen(
    context: android.content.Context,
    uiState: com.example.audioloop.AudioLoopUiState,
    viewModel: com.example.audioloop.AudioLoopViewModel,
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    onBackupSignIn: () -> Unit
) {
    // Bridge local variables
    val recordingItems = viewModel.getFilteredItems()
    val categories = uiState.categories
    val currentCategory = uiState.currentCategory
    val currentProgress = uiState.currentProgress
    val currentTimeString = uiState.currentTimeString
    val playingFileName = uiState.playingFileName
    val isPaused = uiState.isPaused
    
    val onPlayingFileNameChange: (String) -> Unit = { /* managed by ViewModel */ }
    val onCategoryChange: (String) -> Unit = { newCat ->
        viewModel.changeCategory(newCat)
        com.example.audioloop.widget.WidgetStateHelper.updateWidget(context, category = newCat)
    }
    val onAddCategory: (String) -> Unit = { viewModel.addCategory(it) }
    val onRenameCategory: (String, String) -> Unit = { old, new -> viewModel.renameCategory(old, new) }
    val onDeleteCategory: (String) -> Unit = { viewModel.deleteCategory(it) }

    val onReorderCategory: (String, Int) -> Unit = { cat, dir -> viewModel.reorderCategory(cat, dir) }
    val onReorderCategories: (List<String>) -> Unit = { viewModel.reorderCategories(it) }
    
    val onMoveFile: (RecordingItem, String) -> Unit = { item, cat -> viewModel.moveFile(item, cat) }
    val onReorderFile: (File, Int) -> Unit = { file, dir -> viewModel.reorderFile(file, dir) }
    val onReorderFinished: (List<File>) -> Unit = { viewModel.reorderFinished(it) }
    
    val onStartPlaylist: (List<RecordingItem>, Boolean, Float, () -> Unit) -> Unit = { files, loop, speed, onComplete ->
        viewModel.startPlaylistPlayback(files, loop, speed, onComplete)
    }
    val onPlaylistUpdate: () -> Unit = { }
    
    val onSpeedChange: (Float) -> Unit = { viewModel.changeSpeed(it) }
    val onPitchChange: (Float) -> Unit = { viewModel.changePitch(it) }
    val onLoopCountChange: (Int) -> Unit = { viewModel.changeLoopMode(it) }
    val onSeekTo: (Float) -> Unit = { viewModel.seekTo(it) }
    val onPausePlay: () -> Unit = { viewModel.pausePlaying() }
    val onResumePlay: () -> Unit = { viewModel.resumePlaying() }
    val onStopPlay: () -> Unit = { viewModel.stopPlayingAndReset() }
    val onDeleteFile: (RecordingItem) -> Unit = { viewModel.deleteFile(it) }
    val onShareFile: (RecordingItem) -> Unit = { viewModel.shareFile(it) }
    val onRenameFile: (RecordingItem, String) -> Unit = { item, name -> viewModel.renameFile(item, name) }
    val onImportFile: (android.net.Uri) -> Unit = { uri -> viewModel.importFile(uri) }
    val onTrimFile: (File, Long, Long, Boolean, Boolean, Long, Long, Boolean) -> Unit = { file, start, end, replace, remove, fadeIn, fadeOut, norm ->
        viewModel.trimAudioFile(file, start, end, replace, remove, fadeIn, fadeOut, norm) {
            if (replace) viewModel.precomputeWaveformAsync(file, force = true)
        }
    }
    val selectedSpeed = uiState.playbackSpeed
    val selectedPitch = uiState.playbackPitch
    val selectedLoopCount = uiState.loopMode
    val isShadowing = uiState.isShadowingMode
    val onShadowingChange: (Boolean) -> Unit = { viewModel.changeShadowingMode(it) }
    val shadowPauseSeconds = uiState.shadowPauseSeconds
    val onShadowPauseChange: (Int) -> Unit = { viewModel.changeShadowPause(it) }
    val shadowCountdownText = uiState.shadowCountdownText
    
    val waveformCache = viewModel.waveformCache
    val onSeekAbsolute: (Int) -> Unit = { viewModel.seekAbsolute(it) }
    val onSplitFile: (RecordingItem) -> Unit = { viewModel.splitFile(it) }
    val onNormalizeFile: (RecordingItem) -> Unit = { viewModel.normalizeFile(it) }
    val onAutoTrimFile: (RecordingItem) -> Unit = { viewModel.autoTrimFile(it) }
    val onSaveNote: (RecordingItem, String) -> Unit = { item, note -> viewModel.saveNote(item, note) }
    val onFadeFile: (RecordingItem, Long, Long) -> Unit = { item, fadeIn, fadeOut -> viewModel.fadeFile(item, fadeIn, fadeOut) }
    val onMergeFiles: (List<RecordingItem>) -> Unit = { viewModel.mergeFiles(it) }
    val usePublicStorage = uiState.usePublicStorage
    val onPublicStorageChange: (Boolean) -> Unit = { }
    val sleepTimerRemainingMs = uiState.sleepTimerRemainingMs
    val selectedSleepMinutes = uiState.selectedSleepMinutes
    val onSleepTimerChange: (Int) -> Unit = { viewModel.setSleepTimer(it) }
    val currentTheme = uiState.currentTheme
    val onThemeChange: (com.example.audioloop.ui.theme.AppTheme) -> Unit = { viewModel.changeTheme(it) }
    
    // Backup & Restore
    val isBackupSignedIn = uiState.isBackupSignedIn
    val backupEmail = uiState.backupEmail
    val backupProgress = uiState.backupProgress
    val isBackupRunning = uiState.isBackupRunning
    // onBackupSignIn passed via parameter
    val onBackupSignOut: () -> Unit = { viewModel.signOutBackup() }
    val onBackupCreate: () -> Unit = { viewModel.createBackup() }
    val onBackupRestore: () -> Unit = { }
    val onBackupList: () -> Unit = { viewModel.listBackups() }
    val backupList = uiState.backupList
    val onRestoreFromBackup: (String) -> Unit = { viewModel.restoreFromBackup(it) }
    val onDeleteBackup: (String) -> Unit = { viewModel.deleteBackup(it) }

    // Playlists
    val playlists = uiState.playlists
    val onSavePlaylist: (Playlist) -> Unit = { viewModel.savePlaylist(it) }
    val onDeletePlaylist: (Playlist) -> Unit = { viewModel.deletePlaylist(it) }
    val onPlayPlaylist: (Playlist) -> Unit = { viewModel.playPlaylistFromPlaylist(it) }
    val currentlyPlayingPlaylistId = uiState.currentlyPlayingPlaylistId
    val currentPlaylistIteration = uiState.currentPlaylistIteration
    val onGetAllRecordings: () -> List<RecordingItem> = { viewModel.getAllRecordings() }

    // Practice Coach stats
    val practiceWeeklyMinutes = uiState.practiceWeeklyMinutes
    val practiceWeeklyGoal = uiState.practiceWeeklyGoal
    val practiceStreak = uiState.practiceStreak
    val practiceTodayMinutes = uiState.practiceTodayMinutes
    val practiceWeeklySessions = uiState.practiceWeeklySessions
    val practiceWeeklyEdits = uiState.practiceWeeklyEdits
    val practiceGoalProgress = uiState.practiceGoalProgress
    val practiceRecommendation = uiState.practiceRecommendation
    val onStartRecommendedSession: (Int) -> Unit = { viewModel.startRecommendedSession(it) }
    val onViewPracticeStats: () -> Unit = { viewModel.setShowPracticeStats(true) }
    val isSmartCoachExpanded = uiState.isSmartCoachExpanded
    val onSmartCoachToggle: () -> Unit = { viewModel.toggleSmartCoach() }
    val currentSessionElapsedMs = uiState.currentSessionElapsedMs
    
    // Search
    val searchQuery = uiState.searchQuery
    val onSearchQueryChange: (String) -> Unit = { viewModel.updateSearchQuery(it) }
    // Get theme colors
    val themeColors = currentTheme.palette
    var settingsOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Speech") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var playingPlaylistFiles by remember { mutableStateOf(listOf<String>()) } // Track files in current playlist
    var showCategorySheet by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    
    // Local source of truth for the list to allow live updates
    val uiRecordingItems = remember { mutableStateListOf<RecordingItem>() }
    // Sync when source changes (and not dragging)
    LaunchedEffect(recordingItems) {
         // Only update if we are not currently dragging to avoid jumpiness
         // But usually if recordingItems changes, it means an external update or save finished.
         // We should probably respect it.
         uiRecordingItems.clear()
         uiRecordingItems.addAll(recordingItems)
    }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showPlaylistView by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var viewingPlaylistId by remember { mutableStateOf<String?>(null) } // To allow viewing without playing

    // When playback stops, return to playlist list (stay in playlist mode)
    LaunchedEffect(currentlyPlayingPlaylistId) {
        if (currentlyPlayingPlaylistId == null && showPlaylistView) {
            showPlaylistView = false
            showPlaylistSheet = true  // Go back to playlist list, not main screen
        }
    }

    var itemToModify by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToTrim by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToNote by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToInfo by remember { mutableStateOf<RecordingItem?>(null) }
    var draggingItemName by remember { mutableStateOf<String?>(null) }
    var draggingItemIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    
    val density = LocalDensity.current.density
    val itemHeightPx = 72 * density // Approximate height of an item


    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri -> onImportFile(uri) }
    }
    
    // val context = LocalContext.current // Using parameter instead

    // Helper to toggle selection
    fun toggleSelection(name: String) {
        selectedFiles = if (selectedFiles.contains(name)) {
            selectedFiles - name
        } else {
            selectedFiles + name
        }
    }

    // Back handler: return from playlist view to playlist list
    BackHandler(enabled = showPlaylistView) {
        showPlaylistView = false
        viewingPlaylistId = null
        showPlaylistSheet = true  // Go back to playlist list
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(themeColors.primary900.copy(alpha = 0.4f))
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Header - Modern MD3 Design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false) // Allow logo to shrink a bit if needed
                ) {
                    Icon(
                        imageVector = AppIcons.GraphicEq, 
                        contentDescription = "Logo", 
                        tint = themeColors.primary400,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Loop & Learn",
                        style = MaterialTheme.typography.titleLarge.copy(
                            brush = Brush.horizontalGradient(
                                colors = listOf(themeColors.primary300, themeColors.primary400)
                            ),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    // Search icon button
                    IconButton(
                        onClick = { isSearchVisible = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Search,
                            contentDescription = "Search files",
                            tint = if (searchQuery.isNotEmpty()) themeColors.primary400 else Zinc500,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Backup & Restore icon button
                    IconButton(
                        onClick = { showBackupSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.CloudSync,
                            contentDescription = "Backup & Restore",
                            tint = if (isBackupSignedIn) themeColors.primary400 else Zinc500,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Premium Playlists Button
                    Surface(
                        onClick = { showPlaylistSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = themeColors.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.2.dp, themeColors.primary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = AppIcons.QueueMusic,
                                contentDescription = null,
                                tint = themeColors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Playlists",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = themeColors.primary,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }



            // Category Navigation - Underline Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(categories) { cat ->
                        val isActive = currentCategory == cat
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { onCategoryChange(cat) }
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (isActive) themeColors.primary300 else Zinc500,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            // Underline indicator
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) themeColors.primary400
                                        else Color.Transparent,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        }
                    }
                }

                // Category Management Button
                IconButton(
                    onClick = { showCategorySheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                   Icon(
                       imageVector = AppIcons.Edit,
                       contentDescription = "Manage Categories",
                       tint = Zinc500,
                       modifier = Modifier.size(18.dp)
                   )
                }
            }

            HorizontalDivider(
                color = Zinc700.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Search Bar
            AnimatedVisibility(visible = isSearchVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(Zinc800.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(themeColors.primary400),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = "Search",
                                    tint = Zinc500,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text("Search files...", color = Zinc500, fontSize = 14.sp)
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        imageVector = AppIcons.Close,
                                        contentDescription = "Clear search",
                                        tint = Zinc400,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onSearchQueryChange("") }
                                    )
                                }
                            }
                        }
                    )
                    // Close search
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "Close search",
                        tint = Zinc500,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                onSearchQueryChange("")
                                isSearchVisible = false
                            }
                    )
                }
            }

            // Recording Section - Modern MD3 Design
            AnimatedVisibility(visible = !isSelectionMode && !showCategorySheet) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mode Switcher - MD3 Segmented Button Style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Zinc800.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .border(1.dp, Zinc600, RoundedCornerShape(24.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Speech" to AppIcons.Mic, "Stream" to AppIcons.Stream).forEach { (m, icon) ->
                            val active = mode == m
                            Surface(
                                onClick = { mode = m },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = if (active)
                                    themeColors.primary
                                else
                                    Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (active) Color.White else Zinc400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = m,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = if (active) Color.White else Zinc400,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Record Button - Studio Pro Design
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .height(84.dp) // Professional height
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isRecording) {
                                    // Recording: Deep Red Pulse
                                    Brush.radialGradient(
                                        colors = listOf(Red900, Color.Black),
                                        radius = 600f
                                    )
                                } else {
                                    // Idle: Studio Dark (Zinc900)
                                    SolidColor(Zinc900)
                                }
                            )
                            .border(
                                width = 2.dp, // Thicker border for premium look
                                color = if (isRecording) Red500 else Red800, // More visible red border
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                if (isRecording) {
                                    onStopRecord()
                                    isRecording = false
                                } else {
                                    val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.getDefault())
                                    val dateStr = dateFormat.format(java.util.Date())
                                    val prefix = if (mode == "Speech") "Speech" else "Stream"
                                    val name = "${prefix}_$dateStr"
                                    val commenced = onStartRecord(name, mode == "Stream")
                                    if (commenced) isRecording = true
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Record Indicator / Icon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(
                                        elevation = if (isRecording) 12.dp else 0.dp,
                                        spotColor = Red500,
                                        shape = CircleShape
                                    )
                                    .background(
                                        color = if (isRecording) Red600 else Zinc800,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isRecording) Red400 else Zinc600,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRecording) {
                                    // Pulsing stop icon
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    // Mic icon (Red accent)
                                    Icon(
                                        imageVector = if (mode == "Speech") AppIcons.Mic else AppIcons.Stream,
                                        contentDescription = null,
                                        tint = Red500, // Red accent icon
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Text Content
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Timer Logic
                                var recordingDurationSeconds by remember { mutableLongStateOf(0L) }
                                
                                LaunchedEffect(isRecording) {
                                    if (isRecording) {
                                        val startTime = System.currentTimeMillis()
                                        while (isActive) {
                                            recordingDurationSeconds = (System.currentTimeMillis() - startTime) / 1000
                                            delay(1000)
                                        }
                                    } else {
                                        recordingDurationSeconds = 0L
                                    }
                                }

                                if (isRecording) {
                                    val hours = recordingDurationSeconds / 3600
                                    val minutes = (recordingDurationSeconds % 3600) / 60
                                    val seconds = recordingDurationSeconds % 60
                                    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                    
                                    Text(
                                        text = timeString,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp
                                        )
                                    )
                                    Text(
                                        text = "Recording...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Red400,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "Start Recording",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 20.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Text(
                                        text = "$mode mode",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Zinc500,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Settings Section - Reusable Card
            PlaybackSettingsCard(
                settingsOpen = settingsOpen,
                onToggleSettings = { settingsOpen = !settingsOpen },
                selectedSpeed = selectedSpeed,
                onSpeedChange = onSpeedChange,
                selectedLoopCount = selectedLoopCount,
                onLoopCountChange = onLoopCountChange,
                isShadowing = isShadowing,
                onShadowingChange = onShadowingChange,
                shadowPauseSeconds = shadowPauseSeconds,
                onShadowPauseChange = onShadowPauseChange,
                selectedSleepMinutes = selectedSleepMinutes,
                onSleepTimerChange = onSleepTimerChange,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                // gapSeconds / onGapChange omitted - not used on main screen
                themeColors = themeColors,
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Categories Management Embedded Frame
            AnimatedVisibility(visible = showCategorySheet) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    CategoryManagementSheet(
                        categories = categories,
                        currentCategory = currentCategory,
                        onSelect = onCategoryChange,
                        onAdd = onAddCategory,
                        onRename = onRenameCategory,
                        onDelete = onDeleteCategory,
                        onReorder = onReorderCategories,
                        onClose = { showCategorySheet = false },
                        themeColors = themeColors
                    )
                }
            }

            if (!showCategorySheet) {
                HorizontalDivider(color = Zinc800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode && selectedFiles.isNotEmpty()) {
                            // â”€â”€ Selection Action Bar â”€â”€
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Play selected
                                Button(
                                    onClick = {
                                        val orderedSelection = selectedFiles.toList()
                                        val filesToPlay = orderedSelection.mapNotNull { name ->
                                            recordingItems.find { it.name == name }
                                        }
                                        if (filesToPlay.isNotEmpty()) {
                                            playingPlaylistFiles = filesToPlay.map { it.name }
                                            onStartPlaylist(filesToPlay, selectedLoopCount == -1, selectedSpeed) {
                                                playingPlaylistFiles = emptyList()
                                            }
                                            isSelectionMode = false
                                            selectedFiles = emptySet()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("PLAY ${selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                // Save as Playlist
                                Button(
                                    onClick = {
                                        val orderedSelection = selectedFiles.toList()
                                        val relativePaths = orderedSelection.map { name ->
                                            val item = recordingItems.find { it.name == name }
                                            if (item != null) {
                                                val path = item.file.absolutePath.replace("\\", "/")
                                                val cat = if (path.contains("Music/AudioLoop/")) {
                                                    val subPath = path.substringAfter("Music/AudioLoop/")
                                                    if (subPath.contains("/")) subPath.substringBefore("/") else ""
                                                } else ""
                                                if (cat.isNotEmpty()) "$cat/$name" else name
                                            } else name
                                        }
                                        editingPlaylist = Playlist(
                                            id = "new_" + java.util.UUID.randomUUID().toString(),
                                            name = "",
                                            files = relativePaths,
                                            createdAt = System.currentTimeMillis()
                                        )
                                        isSelectionMode = false
                                        selectedFiles = emptySet()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary800),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, themeColors.primary600),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp), tint = themeColors.primary300)
                                    Spacer(Modifier.width(4.dp))
                                    Text("PLAYLIST", color = themeColors.primary300, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                // Merge (2+ files)
                                if (selectedFiles.size >= 2) {
                                    Button(
                                        onClick = {
                                            val orderedSelection = selectedFiles.toList()
                                            val filesToMerge = orderedSelection.mapNotNull { name ->
                                                recordingItems.find { it.name == name }
                                            }
                                            if (filesToMerge.size >= 2) {
                                                onMergeFiles(filesToMerge)
                                                isSelectionMode = false
                                                selectedFiles = emptySet()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.secondary),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("MERGE ${selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            // â”€â”€ Normal file list header with Select button â”€â”€
                            Text(
                                text = "Files ($currentCategory):",
                                style = TextStyle(color = Zinc200, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { filePickerLauncher.launch("audio/*") },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Add", fontSize = 12.sp, color = Color.White)
                                }
                                Surface(
                                    onClick = {
                                        isSelectionMode = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Zinc800.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, Zinc600),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            AppIcons.DoneAll,
                                            contentDescription = "Select",
                                            tint = Zinc300,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text("Select", fontSize = 12.sp, color = Zinc300)
                                    }
                                }
                            }
                        }
                    }

                    // Cancel hint when in selection mode with empty selection
                    if (isSelectionMode && selectedFiles.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap files to select",
                                style = TextStyle(color = Zinc500, fontSize = 13.sp)
                            )
                            Surface(
                                onClick = {
                                    isSelectionMode = false
                                    selectedFiles = emptySet()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Red500.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Red400.copy(alpha = 0.5f)),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(AppIcons.Close, contentDescription = null, tint = Red400, modifier = Modifier.size(14.dp))
                                    Text("Cancel", fontSize = 12.sp, color = Red400, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // â”€â”€ Premium Playlist Playback Banner â”€â”€
                    val activePlaylist = if (currentlyPlayingPlaylistId != null)
                        playlists.find { it.id == currentlyPlayingPlaylistId } else null
                    if (activePlaylist != null) {
                        val glowAlpha by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bannerGlow"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            themeColors.primary900.copy(alpha = 0.75f),
                                            themeColors.primary800.copy(alpha = 0.45f)
                                        )
                                    )
                                )
                                .border(
                                    1.5.dp,
                                    themeColors.primary500.copy(alpha = 0.4f + 0.3f * glowAlpha),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { 
                                    viewingPlaylistId = currentlyPlayingPlaylistId
                                    showPlaylistView = true 
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Animated play dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(themeColors.primary400.copy(alpha = 0.6f + 0.4f * glowAlpha))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "NOW PLAYING",
                                        style = TextStyle(
                                            color = themeColors.primary400,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        )
                                    )
                                    Text(
                                        activePlaylist.name,
                                        style = TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                // Session time + today total
                                if (currentSessionElapsedMs > 0L) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            formatSessionTime(currentSessionElapsedMs),
                                            style = TextStyle(
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                        val totalTodayMs = (practiceTodayMinutes * 60_000L).toLong() + currentSessionElapsedMs
                                        Text(
                                            "Today ${formatSessionTime(totalTodayMs)}",
                                            style = TextStyle(
                                                color = themeColors.primary400,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                            }
                            // Pills row
                            val loopText = when (activePlaylist.loopCount) {
                                -1 -> "âˆž"
                                else -> "$currentPlaylistIteration / ${activePlaylist.loopCount}Ã—"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Loop pill â€” always visible
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(themeColors.primary700.copy(alpha = 0.55f))
                                        .border(1.dp, themeColors.primary500.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("ðŸ” $loopText",
                                        color = themeColors.primary200,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                // Speed pill
                                if (activePlaylist.speed != 1.0f) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Zinc700.copy(alpha = 0.7f))
                                            .border(1.dp, Zinc600, RoundedCornerShape(20.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("ðŸŽš ${String.format("%.1f", activePlaylist.speed)}Ã—",
                                            color = Zinc300, fontSize = 12.sp)
                                    }
                                }
                                // Gap pill
                                if (activePlaylist.gapSeconds > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Zinc700.copy(alpha = 0.7f))
                                            .border(1.dp, Zinc600, RoundedCornerShape(20.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("â± ${activePlaylist.gapSeconds}s gap",
                                            color = Zinc300, fontSize = 12.sp)
                                    }
                                }
                                // Shuffle pill
                                if (activePlaylist.shuffle) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Zinc700.copy(alpha = 0.7f))
                                            .border(1.dp, Zinc600, RoundedCornerShape(20.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("ðŸ”€ Shuffle",
                                            color = Zinc300, fontSize = 12.sp)
                                    }
                                }
                                // Track count
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${activePlaylist.files.size} tracks",
                                    color = Zinc500,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // â”€â”€ Sticky Smart Coach bar (always visible, not scrollable) â”€â”€
                    val showPracticeCard = practiceRecommendation.title.isNotEmpty()
                    if (showPracticeCard) {
                        PracticeProgressCard(
                            weeklyMinutes = practiceWeeklyMinutes,
                            weeklyGoal = practiceWeeklyGoal,
                            streak = practiceStreak,
                            todayMinutes = practiceTodayMinutes,
                            weeklySessions = practiceWeeklySessions,
                            weeklyEdits = practiceWeeklyEdits,
                            recommendation = practiceRecommendation,
                            goalProgress = practiceGoalProgress,
                            themeColors = themeColors,
                            onStartRecommended = onStartRecommendedSession,
                            onViewDetails = onViewPracticeStats,
                            isExpanded = isSmartCoachExpanded,
                            onToggleExpanded = onSmartCoachToggle,
                            isPlaying = playingFileName.isNotEmpty(),
                            currentSessionElapsedMs = currentSessionElapsedMs,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    val density = LocalDensity.current
                    val scrollState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    var draggingItemIndex by remember { mutableIntStateOf(-1) }
                    var draggingItem by remember { mutableStateOf<RecordingItem?>(null) }
                    var overlayOffsetY by remember { mutableFloatStateOf(0f) }
                    var grabOffsetY by remember { mutableFloatStateOf(0f) }
                    var overscrollSpeed by remember { mutableFloatStateOf(0f) }

                    fun checkForSwap() {
                        val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                        if (currentVisibleItems.isEmpty()) return
                        val overlayCenterY = overlayOffsetY + (with(density) { 36.dp.toPx() })
                        var targetIndex = -1
                        val hoveredItem = currentVisibleItems.find { overlayCenterY >= it.offset && overlayCenterY <= it.offset + it.size }
                        if (hoveredItem != null) {
                            targetIndex = hoveredItem.index
                        } else {
                            val firstVisible = currentVisibleItems.first()
                            val lastVisible = currentVisibleItems.last()
                            if (overlayCenterY < firstVisible.offset) targetIndex = firstVisible.index
                            else if (overlayCenterY > lastVisible.offset + lastVisible.size) targetIndex = lastVisible.index
                        }
                        if (targetIndex != -1 && targetIndex != draggingItemIndex) {
                            if (draggingItemIndex in uiRecordingItems.indices && targetIndex in uiRecordingItems.indices) {
                                val itemToMove = uiRecordingItems.removeAt(draggingItemIndex)
                                uiRecordingItems.add(targetIndex, itemToMove)
                                draggingItemIndex = targetIndex
                            }
                        }
                    }

                    LaunchedEffect(overscrollSpeed) {
                        if (overscrollSpeed != 0f) {
                            while (true) {
                                val scrolled = scrollState.scrollBy(overscrollSpeed)
                                if (scrolled != 0f) checkForSwap()
                                kotlinx.coroutines.delay(10)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                val gripWidth = 60.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val y = down.position.y
                                    val x = down.position.x
                                    val hitItem = scrollState.layoutInfo.visibleItemsInfo.find { y >= it.offset && y <= it.offset + it.size }
                                    if (hitItem != null && x <= gripWidth) {
                                        down.consume()
                                        val index = hitItem.index
                                        if (index in uiRecordingItems.indices) {
                                            draggingItemIndex = index
                                            draggingItem = uiRecordingItems[index]
                                            val itemTop = hitItem.offset.toFloat()
                                            overlayOffsetY = itemTop
                                            grabOffsetY = y - itemTop
                                            var isDragging = true
                                            while (isDragging) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) {
                                                    isDragging = false
                                                    break
                                                }
                                                if (change.positionChanged()) {
                                                    change.consume()
                                                    val newY = change.position.y
                                                    overlayOffsetY = newY - grabOffsetY
                                                    val viewportHeight = size.height.toFloat()
                                                    val threshold = 60.dp.toPx()
                                                    val maxSpeed = 20.dp.toPx()
                                                    if (newY < threshold) overscrollSpeed = -maxSpeed * ((threshold - newY) / threshold).coerceIn(0.1f, 1f)
                                                    else if (newY > viewportHeight - threshold) overscrollSpeed = maxSpeed * ((newY - (viewportHeight - threshold)) / threshold).coerceIn(0.1f, 1f)
                                                    else overscrollSpeed = 0f
                                                    checkForSwap()
                                                }
                                            }
                                            overscrollSpeed = 0f
                                            onReorderFinished(uiRecordingItems.map { it.file })
                                            draggingItemIndex = -1
                                            draggingItem = null
                                        }
                                    }
                                }
                            }
                    ) {
                        if (uiRecordingItems.isEmpty()) {
                            // â”€â”€ Empty State â”€â”€
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Large icon
                                Icon(
                                    imageVector = if (searchQuery.isNotEmpty()) AppIcons.Search else AppIcons.Mic,
                                    contentDescription = null,
                                    tint = themeColors.primary400.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching files"
                                           else "No recordings yet",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Zinc300,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "Try a different search term"
                                           else "Tap the record button above to create your first recording, or import an audio file.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Zinc500
                                    ),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (searchQuery.isEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch("audio/*") },
                                        border = BorderStroke(1.dp, themeColors.primary400.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Add,
                                            contentDescription = null,
                                            tint = themeColors.primary400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Import Audio File",
                                            color = themeColors.primary300,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = scrollState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiRecordingItems, key = { _, item -> item.name }) { index, item ->
                                val isPlaying = item.file.name == playingFileName
                                val isSelected = selectedFiles.contains(item.name)
                                val alpha = if (draggingItemIndex == index) 0f else 1f
                                FileItem(
                                    modifier = Modifier.alpha(alpha),
                                    item = item,
                                    isPlaying = isPlaying,
                                    isPaused = if (isPlaying) isPaused else false,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    selectionOrder = selectedFiles.indexOf(item.name) + 1,
                                    onPlay = { onStartPlaylist(listOf(item), selectedLoopCount == -1, selectedSpeed, {}) },
                                    onPause = onPausePlay,
                                    onResume = onResumePlay,
                                    onStop = { playingPlaylistFiles = emptyList(); onStopPlay() },
                                    onToggleSelect = { toggleSelection(item.name) },
                                    onRename = { itemToModify = item; showRenameDialog = true },
                                    onTrim = { recordingToTrim = item; showTrimDialog = true },
                                    onMove = { itemToModify = item; showMoveDialog = true },
                                    onShare = { onShareFile(item) },
                                    onDelete = { recordingToDelete = item; showDeleteDialog = true },
                                    onSplit = { onSplitFile(item) },
                                    onNormalize = { onNormalizeFile(item) },
                                    onAutoTrim = { onAutoTrimFile(item) },
                                    onEditNote = { recordingToNote = item; showNoteDialog = true },
                                    onShowInfo = { recordingToInfo = item; showInfoDialog = true },
                                    currentProgress = if (isPlaying) currentProgress else 0f,
                                    currentTimeString = if (isPlaying) currentTimeString else "00:00",
                                    onSeek = onSeekTo,
                                    onReorder = { },
                                    isDragging = draggingItemIndex == index,
                                    themeColors = themeColors,
                                    playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1,
                                    waveformData = waveformCache[item.file.absolutePath] ?: emptyList(),
                                    onSeekAbsolute = onSeekAbsolute,
                                    shadowCountdownText = if (isPlaying) shadowCountdownText else ""
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }

                        if (draggingItem != null) {
                            val item = draggingItem!!
                            FileItem(
                                modifier = Modifier
                                    .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                                item = item,
                                isPlaying = item.file.name == playingFileName,
                                isPaused = if (item.file.name == playingFileName) isPaused else false,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedFiles.contains(item.name),
                                selectionOrder = selectedFiles.indexOf(item.name) + 1,
                                currentProgress = if (item.file.name == playingFileName) currentProgress else 0f,
                                currentTimeString = if (item.file.name == playingFileName) currentTimeString else "00:00",
                                onPlay = {},
                                onPause = {},
                                onResume = {},
                                onStop = {},
                                onToggleSelect = {},
                                onRename = {},
                                onTrim = {},
                                onMove = {},
                                onShare = {},
                                onDelete = {},
                                onSeek = {},
                                onReorder = {},
                                isDragging = true,
                                themeColors = themeColors,
                                playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1
                            )
                        }
                    }
            }
        }

        
        // Dialogs
        if (showRenameDialog && itemToModify != null) {
            RenameDialog(
                currentName = itemToModify!!.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName: String -> onRenameFile(itemToModify!!, newName); showRenameDialog = false },
                themeColors = themeColors
            )
        }

        if (showMoveDialog && itemToModify != null) {
            MoveFileDialog(
                categories = categories,
                onDismiss = { showMoveDialog = false },
                onSelect = { targetCat: String -> onMoveFile(itemToModify!!, targetCat); showMoveDialog = false }
            )
        }
        
        if (showDeleteDialog && recordingToDelete != null) {
            DeleteConfirmDialog(
                title = "Delete file?",
                text = "Are you sure you want to delete '${recordingToDelete!!.name}'?",
                onDismiss = { showDeleteDialog = false },
                onConfirm = { onDeleteFile(recordingToDelete!!); showDeleteDialog = false }
            )
        }
        

        if (showNoteDialog && recordingToNote != null) {
            NoteEditDialog(
                currentNote = recordingToNote!!.note,
                fileName = recordingToNote!!.name.substringBeforeLast(".")
                    .replace(Regex("(\\d{2})_(\\d{2})_(\\d{2})"), "$1:$2:$3"),
                onDismiss = { showNoteDialog = false },
                onConfirm = { note ->
                    onSaveNote(recordingToNote!!, note)
                    showNoteDialog = false
                },
                themeColors = themeColors
            )
        }

        if (showInfoDialog && recordingToInfo != null) {
            FileInfoDialog(
                item = recordingToInfo!!,
                onDismiss = { showInfoDialog = false },
                themeColors = themeColors
            )
        }

        } // end inner Column
        } // end outer Column

        // â”€â”€ Backup & Restore overlay (Full-Screen) â”€â”€
        AnimatedVisibility(
            visible = showBackupSheet,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            BackupRestoreSheet(
                isBackupSignedIn = isBackupSignedIn,
                backupEmail = backupEmail,
                backupProgress = backupProgress,
                isBackupRunning = isBackupRunning,
                onBackupSignIn = onBackupSignIn,
                onBackupSignOut = onBackupSignOut,
                onBackupCreate = onBackupCreate,
                onBackupList = onBackupList,
                backupList = backupList,
                onRestoreFromBackup = onRestoreFromBackup,
                onDeleteBackup = onDeleteBackup,
                onClose = { showBackupSheet = false },
                themeColors = themeColors
            )
        }

        // â”€â”€ Playlist Sheets (direct Box children for fullscreen overlay) â”€â”€
        // â”€â”€ Playlist List overlay (Full-Screen) â”€â”€
        AnimatedVisibility(
            visible = showPlaylistSheet,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            BackHandler { showPlaylistSheet = false }

            PlaylistListSheet(
                playlists = playlists,
                formatDuration = { p ->
                    val totalMs = p.files.sumOf { path ->
                        val name = path.substringAfter("/")
                        onGetAllRecordings().find { it.name == name }?.durationMillis ?: 0L
                    }
                    val mins = (totalMs / 1000) / 60
                    val secs = (totalMs / 1000) % 60
                    if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                },
                getCategoryForFile = { path -> path.substringBefore("/", "General") },
                onCreateNew = {
                    editingPlaylist = Playlist(
                        id = "new_" + java.util.UUID.randomUUID().toString(),
                        name = "",
                        files = emptyList(),
                        createdAt = System.currentTimeMillis()
                    )
                    showPlaylistSheet = false // Hide list to show editor
                },
                onEdit = {
                    editingPlaylist = it
                    showPlaylistSheet = false // Hide list to show editor
                },
                onView = {
                    viewingPlaylistId = it.id
                    showPlaylistView = true
                    showPlaylistSheet = false
                },
                onPlay = {
                    onPlayPlaylist(it)
                    showPlaylistSheet = false
                    showPlaylistView = true
                },
                onPause = onPausePlay,
                onDelete = onDeletePlaylist,
                onClose = { showPlaylistSheet = false },
                themeColors = themeColors,
                currentlyPlayingPlaylistId = currentlyPlayingPlaylistId
            )
        }

        // â”€â”€ Playlist View overlay (Full-Screen) â”€â”€
        AnimatedVisibility(
            visible = showPlaylistView,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val activePlaylist = playlists.find { it.id == (viewingPlaylistId ?: currentlyPlayingPlaylistId) }
            if (activePlaylist != null) {
                BackHandler {
                    showPlaylistView = false
                    viewingPlaylistId = null
                    showPlaylistSheet = true  // Return to playlist list
                }

                PlaylistViewScreen(
                    playlist = activePlaylist,
                    playingFileName = if (activePlaylist.id == currentlyPlayingPlaylistId) playingFileName else "",
                    currentIteration = if (activePlaylist.id == currentlyPlayingPlaylistId) currentPlaylistIteration else 1,
                    isPaused = isPaused,
                    allRecordings = onGetAllRecordings(),
                    themeColors = themeColors,
                    onBack = {
                        showPlaylistView = false
                        viewingPlaylistId = null
                        showPlaylistSheet = true  // Return to playlist list
                    },
                    onPause = onPausePlay,
                    onResume = onResumePlay,
                    onStop = {
                        onStopPlay()
                        showPlaylistView = false
                        viewingPlaylistId = null
                        showPlaylistSheet = true  // Return to playlist list
                    }
                )
            }
        }

        // â”€â”€ Playlist Editor overlay (Full-Screen) â”€â”€
        AnimatedVisibility(
            visible = editingPlaylist != null,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val targetPlaylist = editingPlaylist
            if (targetPlaylist != null) {
                BackHandler {
                    editingPlaylist = null
                    showPlaylistSheet = true  // Return to playlist list
                }

                PlaylistEditorScreen(
                    playlist = targetPlaylist,
                    allCategories = categories,
                    getFilesForCategory = { cat ->
                        onGetAllRecordings().filter { item ->
                            val path = item.file.absolutePath.replace("\\", "/")
                            val itemCat = if (path.contains("Music/AudioLoop/")) {
                                val subPath = path.substringAfter("Music/AudioLoop/")
                                if (subPath.contains("/")) subPath.substringBefore("/") else "General"
                            } else {
                                val parentName = item.file.parentFile?.name
                                if (parentName == "files" || parentName == null) "General" else parentName
                            }
                            itemCat == cat
                        }
                    },
                    getCategoryForFile = { path -> path.substringBefore("/", "General") },
                    resolveFileName = { path -> path.substringAfter("/") },
                    resolveFileDuration = { path ->
                        val fileName = path.substringAfter("/")
                        onGetAllRecordings().find { it.name == fileName }?.durationString ?: "00:00"
                    },
                    onSave = {
                        onSavePlaylist(it)
                        editingPlaylist = null
                        showPlaylistSheet = true  // Return to playlist list after save
                    },
                    onClose = {
                        editingPlaylist = null
                        showPlaylistSheet = true  // Return to playlist list
                    },
                    themeColors = themeColors
                )
            }
        }
        // â”€â”€ Trim Editor overlay (Full-Screen Studio) â”€â”€
        AnimatedVisibility(
            visible = showTrimDialog,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val trimItem = recordingToTrim
            if (trimItem != null) {
                BackHandler { showTrimDialog = false }
                
                TrimAudioScreen(
                    file = trimItem.file,
                    uri = trimItem.uri,
                    durationMs = trimItem.durationMillis,
                    onDismiss = { showTrimDialog = false },
                    onConfirm = { start, end, replace, remove, fadeIn, fadeOut, normalize ->
                        onTrimFile(trimItem.file, start, end, replace, remove, fadeIn, fadeOut, normalize)
                        showTrimDialog = false
                    },
                    themeColors = themeColors
                )
            }
        }
    } // end Box
} // end AudioLoopMainScreen
// Dialog Helpers adapted from MainActivity

// Dialog Definitions moved to ui/Dialogs.kt

// TrimAudioScreen moved to ui/TrimAudioDialog.kt




