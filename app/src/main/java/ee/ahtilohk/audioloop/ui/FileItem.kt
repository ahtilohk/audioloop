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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope

import androidx.compose.runtime.mutableFloatStateOf


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    onSaveLoopToFile: () -> Unit = {},
    sleepTimerRemainingMs: Long = 0L,
    onSleepTimerChange: (Int) -> Unit = {},
    onTuneClick: () -> Unit = {}
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
            .border(
                0.5.dp,
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
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val icon = when {
                        isPlaying && !isPaused -> AppIcons.Pause
                        isPlaying && isPaused -> AppIcons.PlayArrow
                        else -> AppIcons.PlayArrow
                    }
                    val containerColor = if (isPlaying) themeColors.primary else themeColors.primaryContainer
                    val contentColor = if (isPlaying) Color.White else themeColors.onPrimaryContainer
                    
                    FilledIconButton(
                        onClick = { if (isPlaying) (if (isPaused) onResume() else onPause()) else onPlay() },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Icon(icon, null, modifier = Modifier.size(18.dp))
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
                        val displayName = item.name.substringBeforeLast(".")
                            .replace(Regex("(\\d{2})_(\\d{2})_(\\d{2})"), "$1:$2:$3")
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isPlaying) themeColors.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.note.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = themeColors.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Aa",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    
                    // Time / Status text
                    val statusText = if (isPlaying) {
                        if (shadowCountdownText.isNotEmpty()) "\u23F8 $shadowCountdownText" 
                        else "$currentTimeString / ${item.durationString}"
                    } else item.durationString
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isPlaying) themeColors.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                // Tune (Practice Toolbox) Icon
                // Always visible Overflow Menu (unless in selection mode)
                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(R.string.a11y_menu),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            if (isPlaying) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_stop), color = Red400) },
                                    leadingIcon = { Icon(AppIcons.Stop, null, tint = Red400) },
                                    onClick = { menuExpanded = false; onStop() }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
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
                                text = { Text(stringResource(R.string.menu_move), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onMove() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onShare() }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_delete), color = Red400) },
                                leadingIcon = { Icon(AppIcons.Delete, null, tint = Red400) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                        }
                    }
                }
            }
            
            // Minimal Progress Bar below the row
            if (isPlaying && !isSelectionMode) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = currentProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape),
                    color = themeColors.primary,
                    trackColor = themeColors.primary.copy(alpha = 0.1f)
                )
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


@OptIn(ExperimentalMaterial3Api::class)
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
            modifier = Modifier.size(36.dp), // Improved touch target
            containerColor = themeColors.primary.copy(alpha = 0.08f),
            contentColor = themeColors.primary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            shape = CircleShape
        ) {
            Icon(AppIcons.ChevronLeft, null, modifier = Modifier.size(18.dp))
        }

        // Main A/B Circle
        Surface(
            onClick = onMainClick,
            shape = CircleShape,
            color = if (isActive) themeColors.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(36.dp), // Normalized size
            border = BorderStroke(1.dp, if (isActive) themeColors.primary else themeColors.primary.copy(alpha = 0.3f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = TextStyle(
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        // Nudge Forward
        SmallFloatingActionButton(
            onClick = { onNudge(100) },
            modifier = Modifier.size(36.dp), // Improved touch target
            containerColor = themeColors.primary.copy(alpha = 0.08f),
            contentColor = themeColors.primary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            shape = CircleShape
        ) {
            Icon(AppIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
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
                    text = labelProvider(item).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

// Reusable expert controls for the Bottom Sheet
@Composable
fun PracticeControlsContent(
    item: RecordingItem,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    loopCount: Int,
    onLoopCountChange: (Int) -> Unit,
    sleepTimerRemainingMs: Long,
    onSleepTimerChange: (Int) -> Unit,
    isShadowingMode: Boolean,
    onToggleShadowingMode: (Boolean) -> Unit,
    abLoopStart: Float,
    abLoopEnd: Float,
    onSetAbLoopStart: (Float) -> Unit,
    onSetAbLoopEnd: (Float) -> Unit,
    onNudgeAbLoopStart: (Int) -> Unit,
    onNudgeAbLoopEnd: (Int) -> Unit,
    onSaveLoopToFile: () -> Unit,
    currentProgress: Float,
    currentTimeString: String,
    waveformData: List<Int>,
    onSeek: (Float) -> Unit,
    themeColors: AppColorPalette
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val primaryColor = themeColors.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val abActive = abLoopStart >= 0f && abLoopEnd >= 0f && abLoopEnd > abLoopStart

    // Waveform HUD state
    var hudVisible by remember { mutableStateOf(false) }
    var hudText by remember { mutableStateOf("") }
    var hudIcon by remember { mutableStateOf<ImageVector>(AppIcons.Speed) }
    var accumulatedSpeedDelta by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Waveform Seeker
        val bars = if (waveformData.isNotEmpty()) waveformData else remember { List(100) { 15 } }
        val markerWidthPx = with(LocalDensity.current) { 2.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp) // Taller in MD3/Practice sheet
                .clip(RoundedCornerShape(12.dp))
                .background(onSurfaceVariantColor.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.file.absolutePath) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var isTwoFinger = false
                            
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
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    if (changes.all { !it.pressed }) break
                                    val deltaX = changes.map { it.position.x - it.previousPosition.x }.average().toFloat()
                                    accumulatedSpeedDelta += deltaX
                                    if (kotlin.math.abs(accumulatedSpeedDelta) > 120f) {
                                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                                        val currentIndex = speeds.indexOf(speed).coerceAtLeast(1)
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
                val w = size.width
                val h = size.height
                val barCount = bars.size
                val barWidth = if (barCount > 0) w / barCount else 1f
                val strokeWidth = (barWidth * 0.5f).coerceIn(1f, 4.dp.toPx())

                fun drawWaveform(color: Color, isFullColor: Boolean = false) {
                    for (i in 0 until barCount) {
                        val amp = bars[i]
                        val barFraction = (amp / 100f).coerceIn(0.08f, 1f)
                        val barH = barFraction * h * 0.75f
                        val x = i * barWidth + barWidth / 2f
                        
                        drawLine(
                            brush = SolidColor(if (isFullColor) color else color.copy(alpha = 0.5f)),
                            start = Offset(x, (h - barH) / 2),
                            end = Offset(x, (h + barH) / 2),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // A-B region
                if (abActive) {
                    drawRect(
                        brush = SolidColor(primaryColor.copy(alpha = 0.15f)),
                        topLeft = Offset(abLoopStart * w, 0f),
                        size = androidx.compose.ui.geometry.Size((abLoopEnd - abLoopStart) * w, h)
                    )
                }

                drawWaveform(onSurfaceVariantColor.copy(alpha = 0.4f))
                clipRect(right = currentProgress * w) {
                    drawWaveform(primaryColor, isFullColor = true)
                }

                // Markers
                if (abLoopStart >= 0f) {
                    val ax = abLoopStart * w
                    drawLine(onSurfaceColor, Offset(ax, 0f), Offset(ax, h), strokeWidth = 3.dp.toPx())
                }
                if (abLoopEnd >= 0f) {
                    val bx = abLoopEnd * w
                    drawLine(onSurfaceColor, Offset(bx, 0f), Offset(bx, h), strokeWidth = 3.dp.toPx())
                }

                // Playhead
                val px = currentProgress * w
                drawLine(onSurfaceColor, Offset(px, 0f), Offset(px, h), strokeWidth = markerWidthPx)
            }
            
            // HUD
            androidx.compose.animation.AnimatedVisibility(visible = hudVisible) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(8.dp)) {
                        Icon(hudIcon, null, tint = Color.White)
                        Text(hudText, color = Color.White)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Practice Toolbox",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = primaryColor
                )
                Text(
                    text = "$currentTimeString / ${item.durationString}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariantColor
                )
            }
            
            // Listen & Repeat Toggle
            Surface(
                onClick = { onToggleShadowingMode(!isShadowingMode); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                shape = RoundedCornerShape(8.dp),
                color = if (isShadowingMode) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isShadowingMode) primaryColor.copy(alpha = 0.3f) else Color.Transparent)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), // Improved touch target
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = AppIcons.Shadow,
                            contentDescription = null,
                            tint = if (isShadowingMode) primaryColor else onSurfaceVariantColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = 90f }
                        )
                        Text(
                            "LISTEN & REPEAT", 
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = if (isShadowingMode) primaryColor else onSurfaceVariantColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Speed Chip
            ControlChip(
                icon = AppIcons.Speed,
                label = "${speed}x",
                onClick = { 
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)
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
                    val loops = listOf(1, 2, 3, 5, -1)
                    val next = loops[(loops.indexOf(loopCount) + 1) % loops.size]
                    onLoopCountChange(next)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                themeColors = themeColors
            )

            // Sleep Timer Chip
            val sleepActive = sleepTimerRemainingMs > 0L
            val sleepLabel = if (sleepActive) {
                val totalSecs = sleepTimerRemainingMs / 1000
                val mins = totalSecs / 60
                val secs = totalSecs % 60
                String.format("%02d:%02d", mins, secs)
            } else "Off"
            
            ControlChip(
                icon = AppIcons.Sleep,
                label = sleepLabel,
                onClick = {
                    val nextMinutes = when {
                        !sleepActive -> 15
                        sleepTimerRemainingMs <= 15 * 60 * 1000L -> 30
                        sleepTimerRemainingMs <= 30 * 60 * 1000L -> 45
                        sleepTimerRemainingMs <= 45 * 60 * 1000L -> 60
                        else -> 0
                    }
                    onSleepTimerChange(nextMinutes)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                themeColors = themeColors
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Marker Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Button(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSaveLoopToFile() },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Save, null, Modifier.size(16.dp))
                        Text("Export Loop", fontSize = 12.sp)
                    }
                }
            } else if (abLoopStart >= 0f || abLoopEnd >= 0f) {
                OutlinedButton(
                    onClick = { onSetAbLoopStart(-1f); onSetAbLoopEnd(-1f) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear A-B", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }

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

