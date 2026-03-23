package ee.ahtilohk.audioloop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.ui.theme.AppColorPalette
import ee.ahtilohk.audioloop.ui.theme.Red400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlChip(
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
fun MarkerControlGroup(
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
            modifier = Modifier.size(36.dp),
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
            modifier = Modifier.size(36.dp),
            border = BorderStroke(1.dp, if (isActive) themeColors.primary else themeColors.primary.copy(alpha = 0.3f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "MARK $label",
                    style = TextStyle(
                        color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                )
            }
        }

        // Nudge Forward
        SmallFloatingActionButton(
            onClick = { onNudge(100) },
            modifier = Modifier.size(36.dp),
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
fun <T> CompactOptionSelector(
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
    val haptic = LocalHapticFeedback.current
    val primaryColor = themeColors.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val abActive = abLoopStart >= 0f && abLoopEnd >= 0f && abLoopEnd > abLoopStart

    // HUD state
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
                .height(64.dp)
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
                                    if (event.changes.all { !it.pressed }) break
                                    val deltaX = event.changes.map { it.position.x - it.previousPosition.x }.average().toFloat()
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
                                    event.changes.forEach { it.consume() }
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

                if (abLoopStart >= 0f) {
                    val ax = abLoopStart * w
                    drawLine(onSurfaceColor, Offset(ax, 0f), Offset(ax, h), strokeWidth = 3.dp.toPx())
                }
                if (abLoopEnd >= 0f) {
                    val bx = abLoopEnd * w
                    drawLine(onSurfaceColor, Offset(bx, 0f), Offset(bx, h), strokeWidth = 3.dp.toPx())
                }

                val px = currentProgress * w
                drawLine(onSurfaceColor, Offset(px, 0f), Offset(px, h), strokeWidth = markerWidthPx)
            }
            
            androidx.compose.animation.AnimatedVisibility(visible = hudVisible) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(8.dp)) {
                        Icon(hudIcon, null, tint = Color.White)
                        Text(hudText, color = Color.White)
                    }
                }
            }
        }

        // Header showing "Now Playing" context
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Practice Session",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor.copy(alpha = 0.8f),
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = onSurfaceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$currentTimeString / ${item.durationString}",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariantColor
                )
            }
        }

        // --- SESSION GROUP ---
        SectionHeader("SESSION MODIFIERS", themeColors)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Shadowing (Listen & Repeat)
            Surface(
                onClick = { onToggleShadowingMode(!isShadowingMode); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                shape = RoundedCornerShape(8.dp),
                color = if (isShadowingMode) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isShadowingMode) primaryColor.copy(alpha = 0.3f) else Color.Transparent),
                modifier = Modifier.weight(1.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Shadow,
                        contentDescription = null,
                        tint = if (isShadowingMode) primaryColor else onSurfaceVariantColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "SHADOW", 
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isShadowingMode) primaryColor else onSurfaceVariantColor
                    )
                }
            }

            // Speed
            Box(modifier = Modifier.weight(1f)) {
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
            }

            // Loop / Continuous
            Box(modifier = Modifier.weight(1f)) {
                ControlChip(
                    icon = AppIcons.Loop,
                    label = if (loopCount == 0) "∞" else "${loopCount}x",
                    onClick = {
                        val counts = listOf(1, 3, 5, 0)
                        val next = counts[(counts.indexOf(loopCount) + 1) % counts.size]
                        onLoopCountChange(next)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    themeColors = themeColors
                )
            }

            // Sleep Timer
            Box(modifier = Modifier.weight(1f)) {
                ControlChip(
                    icon = AppIcons.Sleep,
                    label = if (sleepTimerRemainingMs == 0L) "OFF" else {
                        val mins = (sleepTimerRemainingMs / 60000).toInt() + 1
                        "${mins}m"
                    },
                    onClick = {
                        val currentMins = (sleepTimerRemainingMs / 60000).toInt()
                        val nextMins = when {
                            currentMins == 0 -> 5
                            currentMins < 15 -> currentMins + 5
                            currentMins < 60 -> currentMins + 15
                            else -> 0
                        }
                        onSleepTimerChange(nextMins)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    themeColors = themeColors
                )
            }
        }

        // --- TRACK GROUP ---
        SectionHeader("A-B LOOP", themeColors)

        // Markers and Save row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MarkerControlGroup(
                    label = "START (A)",
                    isActive = abLoopStart >= 0f,
                    onMainClick = { onSetAbLoopStart(if (abLoopStart >= 0f) -1f else currentProgress) },
                    onNudge = onNudgeAbLoopStart,
                    themeColors = themeColors
                )

                MarkerControlGroup(
                    label = "END (B)",
                    isActive = abLoopEnd >= 0f,
                    onMainClick = { onSetAbLoopEnd(if (abLoopEnd >= 0f) -1f else currentProgress) },
                    onNudge = onNudgeAbLoopEnd,
                    themeColors = themeColors
                )
            }

            Surface(
                onClick = onSaveLoopToFile,
                enabled = abActive,
                shape = RoundedCornerShape(8.dp),
                color = if (abActive) themeColors.secondary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (abActive) themeColors.secondary.copy(alpha = 0.3f) else Color.Transparent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(AppIcons.Save, null, modifier = Modifier.size(14.dp), 
                         tint = if (abActive) themeColors.secondary else MaterialTheme.colorScheme.outline)
                    Text("SAVE LOOP", 
                         style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                         color = if (abActive) themeColors.secondary else MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Explanation
        Text(
            text = "Markers and timeline focus on the current recording.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 11.sp
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SectionHeader(text: String, themeColors: AppColorPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        )
    }
}
