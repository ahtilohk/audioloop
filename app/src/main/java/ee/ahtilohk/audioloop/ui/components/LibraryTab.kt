package ee.ahtilohk.audioloop.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.AudioLoopUiState
import ee.ahtilohk.audioloop.AudioLoopViewModel
import ee.ahtilohk.audioloop.Playlist
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.SortMode
import ee.ahtilohk.audioloop.ui.FileItem
import ee.ahtilohk.audioloop.ui.PracticeProgressCard
import ee.ahtilohk.audioloop.ui.formatSessionTime
import ee.ahtilohk.audioloop.ui.theme.Red500
import ee.ahtilohk.audioloop.ui.theme.LocalSpacing
import ee.ahtilohk.audioloop.ui.theme.LocalRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun LibraryTab(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    isWide: Boolean = false,
    onImportClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val recordingItems = uiState.filteredItems
    val themeColors = uiState.currentTheme.palette
    val uiRecordingItems = remember { mutableStateListOf<RecordingItem>() }

    LaunchedEffect(recordingItems) {
        uiRecordingItems.clear()
        uiRecordingItems.addAll(recordingItems)
    }

    val spacing = LocalSpacing.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = spacing.small)
    ) {
        if (uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = uiState.currentTheme.palette.primary,
                trackColor = Color.Transparent
            )
        }
        // Selection/Header Row
        LibraryHeader(uiState, viewModel, recordingItems, onImportClick)

        // Smart Coach (Progress & Recommendations)
        if (uiState.isSmartCoachEnabled && uiState.practiceRecommendation.title.isNotEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                PracticeProgressCard(
                    weeklyMinutes = uiState.practiceWeeklyMinutes,
                    weeklyGoal = uiState.practiceWeeklyGoal,
                    streak = uiState.practiceStreak,
                    todayMinutes = uiState.practiceTodayMinutes,
                    weeklySessions = uiState.practiceWeeklySessions,
                    weeklyEdits = uiState.practiceWeeklyEdits,
                    recommendation = uiState.practiceRecommendation,
                    goalProgress = uiState.practiceGoalProgress,
                    themeColors = themeColors,
                    onStartRecommended = { suggested -> viewModel.startRecommendedSession(suggested) },
                    onViewDetails = { viewModel.setShowPracticeStats(true) },
                    isExpanded = uiState.isSmartCoachExpanded,
                    onToggleExpanded = { viewModel.toggleSmartCoach() },
                    isPlaying = uiState.playingFileName.isNotEmpty(),
                    currentSessionElapsedMs = uiState.currentSessionElapsedMs,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (uiState.searchQuery.isNotEmpty()) {
            SearchFilterBadge(uiState, onClearQuery = { viewModel.updateSearchQuery("") }, onClearCategory = { viewModel.setSearchCategory(null) })
        }

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
                EmptyLibraryState(uiState.searchQuery.isNotEmpty(), viewModel, onImportClick, onNavigate)
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

    val spacing = LocalSpacing.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large)
            .padding(bottom = spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.isSelectionMode && uiState.selectedFiles.isNotEmpty()) {
            SelectionActionBar(uiState, viewModel, recordingItems)
        } else {
            NormalHeader(uiState, viewModel, onImportClick, onSelectClick = { viewModel.setSelectionMode(true) })
        }
    }

    // Nudge for creating playlist when 2+ files selected
    AnimatedVisibility(
        visible = uiState.isSelectionMode && uiState.selectedFiles.size >= 2,
        enter = expandIn() + fadeIn(),
        exit = shrinkOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.small)
                .background(themeColors.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .border(1.dp, themeColors.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.QueueMusic, 
                    contentDescription = null, 
                    tint = themeColors.primary, 
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.nudge_try_playlist),
                    style = MaterialTheme.typography.labelMedium.copy(color = themeColors.primary),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Cancel hint when in selection mode with empty selection
    if (uiState.isSelectionMode && uiState.selectedFiles.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.large, end = spacing.large, bottom = spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_tap_to_select),
                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            )
            Surface(
                onClick = { viewModel.setSelectionMode(false) },
                shape = MaterialTheme.shapes.small,
                color = Red500.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Red500.copy(alpha = 0.5f)),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.a11y_cancel_selection), tint = Red500, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.btn_cancel), fontSize = 12.sp, color = Red500, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play selected
        Button(
            onClick = {
                val orderedSelection = uiState.selectedFiles.toList()
                val filesToPlay = orderedSelection.mapNotNull { path ->
                    recordingItems.find { it.file.absolutePath == path }
                }
                if (filesToPlay.isNotEmpty()) {
                    viewModel.startPlaylistPlayback(filesToPlay, loop = false, speed = 1.0f) {}
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp).weight(1.1f)
        ) {
            Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.library_play_count, uiState.selectedFiles.size).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Save as Playlist
        FilledTonalButton(
            onClick = {
                val orderedSelection = uiState.selectedFiles.toList()
                val relativePaths = orderedSelection.map { path ->
                    val item = recordingItems.find { it.file.absolutePath == path }
                    if (item != null) {
                        val pathSlash = item.file.absolutePath.replace("\\", "/")
                        val cat = if (pathSlash.contains("Music/AudioLoop/")) {
                            val subPath = pathSlash.substringAfter("Music/AudioLoop/")
                            if (subPath.contains("/")) subPath.substringBefore("/") else ""
                        } else ""
                        if (cat.isNotEmpty()) "$cat/${item.name}" else item.name
                    } else path
                }
                viewModel.openPlaylistEditor(Playlist(
                    id = "new_" + java.util.UUID.randomUUID().toString(),
                    name = "",
                    files = relativePaths,
                    createdAt = System.currentTimeMillis()
                ))
                viewModel.clearSelection()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(36.dp).weight(1f)
        ) {
            Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.library_playlist).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (uiState.selectedFiles.size >= 2) {
            FilledTonalButton(
                onClick = {
                    val orderedSelection = uiState.selectedFiles.toList()
                    val filesToMerge = orderedSelection.mapNotNull { path ->
                        recordingItems.find { it.file.absolutePath == path }
                    }
                    if (filesToMerge.size >= 2) {
                        viewModel.mergeFiles(filesToMerge)
                        viewModel.clearSelection()
                    }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = themeColors.secondary.copy(alpha = 0.15f),
                    contentColor = themeColors.secondary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp).weight(1f)
            ) {
                Text(
                    stringResource(R.string.library_merge_count, uiState.selectedFiles.size).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete selected
        IconButton(
            onClick = { viewModel.openMultiDeleteDialog() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = stringResource(R.string.btn_delete),
                tint = Red500
            )
        }
    }
}

@Composable
private fun NormalHeader(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    onImportClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    val titleText = if (uiState.searchQuery.isNotEmpty()) {
        val count = viewModel.getFilteredItems().size
        if (uiState.searchCategory != null) {
             stringResource(R.string.search_header_category, uiState.searchCategory, count)
        } else {
             stringResource(R.string.search_header_all, count)
        }
    } else {
        stringResource(R.string.library_files_header, uiState.currentCategory)
    }
    val haptic = LocalHapticFeedback.current

    Text(
        text = titleText,
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                onImportClick()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(stringResource(R.string.library_add), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
        Surface(
            onClick = {
                onSelectClick()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
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
                Icon(AppIcons.DoneAll, contentDescription = stringResource(R.string.library_select), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.library_select), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        SortMenuButton(currentSort = uiState.sortMode, onSortSelected = { viewModel.setSortMode(it) })
    }
}

@Composable
private fun SortMenuButton(currentSort: SortMode, onSortSelected: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    
    Box {
        Surface(
            onClick = { 
                expanded = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
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
                Icon(AppIcons.Sort, contentDescription = stringResource(R.string.menu_sort), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.menu_sort), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            SortMenuItem(SortMode.DATE_DESC, R.string.sort_date_desc, currentSort, onSortSelected) { expanded = false }
            SortMenuItem(SortMode.DATE_ASC, R.string.sort_date_asc, currentSort, onSortSelected) { expanded = false }
            SortMenuItem(SortMode.NAME_ASC, R.string.sort_name_asc, currentSort, onSortSelected) { expanded = false }
            SortMenuItem(SortMode.NAME_DESC, R.string.sort_name_desc, currentSort, onSortSelected) { expanded = false }
            SortMenuItem(SortMode.LENGTH_DESC, R.string.sort_length_desc, currentSort, onSortSelected) { expanded = false }
            SortMenuItem(SortMode.LENGTH_ASC, R.string.sort_length_asc, currentSort, onSortSelected) { expanded = false }
        }
    }
}

@Composable
private fun SortMenuItem(
    mode: SortMode,
    labelRes: Int,
    currentSort: SortMode,
    onSortSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), color = if (mode == currentSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = { 
            val icon = if (mode == currentSort) AppIcons.Done else null
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            else Spacer(Modifier.size(18.dp))
        },
        onClick = { onSortSelected(mode); onDismiss() }
    )
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
            .clip(RoundedCornerShape(16.dp))
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
                RoundedCornerShape(16.dp)
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
                    stringResource(R.string.library_now_playing),
                    style = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                )
                Text(
                    activePlaylist.name,
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (uiState.currentSessionElapsedMs > 0L) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatSessionTime(uiState.currentSessionElapsedMs),
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                    val totalTodayMs = (uiState.practiceTodayMinutes * 60_000L).toLong() + uiState.currentSessionElapsedMs
                    Text(
                        stringResource(R.string.library_today_format, formatSessionTime(totalTodayMs)),
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
                Pill(text = "⌛ " + stringResource(R.string.label_gap_pill, activePlaylist.gapSeconds), color = MaterialTheme.colorScheme.onSurface)
            }
            if (activePlaylist.shuffle) {
                Pill(text = "🔀 " + stringResource(R.string.label_shuffle), color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.library_tracks_count, activePlaylist.files.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
private fun EmptyLibraryState(isSearching: Boolean, viewModel: AudioLoopViewModel, onImportClick: () -> Unit, onNavigate: (String) -> Unit) {
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
            text = if (isSearching) stringResource(R.string.label_no_matching_files) else stringResource(R.string.label_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSearching) stringResource(R.string.label_try_different_search)
                   else stringResource(R.string.label_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (!isSearching) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Action: Record
                Button(
                    onClick = { onNavigate(ee.ahtilohk.audioloop.ui.navigation.Screen.Record.route) }, // Switch to Record tab
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) {
                    Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.record_start), fontWeight = FontWeight.Bold)
                }

                // Secondary Action: Import
                OutlinedButton(
                    onClick = onImportClick,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(0.7f).height(48.dp)
                ) {
                    Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.btn_import_audio), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    val haptic = LocalHapticFeedback.current
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
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            itemsIndexed(uiRecordingItems, key = { _, item -> item.file.absolutePath }) { index, item ->
                val isPlaying = item.name == uiState.playingFileName
                val alpha = if (draggingItemIndex == index) 0f else 1f
                FileItem(
                    modifier = Modifier.alpha(alpha),
                    item = item,
                    isPlaying = isPlaying,
                    isPaused = if (isPlaying) uiState.isPaused else false,
                    isSelectionMode = uiState.isSelectionMode,
                    isSelected = uiState.selectedFiles.contains(item.file.absolutePath),
                    selectionOrder = uiState.selectedFiles.toList().indexOf(item.file.absolutePath) + 1,
                    onPlay = { viewModel.playFile(item) },
                    onPause = { viewModel.pausePlaying() },
                    onResume = { viewModel.resumePlaying() },
                    onStop = { viewModel.stopPlayingAndReset() },
                    onToggleSelect = { viewModel.toggleFileSelection(item.file.absolutePath) },
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
                    shadowCountdownText = if (isPlaying) uiState.shadowCountdownText else "",
                    abLoopStart = if (isPlaying) uiState.abLoopStart else -1f,
                    abLoopEnd = if (isPlaying) uiState.abLoopEnd else -1f,
                    onSetAbLoopStart = { viewModel.setAbLoopStart(it) },
                    onSetAbLoopEnd = { viewModel.setAbLoopEnd(it) },
                    onNudgeAbLoopStart = { viewModel.nudgeAbLoopStart(it) },
                    onNudgeAbLoopEnd = { viewModel.nudgeAbLoopEnd(it) },
                    isShadowingMode = uiState.isShadowingMode,
                    onToggleShadowingMode = { viewModel.setShadowingMode(it) },
                    speed = uiState.playbackSpeed,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    loopCount = uiState.loopMode,
                    onLoopCountChange = { viewModel.setLoopMode(it) },
                    onSaveLoopToFile = { 
                        viewModel.startExportLoop(
                            item, 
                            (uiState.abLoopStart * item.durationMillis).toLong(),
                            (uiState.abLoopEnd * item.durationMillis).toLong(),
                            0L, 0L, false,
                            uiState.playbackSpeed
                        )
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (draggingItem != null) {
            val item = draggingItem!!
            FileItem(
                modifier = Modifier
                    .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
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
                playlistPosition = -1,
                abLoopStart = -1f,
                abLoopEnd = -1f,
                onSetAbLoopStart = {},
                onSetAbLoopEnd = {},
                onNudgeAbLoopStart = {},
                onNudgeAbLoopEnd = {},
                isShadowingMode = uiState.isShadowingMode,
                onToggleShadowingMode = { viewModel.setShadowingMode(it) },
                speed = uiState.playbackSpeed,
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                loopCount = uiState.loopMode,
                onLoopCountChange = { viewModel.setLoopMode(it) },
                onSaveLoopToFile = { }
            )
        }
    }
}
@Composable
private fun SearchFilterBadge(
    uiState: AudioLoopUiState,
    onClearQuery: () -> Unit,
    onClearCategory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (uiState.searchQuery.isNotEmpty()) {
            InputChip(
                selected = true,
                onClick = onClearQuery,
                label = { Text(stringResource(R.string.label_search_query_format, stringResource(R.string.a11y_search), uiState.searchQuery)) },
                trailingIcon = { Icon(AppIcons.Close, null, modifier = Modifier.size(16.dp)) }
            )
        }
        if (uiState.searchCategory != null) {
            InputChip(
                selected = true,
                onClick = onClearCategory,
                label = { Text(uiState.searchCategory) },
                trailingIcon = { Icon(AppIcons.Close, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}
