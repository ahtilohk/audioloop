package ee.ahtilohk.audioloop.ui

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.ui.theme.*
import ee.ahtilohk.audioloop.R
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


@OptIn(ExperimentalLayoutApi::class)
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
    themeColors: ee.ahtilohk.audioloop.ui.theme.AppColorPalette = ee.ahtilohk.audioloop.ui.theme.AppTheme.SLATE.palette,
    playlistPosition: Int = 0, // 0 means not in playlist, >0 is position in playlist
    waveformData: List<Int> = emptyList(),
    onSeekAbsolute: (Int) -> Unit = {}, // seek to absolute ms position
    shadowCountdownText: String = "", // countdown text during Listen & Repeat pause
    abLoopStart: Float = -1f,
    abLoopEnd: Float = -1f,
    onSetAbLoopStart: (Float) -> Unit = {},
    onSetAbLoopEnd: (Float) -> Unit = {},
    onNudgeAbLoopStart: (Int) -> Unit = {},
    onNudgeAbLoopEnd: (Int) -> Unit = {},
    isShadowingMode: Boolean = false,
    onToggleShadowingMode: (Boolean) -> Unit = {},
    speed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    loopCount: Int = 1,
    onLoopCountChange: (Int) -> Unit = {},
    onSaveLoopToFile: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Modern MD3 card states
    val backgroundColor = when {
        isDragging -> themeColors.primary.copy(alpha = 0.15f)
        isPlaying -> themeColors.primary.copy(alpha = 0.1f)
        isSelected -> themeColors.secondary.copy(alpha = 0.12f)
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .shadow(if (isPlaying) 6.dp else 2.dp, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isPlaying) themeColors.primary.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
                }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
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
                        modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(themeColors.primaryGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pause Icon
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                                Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                            }
                        }
                    }

                } else {
                    // Paused -> Show Play (Resume)
                    FilledIconButton(
                        onClick = { onResume() },
                        modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                         Box(
                            modifier = Modifier.fillMaxSize().background(themeColors.primaryGradient, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.PlayArrow,
                                contentDescription = stringResource(R.string.btn_resume),
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isPlaying && shadowCountdownText.isNotEmpty(),
                        label = "status_text"
                    ) { showShadow ->
                        if (showShadow) {
                            Text(
                                text = "\u23F8 $shadowCountdownText",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        } else {
                            Text(
                                text = "${if (isPlaying) stringResource(R.string.label_playing_indicator) else ""}${item.durationString}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
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
            val abActive = abLoopStart >= 0f && abLoopEnd >= 0f && abLoopEnd > abLoopStart
            val haptic = LocalHapticFeedback.current
            
            // HUD State for gestures
            var hudVisible by remember { mutableStateOf(false) }
            var hudText by remember { mutableStateOf("") }
            var hudIcon by remember { mutableStateOf<ImageVector>(AppIcons.Speed) }
            var accumulatedSpeedDelta by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
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
                        
                        val primaryColor = themeColors.primary
                        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                        val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val markerWidthPx = with(LocalDensity.current) { 2.dp.toPx() }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(onSurfaceVariantColor.copy(alpha = 0.04f)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics {
                                        contentDescription = ctx.getString(R.string.a11y_waveform_seeker)
                                        progressBarRangeInfo = ProgressBarRangeInfo(currentProgress, 0f..1f)
                                    }
                                    .pointerInput(item.file.absolutePath) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            var isTwoFinger = false
                                            
                                            // Look for common states
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size > 1) {
                                                    isTwoFinger = true
                                                    hudIcon = AppIcons.Speed
                                                    hudVisible = true
                                                    accumulatedSpeedDelta = 0f
                                                    break
                                                }
                                                if (event.changes.any { it.positionChanged() }) break
                                                if (event.changes.all { !it.pressed }) break
                                            }

                                            if (isTwoFinger) {
                                                // Handle Speed Adjustment (Two fingers)
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val changes = event.changes
                                                    if (changes.all { !it.pressed }) break
                                                    
                                                    // Average delta of all fingers
                                                    val deltaX = changes.map { it.position.x - it.previousPosition.x }.average().toFloat()
                                                    accumulatedSpeedDelta += deltaX
                                                    
                                                    // Change speed every 120 pixels
                                                    if (kotlin.math.abs(accumulatedSpeedDelta) > 120f) {
                                                        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                                                        val currentIndex = speeds.indexOf(speed).coerceAtLeast(1) // default to 1x
                                                        val direction = if (accumulatedSpeedDelta > 0) 1 else -1
                                                        val nextIndex = (currentIndex + direction).coerceIn(0, speeds.size - 1)
                                                        val nextSpeed = speeds[nextIndex]
                                                        
                                                        if (nextSpeed != speed) {
                                                            onSpeedChange(nextSpeed)
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                        accumulatedSpeedDelta = 0f
                                                    }
                                                    
                                                    hudText = "${speed}x"
                                                    changes.forEach { it.consume() }
                                                }
                                            } else {
                                                // Standard Seek (One finger)
                                                onSeek((down.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                                                drag(down.id) { change ->
                                                    change.consume()
                                                    onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                                                }
                                            }
                                            hudVisible = false
                                        }
                                    }
                            ) {
                                val barCount = bars.size
                                val w = size.width
                                val h = size.height
                                val barWidth = if (barCount > 0) w / barCount else 1f
                                // Thinner lines for higher definition and more "Premium" feel
                                val strokeWidth = (barWidth * 0.4f).coerceIn(1f, 2.5.dp.toPx())

                                fun drawWaveform(color: Color, isFullColor: Boolean = false) {
                                    for (i in 0 until barCount) {
                                        val amp = bars[i]
                                        val barFraction = (amp / 100f).coerceIn(0.08f, 1f)
                                        val barH = barFraction * h * 0.7f
                                        val x = i * barWidth + barWidth / 2f
                                        
                                        val barColor = if (isFullColor) {
                                            Brush.verticalGradient(
                                                colors = listOf(color.copy(alpha = 0.7f), color, color.copy(alpha = 0.7f)),
                                                startY = (h - barH) / 2,
                                                endY = (h + barH) / 2
                                            )
                                        } else {
                                            SolidColor(color)
                                        }

                                        drawLine(
                                            brush = if (barColor is Brush) barColor else SolidColor(color),
                                            start = Offset(x, (h - barH) / 2),
                                            end = Offset(x, (h + barH) / 2),
                                            strokeWidth = strokeWidth,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }

                                // 1. Draw A-B loop region highlight (Premium Gradient)
                                if (abActive) {
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(primaryColor.copy(alpha = 0.1f), primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.1f)),
                                            startX = abLoopStart * w,
                                            endX = abLoopEnd * w
                                        ),
                                        topLeft = Offset(abLoopStart * w, 0f),
                                        size = androidx.compose.ui.geometry.Size((abLoopEnd - abLoopStart) * w, h)
                                    )
                                }

                                // 2. Background (Grayed out)
                                drawWaveform(onSurfaceVariantColor.copy(alpha = 0.15f))

                                // 3. Active Waveform (Primary themed)
                                clipRect(right = currentProgress * w) {
                                    drawWaveform(primaryColor, isFullColor = true)
                                }

                                // 4. A-B Markers (Premium Glowing Lines)
                                if (abLoopStart >= 0f) {
                                    val ax = abLoopStart * w
                                    // A Marker - Refined visualization
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.8f),
                                        start = Offset(ax, 0f),
                                        end = Offset(ax, h),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    // Highlight top/bottom
                                    drawCircle(primaryColor, radius = 5.dp.toPx(), center = Offset(ax, 0f))
                                    drawCircle(primaryColor, radius = 5.dp.toPx(), center = Offset(ax, h))
                                }
                                if (abLoopEnd >= 0f) {
                                    val bx = abLoopEnd * w
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.8f),
                                        start = Offset(bx, 0f),
                                        end = Offset(bx, h),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    // Highlight top/bottom
                                    drawCircle(primaryColor, radius = 5.dp.toPx(), center = Offset(bx, 0f))
                                    drawCircle(primaryColor, radius = 5.dp.toPx(), center = Offset(bx, h))
                                }

                                // 5. Playhead
                                val px = currentProgress * w
                                drawLine(onSurfaceColor, Offset(px, 0f), Offset(px, h), strokeWidth = markerWidthPx * 1f)
                                drawCircle(onSurfaceColor, radius = markerWidthPx * 2f, center = Offset(px, 0f))
                                drawCircle(onSurfaceColor, radius = markerWidthPx * 2f, center = Offset(px, h))
                            }
                            
                            // Gesture HUD Overlay
                            androidx.compose.animation.AnimatedVisibility(
                                visible = hudVisible,
                                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(hudIcon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = hudText, 
                                            color = Color.White, 
                                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                }
                            }
                        }

                        // Modern Minimal Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Time Display
                             Text(
                                    text = "$currentTimeString / ${item.durationString}",
                                    style = TextStyle(
                                        color = onSurfaceColor.copy(alpha = 0.7f), 
                                        fontSize = 10.sp, 
                                        fontWeight = FontWeight.Bold, 
                                        letterSpacing = 0.4.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Speed Chip
                                ControlChip(
                                    icon = AppIcons.Speed,
                                    label = "${speed}x",
                                    onClick = { 
                                        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
                                        val next = speeds[(speeds.indexOf(speed) + 1) % speeds.size]
                                        onSpeedChange(next)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    themeColors = themeColors
                                )

                                // Loop Chip
                                ControlChip(
                                    icon = AppIcons.Loop,
                                    label = if (loopCount == -1) "∞" else "${loopCount}x",
                                    onClick = {
                                        val loops = listOf(1, 3, 5, -1)
                                        val next = loops[(loops.indexOf(loopCount) + 1) % loops.size]
                                        onLoopCountChange(next)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    themeColors = themeColors
                                )

                                 // Listen & Repeat Toggle
                                 Surface(
                                        onClick = { onToggleShadowingMode(!isShadowingMode); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isShadowingMode) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, if (isShadowingMode) primaryColor.copy(alpha = 0.4f) else Color.Transparent)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.Shadow,
                                                contentDescription = null,
                                                tint = if (isShadowingMode) primaryColor else onSurfaceVariantColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = 90f }
                                            )
                                        }
                                    }
                            }
                        }

                        
                        // Marker Controls (A-B Buttons + Export combined for maximum compactness)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // A-Group
                            MarkerControlGroup(
                                label = "A",
                                isActive = abLoopStart >= 0f,
                                onMainClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetAbLoopStart(if (abLoopStart >= 0f && abLoopEnd < 0f) -1f else currentProgress) 
                                },
                                onNudge = { delta -> 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNudgeAbLoopStart(delta) 
                                },
                                themeColors = themeColors
                            )

                            // Export Button (Only when A-B active)
                            if (abActive) {
                                FilledIconButton(
                                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSaveLoopToFile() },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = themeColors.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(AppIcons.Save, null, Modifier.size(16.dp))
                                }
                            } else {
                                // Close/Clear AB when only one marker exists
                                if (abLoopStart >= 0f || abLoopEnd >= 0f) {
                                    IconButton(
                                        onClick = { onSetAbLoopStart(-1f); onSetAbLoopEnd(-1f) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(AppIcons.Close, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            // B-Group
                            MarkerControlGroup(
                                label = "B",
                                isActive = abLoopEnd >= 0f,
                                onMainClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSetAbLoopEnd(if (abLoopEnd >= 0f) -1f else currentProgress) 
                                },
                                onNudge = { delta -> 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNudgeAbLoopEnd(delta) 
                                },
                                themeColors = themeColors
                            )
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


@Composable
private fun ControlChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    themeColors: AppColorPalette
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(11.dp), tint = themeColors.primary)
            Text(
                text = label,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}


@Composable
private fun MarkerControlGroup(
    label: String,
    isActive: Boolean,
    onMainClick: () -> Unit,
    onNudge: (Int) -> Unit,
    themeColors: AppColorPalette
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Nudge Back
        SmallFloatingActionButton(
            onClick = { onNudge(-100) },
            modifier = Modifier.size(28.dp),
            containerColor = Color.Transparent,
            contentColor = themeColors.primary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(AppIcons.ChevronLeft, null, modifier = Modifier.size(14.dp))
        }

        // Main A/B Circle
        Surface(
            onClick = onMainClick,
            shape = CircleShape,
            color = if (isActive) themeColors.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(32.dp),
            border = BorderStroke(1.2.dp, if (isActive) themeColors.primary else themeColors.primary.copy(alpha = 0.2f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        // Nudge Forward
        SmallFloatingActionButton(
            onClick = { onNudge(100) },
            modifier = Modifier.size(28.dp),
            containerColor = Color.Transparent,
            contentColor = themeColors.primary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(AppIcons.ChevronRight, null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun <T> CompactOptionSelector(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelProvider: (T) -> String,
    themeColors: AppColorPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items.forEach { item ->
            val isActive = item == selected
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) themeColors.primary else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

