package com.example.audioloop.ui

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

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
import android.media.MediaPlayer
import com.example.audioloop.AppIcons
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.geometry.Offset
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
    recordingItems: List<RecordingItem>,
    categories: List<String>,    // State
    currentCategory: String,
    currentProgress: Float,
    currentTimeString: String,
    playingFileName: String,
    isPaused: Boolean,
    
    // Callbacks
    onPlayingFileNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,

    onReorderCategory: (String, Int) -> Unit, // -1 up, +1 down
    onReorderCategories: (List<String>) -> Unit,
    
    onMoveFile: (RecordingItem, String) -> Unit,
    onReorderFile: (File, Int) -> Unit,
    onReorderFinished: (List<File>) -> Unit,
    
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    
    onStartPlaylist: (List<RecordingItem>, Boolean, Float, () -> Unit) -> Unit,
    onPlaylistUpdate: () -> Unit,
    
    onSpeedChange: (Float) -> Unit,
    onLoopCountChange: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onPausePlay: () -> Unit,
    onResumePlay: () -> Unit,
    onStopPlay: () -> Unit,
    onDeleteFile: (RecordingItem) -> Unit,
    onShareFile: (RecordingItem) -> Unit,
    onRenameFile: (RecordingItem, String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onTrimFile: (File, Long, Long, Boolean, Boolean) -> Unit,
    selectedSpeed: Float,
    selectedLoopCount: Int,
    isShadowing: Boolean,
    onShadowingChange: (Boolean) -> Unit,
    
    usePublicStorage: Boolean,
    onPublicStorageChange: (Boolean) -> Unit,
    sleepTimerRemainingMs: Long = 0L,
    selectedSleepMinutes: Int = 0,
    onSleepTimerChange: (Int) -> Unit = {},
    currentTheme: com.example.audioloop.ui.theme.AppTheme = com.example.audioloop.ui.theme.AppTheme.SLATE,
    onThemeChange: (com.example.audioloop.ui.theme.AppTheme) -> Unit = {}
) {
    // Get theme colors
    val themeColors = currentTheme.palette
    var settingsOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Speech") }
    // Selection Mode State
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var playingPlaylistFiles by remember { mutableStateOf(listOf<String>()) } // Track files in current playlist
    var showCategorySheet by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    
    // Local source of truth for the list to allow live updates
    val uiRecordingItems = remember { mutableStateListOf<RecordingItem>() }
    // Sync when source changes (and not dragging)
    LaunchedEffect(recordingItems) {
         uiRecordingItems.clear()
         uiRecordingItems.addAll(recordingItems)
    }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    
    var itemToModify by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToTrim by remember { mutableStateOf<RecordingItem?>(null) }
    var draggingItemName by remember { mutableStateOf<String?>(null) }
    
    val density = LocalDensity.current
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportFile(uri)
    }
    
    // Helper to toggle selection
    fun toggleSelection(name: String) {
        selectedFiles = if (selectedFiles.contains(name)) {
            selectedFiles - name
        } else {
            selectedFiles + name
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "AudioLoop",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = themeColors.onPrimaryContainer
                        )
                    ) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = themeColors.primaryContainer,
                    titleContentColor = themeColors.onPrimaryContainer,
                    actionIconContentColor = themeColors.onPrimaryContainer,
                    navigationIconContentColor = themeColors.onPrimaryContainer
                ),
                actions = {
                    // Playlist Toggle
                    IconButton(onClick = { 
                        isSelectionMode = !isSelectionMode
                        if (!isSelectionMode) selectedFiles = emptySet()
                    }) {
                        Icon(
                            imageVector = if (isSelectionMode) AppIcons.Close else AppIcons.PlayArrow, // Using PlayArrow as Playlist icon for now or List
                            contentDescription = if (isSelectionMode) "Cancel Selection" else "Select Files",
                            tint = if (isSelectionMode) themeColors.onPrimaryContainer else themeColors.onPrimaryContainer
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // FIXED CONTROLS AREA
            
            // 1. Playback Settings Bar (Collapsible/Condensed)
            Surface(
                color = themeColors.primaryContainer.copy(alpha = 0.3f), // Subtle background
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Quick Status or Expand Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsOpen = !settingsOpen },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!settingsOpen) {
                            // Summary View
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Settings, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${selectedSpeed}x Speed  •  ${if(selectedLoopCount == -1) "∞" else "$selectedLoopCount"} Loop  •  ${if(sleepTimerRemainingMs > 0) "Sleep On" else "Sleep Off"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = themeColors.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Text("Playback Settings", style = MaterialTheme.typography.titleSmall, color = themeColors.primary)
                        }
                        Icon(
                            imageVector = if (settingsOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                            contentDescription = null,
                            tint = themeColors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Expanded Settings
                    AnimatedVisibility(visible = settingsOpen) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                             // --- Speed ---
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Text("Speed", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium)
                                 Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                     listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f).forEach { s ->
                                         FilterChip(
                                             selected = selectedSpeed == s,
                                             onClick = { onSpeedChange(s) },
                                             label = { Text("${s}x") },
                                             colors = FilterChipDefaults.filterChipColors(
                                                 selectedContainerColor = themeColors.primary,
                                                 selectedLabelColor = themeColors.onPrimary
                                             )
                                         )
                                     }
                                 }
                             }
                             // --- Repeats ---
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Text("Loop", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium)
                                 Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                     listOf(1, 5, -1).forEach { r ->
                                         FilterChip(
                                             selected = selectedLoopCount == r,
                                             onClick = { onLoopCountChange(r) },
                                             label = { Text(if (r == -1) "∞" else "${r}x") },
                                             colors = FilterChipDefaults.filterChipColors(
                                                 selectedContainerColor = themeColors.primary,
                                                 selectedLabelColor = themeColors.onPrimary
                                             )
                                         )
                                     }
                                 }
                             }
                             // --- Theme ---
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Text("Theme", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelMedium)
                                 Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                     com.example.audioloop.ui.theme.AppTheme.entries.forEach { theme ->
                                         FilterChip(
                                             selected = currentTheme == theme,
                                             onClick = { onThemeChange(theme) },
                                             label = { Text(theme.displayName) },
                                             colors = FilterChipDefaults.filterChipColors(
                                                 selectedContainerColor = theme.palette.primary,
                                                 selectedLabelColor = theme.palette.onPrimary
                                             )
                                         )
                                     }
                                 }
                             }
                        }
                    }
                }
            }

            // 2. Recording Area (Visible unless Selecting)
            AnimatedVisibility(visible = !isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(64.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Switch
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                             listOf("Speech" to AppIcons.Mic, "Stream" to AppIcons.Radio).forEach { (m, icon) ->
                                 val active = mode == m
                                 FilterChip(
                                     selected = active,
                                     onClick = { mode = m },
                                     label = { Text(m) },
                                     leadingIcon = { Icon(icon, null, modifier = Modifier.size(16.dp)) },
                                     shape = RoundedCornerShape(16.dp),
                                     colors = FilterChipDefaults.filterChipColors(
                                          selectedContainerColor = themeColors.secondaryContainer,
                                          selectedLabelColor = themeColors.onSecondaryContainer
                                     )
                                 )
                             }
                        }
                        
                        // Record Button
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                             FilledIconButton(
                                 onClick = {
                                    if (isRecording) {
                                        onStopRecord()
                                        isRecording = false
                                    } else {
                                        val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                                        val dateStr = dateFormat.format(java.util.Date())
                                        val prefix = if (mode == "Speech") "Speech" else "Stream"
                                        val name = "${prefix}_$dateStr"
                                        if (onStartRecord(name, mode == "Stream")) isRecording = true
                                    }
                                 },
                                 modifier = Modifier.size(56.dp),
                                 colors = IconButtonDefaults.filledIconButtonColors(
                                     containerColor = if (isRecording) Red500 else themeColors.primary,
                                     contentColor = Color.White
                                 )
                             ) {
                                 if (isRecording) {
                                     Box(modifier = Modifier.size(24.dp).background(Color.White, RoundedCornerShape(4.dp)))
                                 } else {
                                     Icon(AppIcons.Mic, null, modifier = Modifier.size(28.dp))
                                 }
                             }
                        }
                    }
                }
            }
            
            // 3. Categories with Edit Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = currentCategory == cat,
                            onClick = { onCategoryChange(cat) },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = themeColors.primaryContainer,
                                selectedLabelColor = themeColors.onPrimaryContainer
                            )
                        )
                    }
                }
                IconButton(onClick = { showCategorySheet = true }) {
                    Icon(AppIcons.Edit, "Edit Categories", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            // SCROLLABLE LIST AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                 val scrollState = rememberLazyListState()
                 
                 // Drag Logic Variables
                 var draggingItemIndex by remember { mutableIntStateOf(-1) }
                 var draggingItem by remember { mutableStateOf<RecordingItem?>(null) }
                 var overlayOffsetY by remember { mutableFloatStateOf(0f) }
                 var grabOffsetY by remember { mutableFloatStateOf(0f) }
                 var overscrollSpeed by remember { mutableFloatStateOf(0f) }

                 // Define Drag Logic (Re-implemented for cleaner structure)
                 // Keep pointerInput on the Box
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .pointerInput(Unit) {
                             val gripWidth = 60.dp.toPx() // Only drag from left icon area
                             awaitEachGesture {
                                 val down = awaitFirstDown(requireUnconsumed = false)
                                 val y = down.position.y
                                 val x = down.position.x
                                 
                                 // Identify item under finger
                                 val hitItem = scrollState.layoutInfo.visibleItemsInfo.find { 
                                     y >= it.offset && y <= it.offset + it.size
                                 }
                                 
                                 if (hitItem != null && x <= gripWidth) {
                                     // Start Drag
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
                                             if (change == null || !change.pressed) { isDragging = false; break }
                                             
                                             if (change.positionChanged()) {
                                                 change.consume()
                                                 val newY = change.position.y
                                                 overlayOffsetY = newY - grabOffsetY
                                                 
                                                 // Auto-scroll checks
                                                 val viewportHeight = size.height.toFloat()
                                                 val threshold = 60.dp.toPx()
                                                 val maxSpeed = 20.dp.toPx()
                                                 if (newY < threshold) overscrollSpeed = -maxSpeed * ((threshold - newY) / threshold).coerceIn(0.1f, 1f)
                                                 else if (newY > viewportHeight - threshold) overscrollSpeed = maxSpeed * ((newY - (viewportHeight - threshold)) / threshold).coerceIn(0.1f, 1f)
                                                 else overscrollSpeed = 0f
                                                 
                                                 // Swap Logic
                                                 val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                                                 if (currentVisibleItems.isNotEmpty()) {
                                                     val overlayCenterY = overlayOffsetY + (hitItem.size / 2)
                                                     val hoveredItem = currentVisibleItems.find { overlayCenterY >= it.offset && overlayCenterY <= it.offset + it.size }
                                                     val targetIndex = hoveredItem?.index ?: if (overlayCenterY < currentVisibleItems.first().offset) currentVisibleItems.first().index else if (overlayCenterY > currentVisibleItems.last().offset + currentVisibleItems.last().size) currentVisibleItems.last().index else -1
                                                     
                                                     if (targetIndex != -1 && targetIndex != draggingItemIndex && targetIndex in uiRecordingItems.indices) {
                                                         val itemToMove = uiRecordingItems.removeAt(draggingItemIndex)
                                                         uiRecordingItems.add(targetIndex, itemToMove)
                                                         draggingItemIndex = targetIndex
                                                     }
                                                 }
                                             }
                                         }
                                         // End Drag
                                         overscrollSpeed = 0f
                                         onReorderFinished(uiRecordingItems.map { it.file })
                                         draggingItemIndex = -1
                                         draggingItem = null
                                     }
                                 }
                             }
                         }
                 ) {
                     // Empty Box content, just handling input
                 }

                 // Auto-scroll Effect
                 LaunchedEffect(overscrollSpeed) {
                     if (overscrollSpeed != 0f) {
                         while (true) {
                             val scrolled = scrollState.scrollBy(overscrollSpeed)
                             if (scrolled != 0f) { /* Trigger swap not needed here, pointerInput handles layout changes? Actually pointerInput needs move events. */ }
                             kotlinx.coroutines.delay(10)
                         }
                     }
                 }

                 // The List
                 LazyColumn(
                     state = scrollState,
                     contentPadding = PaddingValues(bottom = 80.dp),
                     modifier = Modifier.fillMaxSize()
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
                             currentProgress = if (isPlaying) currentProgress else 0f,
                             currentTimeString = if (isPlaying) currentTimeString else "00:00",
                             onSeek = onSeekTo,
                             onReorder = {}, // Legacy disabled
                             isDragging = draggingItemIndex == index,
                             themeColors = themeColors,
                             playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1
                         )
                         HorizontalDivider(modifier = Modifier.padding(start=72.dp), color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f))
                     }
                 }
                 
                 // Drag Overlay
                 if (draggingItem != null) {
                     FileItem(
                         modifier = Modifier
                             .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                             .shadow(8.dp, RoundedCornerShape(12.dp))
                             .zIndex(1f),
                         item = draggingItem!!,
                         isPlaying = draggingItem!!.file.name == playingFileName,
                         themeColors = themeColors,
                         // Stub callbacks for overlay
                         isDragging = true,
                         onPlay = {}, onPause = {}, onResume = {}, onStop = {}, onToggleSelect = {}, onRename = {}, onTrim = {}, onMove = {}, onShare = {}, onDelete = {}, onSeek = {}, onReorder = {}, currentProgress = 0f, currentTimeString = "00:00", isPaused = false, isSelectionMode = false, isSelected = false, selectionOrder = 0, playlistPosition = 0
                     )
                 }

                 // Floating Selection Action Button (if items selected)
                 androidx.compose.animation.AnimatedVisibility(
                     visible = isSelectionMode && selectedFiles.isNotEmpty(),
                     modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                 ) {
                     ExtendedFloatingActionButton(
                         onClick = {
                             val orderedSelection = selectedFiles.toList()
                             val filesToPlay = orderedSelection.mapNotNull { name -> recordingItems.find { it.name == name } }
                             if (filesToPlay.isNotEmpty()) {
                                 playingPlaylistFiles = filesToPlay.map { it.name }
                                 onStartPlaylist(filesToPlay, selectedLoopCount == -1, selectedSpeed) { playingPlaylistFiles = emptyList() }
                                 isSelectionMode = false
                                 selectedFiles = emptySet()
                             }
                         },
                         containerColor = themeColors.primary,
                         contentColor = themeColors.onPrimary
                     ) {
                         Icon(AppIcons.PlayArrow, null)
                         Spacer(Modifier.width(8.dp))
                         Text("Play ${selectedFiles.size} Selected")
                     }
                 }
            }
        }
    }

    // Dialogs (unchanged references)
    if (showCategorySheet) {
        CategoryManagementSheet(categories, currentCategory, onCategoryChange, onAddCategory, onRenameCategory, onDeleteCategory, onReorderCategories, { showCategorySheet = false }, themeColors)
    }
    if (showRenameDialog && itemToModify != null) {
        RenameDialog(itemToModify!!.name, { showRenameDialog = false }, { newName -> onRenameFile(itemToModify!!, newName); showRenameDialog = false }, themeColors)
    }
    if (showMoveDialog && itemToModify != null) {
        MoveFileDialog(categories, { showMoveDialog = false }, { targetCat -> onMoveFile(itemToModify!!, targetCat); showMoveDialog = false })
    }
    if (showDeleteDialog && recordingToDelete != null) {
        DeleteConfirmDialog("Delete file?", "Are you sure you want to delete '${recordingToDelete!!.name}'?", { showDeleteDialog = false }, { onDeleteFile(recordingToDelete!!); showDeleteDialog = false })
    }
    if (showTrimDialog && recordingToTrim != null) {
         TrimAudioDialog(recordingToTrim!!.file, recordingToTrim!!.uri, recordingToTrim!!.durationMillis, { showTrimDialog = false }, { start, end, replace, sel -> onTrimFile(recordingToTrim!!.file, start, end, replace, sel); showTrimDialog = false }, themeColors)
    }
}

