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

    // Glow animation
    val glowAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
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
            .background(themeColors.primary900.copy(alpha = 0.5f))
            .statusBarsPadding()
    ) {
        // ── Header ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            themeColors.primary900.copy(alpha = 0.9f),
                            themeColors.primary900.copy(alpha = 0.0f)
                        )
                    )
                )
        ) {
            // Title row: back | name | pause | stop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = themeColors.primary300,
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
                                    if (isPaused) Zinc500
                                    else themeColors.primary400.copy(alpha = 0.5f + 0.5f * glowAlpha)
                                )
                        )
                        Text(
                            text = if (isPaused) "PAUSED" else "NOW PLAYING",
                            style = TextStyle(
                                color = if (isPaused) Zinc500 else themeColors.primary400,
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

                Spacer(Modifier.width(8.dp))

                // Pause / Resume
                IconButton(
                    onClick = if (isPaused) onResume else onPause,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeColors.primary800.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (isPaused) AppIcons.PlayArrow else AppIcons.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = themeColors.primary300,
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
                        .background(Zinc800.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = AppIcons.Stop,
                        contentDescription = "Stop",
                        tint = Zinc400,
                        modifier = Modifier.size(20.dp)
                    )
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoPill("🔁 $loopText", themeColors.primary700.copy(alpha = 0.6f), themeColors.primary500.copy(alpha = 0.4f), themeColors.primary200)
                if (playlist.speed != 1.0f)
                    InfoPill("🎚 ${"%.1f".format(playlist.speed)}×", Zinc700.copy(alpha = 0.6f), Zinc600, Zinc300)
                if (playlist.gapSeconds > 0)
                    InfoPill("⏱ ${playlist.gapSeconds}s gap", Zinc700.copy(alpha = 0.6f), Zinc600, Zinc300)
                if (playlist.shuffle)
                    InfoPill("🔀 Shuffle", Zinc700.copy(alpha = 0.6f), Zinc600, Zinc300)
                Spacer(Modifier.weight(1f))
                Text("${playlist.files.size} tracks", color = Zinc500, fontSize = 11.sp)
            }

            HorizontalDivider(color = themeColors.primary700.copy(alpha = 0.3f), thickness = 1.dp)
        }

        // ── Track List ──
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(playlist.files) { idx, filePath ->
                val fileName = filePath.substringAfterLast("/")
                val isPlaying = fileName == playingFileName
                val item = resolvedFiles.getOrNull(idx)

                val rowBg = if (isPlaying)
                    Brush.horizontalGradient(
                        listOf(
                            themeColors.primary800.copy(alpha = 0.7f),
                            themeColors.primary800.copy(alpha = 0.3f)
                        )
                    )
                else
                    Brush.horizontalGradient(
                        listOf(Zinc800.copy(alpha = 0.4f), Zinc800.copy(alpha = 0.4f))
                    )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBg)
                        .then(
                            if (isPlaying) Modifier.border(
                                1.dp,
                                themeColors.primary500.copy(alpha = 0.5f + 0.3f * glowAlpha),
                                RoundedCornerShape(12.dp)
                            ) else Modifier.border(1.dp, Zinc700.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                else Zinc700.copy(alpha = 0.4f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            Icon(
                                imageVector = AppIcons.GraphicEq,
                                contentDescription = null,
                                tint = themeColors.primary300,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${idx + 1}",
                                color = Zinc400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName.substringBeforeLast("."),
                            style = TextStyle(
                                color = if (isPlaying) Color.White else Zinc200,
                                fontSize = 14.sp,
                                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item != null) {
                            val cat = filePath.substringBefore("/", "General")
                                .let { if (it == filePath) "General" else it }
                            val dur = item.durationMillis
                            val mins = (dur / 1000) / 60
                            val secs = (dur / 1000) % 60
                            val durStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                            Text(
                                text = "$cat · $durStr",
                                color = Zinc500,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isPlaying) {
                        Icon(
                            imageVector = AppIcons.GraphicEq,
                            contentDescription = null,
                            tint = themeColors.primary400.copy(alpha = 0.5f + 0.5f * glowAlpha),
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
