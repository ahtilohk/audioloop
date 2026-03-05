package com.example.audioloop.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.Playlist
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import com.example.audioloop.R
import androidx.compose.ui.res.stringResource

@Composable
fun PlaylistViewScreen(
    playlist: Playlist,
    playingFileName: String,          // currently playing file basename
    currentIteration: Int,
    isPaused: Boolean,
    allRecordings: List<RecordingItem>, // all recordings to resolve paths
    themeColors: AppColorPalette,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    // Resolve playlist file paths → RecordingItems
    val resolvedFiles: List<RecordingItem?> = remember(playlist.files, allRecordings) {
        playlist.files.map { path ->
            val name = path.substringAfterLast("/")
            allRecordings.find { it.name == name }
        }
    }

    // Whether the playlist is actively playing or paused (vs finished)
    val isActive = playingFileName.isNotEmpty() || isPaused

    // Glow animation
    val glowAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (isActive) infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ) else tween(300),
        label = "pvGlow"
    )

    val scrollState = rememberLazyListState()

    // Auto-scroll to currently playing file
    val playingIndex = remember(playingFileName, playlist.files) {
        playlist.files.indexOfFirst { it.substringAfterLast("/") == playingFileName }
    }
    LaunchedEffect(playingIndex) {
        if (playingIndex >= 0) scrollState.animateScrollToItem(playingIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .statusBarsPadding()
    ) {
        // ── Header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.0f)
                        )
                    )
                )
        ) {
            // Title row: back | name | pause | stop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = stringResource(R.string.a11y_close),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                // Animated playing dot + name + label
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isActive -> MaterialTheme.colorScheme.outline
                                        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.5f * glowAlpha)
                                    }
                                )
                        )
                        Text(
                            text = when {
                                !isActive -> stringResource(R.string.label_status_completed)
                                isPaused -> stringResource(R.string.label_status_paused)
                                else -> stringResource(R.string.label_status_now_playing)
                            },
                            style = TextStyle(
                                color = when {
                                    !isActive -> MaterialTheme.colorScheme.outline
                                    isPaused -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                    }
                    Text(
                        text = playlist.name,
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isActive) {
                    Spacer(Modifier.width(8.dp))

                    // Pause / Resume
                    IconButton(
                        onClick = if (isPaused) onResume else onPause,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (isPaused) AppIcons.PlayArrow else AppIcons.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.btn_resume) else stringResource(R.string.menu_pause),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // Stop
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = AppIcons.Stop,
                            contentDescription = stringResource(R.string.btn_stop),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Pills row
            val loopText = when (playlist.loopCount) {
                -1 -> "∞"
                else -> "$currentIteration / ${playlist.loopCount}×"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoPill("🔁 $loopText", themeColors.primary700.copy(alpha = 0.6f), MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), themeColors.primary200)
                if (playlist.speed != 1.0f)
                    InfoPill("🎚 ${"%.1f".format(playlist.speed)}×", MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
                if (playlist.gapSeconds > 0)
                    InfoPill("⏱ " + stringResource(R.string.label_gap_pill, playlist.gapSeconds), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
                if (playlist.shuffle)
                    InfoPill("🔀 " + stringResource(R.string.label_shuffle), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.label_playlist_tracks, playlist.files.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }

            HorizontalDivider(color = themeColors.primary700.copy(alpha = 0.3f), thickness = 1.dp)
        }

        // ── Track List ──
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(playlist.files) { idx, filePath ->
                val fileName = filePath.substringAfterLast("/")
                val isPlaying = fileName == playingFileName
                val item = resolvedFiles.getOrNull(idx)

                val rowBg = if (isPlaying)
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                else
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBg)
                        .then(
                            if (isPlaying) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.3f * glowAlpha),
                                RoundedCornerShape(16.dp)
                            ) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Track number / playing indicator
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPlaying) themeColors.primary700.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            Icon(
                                imageVector = AppIcons.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName.substringBeforeLast("."),
                            style = TextStyle(
                                color = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item != null) {
                            val generalLabel = stringResource(R.string.label_general)
                            val cat = filePath.substringBefore("/", generalLabel)
                                .let { if (it == filePath) generalLabel else it }
                            val dur = item.durationMillis
                            val mins = (dur / 1000) / 60
                            val secs = (dur / 1000) % 60
                            val durStr = if (mins > 0) stringResource(R.string.label_duration_min_sec, mins, secs) else stringResource(R.string.label_duration_sec, secs)
                            Text(
                                text = "$cat · $durStr",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isPlaying) {
                        Icon(
                            imageVector = AppIcons.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f + 0.5f * glowAlpha),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // bottom padding
        }
    }
}

@Composable
private fun InfoPill(
    text: String,
    bg: Color,
    border: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

