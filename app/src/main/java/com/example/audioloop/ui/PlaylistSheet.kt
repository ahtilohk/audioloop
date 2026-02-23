package com.example.audioloop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.Playlist
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import kotlin.math.roundToInt

// ============================================================
// PLAYLIST LIST VIEW — Shows all playlists with premium cards
// ============================================================

@Composable
fun PlaylistListSheet(
    playlists: List<Playlist>,
    formatDuration: (Playlist) -> String,
    getCategoryForFile: (String) -> String,
    onCreateNew: () -> Unit,
    onEdit: (Playlist) -> Unit,
    onPlay: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette,
    currentlyPlayingPlaylistId: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Zinc900, RoundedCornerShape(16.dp))
            .border(1.dp, Zinc600, RoundedCornerShape(16.dp))
    ) {
        // ── Gradient Header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            themeColors.primary900.copy(alpha = 0.4f),
                            Zinc900
                        )
                    ),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "🎵 Playlists",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                    Text(
                        "${playlists.size} playlist${if (playlists.size != 1) "s" else ""}",
                        style = TextStyle(color = themeColors.primary400, fontSize = 13.sp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // New playlist button
                    IconButton(
                        onClick = onCreateNew,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(themeColors.primary600, themeColors.primary700)
                                ),
                                CircleShape
                            )
                    ) {
                        Icon(
                            AppIcons.Add,
                            contentDescription = "New",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Close button
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Zinc800, CircleShape)
                    ) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = "Close",
                            tint = Zinc400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── Playlist Cards ──
        if (playlists.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🎶", fontSize = 48.sp)
                Text(
                    "No playlists yet",
                    style = TextStyle(
                        color = Zinc400,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    "Create a study session to get started",
                    style = TextStyle(color = Zinc500, fontSize = 13.sp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onCreateNew,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = themeColors.primary600
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("➕ Create Playlist", color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val isPlaying = currentlyPlayingPlaylistId == playlist.id

                    // Animated glow for currently playing
                    val glowAlpha by animateFloatAsState(
                        targetValue = if (isPlaying) 0.6f else 0f,
                        animationSpec = tween(600),
                        label = "glow"
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isPlaying) Modifier.border(
                                    1.5.dp,
                                    themeColors.primary400.copy(alpha = glowAlpha),
                                    RoundedCornerShape(14.dp)
                                ) else Modifier.border(
                                    1.dp, Zinc700, RoundedCornerShape(14.dp)
                                )
                            )
                            .clickable { onEdit(playlist) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying)
                                themeColors.primary900.copy(alpha = 0.3f)
                            else Zinc800.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Playlist info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // File count
                                    Text(
                                        "${playlist.files.size} tracks",
                                        style = TextStyle(
                                            color = Zinc400,
                                            fontSize = 12.sp
                                        )
                                    )
                                    // Duration
                                    Text(
                                        "⏱ ${formatDuration(playlist)}",
                                        style = TextStyle(
                                            color = themeColors.primary400,
                                            fontSize = 12.sp
                                        )
                                    )
                                    // Play count badge
                                    if (playlist.playCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    themeColors.primary800.copy(alpha = 0.5f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "🔄 ${playlist.playCount}×",
                                                style = TextStyle(
                                                    color = themeColors.primary300,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                    // Shuffle indicator
                                    if (playlist.shuffle) {
                                        Text(
                                            "🔀",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Right: Play + Delete buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Play button
                                IconButton(
                                    onClick = { onPlay(playlist) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    themeColors.primary500,
                                                    themeColors.primary700
                                                )
                                            ),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                // Delete button
                                IconButton(
                                    onClick = { onDelete(playlist) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Zinc700.copy(alpha = 0.5f), CircleShape)
                                ) {
                                    Icon(
                                        AppIcons.Delete,
                                        contentDescription = "Delete",
                                        tint = Zinc400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}


// ============================================================
// PLAYLIST EDITOR — Edit playlist name, files, settings
// ============================================================

@Composable
fun PlaylistEditorSheet(
    playlist: Playlist,
    allCategories: List<String>,
    getFilesForCategory: (String) -> List<RecordingItem>,
    getCategoryForFile: (String) -> String,
    resolveFileName: (String) -> String,
    resolveFileDuration: (String) -> String,
    onSave: (Playlist) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette
) {
    var name by remember { mutableStateOf(playlist.name) }
    var files by remember { mutableStateOf(playlist.files.toMutableList()) }
    var gapSeconds by remember { mutableStateOf(playlist.gapSeconds) }
    var shuffle by remember { mutableStateOf(playlist.shuffle) }
    var showFilePicker by remember { mutableStateOf(false) }

    if (showFilePicker) {
        FilePickerSheet(
            allCategories = allCategories,
            getFilesForCategory = getFilesForCategory,
            alreadySelected = files,
            onConfirm = { selected ->
                files = (files + selected).distinct().toMutableList()
                showFilePicker = false
            },
            onClose = { showFilePicker = false },
            themeColors = themeColors
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Zinc900, RoundedCornerShape(16.dp))
                .border(1.dp, Zinc600, RoundedCornerShape(16.dp))
        ) {
            // ── Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(themeColors.primary900.copy(alpha = 0.3f), Zinc900)
                        ),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Zinc800, CircleShape)
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = "Back",
                            tint = Zinc400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Edit Playlist",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    // Save button
                    Button(
                        onClick = {
                            onSave(
                                playlist.copy(
                                    name = name.ifBlank { "Untitled" },
                                    files = files,
                                    gapSeconds = gapSeconds,
                                    shuffle = shuffle
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = themeColors.primary600
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("💾 Save", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            // ── Name Field ──
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(themeColors.primary500),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .background(Zinc800, RoundedCornerShape(12.dp))
                    .border(1.dp, Zinc600, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (name.isEmpty()) Text(
                            "Playlist name...",
                            color = Zinc500,
                            fontSize = 16.sp
                        )
                        innerTextField()
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )

            // ── Settings Row: Gap + Shuffle ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gap selector
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Zinc800, RoundedCornerShape(10.dp))
                        .border(1.dp, Zinc700, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⏸", fontSize = 14.sp)
                    Text("Gap:", color = Zinc300, fontSize = 13.sp)
                    val gapOptions = listOf(0, 2, 5, 10)
                    gapOptions.forEach { gap ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (gapSeconds == gap) themeColors.primary600
                                    else Zinc700
                                )
                                .clickable { gapSeconds = gap }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${gap}s",
                                color = if (gapSeconds == gap) Color.White else Zinc400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Shuffle toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (shuffle) themeColors.primary800.copy(alpha = 0.5f)
                            else Zinc800
                        )
                        .border(
                            1.dp,
                            if (shuffle) themeColors.primary500 else Zinc700,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { shuffle = !shuffle }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🔀", fontSize = 14.sp)
                    Text(
                        if (shuffle) "On" else "Off",
                        color = if (shuffle) themeColors.primary300 else Zinc400,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Add Files Button ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        themeColors.primary600.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { showFilePicker = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    AppIcons.Add,
                    contentDescription = null,
                    tint = themeColors.primary400,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add Files from Categories",
                    color = themeColors.primary400,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // ── Track Count ──
            if (files.isNotEmpty()) {
                Text(
                    "${files.size} tracks",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = TextStyle(
                        color = Zinc500,
                        fontSize = 12.sp
                    )
                )
            }

            // ── File List with drag reorder ──
            val uiFiles = remember { mutableStateListOf<String>() }
            LaunchedEffect(files.toList()) {
                uiFiles.clear()
                uiFiles.addAll(files)
            }

            val scrollState = rememberLazyListState()
            var draggingIndex by remember { mutableStateOf(-1) }
            var draggingItem by remember { mutableStateOf<String?>(null) }
            var overlayOffsetY by remember { mutableStateOf(0f) }
            var grabOffsetY by remember { mutableStateOf(0f) }
            var draggingItemSizePx by remember { mutableStateOf(0f) }

            fun checkSwap() {
                if (uiFiles.size <= 1) return
                val visible = scrollState.layoutInfo.visibleItemsInfo
                if (visible.isEmpty()) return
                val centerY = overlayOffsetY + (draggingItemSizePx / 2f)
                val target = visible.find { centerY >= it.offset && centerY <= it.offset + it.size }
                val targetIdx = target?.index ?: return
                if (targetIdx >= 0 && targetIdx != draggingIndex && targetIdx in uiFiles.indices && draggingIndex in uiFiles.indices) {
                    val item = uiFiles.removeAt(draggingIndex)
                    uiFiles.add(targetIdx, item)
                    draggingIndex = targetIdx
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .pointerInput(Unit) {
                        val gripWidth = 56.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val y = down.position.y
                            val x = down.position.x
                            val hitItem = scrollState.layoutInfo.visibleItemsInfo.find {
                                y >= it.offset && y <= it.offset + it.size
                            }
                            if (hitItem != null && x <= gripWidth) {
                                val index = hitItem.index
                                if (index in uiFiles.indices) {
                                    down.consume()
                                    draggingIndex = index
                                    draggingItem = uiFiles[index]
                                    draggingItemSizePx = hitItem.size.toFloat()
                                    overlayOffsetY = hitItem.offset.toFloat()
                                    grabOffsetY = y - hitItem.offset.toFloat()

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
                                            overlayOffsetY = change.position.y - grabOffsetY
                                            checkSwap()
                                        }
                                    }
                                    // Commit reorder
                                    files = uiFiles.toMutableList()
                                    draggingIndex = -1
                                    draggingItem = null
                                    draggingItemSizePx = 0f
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = scrollState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(uiFiles) { index, filePath ->
                        val isDragging = draggingIndex == index
                        val category = getCategoryForFile(filePath)
                        val fileName = resolveFileName(filePath)
                        val duration = resolveFileDuration(filePath)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isDragging) 0f else 1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Zinc800.copy(alpha = 0.5f))
                                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Drag handle
                            Box(
                                modifier = Modifier.size(width = 32.dp, height = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    AppIcons.GripVertical,
                                    contentDescription = "Drag",
                                    tint = Zinc500,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Track number
                            Text(
                                "${index + 1}",
                                color = Zinc500,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(20.dp)
                            )

                            // File info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fileName,
                                    color = Zinc200,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Category badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                themeColors.primary900.copy(alpha = 0.4f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            category,
                                            color = themeColors.primary400,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        duration,
                                        color = Zinc500,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Remove button
                            IconButton(
                                onClick = {
                                    val newFiles = files.toMutableList()
                                    newFiles.removeAt(index)
                                    files = newFiles
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        Zinc700.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    AppIcons.Close,
                                    contentDescription = "Remove",
                                    tint = Zinc500,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Drag overlay
                if (draggingItem != null) {
                    val filePath = draggingItem!!
                    val category = getCategoryForFile(filePath)
                    val fileName = resolveFileName(filePath)

                    Row(
                        modifier = Modifier
                            .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                            .padding(horizontal = 16.dp)
                            .shadow(8.dp, RoundedCornerShape(10.dp))
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(themeColors.primary900.copy(alpha = 0.5f))
                            .border(
                                1.5.dp,
                                themeColors.primary500,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(
                                start = 4.dp,
                                end = 8.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(width = 32.dp, height = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.GripVertical,
                                contentDescription = null,
                                tint = themeColors.primary400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            fileName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}


// ============================================================
// FILE PICKER — Multi-select files from all categories
// ============================================================

@Composable
fun FilePickerSheet(
    allCategories: List<String>,
    getFilesForCategory: (String) -> List<RecordingItem>,
    alreadySelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette
) {
    var selectedCategory by remember { mutableStateOf(allCategories.firstOrNull() ?: "General") }
    val selected = remember { mutableStateListOf<String>() }
    val categoryFiles = remember(selectedCategory) { getFilesForCategory(selectedCategory) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Zinc900, RoundedCornerShape(16.dp))
            .border(1.dp, Zinc600, RoundedCornerShape(16.dp))
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(themeColors.primary900.copy(alpha = 0.3f), Zinc900)
                    ),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .background(Zinc800, CircleShape)
            ) {
                Icon(
                    AppIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = Zinc400,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                "Add Files",
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColors.primary600,
                    disabledContainerColor = Zinc700
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    if (selected.isEmpty()) "Select" else "Add ${selected.size}",
                    color = if (selected.isNotEmpty()) Color.White else Zinc500,
                    fontSize = 13.sp
                )
            }
        }

        // ── Category Tabs ──
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allCategories) { category ->
                val isActive = selectedCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) themeColors.primary600
                            else Zinc800
                        )
                        .border(
                            1.dp,
                            if (isActive) themeColors.primary500 else Zinc700,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        category,
                        color = if (isActive) Color.White else Zinc400,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // ── File List ──
        if (categoryFiles.isEmpty()) {
            Text(
                "No files in this category",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                style = TextStyle(color = Zinc500, fontSize = 14.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(categoryFiles, key = { it.file.absolutePath }) { item ->
                    val relativePath = if (selectedCategory == "General") {
                        item.file.name
                    } else {
                        "${selectedCategory}/${item.file.name}"
                    }
                    val isSelected = selected.contains(relativePath)
                    val isAlready = alreadySelected.contains(relativePath)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    isAlready -> Zinc800.copy(alpha = 0.3f)
                                    isSelected -> themeColors.primary900.copy(alpha = 0.3f)
                                    else -> Zinc800.copy(alpha = 0.5f)
                                }
                            )
                            .border(
                                1.dp,
                                when {
                                    isAlready -> Zinc700
                                    isSelected -> themeColors.primary500
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = !isAlready) {
                                if (isSelected) selected.remove(relativePath)
                                else selected.add(relativePath)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Checkbox
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isAlready -> Zinc700
                                        isSelected -> themeColors.primary600
                                        else -> Zinc700
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isAlready -> Zinc600
                                        isSelected -> themeColors.primary400
                                        else -> Zinc600
                                    },
                                    RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected || isAlready) {
                                Icon(
                                    AppIcons.Check,
                                    contentDescription = null,
                                    tint = if (isAlready) Zinc500 else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // File info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = if (isAlready) Zinc500 else Zinc200,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.durationString,
                                color = Zinc500,
                                fontSize = 11.sp
                            )
                        }

                        if (isAlready) {
                            Text(
                                "added",
                                color = Zinc600,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}
