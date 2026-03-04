package com.example.audioloop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.Playlist
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.FileItem
import com.example.audioloop.ui.formatSessionTime
import com.example.audioloop.ui.theme.Red400
import com.example.audioloop.ui.theme.Red500
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun LibraryTab(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    onImportClick: () -> Unit
) {
    val recordingItems = viewModel.getFilteredItems()
    val themeColors = uiState.currentTheme.palette
    val uiRecordingItems = remember { mutableStateListOf<RecordingItem>() }

    LaunchedEffect(recordingItems) {
        uiRecordingItems.clear()
        uiRecordingItems.addAll(recordingItems)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        // Selection/Header Row
        LibraryHeader(uiState, viewModel, recordingItems, onImportClick)

        // Banner if playlist is playing
        val playlists = uiState.playlists
        val activePlaylist = if (uiState.currentlyPlayingPlaylistId != null)
            playlists.find { it.id == uiState.currentlyPlayingPlaylistId } else null

        if (activePlaylist != null) {
            PlaylistPlayingBanner(activePlaylist, uiState, viewModel)
        }

        // Main File List with Drag & Drop
        Box(modifier = Modifier.weight(1f)) {
            if (uiRecordingItems.isEmpty()) {
                EmptyLibraryState(uiState.searchQuery.isNotEmpty(), onImportClick)
            } else {
                DraggableFileList(uiState, viewModel, uiRecordingItems)
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    recordingItems: List<RecordingItem>,
    onImportClick: () -> Unit
) {
    val themeColors = uiState.currentTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.isSelectionMode && uiState.selectedFiles.isNotEmpty()) {
            SelectionActionBar(uiState, viewModel, recordingItems)
        } else {
            NormalHeader(uiState, onImportClick, onSelectClick = { viewModel.setSelectionMode(true) })
        }
    }

    // Cancel hint when in selection mode with empty selection
    if (uiState.isSelectionMode && uiState.selectedFiles.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap files to select",
                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            )
            Surface(
                onClick = { viewModel.clearSelection() },
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
}

@Composable
private fun SelectionActionBar(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    recordingItems: List<RecordingItem>
) {
    val themeColors = uiState.currentTheme.palette

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Play selected
        Button(
            onClick = {
                val orderedSelection = uiState.selectedFiles.toList()
                val filesToPlay = orderedSelection.mapNotNull { name ->
                    recordingItems.find { it.name == name }
                }
                if (filesToPlay.isNotEmpty()) {
                    viewModel.startPlaylistPlayback(filesToPlay, loop = false, speed = 1.0f) {}
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("PLAY ${uiState.selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        // Save as Playlist
        Button(
            onClick = {
                val orderedSelection = uiState.selectedFiles.toList()
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
                viewModel.openPlaylistEditor(Playlist(
                    id = "new_" + java.util.UUID.randomUUID().toString(),
                    name = "",
                    files = relativePaths,
                    createdAt = System.currentTimeMillis()
                ))
                viewModel.clearSelection()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f)
        ) {
            Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("PLAYLIST", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        if (uiState.selectedFiles.size >= 2) {
            Button(
                onClick = {
                    val orderedSelection = uiState.selectedFiles.toList()
                    val filesToMerge = orderedSelection.mapNotNull { name ->
                        recordingItems.find { it.name == name }
                    }
                    if (filesToMerge.size >= 2) {
                        viewModel.mergeFiles(filesToMerge)
                        viewModel.clearSelection()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.secondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("MERGE ${uiState.selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun NormalHeader(
    uiState: AudioLoopUiState,
    onImportClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Text(
        text = "Files (${uiState.currentCategory}):",
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onImportClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("+ Add", fontSize = 12.sp, color = Color.White)
        }
        Surface(
            onClick = onSelectClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(AppIcons.DoneAll, contentDescription = "Select", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                Text("Select", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun PlaylistPlayingBanner(
    activePlaylist: Playlist,
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel
) {
    val themeColors = uiState.currentTheme.palette
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
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                )
            )
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.3f * glowAlpha),
                RoundedCornerShape(14.dp)
            )
            .clickable { uiState.currentlyPlayingPlaylistId?.let { viewModel.openPlaylistView(it) } }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f + 0.4f * glowAlpha))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "NOW PLAYING",
                    style = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                )
                Text(
                    activePlaylist.name,
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (uiState.currentSessionElapsedMs > 0L) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatSessionTime(uiState.currentSessionElapsedMs),
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    val totalTodayMs = (uiState.practiceTodayMinutes * 60_000L).toLong() + uiState.currentSessionElapsedMs
                    Text(
                        "Today ${formatSessionTime(totalTodayMs)}",
                        style = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
        // Pills
        val loopText = when (activePlaylist.loopCount) {
            -1 -> "∞"
            else -> "${uiState.currentPlaylistIteration} / ${activePlaylist.loopCount}×"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill(text = "🔄 $loopText", color = themeColors.primary200, bgColor = themeColors.primary700.copy(alpha = 0.55f))
            if (activePlaylist.speed != 1.0f) {
                Pill(text = "🎚 ${String.format("%.1f", activePlaylist.speed)}×", color = MaterialTheme.colorScheme.onSurface)
            }
            if (activePlaylist.gapSeconds > 0) {
                Pill(text = "⌛ ${activePlaylist.gapSeconds}s gap", color = MaterialTheme.colorScheme.onSurface)
            }
            if (activePlaylist.shuffle) {
                Pill(text = "🔀 Shuffle", color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text("${activePlaylist.files.size} tracks", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, bgColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyLibraryState(isSearching: Boolean, onImportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearching) AppIcons.Search else AppIcons.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = if (isSearching) "No matching files" else "No recordings yet",
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSearching) "Try a different search term"
                   else "Tap the record button above to create your first recording, or import an audio file.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = onImportClick,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Audio File", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DraggableFileList(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    uiRecordingItems: MutableList<RecordingItem>
) {
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
            val firstVisible = currentVisibleItems.firstOrNull()
            val lastVisible = currentVisibleItems.lastOrNull()
            if (firstVisible != null && overlayCenterY < firstVisible.offset) targetIndex = firstVisible.index
            else if (lastVisible != null && overlayCenterY > lastVisible.offset + lastVisible.size) targetIndex = lastVisible.index
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
            while (isActive) {
                val scrolled = scrollState.scrollBy(overscrollSpeed)
                if (scrolled != 0f) checkForSwap()
                delay(10)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                            viewModel.reorderFinished(uiRecordingItems.map { it.file })
                            draggingItemIndex = -1
                            draggingItem = null
                        }
                    }
                }
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            itemsIndexed(uiRecordingItems, key = { _, item -> item.name }) { index, item ->
                val isPlaying = item.file.name == uiState.playingFileName
                val alpha = if (draggingItemIndex == index) 0f else 1f
                FileItem(
                    modifier = Modifier.alpha(alpha),
                    item = item,
                    isPlaying = isPlaying,
                    isPaused = if (isPlaying) uiState.isPaused else false,
                    isSelectionMode = uiState.isSelectionMode,
                    isSelected = uiState.selectedFiles.contains(item.name),
                    selectionOrder = uiState.selectedFiles.indexOf(item.name) + 1,
                    onPlay = { viewModel.playFile(item) },
                    onPause = { viewModel.pausePlaying() },
                    onResume = { viewModel.resumePlaying() },
                    onStop = { viewModel.stopPlayingAndReset() },
                    onToggleSelect = { viewModel.toggleFileSelection(item.name) },
                    onRename = { viewModel.openRenameDialog(item) },
                    onTrim = { viewModel.openTrimDialog(item) },
                    onMove = { viewModel.openMoveDialog(item) },
                    onShare = { viewModel.shareFile(item) },
                    onDelete = { viewModel.openDeleteDialog(item) },
                    onSplit = { viewModel.splitFile(item) },
                    onNormalize = { viewModel.normalizeFile(item) },
                    onAutoTrim = { viewModel.autoTrimFile(item) },
                    onEditNote = { viewModel.openNoteDialog(item) },
                    onShowInfo = { viewModel.openInfoDialog(item) },
                    currentProgress = if (isPlaying) uiState.currentProgress else 0f,
                    currentTimeString = if (isPlaying) uiState.currentTimeString else "00:00",
                    onSeek = { viewModel.seekTo(it) },
                    onReorder = { },
                    isDragging = draggingItemIndex == index,
                    themeColors = uiState.currentTheme.palette,
                    playlistPosition = if (uiState.currentlyPlayingPlaylistId != null) {
                        uiState.playlists.find { it.id == uiState.currentlyPlayingPlaylistId }?.files?.indexOf(item.name) ?: -1
                    } else -1,
                    waveformData = viewModel.waveformCache[item.file.absolutePath] ?: emptyList(),
                    onSeekAbsolute = { viewModel.seekAbsolute(it) }, // use absolute ms
                    shadowCountdownText = if (isPlaying) uiState.shadowCountdownText else ""
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
                isPlaying = item.file.name == uiState.playingFileName,
                isPaused = if (item.file.name == uiState.playingFileName) uiState.isPaused else false,
                isSelectionMode = uiState.isSelectionMode,
                isSelected = uiState.selectedFiles.contains(item.name),
                selectionOrder = uiState.selectedFiles.indexOf(item.name) + 1,
                currentProgress = if (item.file.name == uiState.playingFileName) uiState.currentProgress else 0f,
                currentTimeString = if (item.file.name == uiState.playingFileName) uiState.currentTimeString else "00:00",
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
                themeColors = uiState.currentTheme.palette,
                playlistPosition = -1
            )
        }
    }
}
