package com.example.audioloop.ui

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audioloop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

private enum class TrimDragTarget {
    Start,
    End,
    Playhead
}

private enum class TrimMode {
    Keep,
    Remove
}

@Composable
fun TrimAudioDialog(
    file: File,
    uri: android.net.Uri,
    durationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long, replace: Boolean, removeSelection: Boolean) -> Unit,
    themeColors: AppColorPalette = AppTheme.CYAN.palette
) {
    var range by remember { mutableStateOf(0f..durationMs.toFloat()) }
    val context = LocalContext.current
    val previewPlayer = remember(file) { MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var trimMode by remember { mutableStateOf(TrimMode.Keep) }
    var playerReady by remember { mutableStateOf(false) }
    var playerInitError by remember { mutableStateOf(false) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    
    fun resolvePreviewPosition(rawMs: Float): Long {
        val total = durationMs.toFloat()
        val clamped = rawMs.coerceIn(0f, total)
        return if (trimMode == TrimMode.Keep) {
            clamped.coerceIn(range.start, range.endInclusive).toLong()
        } else {
            val start = range.start
            val end = range.endInclusive
            if (clamped in start..end) {
                val distStart = clamped - start
                val distEnd = end - clamped
                if (distStart <= distEnd) start.toLong() else end.toLong()
            } else {
                clamped.toLong()
            }
        }
    }
    
    // Waveform Loading
    val waveform = produceState<List<Int>?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) {
            com.example.audioloop.WaveformGenerator.extractWaveform(file, 60)
        }
    }

    // Initialize MediaPlayer using ContentResolver (professional approach)
    LaunchedEffect(uri) {
        isPreviewPlaying = false
        previewPositionMs = 0L
        playerReady = false
        playerInitError = false

        // Retry up to 10 times with increasing delays
        val delays = listOf(0L, 200L, 400L, 600L, 800L, 1000L, 1500L, 2000L, 2500L, 3000L)
        for ((attempt, delayMs) in delays.withIndex()) {
            if (delayMs > 0) delay(delayMs)
            try {
                val success = withContext(Dispatchers.IO) {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        previewPlayer.reset()
                        previewPlayer.setDataSource(pfd.fileDescriptor)
                        previewPlayer.prepare()
                        pfd.close()
                        true
                    } else {
                        false
                    }
                }
                if (success) {
                    playerReady = true
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                if (attempt == delays.lastIndex) {
                    playerInitError = true
                }
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            try {
                previewPlayer.release()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(playerInitError) {
        if (playerInitError) {
            android.widget.Toast.makeText(context, "Unable to load audio file for trimming", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Zinc800),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (playerReady) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Trim Audio",
                        style = MaterialTheme.typography.titleLarge.copy(color = Zinc100, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Drag handles to select range. Tap waveform to seek.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Zinc400)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                                    
                    // Trim Mode Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mode", style = MaterialTheme.typography.labelMedium.copy(color = Zinc500))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val keepSelected = trimMode == TrimMode.Keep
                            val removeSelected = trimMode == TrimMode.Remove
                            
                            // Keep Button
                            FilterChip(
                                selected = keepSelected,
                                onClick = { trimMode = TrimMode.Keep },
                                label = { Text("Keep Selected") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = themeColors.primaryContainer,
                                    selectedLabelColor = themeColors.onPrimaryContainer,
                                    containerColor = Zinc800,
                                    labelColor = Zinc300
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Zinc700,
                                    selectedBorderColor = Color.Transparent,
                                    enabled = true,
                                    selected = keepSelected
                                )
                            )
                            
                            // Remove Button
                            FilterChip(
                                selected = removeSelected,
                                onClick = { trimMode = TrimMode.Remove },
                                label = { Text("Remove Selected") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Red900.copy(alpha=0.3f), // Subtle red for remove
                                    selectedLabelColor = Red200,
                                    containerColor = Zinc800,
                                    labelColor = Zinc300
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Zinc700,
                                    selectedBorderColor = Red700,
                                    enabled = true,
                                    selected = removeSelected
                                )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Visual Trimmer with Waveform
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp) // Slightly taller
                            .background(
                                Brush.verticalGradient(listOf(Zinc800, Zinc900)),
                                RoundedCornerShape(16.dp)
                            )
                            .border(1.dp, Zinc700, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val totalDuration = durationMs.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        val handleHitWidth = with(LocalDensity.current) { 56.dp.toPx() }
                        val handleBarWidth = with(LocalDensity.current) { 6.dp.toPx() }
                        val handleBarRadius = with(LocalDensity.current) { 3.dp.toPx() }
                        val labelAreaHeight = with(LocalDensity.current) { 24.dp.toPx() }
                        val waveTop = labelAreaHeight
                        val waveBottom = heightPx
                        
                        var startX by remember { mutableFloatStateOf(0f) }
                        var endX by remember { mutableFloatStateOf(widthPx) }
                        val selectionStartX = min(startX, endX)
                        val selectionEndX = max(startX, endX)
                        val labelTextSize = with(LocalDensity.current) { 12.sp.toPx() }
                        val labelPadding = with(LocalDensity.current) { 6.dp.toPx() }
                        val labelGap = with(LocalDensity.current) { 8.dp.toPx() }
                        val handleTextPaint = remember {
                            Paint().apply {
                                isAntiAlias = true
                                textAlign = Paint.Align.CENTER
                                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                            }
                        }
                        val startHandleColor = themeColors.primary500
                        val endHandleColor = Red500
                        val selectionColor = themeColors.primary400
                        val remainingColor = Zinc700
                        val labelBackgroundColor = Zinc800.copy(alpha = 0.95f)
                        val labelTextColor = Color.White
                        val startHandleMs = if (widthPx > 0f) {
                            ((startX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                        } else {
                            0f
                        }
                        val endHandleMs = if (widthPx > 0f) {
                            ((endX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                        } else {
                            totalDuration
                        }
                        val selectionStartMs = if (widthPx > 0f) {
                            ((selectionStartX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                        } else {
                            0f
                        }
                        val selectionEndMs = if (widthPx > 0f) {
                            ((selectionEndX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                        } else {
                            totalDuration
                        }
    
                        LaunchedEffect(widthPx) {
                            startX = startX.coerceIn(0f, widthPx)
                            endX = endX.coerceIn(0f, widthPx)
                            range = selectionStartMs..selectionEndMs
                        }
    
                        LaunchedEffect(selectionStartMs, selectionEndMs, trimMode) {
                            val clampedPreviewMs = resolvePreviewPosition(previewPositionMs.toFloat())
                            if (isPreviewPlaying) {
                                previewPlayer.pause()
                                previewPlayer.seekTo(clampedPreviewMs.toInt())
                                isPreviewPlaying = false
                            }
                            previewPositionMs = clampedPreviewMs
                        }
    
                        LaunchedEffect(isPreviewPlaying, selectionStartMs, selectionEndMs, trimMode) {
                            if (isPreviewPlaying) {
                                while (isActive && previewPlayer.isPlaying) {
                                    val current = previewPlayer.currentPosition.toLong()
                                    previewPositionMs = current
                                    if (trimMode == TrimMode.Keep) {
                                        if (current >= selectionEndMs.toLong()) {
                                            previewPlayer.pause()
                                            previewPlayer.seekTo(selectionStartMs.toInt())
                                            isPreviewPlaying = false
                                            break
                                        }
                                    } else {
                                        if (current in selectionStartMs.toLong()..selectionEndMs.toLong()) {
                                            previewPlayer.seekTo(selectionEndMs.toInt())
                                            previewPositionMs = selectionEndMs.toLong()
                                        } else if (current >= totalDuration.toLong()) {
                                            previewPlayer.pause()
                                            previewPlayer.seekTo(0)
                                            previewPositionMs = 0L
                                            isPreviewPlaying = false
                                            break
                                        }
                                    }
                                    delay(50)
                                }
                            }
                        }
    
                        // Render Waveform and UI
                        if (waveform.value == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = themeColors.primary500, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            val bars = waveform.value!!
                            val barWidth = widthPx / bars.size
                            
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val waveAreaHeight = size.height - labelAreaHeight
    
                                // 1. Waveform bars
                                bars.forEachIndexed { index, amplitude ->
                                    val x = index * barWidth
                                    val barHeight = (amplitude / 100f) * waveAreaHeight * 0.8f // Scale down slightly
                                    val isSelected = x >= selectionStartX && x <= selectionEndX
                                    val barColor = if (trimMode == TrimMode.Keep) {
                                        if (isSelected) selectionColor else remainingColor
                                    } else {
                                        if (isSelected) Zinc600 else selectionColor
                                    }
                                    // Rounded caps for bars
                                    drawLine(
                                        color = barColor,
                                        start = Offset(x + barWidth / 2, waveTop + (waveAreaHeight - barHeight) / 2),
                                        end = Offset(x + barWidth / 2, waveTop + (waveAreaHeight + barHeight) / 2),
                                        strokeWidth = (barWidth * 0.7f).coerceAtLeast(1f),
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
    
                                // 2. Dim unselected regions
                                if (trimMode == TrimMode.Keep) {
                                    drawRect(
                                        color = Zinc900.copy(alpha = 0.6f),
                                        topLeft = Offset(0f, waveTop),
                                        size = Size(selectionStartX, waveAreaHeight)
                                    )
                                    drawRect(
                                        color = Zinc900.copy(alpha = 0.6f),
                                        topLeft = Offset(selectionEndX, waveTop),
                                        size = Size(size.width - selectionEndX, waveAreaHeight)
                                    )
                                } else {
                                    drawRect(
                                        color = Red900.copy(alpha = 0.2f),
                                        topLeft = Offset(selectionStartX, waveTop),
                                        size = Size(selectionEndX - selectionStartX, waveAreaHeight)
                                    )
                                }
    
                                // 3. Playhead
                                val playheadX = if (totalDuration > 0f) {
                                    ((previewPositionMs.toFloat() / totalDuration) * size.width).coerceIn(0f, size.width)
                                } else {
                                    0f
                                }
                                drawLine(
                                    color = Color.White.copy(alpha = if (playheadX in selectionStartX..selectionEndX) 1f else 0.5f),
                                    start = Offset(playheadX, waveTop),
                                    end = Offset(playheadX, waveBottom),
                                    strokeWidth = 2.dp.toPx()
                                )
    
                                // 4. Edge-bar handles
                                drawRoundRect(
                                    color = startHandleColor,
                                    topLeft = Offset(startX - handleBarWidth / 2, waveTop),
                                    size = Size(handleBarWidth, waveAreaHeight),
                                    cornerRadius = CornerRadius(handleBarRadius)
                                )
                                drawRoundRect(
                                    color = endHandleColor,
                                    topLeft = Offset(endX - handleBarWidth / 2, waveTop),
                                    size = Size(handleBarWidth, waveAreaHeight),
                                    cornerRadius = CornerRadius(handleBarRadius)
                                )
    
                                // 5. Time labels
                                drawIntoCanvas { canvas ->
                                    handleTextPaint.textSize = labelTextSize
                                    handleTextPaint.color = labelTextColor.toArgb()
                                    val startLabel = formatDuration(startHandleMs.toLong())
                                    val endLabel = formatDuration(endHandleMs.toLong())
                                    val startLabelWidth = handleTextPaint.measureText(startLabel)
                                    val endLabelWidth = handleTextPaint.measureText(endLabel)
                                    val labelHeight = labelTextSize + (labelPadding * 2)
                                    val startLabelBound = (startLabelWidth / 2) + labelPadding
                                    val endLabelBound = (endLabelWidth / 2) + labelPadding
                                    val startMin = startLabelBound
                                    val startMax = widthPx - startLabelBound
                                    val endMin = endLabelBound
                                    val endMax = widthPx - endLabelBound
                                    var startLabelX = startX.coerceIn(startMin, startMax)
                                    var endLabelX = endX.coerceIn(endMin, endMax)
                                    val requiredGap = startLabelBound + endLabelBound + labelGap
                                    if (endLabelX - startLabelX < requiredGap) {
                                        val mid = (startLabelX + endLabelX) / 2f
                                        startLabelX = (mid - requiredGap / 2f).coerceIn(startMin, startMax)
                                        endLabelX = (mid + requiredGap / 2f).coerceIn(endMin, endMax)
                                        if (endLabelX - startLabelX < requiredGap) {
                                            startLabelX = (endLabelX - requiredGap).coerceIn(startMin, startMax)
                                            endLabelX = (startLabelX + requiredGap).coerceIn(endMin, endMax)
                                        }
                                    }
                                    val startRectLeft = startLabelX - (startLabelWidth / 2) - labelPadding
                                    val startRectRight = startLabelX + (startLabelWidth / 2) + labelPadding
                                    val endRectLeft = endLabelX - (endLabelWidth / 2) - labelPadding
                                    val endRectRight = endLabelX + (endLabelWidth / 2) + labelPadding
                                    val labelTop = (labelAreaHeight - labelHeight) / 2f
                                    drawRoundRect(
                                        color = labelBackgroundColor,
                                        topLeft = Offset(startRectLeft, labelTop),
                                        size = Size(startRectRight - startRectLeft, labelHeight),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                    drawRoundRect(
                                        color = labelBackgroundColor,
                                        topLeft = Offset(endRectLeft, labelTop),
                                        size = Size(endRectRight - endRectLeft, labelHeight),
                                        cornerRadius = CornerRadius(6.dp.toPx())
                                    )
                                    val labelBaseline = labelTop + labelPadding + labelTextSize - 3 // small visual adjustment
                                    canvas.nativeCanvas.drawText(startLabel, startLabelX, labelBaseline, handleTextPaint)
                                    canvas.nativeCanvas.drawText(endLabel, endLabelX, labelBaseline, handleTextPaint)
                                }
                            }
                        }
                        
                        // Touch/Drag Layer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        down.consume()
                                        var playheadX = if (totalDuration > 0f) {
                                            ((previewPositionMs.toFloat() / totalDuration) * widthPx).coerceIn(0f, widthPx)
                                        } else {
                                            0f
                                        }
                                        val edgeThreshold = handleHitWidth * 0.75f
                                        val startHitLeft = if (startX < edgeThreshold) 0f else startX - handleHitWidth / 2
                                        val startHitRight = startX + handleHitWidth / 2
                                        val endHitLeft = endX - handleHitWidth / 2
                                        val endHitRight = if (endX > widthPx - edgeThreshold) widthPx else endX + handleHitWidth / 2
                                        val touchX = down.position.x
                                        val distToStart = kotlin.math.abs(touchX - startX)
                                        val distToEnd = kotlin.math.abs(touchX - endX)
                                        val isNearStart = touchX >= startHitLeft && touchX <= startHitRight
                                        val isNearEnd = touchX >= endHitLeft && touchX <= endHitRight
                                        val dragTarget = when {
                                            isNearStart && isNearEnd -> {
                                                if (kotlin.math.abs(down.position.x - startX) <= kotlin.math.abs(down.position.x - endX))
                                                    TrimDragTarget.Start else TrimDragTarget.End
                                            }
                                            isNearStart -> TrimDragTarget.Start
                                            isNearEnd -> TrimDragTarget.End
                                            else -> TrimDragTarget.Playhead
                                        }
                                        if (dragTarget != TrimDragTarget.Playhead) {
                                            isDraggingHandle = true
                                        }
                                        try {
                                            if (dragTarget == TrimDragTarget.Playhead) {
                                                playheadX = down.position.x.coerceIn(0f, widthPx)
                                                val newPreviewMs = if (widthPx > 0f) {
                                                    ((playheadX / widthPx) * totalDuration)
                                                        .coerceIn(selectionStartMs, selectionEndMs)
                                                } else {
                                                    0f
                                                }
                                                if (isPreviewPlaying) {
                                                    previewPlayer.pause()
                                                    isPreviewPlaying = false
                                                }
                                                previewPositionMs = newPreviewMs.toLong()
                                                previewPlayer.seekTo(previewPositionMs.toInt())
                                            }
                                            var lastX = down.position.x
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) {
                                                    break
                                                }
                                                change.consume()
                                                val dragAmount = change.position.x - lastX
                                                lastX = change.position.x
                                                if (dragAmount == 0f) {
                                                    continue
                                                }
                                                when (dragTarget) {
                                                    TrimDragTarget.Start -> {
                                                        startX = (startX + dragAmount).coerceIn(0f, widthPx)
                                                    }
                                                    TrimDragTarget.End -> {
                                                        endX = (endX + dragAmount).coerceIn(0f, widthPx)
                                                    }
                                                    TrimDragTarget.Playhead -> {
                                                        playheadX = (playheadX + dragAmount).coerceIn(0f, widthPx)
                                                        val newPreviewMs = if (widthPx > 0f) {
                                                            resolvePreviewPosition((playheadX / widthPx) * totalDuration)
                                                        } else {
                                                            0L
                                                        }
                                                        if (isPreviewPlaying) {
                                                            previewPlayer.pause()
                                                            isPreviewPlaying = false
                                                        }
                                                        previewPositionMs = newPreviewMs
                                                        previewPlayer.seekTo(previewPositionMs.toInt())
                                                    }
                                                }
                                                if (dragTarget != TrimDragTarget.Playhead) {
                                                    val newSelectionStart = min(startX, endX)
                                                    val newSelectionEnd = max(startX, endX)
                                                    val newStartMs = if (widthPx > 0f) {
                                                        ((newSelectionStart / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                                                    } else {
                                                        0f
                                                    }
                                                    val newEndMs = if (widthPx > 0f) {
                                                        ((newSelectionEnd / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                                                    } else {
                                                        totalDuration
                                                    }
                                                    range = newStartMs..newEndMs
                                                }
                                            }
                                        } finally {
                                            isDraggingHandle = false
                                        }
                                    }
                                }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
    
                    // Professional Preview Time Display
                    val selectionDurationMs = if (trimMode == TrimMode.Keep) {
                        (range.endInclusive - range.start).toLong()
                    } else {
                        durationMs - (range.endInclusive - range.start).toLong()
                    }
                    val progressFraction = if (selectionDurationMs > 0) {
                        ((previewPositionMs - range.start.toLong()).toFloat() / (range.endInclusive - range.start).toLong()).coerceIn(0f, 1f)
                    } else 0f
    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Two-column time display with labels
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Current position column
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Position",
                                    color = Zinc500,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    formatDuration(previewPositionMs),
                                    color = if (isPreviewPlaying) themeColors.primary400 else Zinc100,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
    
                            Text(
                                "  /  ",
                                color = Zinc600,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
    
                            // New file length column
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "New length",
                                    color = Zinc500,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    formatDuration(selectionDurationMs),
                                    color = Zinc400,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
    
                        Spacer(modifier = Modifier.height(12.dp))
    
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Zinc800, RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .background(
                                        if (isPreviewPlaying) themeColors.primary500 else Zinc600,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
    
                    Spacer(modifier = Modifier.height(24.dp))
    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { 
                            Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = Zinc400)) 
                        }
                        
                        // Play/Pause
                        Button(
                            onClick = {
                                if (isPreviewPlaying) {
                                    previewPlayer.pause()
                                    isPreviewPlaying = false
                                } else {
                                    val baseStartMs = previewPositionMs.toFloat()
                                    val startMs = resolvePreviewPosition(baseStartMs)
                                    previewPositionMs = startMs
                                    previewPlayer.seekTo(startMs.toInt())
                                    previewPlayer.start()
                                    isPreviewPlaying = true
                                }
                            },
                             colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewPlaying) Zinc800 else Zinc700
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                if(isPreviewPlaying) com.example.audioloop.AppIcons.Pause else com.example.audioloop.AppIcons.PlayArrow, 
                                null, 
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPreviewPlaying) "Pause" else "Preview")
                        }
    
                        // Save
                        Button(
                            onClick = {
                                previewPlayer.release()
                                onConfirm(range.start.toLong(), range.endInclusive.toLong(), true, trimMode == TrimMode.Remove)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                             Text("Save")
                        }
                    }
                }
            } else {
                 Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = themeColors.primary500)
                }
            }
        }
    }
}
