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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.Role
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.media.MediaPlayer
import com.example.audioloop.AppIcons
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import com.example.audioloop.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope

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
    onAutoTrim: () -> Unit = {},
    onEditNote: () -> Unit = {},
    onShowInfo: () -> Unit = {},
    currentProgress: Float = 0f,
    currentTimeString: String = "00:00",
    onSeek: (Float) -> Unit = {},
    onReorder: (Int) -> Unit = {},
    isDragging: Boolean = false,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.SLATE.palette,
    playlistPosition: Int = 0, // 0 means not in playlist, >0 is position in playlist
    waveformData: List<Int> = emptyList(),
    onSeekAbsolute: (Int) -> Unit = {}, // seek to absolute ms position
    shadowCountdownText: String = "" // countdown text during Listen & Repeat pause
) {
    val ctx = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Modern MD3 card states
    val backgroundColor = when {
        isDragging -> themeColors.primary.copy(alpha = 0.12f)
        isPlaying -> themeColors.primary.copy(alpha = 0.08f)
        isSelected -> themeColors.secondary.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isDragging -> themeColors.primary
        isPlaying -> themeColors.primary.copy(alpha = 0.5f)
        isSelected -> themeColors.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1f, label = "scale")
    val spacing = LocalSpacing.current
    val elevation by animateDpAsState(targetValue = if (isDragging) spacing.small else if (isPlaying) spacing.extraSmall else 2.dp, label = "elevation")

    Surface(
        onClick = {
            if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics(mergeDescendants = true) {
                val status = when {
                    isPlaying && isPaused -> ctx.getString(R.string.a11y_paused)
                    isPlaying -> ctx.getString(R.string.a11y_playing)
                    else -> ""
                }
                contentDescription = "${item.name}, ${item.durationString}. $status"
            },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.small, vertical = spacing.small)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {

                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 48.dp), // Narrow but tall touch target
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GripVertical,
                        contentDescription = stringResource(R.string.a11y_drag_reorder),
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, // Visual feedback
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
                        if (isSelected) themeColors.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                                    color = Color.White, // Always white on secondary solid color
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
                            contentDescription = stringResource(R.string.btn_resume),
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
                        contentDescription = stringResource(R.string.menu_play),
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
                    // Restore colons in time portion (sanitizeName replaces : with _ for filesystem)
                    val displayName = item.name.substringBeforeLast(".")
                        .replace(Regex("(\\d{2})_(\\d{2})_(\\d{2})"), "$1:$2:$3")
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Note indicator
                    if (item.note.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Aa",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
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
                if (isPlaying && shadowCountdownText.isNotEmpty()) {
                    Text(
                        text = "\u23F8 $shadowCountdownText",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else {
                    Text(
                        text = "${if(isPlaying) stringResource(R.string.label_playing_indicator) else ""}${item.durationString}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            if (!isSelectionMode && !isPlaying) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = stringResource(R.string.a11y_menu),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_rename), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_trim), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.ContentCut, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onTrim() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.label_auto_trim_silence), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.ContentCut, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onAutoTrim() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_normalize), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onNormalize() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.note.isNotBlank()) stringResource(R.string.menu_note_edit) else stringResource(R.string.menu_note_add), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onEditNote() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_move), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onMove() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_share), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_info), color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { Icon(AppIcons.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { menuExpanded = false; onShowInfo() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.btn_delete), color = Red400) },
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
                    .padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)) {
                        // Interactive Waveform with progress + A-B markers
                        var localWaveform by remember(item.file.absolutePath) { mutableStateOf<List<Int>?>(null) }
                        
                        LaunchedEffect(item.file.absolutePath, waveformData) {
                            if (waveformData.isEmpty() && localWaveform == null) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val waveFile = File(item.file.parent, "${item.file.name}.wave")
                                        if (waveFile.exists()) {
                                            val content = waveFile.readText()
                                            if (content.isNotEmpty()) {
                                                val list = content.split(",").mapNotNull { it.trim().toIntOrNull() }
                                                if (list.isNotEmpty()) {
                                                    withContext(Dispatchers.Main) { localWaveform = list }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        val bars = if (waveformData.isNotEmpty()) waveformData else (localWaveform ?: remember { List(100) { 15 } })
                        val barCount = bars.size
                        
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val markerWidthPx = with(LocalDensity.current) { 2.dp.toPx() }

                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .semantics {
                                    contentDescription = ctx.getString(R.string.a11y_waveform_seeker)
                                    // role = Role.ProgressBar
                                    progressBarRangeInfo = ProgressBarRangeInfo(currentProgress, 0f..1f)
                                }
                                .pointerInput(Unit) {
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
                            val barWidth = w / barCount
                            val strokeWidth = (barWidth * 0.7f).coerceAtLeast(1.5f)

                            // 1. Draw A-B loop region highlight
                            if (abActive) {
                                drawRect(
                                    color = primaryColor.copy(alpha = 0.15f),
                                    topLeft = Offset(abLoopA * w, 0f),
                                    size = androidx.compose.ui.geometry.Size((abLoopB - abLoopA) * w, h)
                                )
                            }

                            fun drawWaveform(color: Color) {
                                for (i in 0 until barCount) {
                                    val amp = bars[i]
                                    val barFraction = (amp / 100f).coerceIn(0.05f, 1f)
                                    val barH = barFraction * h * 0.8f
                                    val x = i * barWidth + barWidth / 2f
                                    
                                    drawLine(
                                        color = color,
                                        start = Offset(x, (h - barH) / 2),
                                        end = Offset(x, (h + barH) / 2),
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // 2. Draw Background Waveform
                            drawWaveform(onSurfaceVariantColor.copy(alpha = 0.25f))

                            // 3. Draw Active Waveform (Clipped)
                            clipRect(right = currentProgress * w) {
                                drawWaveform(primaryColor)
                            }

                            // 4. Draw markers
                            if (abLoopA >= 0f) {
                                val ax = abLoopA * w
                                drawLine(primaryColor, Offset(ax, 0f), Offset(ax, h), strokeWidth = markerWidthPx)
                            }
                            if (abLoopB >= 0f) {
                                val bx = abLoopB * w
                                drawLine(primaryColor, Offset(bx, 0f), Offset(bx, h), strokeWidth = markerWidthPx)
                            }

                            // 5. Playhead
                            val px = currentProgress * w
                            drawLine(onSurfaceColor, Offset(px, 0f), Offset(px, h), strokeWidth = markerWidthPx)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Controls row: time + A-B buttons
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$currentTimeString / ${item.durationString}",
                                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            )
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // A/B Buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        onClick = { abLoopA = if (abLoopA >= 0f && abLoopB < 0f) -1f else currentProgress },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (abLoopA >= 0f) themeColors.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.height(44.dp).width(44.dp)
                                            .semantics { contentDescription = ctx.getString(R.string.a11y_set_loop_start) }
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("A", color = if (abLoopA >= 0f) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Surface(
                                        onClick = { abLoopB = if (abLoopB >= 0f) -1f else currentProgress },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (abLoopB >= 0f) themeColors.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.height(44.dp).width(44.dp)
                                            .semantics { contentDescription = ctx.getString(R.string.a11y_set_loop_end) }
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("B", color = if (abLoopB >= 0f) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Reset A-B
                                if (abActive) {
                                    IconButton(
                                        onClick = { abLoopA = -1f; abLoopB = -1f },
                                        modifier = Modifier.size(44.dp) // Professional touch target
                                    ) {
                                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.a11y_clear_loop), tint = Red500, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Note text display during playback
            if (item.note.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.label_note),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            ),
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

    }
}
}

