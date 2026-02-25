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
// PLAYLIST LIST VIEW Å“â‚¬â€ Shows all playlists with premium cards
// ============================================================

@Composable
fun PlaylistListSheet(
    playlists: List<Playlist>,
    formatDuration: (Playlist) -> String,
    getCategoryForFile: (String) -> String,
    onCreateNew: () -> Unit,
    onEdit: (Playlist) -> Unit,
    onView: (Playlist) -> Unit,
    onPlay: (Playlist) -> Unit,
    onPause: () -> Unit,
    onDelete: (Playlist) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette,
    currentlyPlayingPlaylistId: String? = null
) {
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    // Delete confirmation dialog
    playlistToDelete?.let { p ->
        DeleteConfirmDialog(
            title = "Delete playlist?",
            text = "Delete \"${p.name}\"? This cannot be undone.",
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                onDelete(p)
                playlistToDelete = null
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Zinc900, RoundedCornerShape(16.dp))
            .border(1.dp, Zinc600, RoundedCornerShape(16.dp))
    ) {
        //  Gradient Header 
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            themeColors.primary900.copy(alpha = 0.4f),
                            Zinc950
                        )
                    )
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
                        "ðŸŽµ Playlists",
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

        //  Playlist Cards 
        if (playlists.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("ðŸŽ¶", fontSize = 48.sp)
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
                    Text("âž• Create Playlist", color = Color.White)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                            .clickable { onView(playlist) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPlaying)
                                themeColors.primary900.copy(alpha = 0.4f)
                            else Zinc800.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                        "â± ${formatDuration(playlist)}",
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
                                                "ðŸ”„ ${playlist.playCount}Ã—",
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
                                            "ðŸ”€",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // Right: Play + Delete buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Play button
                                IconButton(
                                    onClick = { if (isPlaying) onPause() else onPlay(playlist) },
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
                                // Edit button
                                IconButton(
                                    onClick = { onEdit(playlist) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Zinc700.copy(alpha = 0.3f), CircleShape)
                                ) {
                                    Icon(
                                        AppIcons.Edit,
                                        contentDescription = "Edit",
                                        tint = Zinc400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                // Delete button
                                IconButton(
                                    onClick = { playlistToDelete = playlist },
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
        }

        Spacer(Modifier.height(8.dp))
    }
}



















// ============================================================
// FILE PICKER Å“â‚¬â€ Multi-select files from all categories
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
        //  Header 
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

        //  Category Tabs 
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

        //  File List 
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
