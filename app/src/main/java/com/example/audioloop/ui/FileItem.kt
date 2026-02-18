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


@Composable
fun FileItem(
    modifier: Modifier = Modifier,
    item: RecordingItem,
    isPlaying: Boolean,
    isPaused: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    selectionOrder: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onTrim: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onSplit: () -> Unit = {},
    onNormalize: () -> Unit = {},
    onFade: () -> Unit = {},
    currentProgress: Float = 0f,
    currentTimeString: String = "00:00",
    onSeek: (Float) -> Unit = {},
    onReorder: (Int) -> Unit = {},
    isDragging: Boolean = false,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.SLATE.palette,
    playlistPosition: Int = 0, // 0 means not in playlist, >0 is position in playlist
    waveformData: List<Int> = emptyList(),
    onSeekAbsolute: (Int) -> Unit = {} // seek to absolute ms position
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Modern MD3 card states
    val backgroundColor = when {
        isDragging -> themeColors.primaryContainer.copy(alpha = 0.6f)
        isPlaying -> themeColors.primaryContainer.copy(alpha = 0.4f)
        isSelected -> themeColors.secondaryContainer.copy(alpha = 0.3f)
        else -> Zinc800.copy(alpha = 0.3f)
    }

    val borderColor = when {
        isDragging -> themeColors.primary
        isPlaying -> themeColors.primary
        isSelected -> themeColors.secondary.copy(alpha = 0.8f)
        else -> Zinc600
    }

    val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1f, label = "scale")
    val elevation by animateDpAsState(targetValue = if (isDragging) 8.dp else if (isPlaying) 4.dp else 2.dp, label = "elevation")

    Surface(
        onClick = {
            if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp, horizontal = 20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 48.dp), // Narrow but tall touch target
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GripVertical,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) themeColors.primary400 else Zinc600, // Visual feedback
                        modifier = Modifier.size(20.dp)
                    )
                }

            if (isSelectionMode) {
                Surface(
                    onClick = { onToggleSelect() },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isSelected) themeColors.primary else Color.Transparent,
                    border = BorderStroke(
                        2.dp,
                        if (isSelected) themeColors.primary else Zinc500
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                text = selectionOrder.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            } else if (isPlaying) {
                // Modern Play/Pause Button
                if (!isPaused) {
                   FilledIconButton(
                        onClick = { onPause() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = themeColors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        // Pause Icon (Two vertical bars)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                            Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                } else {
                    // Paused -> Show Play (Resume)
                    FilledIconButton(
                        onClick = { onResume() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = themeColors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

               // Stop Button
                FilledIconButton(
                    onClick = { onStop() },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Red800.copy(alpha = 0.4f),
                        contentColor = Red400
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Red400, RoundedCornerShape(2.dp))
                    )
                }
            } else {
                // Not playing - show play button
                FilledIconButton(
                    onClick = { onPlay() },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = themeColors.primaryContainer,
                        contentColor = themeColors.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name.substringBeforeLast("."),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isPlaying) Color.White else Zinc200,
                            fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Selection/Playlist position badge
                    val badgeNumber = when {
                        isSelectionMode && isSelected -> selectionOrder
                        !isSelectionMode && playlistPosition > 0 -> playlistPosition
                        else -> 0
                    }
                    if (badgeNumber > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isPlaying -> themeColors.primary
                                isSelectionMode -> themeColors.secondary
                                else -> themeColors.tertiary.copy(alpha = 0.8f)
                            }
                        ) {
                            Text(
                                text = "\u266B $badgeNumber",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${if(isPlaying) "Playing \u2022 " else ""}${item.durationString}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isPlaying) themeColors.primary else Zinc500
                    )
                )
            }

            if (!isSelectionMode && !isPlaying) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = "Menu",
                            tint = Zinc500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Zinc900).border(1.dp, Zinc600, RoundedCornerShape(4.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.Edit, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Trim", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.ContentCut, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onTrim() }
                        )
                        DropdownMenuItem(
                            text = { Text("Normalize", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.GraphicEq, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onNormalize() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.ArrowForward, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onMove() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.Share, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        HorizontalDivider(color = Zinc800)
                        DropdownMenuItem(
                            text = { Text("Delete", color = Red400) },
                            leadingIcon = { Icon(AppIcons.Delete, null, tint = Red400) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }

        if (isPlaying) {
            // A-B Loop state
            var abLoopA by remember { mutableFloatStateOf(-1f) } // -1 = not set, 0..1 = position
            var abLoopB by remember { mutableFloatStateOf(-1f) }
            val abActive = abLoopA >= 0f && abLoopB >= 0f && abLoopB > abLoopA

            // A-B Loop enforcement
            if (abActive && currentProgress >= abLoopB) {
                LaunchedEffect(currentProgress) {
                    val targetMs = (abLoopA * item.durationMillis).toInt()
                    onSeekAbsolute(targetMs)
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeColors.primary900.copy(alpha = 0.3f))
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        // Interactive Waveform with progress + A-B markers
                        val bars = if (waveformData.isNotEmpty()) waveformData else List(60) { (10..80).random() }
                        val barCount = bars.size

                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .pointerInput(Unit) {
                                    val slop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val pos = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        onSeek(pos)
                                        drag(down.id) { change ->
                                            change.consume()
                                            val dragPos = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                            onSeek(dragPos)
                                        }
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val barW = w / barCount
                            val gap = 1.dp.toPx()

                            // A-B loop region highlight
                            if (abActive) {
                                drawRect(
                                    color = themeColors.primary500.copy(alpha = 0.15f),
                                    topLeft = Offset(abLoopA * w, 0f),
                                    size = androidx.compose.ui.geometry.Size((abLoopB - abLoopA) * w, h)
                                )
                            }

                            // Waveform bars
                            bars.forEachIndexed { i, amp ->
                                val barFraction = (amp / 100f).coerceIn(0.05f, 1f)
                                val barH = barFraction * h
                                val x = i * barW + gap / 2
                                val barProgress = (i + 0.5f) / barCount

                                val color = when {
                                    barProgress <= currentProgress -> themeColors.primary400
                                    else -> Zinc600.copy(alpha = 0.5f)
                                }
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(x, (h - barH) / 2),
                                    size = androidx.compose.ui.geometry.Size(barW - gap, barH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                            }

                            // A marker line
                            if (abLoopA >= 0f) {
                                val ax = abLoopA * w
                                drawLine(themeColors.primary300, Offset(ax, 0f), Offset(ax, h), strokeWidth = 2.dp.toPx())
                            }
                            // B marker line
                            if (abLoopB >= 0f) {
                                val bx = abLoopB * w
                                drawLine(themeColors.primary300, Offset(bx, 0f), Offset(bx, h), strokeWidth = 2.dp.toPx())
                            }

                            // Playhead
                            val px = currentProgress * w
                            drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2.dp.toPx())
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Controls row: time + A-B buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$currentTimeString / ${item.durationString}",
                                style = TextStyle(color = themeColors.primary300, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            )

                            // A-B Loop controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Set A
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (abLoopA >= 0f) themeColors.primary700 else Zinc700.copy(alpha = 0.5f))
                                        .clickable { abLoopA = if (abLoopA >= 0f) -1f else currentProgress }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("A", style = TextStyle(color = if (abLoopA >= 0f) themeColors.primary200 else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                                // Set B
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (abLoopB >= 0f) themeColors.primary700 else Zinc700.copy(alpha = 0.5f))
                                        .clickable { abLoopB = if (abLoopB >= 0f) -1f else currentProgress }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("B", style = TextStyle(color = if (abLoopB >= 0f) themeColors.primary200 else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                                // Clear A-B
                                if (abActive) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Zinc700.copy(alpha = 0.5f))
                                            .clickable { abLoopA = -1f; abLoopB = -1f }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(AppIcons.Close, contentDescription = "Clear", tint = Zinc400, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
}
