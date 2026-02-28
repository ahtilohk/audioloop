package com.example.audioloop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.max
import com.example.audioloop.ui.theme.*
import com.example.audioloop.WaveformGenerator
import com.example.audioloop.AppIcons

// Define local colors if not resolved
private val Red100 = Color(0xFFFFE5E5)
private val Red200 = Color(0xFFFECACA)
private val Red500 = Color(0xFFEF4444)
private val Red900 = Color(0xFF7F1D1D)
private val Amber500 = Color(0xFFF59E0B)
private val Amber700 = Color(0xFFB45309)


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
fun TrimAudioScreen(
    file: File,
    uri: Uri,
    durationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long, replace: Boolean, removeSelection: Boolean, fadeInMs: Long, fadeOutMs: Long) -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette
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
    var fadeInEnabled by remember { mutableStateOf(false) }
    var fadeOutEnabled by remember { mutableStateOf(false) }
    var autoTrimSilence by remember { mutableStateOf(false) }

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
            WaveformGenerator.extractWaveform(file, 500) // High resolution for zooming
        }
    }

    // Initialize MediaPlayer
    LaunchedEffect(uri) {
        isPreviewPlaying = false
        previewPositionMs = 0L
        playerReady = false
        playerInitError = false

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
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Zinc950
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Zinc800, CircleShape)
                ) {
                    Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        "Trim Audio",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "Studio Editor",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = themeColors.primary400
                        )
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Compact Segmented Control for Mode
                Surface(
                    color = Zinc900,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Zinc700)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        val keepSelected = trimMode == TrimMode.Keep
                        val itemModifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                        
                        Box(
                            modifier = Modifier
                                .background(if (keepSelected) themeColors.primary700 else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { trimMode = TrimMode.Keep }
                                .then(itemModifier)
                        ) {
                            Text("Keep", color = if (keepSelected) Color.White else Zinc500, style = MaterialTheme.typography.labelMedium)
                        }
                        Box(
                            modifier = Modifier
                                .background(if (!keepSelected) Red900 else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { trimMode = TrimMode.Remove }
                                .then(itemModifier)
                        ) {
                            Text("Cut", color = if (!keepSelected) Red100 else Zinc500, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (playerReady) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Shared state for trim handles (accessible by nudge buttons below)
                    var startMs by remember { mutableFloatStateOf(0f) }
                    var endMs by remember { mutableFloatStateOf(durationMs.toFloat()) }
                    var zoomScale by remember { mutableFloatStateOf(1f) }
                    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
                    var waveformWidthPx by remember { mutableFloatStateOf(0f) }

                    // Visual Trimmer with Waveform - Premium Look
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp) // Taller
                            .background(Color.Black, RoundedCornerShape(20.dp)) // Black background for contrast
                            .border(1.dp, Zinc700, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val totalDuration = durationMs.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        val handleHitWidth = with(LocalDensity.current) { 48.dp.toPx() }
                        val handleLineWidth = with(LocalDensity.current) { 2.dp.toPx() }
                        val handleTabWidth = with(LocalDensity.current) { 16.dp.toPx() }
                        val handleTabHeight = with(LocalDensity.current) { 24.dp.toPx() }

                        // Coordinate helpers
                        fun msToPx(ms: Float): Float {
                            return (ms / totalDuration) * widthPx * zoomScale - scrollOffsetPx
                        }
                        fun pxToMs(px: Float): Float {
                            return ((px + scrollOffsetPx) / (widthPx * zoomScale)) * totalDuration
                        }

                        // Sync widthPx to outer scope
                        LaunchedEffect(widthPx) { waveformWidthPx = widthPx }
                        
                        // Handle clamping and range update
                        LaunchedEffect(startMs, endMs) {
                            range = min(startMs, endMs)..max(startMs, endMs)
                        }

                        // State for drag target
                        var dragTarget by remember { mutableStateOf<TrimDragTarget?>(null) }
                        
                        val startX = msToPx(startMs)
                        val endX = msToPx(endMs)
                        val selectionStartX = min(startX, endX)
                        val selectionEndX = max(startX, endX)
                        
                        val selectionStartMs = min(startMs, endMs)
                        val selectionEndMs = max(startMs, endMs)

                        // Auto-trim Silence logic
                        LaunchedEffect(autoTrimSilence, waveform.value) {
                            val bars = waveform.value
                            if (autoTrimSilence && trimMode == TrimMode.Keep && bars != null && bars.isNotEmpty()) {
                                val silenceThreshold = 5
                                val firstLoud = bars.indexOfFirst { it > silenceThreshold }
                                val lastLoud = bars.indexOfLast { it > silenceThreshold }
                                if (firstLoud >= 0 && lastLoud >= firstLoud) {
                                    startMs = (firstLoud.toFloat() / bars.size) * totalDuration
                                    endMs = ((lastLoud + 1).toFloat() / bars.size) * totalDuration
                                }
                            }
                        }

                        LaunchedEffect(selectionStartMs, selectionEndMs) {
                             range = selectionStartMs..selectionEndMs
                        }
                        
                        // Playback sync logic
                        LaunchedEffect(selectionStartMs, selectionEndMs, trimMode) {
                            // Validate current position based on mode
                            // Only force seek if we weren't just scrubbing
                            if (dragTarget == null) {
                                val current = previewPositionMs.toFloat()
                                val validated = resolvePreviewPosition(current)
                                if (validated != current.toLong()) {
                                     previewPositionMs = validated
                                     if (isPreviewPlaying) previewPlayer.seekTo(validated.toInt())
                                }
                            }
                        }

                        LaunchedEffect(isPreviewPlaying, trimMode, selectionStartMs, selectionEndMs, fadeInEnabled, fadeOutEnabled) {
                             if (isPreviewPlaying) {
                                  while (isActive && previewPlayer.isPlaying) {
                                      val currentMs = previewPlayer.currentPosition.toLong()
                                      previewPositionMs = currentMs

                                      // Fade preview via volume
                                      val effectiveStart: Long
                                      val effectiveEnd: Long
                                      if (trimMode == TrimMode.Keep) {
                                          effectiveStart = selectionStartMs.toLong()
                                          effectiveEnd = selectionEndMs.toLong()
                                      } else {
                                          effectiveStart = 0L
                                          effectiveEnd = durationMs
                                      }
                                      val effectiveDuration = (effectiveEnd - effectiveStart).coerceAtLeast(1)
                                      val fadeDurationMs = (effectiveDuration / 10).coerceIn(200, 3000)
                                      var vol = 1f
                                      if (fadeInEnabled) {
                                          val elapsed = currentMs - effectiveStart
                                          if (elapsed < fadeDurationMs) {
                                              vol = (elapsed.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                                          }
                                      }
                                      if (fadeOutEnabled) {
                                          val remaining = effectiveEnd - currentMs
                                          if (remaining < fadeDurationMs) {
                                              val fadeOutVol = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                                              vol = min(vol, fadeOutVol)
                                          }
                                      }
                                      try { previewPlayer.setVolume(vol, vol) } catch (_: Exception) {}

                                      if (trimMode == TrimMode.Keep) {
                                          if (currentMs >= selectionEndMs) {
                                                // Stop and jump back to selection start
                                                previewPlayer.pause()
                                                previewPlayer.setVolume(1f, 1f)
                                                previewPlayer.seekTo(selectionStartMs.toInt())
                                                previewPositionMs = selectionStartMs.toLong()
                                                isPreviewPlaying = false
                                          }
                                      } else {
                                           if (currentMs >= selectionStartMs && currentMs < selectionEndMs) {
                                                val seekPos = (selectionEndMs + 10).toInt().coerceAtMost(durationMs.toInt())
                                                previewPlayer.seekTo(seekPos)
                                                previewPositionMs = seekPos.toLong()
                                                delay(50)
                                           }
                                       }
                                      delay(30)
                                  }
                                  // Playback ended naturally - loop back
                                  try { previewPlayer.setVolume(1f, 1f) } catch (_: Exception) {}
                                  if (trimMode == TrimMode.Keep) {
                                      previewPlayer.seekTo(selectionStartMs.toInt())
                                      previewPositionMs = selectionStartMs.toLong()
                                  } else {
                                      previewPlayer.seekTo(0)
                                      previewPositionMs = 0L
                                  }
                                  isPreviewPlaying = false
                             }
                        }

                        // Drawing
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Grid lines (fixed to screen)
                            val steps = 10
                            val stepPx = size.width / steps
                            for (i in 1 until steps) {
                                drawLine(
                                    color = Zinc800,
                                    start = Offset(i * stepPx, 0f),
                                    end = Offset(i * stepPx, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            
                            val fadeFraction = 0.10f
                            val silenceThreshold = 5
                            val effectiveWidth = if (trimMode == TrimMode.Keep) {
                                (selectionEndX - selectionStartX).coerceAtLeast(1f)
                            } else {
                                (widthPx * zoomScale - (selectionEndX - selectionStartX)).coerceAtLeast(1f)
                            }
                            val fadeInPx = if (fadeInEnabled) effectiveWidth * fadeFraction else 0f
                            val fadeOutPx = if (fadeOutEnabled) effectiveWidth * fadeFraction else 0f

                            if (waveform.value != null) {
                                val bars = waveform.value!!
                                val totalWaveWidth = widthPx * zoomScale
                                val barWidth = totalWaveWidth / bars.size
                                
                                // Optimization: only draw visible bars
                                val firstVisible = ((scrollOffsetPx - barWidth) / barWidth).toInt().coerceAtLeast(0)
                                val lastVisible = ((scrollOffsetPx + widthPx + barWidth) / barWidth).toInt().coerceAtMost(bars.size - 1)

                                for (index in firstVisible..lastVisible) {
                                    val amplitude = bars[index]
                                    val x = index * barWidth - scrollOffsetPx
                                    val isSelected = x >= selectionStartX && x <= selectionEndX
                                    val isSilent = amplitude <= silenceThreshold
                                    val isKept = if (trimMode == TrimMode.Keep) isSelected else !isSelected

                                    var fadeMultiplier = 1f
                                    if (isKept) {
                                        val totalX = index * barWidth
                                        if (trimMode == TrimMode.Keep) {
                                            val posInSelection = totalX - (selectionStartX + scrollOffsetPx)
                                            if (fadeInEnabled && posInSelection < fadeInPx && fadeInPx > 0f) {
                                                fadeMultiplier = (posInSelection / fadeInPx).coerceIn(0f, 1f)
                                            }
                                            val posFromEnd = (selectionEndX + scrollOffsetPx) - totalX
                                            if (fadeOutEnabled && posFromEnd < fadeOutPx && fadeOutPx > 0f) {
                                                fadeMultiplier = min(fadeMultiplier, (posFromEnd / fadeOutPx).coerceIn(0f, 1f))
                                            }
                                        } else {
                                            if (fadeInEnabled && totalX < fadeInPx && fadeInPx > 0f) {
                                                fadeMultiplier = (totalX / fadeInPx).coerceIn(0f, 1f)
                                            }
                                            val posFromEnd = totalWaveWidth - totalX
                                            if (fadeOutEnabled && posFromEnd < fadeOutPx && fadeOutPx > 0f) {
                                                fadeMultiplier = min(fadeMultiplier, (posFromEnd / fadeOutPx).coerceIn(0f, 1f))
                                            }
                                        }
                                    }

                                    val rawHeight = (amplitude / 100f) * size.height * 0.8f
                                    val barHeight = rawHeight * fadeMultiplier
                                    val barColor = if (isKept) {
                                        if (isSilent && autoTrimSilence) Amber500.copy(alpha = 0.5f)
                                        else themeColors.primary400
                                    } else Zinc600

                                    drawLine(
                                        color = barColor,
                                        start = Offset(x, (size.height - barHeight) / 2),
                                        end = Offset(x, (size.height + barHeight) / 2),
                                        strokeWidth = (barWidth * 0.7f).coerceAtLeast(1f),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // Dimming layers
                            val dimColor = Color.Black.copy(alpha = 0.4f)
                            if (trimMode == TrimMode.Keep) {
                                drawRect(dimColor, size = Size(selectionStartX.coerceAtLeast(0f), size.height))
                                drawRect(dimColor, topLeft = Offset(selectionEndX, 0f), size = Size((size.width - selectionEndX).coerceAtLeast(0f), size.height))
                            } else {
                                drawRect(dimColor, topLeft = Offset(selectionStartX, 0f), size = Size((selectionEndX - selectionStartX).coerceAtLeast(0f), size.height))
                            }

                            // Handles
                            val handleH = size.height
                            val gripLineCount = 3
                            val gripLineSpacing = 2.5.dp.toPx()
                            val gripLineH = 8.dp.toPx()
                            val tabCorner = CornerRadius(3.dp.toPx())
                            val hLineW = with(density) { 2.dp.toPx() }

                            // Start Handle
                            drawLine(color = themeColors.primary400, start = Offset(startX, 0f), end = Offset(startX, handleH), strokeWidth = hLineW)
                            drawRoundRect(color = themeColors.primary500, topLeft = Offset(startX - handleTabWidth / 2, 0f), size = Size(handleTabWidth, handleTabHeight), cornerRadius = tabCorner)
                            drawRoundRect(color = themeColors.primary500, topLeft = Offset(startX - handleTabWidth / 2, handleH - handleTabHeight), size = Size(handleTabWidth, handleTabHeight), cornerRadius = tabCorner)

                            // End Handle
                            drawLine(color = Red400, start = Offset(endX, 0f), end = Offset(endX, handleH), strokeWidth = hLineW)
                            drawRoundRect(color = Red500, topLeft = Offset(endX - handleTabWidth / 2, 0f), size = Size(handleTabWidth, handleTabHeight), cornerRadius = tabCorner)
                            drawRoundRect(color = Red500, topLeft = Offset(endX - handleTabWidth / 2, handleH - handleTabHeight), size = Size(handleTabWidth, handleTabHeight), cornerRadius = tabCorner)
                            
                            // Playhead
                            val playheadX = msToPx(previewPositionMs.toFloat())
                            val isDraggingPlayhead = dragTarget == TrimDragTarget.Playhead
                            val playheadAlpha = if (isDraggingPlayhead) 1f else 0.85f
                            val playheadLineWidth = if (isDraggingPlayhead) 2.5f.dp.toPx() else 2.dp.toPx()

                            drawLine(color = Color.White.copy(alpha = playheadAlpha), start = Offset(playheadX, 0f), end = Offset(playheadX, size.height), strokeWidth = playheadLineWidth)
                            val pillW = 10.dp.toPx()
                            val pillH = 16.dp.toPx()
                            drawRoundRect(color = Color.White.copy(alpha = playheadAlpha), topLeft = Offset(playheadX - pillW / 2, 0f), size = Size(pillW, pillH), cornerRadius = CornerRadius(3.dp.toPx()))
                            drawRoundRect(color = Color.White.copy(alpha = playheadAlpha), topLeft = Offset(playheadX - pillW / 2, size.height - pillH), size = Size(pillW, pillH), cornerRadius = CornerRadius(3.dp.toPx()))
                        }
                        
                        // 2. Gesture & Overlay Layer
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(widthPx) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val oldScale = zoomScale
                                        zoomScale = (zoomScale * zoom).coerceIn(1f, 15f)
                                        val maxScroll = (widthPx * zoomScale - widthPx).coerceAtLeast(0f)
                                        
                                        // Center zoom on focus point
                                        val focusFraction = (centroid.x + scrollOffsetPx) / (widthPx * oldScale)
                                        scrollOffsetPx = (scrollOffsetPx - pan.x + focusFraction * widthPx * (zoomScale - oldScale))
                                            .coerceIn(0f, maxScroll)
                                    }
                                }
                                .pointerInput(widthPx, zoomScale, scrollOffsetPx, trimMode) {
                                    val slop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        
                                        // 1. If more than 1 finger is down initially, it's likely a swim/zoom gesture. Skip handle logic.
                                        if (currentEvent.changes.size > 1) return@awaitEachGesture

                                        val downX = down.position.x
                                        val downY = down.position.y

                                        val currentStartX = msToPx(startMs)
                                        val currentEndX = msToPx(endMs)
                                        val playheadPx = msToPx(previewPositionMs.toFloat())

                                        val distStart = kotlin.math.abs(downX - currentStartX)
                                        val distEnd = kotlin.math.abs(downX - currentEndX)
                                        val distPlayhead = kotlin.math.abs(downX - playheadPx)

                                        val pillZone = 25.dp.toPx()
                                        val inPillZone = downY < pillZone || downY > (heightPx - pillZone)

                                        val target = if (inPillZone && distPlayhead < handleHitWidth) {
                                            TrimDragTarget.Playhead
                                        } else {
                                            val handleCandidates = mutableListOf<Pair<TrimDragTarget, Float>>()
                                            if (distStart < handleHitWidth) handleCandidates.add(TrimDragTarget.Start to distStart)
                                            if (distEnd < handleHitWidth) handleCandidates.add(TrimDragTarget.End to distEnd)
                                            if (distPlayhead < handleHitWidth) handleCandidates.add(TrimDragTarget.Playhead to distPlayhead)
                                            handleCandidates.minByOrNull { it.second }?.first
                                        }

                                        var wasDragged = false
                                        var prevX = downX

                                        drag(down.id) { change ->
                                            // 2. If a second finger comes down during a handle drag, cancel the handle drag 
                                            // and let the transform gesture take over.
                                            if (currentEvent.changes.size > 1) return@drag

                                            if (!wasDragged && kotlin.math.abs(change.position.x - downX) > slop) {
                                                wasDragged = true
                                                dragTarget = target
                                                isDraggingHandle = (target != null && target != TrimDragTarget.Playhead)
                                            }

                                            if (wasDragged) {
                                                if (target != null) {
                                                    // Consume ONLY if we are moving a handle
                                                    change.consume()
                                                    val dragDeltaMs = ( (change.position.x - prevX) / (widthPx * zoomScale) ) * totalDuration
                                                    when (target) {
                                                        TrimDragTarget.Start -> startMs = (startMs + dragDeltaMs).coerceIn(0f, totalDuration)
                                                        TrimDragTarget.End -> endMs = (endMs + dragDeltaMs).coerceIn(0f, totalDuration)
                                                        TrimDragTarget.Playhead -> {
                                                            val tappedMs = pxToMs(change.position.x)
                                                            previewPositionMs = resolvePreviewPosition(tappedMs)
                                                            previewPlayer.seekTo(previewPositionMs.toInt())
                                                        }
                                                    }
                                                } else {
                                                    // Empty space drag: DONT consume, let detectTransformGestures handle PAN
                                                }
                                            }
                                            prevX = change.position.x
                                        }

                                        dragTarget = null
                                        isDraggingHandle = false

                                        if (!wasDragged && currentEvent.changes.all { it.changedToUp() }) {
                                            val tapMs = pxToMs(downX)
                                            previewPositionMs = resolvePreviewPosition(tapMs)
                                            previewPlayer.seekTo(previewPositionMs.toInt())
                                        }
                                    }
                                }
                        )

                        // 3. Floating Overlays
                        androidx.compose.animation.AnimatedVisibility(
                            visible = zoomScale > 1.05f,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                    .clickable {
                                        zoomScale = 1f
                                        scrollOffsetPx = 0f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.ZoomOutMap,
                                    contentDescription = "Reset Zoom",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    // Time Info Card Container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Zinc800.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .border(1.dp, Zinc600.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Row 1: CURRENT + NEW LENGTH
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Current Position sub-card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        previewPositionMs = 0L
                                        previewPlayer.seekTo(0)
                                    }
                                    .padding(10.dp)
                            ) {
                                Text("CURRENT", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatDuration(previewPositionMs),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1
                                )
                            }

                            // New Length sub-card
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                val start = range.start.toLong()
                                val end = range.endInclusive.toLong()
                                val projectedDuration = if (trimMode == TrimMode.Keep) {
                                    (end - start).coerceAtLeast(0L)
                                } else {
                                    (durationMs - (end - start)).coerceAtLeast(0L)
                                }

                                Text("NEW LENGTH", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatDuration(projectedDuration),
                                    color = themeColors.primary200,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1
                                )
                            }
                        }

                        // Row 2: START + END with nudge arrows for precision
                        val nudgeMs = 100f // 100ms per tap
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // START card with nudge arrows
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left arrow (decrease start)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Zinc800, RoundedCornerShape(6.dp))
                                        .border(1.dp, Zinc600, RoundedCornerShape(6.dp))
                                        .clickable {
                                            startMs = (startMs - nudgeMs).coerceAtLeast(0f)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.ArrowBack, contentDescription = "Start earlier", tint = themeColors.primary300, modifier = Modifier.size(16.dp))
                                }
                                // Time display
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("START", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        formatDuration(range.start.toLong()),
                                        color = themeColors.primary200,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 1
                                    )
                                }
                                // Right arrow (increase start)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Zinc800, RoundedCornerShape(6.dp))
                                        .border(1.dp, Zinc600, RoundedCornerShape(6.dp))
                                        .clickable {
                                            startMs = (startMs + nudgeMs).coerceAtMost(durationMs.toFloat())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.ArrowForward, contentDescription = "Start later", tint = themeColors.primary300, modifier = Modifier.size(16.dp))
                                }
                            }

                            // END card with nudge arrows
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left arrow (decrease end)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Zinc800, RoundedCornerShape(6.dp))
                                        .border(1.dp, Zinc600, RoundedCornerShape(6.dp))
                                        .clickable {
                                            endMs = (endMs - nudgeMs).coerceAtLeast(0f)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.ArrowBack, contentDescription = "End earlier", tint = Red200, modifier = Modifier.size(16.dp))
                                }
                                // Time display
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("END", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        formatDuration(range.endInclusive.toLong()),
                                        color = Red200,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        maxLines = 1
                                    )
                                }
                                // Right arrow (increase end)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Zinc800, RoundedCornerShape(6.dp))
                                        .border(1.dp, Zinc600, RoundedCornerShape(6.dp))
                                        .clickable {
                                            endMs = (endMs + nudgeMs).coerceAtMost(durationMs.toFloat())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(AppIcons.ArrowForward, contentDescription = "End later", tint = Red200, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        // Row 3: Effect toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Fade In toggle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (fadeInEnabled) themeColors.primary700 else Zinc800,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (fadeInEnabled) themeColors.primary500 else Zinc600,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { fadeInEnabled = !fadeInEnabled }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Fade In",
                                    color = if (fadeInEnabled) Color.White else Zinc500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Auto-trim Silence toggle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (autoTrimSilence) Amber700 else Zinc800,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (autoTrimSilence) Amber500 else Zinc600,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { autoTrimSilence = !autoTrimSilence }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Silence",
                                    color = if (autoTrimSilence) Color.White else Zinc500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Fade Out toggle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (fadeOutEnabled) themeColors.primary700 else Zinc800,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (fadeOutEnabled) themeColors.primary500 else Zinc600,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { fadeOutEnabled = !fadeOutEnabled }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Fade Out",
                                    color = if (fadeOutEnabled) Color.White else Zinc500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Row 4: Play & Reset Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Reset Button
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Zinc800, CircleShape)
                                        .border(1.dp, Zinc600, CircleShape)
                                        .clickable {
                                            previewPositionMs = 0L
                                            previewPlayer.seekTo(0)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Replay,
                                        contentDescription = "Reset",
                                        tint = Zinc400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Play Button
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .shadow(
                                            elevation = if (isPreviewPlaying) 16.dp else 8.dp,
                                            shape = CircleShape,
                                            spotColor = if (isPreviewPlaying) themeColors.primary500 else Color.Black
                                        )
                                        .background(if (isPreviewPlaying) themeColors.primary500 else Zinc800, CircleShape)
                                        .border(1.dp, if (isPreviewPlaying) themeColors.primary400 else Zinc600, CircleShape)
                                        .clickable {
                                             val start = range.start.toLong()
                                             val end = range.endInclusive.toLong()

                                             if (isPreviewPlaying) {
                                                 previewPlayer.pause()
                                                 isPreviewPlaying = false
                                             } else {
                                                  // Smart Start Logic
                                                  previewPositionMs = resolvePreviewPosition(previewPositionMs.toFloat())

                                                 previewPlayer.seekTo(previewPositionMs.toInt())
                                                 previewPlayer.start()
                                                 isPreviewPlaying = true
                                             }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewPlaying) AppIcons.Pause else AppIcons.Play,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(60.dp))

                    // Final Actions at bottom
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val s = range.start.toLong(); val e = range.endInclusive.toLong()
                                val dur = if (trimMode == TrimMode.Keep) e - s else durationMs - (e - s)
                                val fd = (dur / 10).coerceIn(200, 3000)
                                onConfirm(s, e, false, trimMode == TrimMode.Remove, if (fadeInEnabled) fd else 0L, if (fadeOutEnabled) fd else 0L)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc800, contentColor = Color.White),
                            border = BorderStroke(1.dp, Zinc700)
                        ) {
                            Icon(AppIcons.Add, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy File", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val s = range.start.toLong(); val e = range.endInclusive.toLong()
                                val dur = if (trimMode == TrimMode.Keep) e - s else durationMs - (e - s)
                                val fd = (dur / 10).coerceIn(200, 3000)
                                onConfirm(s, e, true, trimMode == TrimMode.Remove, if (fadeInEnabled) fd else 0L, if (fadeOutEnabled) fd else 0L)
                            },
                            modifier = Modifier.weight(1.2f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(AppIcons.Check, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Replace Original", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!playerInitError) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeColors.primary500)
                }
            }
        }
    }
}
