package ee.ahtilohk.audioloop.ui

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
import androidx.compose.ui.res.stringResource
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.Playlist
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.ui.theme.*
import kotlin.math.roundToInt

// ============================================================
// PLAYLIST LIST VIEW — Full-screen with premium cards
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
            title = stringResource(R.string.dialog_delete_playlist_title),
            text = stringResource(R.string.dialog_delete_playlist_confirm, p.name),
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                onDelete(p)
                playlistToDelete = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -- Gradient Header --
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Back button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        ) {
                            Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.a11y_close), tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Column {
                            Text(
                                stringResource(R.string.label_playlists),
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                stringResource(R.string.label_playlists_count, playlists.size, if (playlists.size != 1) "s" else ""),
                                style = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            )
                        }
                    }

                    // New playlist button
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_new), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // -- Playlist Cards --
            if (playlists.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        AppIcons.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.label_playlist_no_playlists),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.label_playlist_create_desc),
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_create_playlist), color = Color.White)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
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
                                        MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                        RoundedCornerShape(16.dp)
                                    ) else Modifier.border(
                                        1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)
                                    )
                                )
                                .clickable { onView(playlist) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPlaying)
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
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
                                                stringResource(R.string.label_playlist_tracks, playlist.files.size),
                                                style = TextStyle(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            // Duration
                                            Text(
                                                formatDuration(playlist),
                                                style = TextStyle(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                            // Play count badge
                                            if (playlist.playCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                ) {
                                                    Text(
                                                        stringResource(R.string.label_play_count_compact, playlist.playCount),
                                                        style = TextStyle(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    )
                                                }
                                            }
                                            // Shuffle indicator
                                            if (playlist.shuffle) {
                                                Text(
                                                    stringResource(R.string.label_shuffle),
                                                    style = TextStyle(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 11.sp
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Right: Play + Edit + Delete buttons
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Play button
                                        IconButton(
                                            onClick = { if (isPlaying) onPause() else onPlay(playlist) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            themeColors.primary700
                                                        )
                                                    ),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                                                contentDescription = if (isPlaying) stringResource(R.string.menu_pause) else stringResource(R.string.menu_play),
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        // Edit button
                                        IconButton(
                                            onClick = { onEdit(playlist) },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                                        ) {
                                            Icon(
                                                AppIcons.Edit,
                                                contentDescription = stringResource(R.string.a11y_edit),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Delete button
                                        IconButton(
                                            onClick = { playlistToDelete = playlist },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                AppIcons.Delete,
                                                contentDescription = stringResource(R.string.btn_delete),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -- Header --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.btn_add_files),
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    Text(
                        if (selected.isEmpty()) stringResource(R.string.label_select) else stringResource(R.string.label_add_count, selected.size),
                        color = if (selected.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // -- Category Tabs --
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allCategories) { category ->
                    val isActive = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (category == "General") stringResource(R.string.label_general) else category,
                            color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            // -- File List --
            if (categoryFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.label_no_files_category),
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isAlready -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isAlready -> MaterialTheme.colorScheme.outlineVariant
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !isAlready) {
                                    if (isSelected) selected.remove(relativePath)
                                    else selected.add(relativePath)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
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
                                            isAlready -> MaterialTheme.colorScheme.outlineVariant
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isAlready -> MaterialTheme.colorScheme.outline
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        RoundedCornerShape(6.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected || isAlready) {
                                    Icon(
                                        AppIcons.Check,
                                        contentDescription = null,
                                        tint = if (isAlready) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // File info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    color = if (isAlready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.durationString,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            if (isAlready) {
                                Text(
                                    stringResource(R.string.label_added),
                                    color = MaterialTheme.colorScheme.outline,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

