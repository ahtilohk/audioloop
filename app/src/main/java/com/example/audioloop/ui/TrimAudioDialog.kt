package com.example.audioloop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
            WaveformGenerator.extractWaveform(file, 80) // Higher resolution for premium feel
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
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(24.dp), // More modern rounding
            border = BorderStroke(1.dp, Zinc600),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp), // Higher elevation
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            if (playerReady) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                        
                        // Compact Segmented Control for Mode
                        Surface(
                            color = Zinc950,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Zinc600)
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
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Visual Trimmer with Waveform - Premium Look
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp) // Taller
                            .background(Color.Black, RoundedCornerShape(16.dp)) // Black background for contrast
                            .border(1.dp, Zinc600, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        val widthPx = constraints.maxWidth.toFloat()
                        val totalDuration = durationMs.toFloat()
                        val heightPx = constraints.maxHeight.toFloat()
                        val handleHitWidth = with(LocalDensity.current) { 48.dp.toPx() }
                        val handleBarWidth = with(LocalDensity.current) { 12.dp.toPx() } // Thicker handles
                        val waveTop = 0f
                        val waveBottom = heightPx
                        
                        var startX by remember { mutableFloatStateOf(0f) }
                        var endX by remember { mutableFloatStateOf(widthPx) }
                        
                        // State for drag target
                        var dragTarget by remember { mutableStateOf<TrimDragTarget?>(null) }
                        
                        val selectionStartX = min(startX, endX)
                        val selectionEndX = max(startX, endX)
                        
                        val startHandleMs = if (widthPx > 0f) ((startX / widthPx) * totalDuration).coerceIn(0f, totalDuration) else 0f
                        val endHandleMs = if (widthPx > 0f) ((endX / widthPx) * totalDuration).coerceIn(0f, totalDuration) else totalDuration
                        val selectionStartMs = min(startHandleMs, endHandleMs)
                        val selectionEndMs = max(startHandleMs, endHandleMs)

                        LaunchedEffect(widthPx) {
                             if (widthPx > 0) {
                                  startX = 0f
                                  endX = widthPx
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
                                // If Keep mode: ensure inside range
                                if (trimMode == TrimMode.Keep) {
                                    if (current < selectionStartMs || current > selectionEndMs) {
                                         previewPositionMs = selectionStartMs.toLong()
                                         if (isPreviewPlaying) previewPlayer.seekTo(selectionStartMs.toInt())
                                    }
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
                                                previewPlayer.pause()
                                                previewPlayer.setVolume(1f, 1f)
                                                previewPlayer.seekTo(selectionStartMs.toInt())
                                                previewPositionMs = selectionStartMs.toLong()
                                                isPreviewPlaying = false
                                          }
                                      } else {
                                          if (currentMs >= selectionStartMs && currentMs < selectionEndMs) {
                                               val seekPos = (selectionEndMs + 20).toInt().coerceAtMost(durationMs.toInt())
                                               previewPlayer.seekTo(seekPos)
                                               previewPositionMs = seekPos.toLong()
                                               delay(100)
                                          }
                                      }
                                      delay(30)
                                  }
                                  try { previewPlayer.setVolume(1f, 1f) } catch (_: Exception) {}
                                  isPreviewPlaying = false
                             }
                        }

                        // Drawing
                        val density = LocalDensity.current.density
                         Canvas(modifier = Modifier.fillMaxSize()) {
                            // Grid lines
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
                            
                            if (waveform.value != null) {
                                val bars = waveform.value!!
                                val barWidth = widthPx / bars.size
                                bars.forEachIndexed { index, amplitude ->
                                    val x = index * barWidth
                                    val barHeight = (amplitude / 100f) * size.height * 0.8f // 80% height
                                    val isSelected = x >= selectionStartX && x <= selectionEndX
                                    val barColor = if (trimMode == TrimMode.Keep) {
                                        if (isSelected) themeColors.primary400 else Zinc600
                                    } else {
                                        if (isSelected) Zinc600 else themeColors.primary400
                                    }
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
                            val dimColor = Color.Black.copy(alpha = 0.3f)
                            if (trimMode == TrimMode.Keep) {
                                drawRect(dimColor, size = Size(selectionStartX, size.height))
                                drawRect(dimColor, topLeft = Offset(selectionEndX, 0f), size = Size(size.width - selectionEndX, size.height))
                            } else {
                                drawRect(dimColor, topLeft = Offset(selectionStartX, 0f), size = Size(selectionEndX - selectionStartX, size.height))
                            }

                            // Handles - Premium
                            val handleY = 0f
                            val handleH = size.height
                            val gripLineWidth = 1.dp.toPx()
                            val gripLineLength = 16.dp.toPx()
                            val gripSpacing = 3.dp.toPx()

                            // Start Handle
                            drawRoundRect(
                                color = themeColors.primary500,
                                topLeft = Offset(startX - handleBarWidth/2, handleY),
                                size = Size(handleBarWidth, handleH),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // Start Handle grip lines
                            val startCenterY = handleH / 2
                            for (i in -1..1) {
                                val ly = startCenterY + i * gripSpacing
                                drawLine(
                                    color = Color.White.copy(alpha = 0.7f),
                                    start = Offset(startX - gripLineLength / 2, ly),
                                    end = Offset(startX + gripLineLength / 2, ly),
                                    strokeWidth = gripLineWidth,
                                    cap = StrokeCap.Round
                                )
                            }

                            // End Handle
                            drawRoundRect(
                                color = Red500,
                                topLeft = Offset(endX - handleBarWidth/2, handleY),
                                size = Size(handleBarWidth, handleH),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // End Handle grip lines
                            for (i in -1..1) {
                                val ly = startCenterY + i * gripSpacing
                                drawLine(
                                    color = Color.White.copy(alpha = 0.7f),
                                    start = Offset(endX - gripLineLength / 2, ly),
                                    end = Offset(endX + gripLineLength / 2, ly),
                                    strokeWidth = gripLineWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                            
                             // Playhead - Premium draggable design
                              val playheadX = if (totalDuration > 0f) {
                                 ((previewPositionMs.toFloat() / totalDuration) * size.width).coerceIn(0f, size.width)
                             } else 0f
                             val isDraggingPlayhead = dragTarget == TrimDragTarget.Playhead
                             val playheadAlpha = if (isDraggingPlayhead) 1f else 0.85f
                             val playheadLineWidth = if (isDraggingPlayhead) 2.5f.dp.toPx() else 2.dp.toPx()

                             // Glow when dragging
                             if (isDraggingPlayhead) {
                                 drawLine(
                                     color = Color.White.copy(alpha = 0.2f),
                                     start = Offset(playheadX, 0f),
                                     end = Offset(playheadX, size.height),
                                     strokeWidth = 8.dp.toPx(),
                                     cap = StrokeCap.Round
                                 )
                             }

                             // Main playhead line
                             drawLine(
                                 color = Color.White.copy(alpha = playheadAlpha),
                                 start = Offset(playheadX, 0f),
                                 end = Offset(playheadX, size.height),
                                 strokeWidth = playheadLineWidth
                             )

                             // Top pill handle
                             val pillW = 10.dp.toPx()
                             val pillH = 16.dp.toPx()
                             val pillR = 3.dp.toPx()
                             drawRoundRect(
                                 color = Color.White.copy(alpha = playheadAlpha),
                                 topLeft = Offset(playheadX - pillW / 2, 0f),
                                 size = Size(pillW, pillH),
                                 cornerRadius = CornerRadius(pillR)
                             )
                             // Bottom pill handle
                             drawRoundRect(
                                 color = Color.White.copy(alpha = playheadAlpha),
                                 topLeft = Offset(playheadX - pillW / 2, size.height - pillH),
                                 size = Size(pillW, pillH),
                                 cornerRadius = CornerRadius(pillR)
                             )
                          }
                        
                         // Touch Logic - unified gesture handler
                         Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    val slop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downX = down.position.x
                                        val downY = down.position.y

                                        // Hit test at INITIAL touch position (before any slop movement)
                                        val distStart = kotlin.math.abs(downX - startX)
                                        val distEnd = kotlin.math.abs(downX - endX)
                                        val playheadPx = if (totalDuration > 0f) {
                                            (previewPositionMs.toFloat() / totalDuration) * widthPx
                                        } else 0f
                                        val distPlayhead = kotlin.math.abs(downX - playheadPx)

                                        // Determine target: playhead pills are at top/bottom 20dp
                                        val pillZone = 20.dp.toPx()
                                        val inPillZone = downY < pillZone || downY > (heightPx - pillZone)

                                        val target = if (inPillZone && distPlayhead < handleHitWidth) {
                                            // Touch is on playhead pill handles - always grab playhead
                                            TrimDragTarget.Playhead
                                        } else {
                                            // Find closest among trim handles only (playhead moves via body drag/tap)
                                            val handleCandidates = mutableListOf<Pair<TrimDragTarget, Float>>()
                                            if (distStart < handleHitWidth) handleCandidates.add(TrimDragTarget.Start to distStart)
                                            if (distEnd < handleHitWidth) handleCandidates.add(TrimDragTarget.End to distEnd)
                                            // Also consider playhead if close
                                            if (distPlayhead < handleHitWidth) handleCandidates.add(TrimDragTarget.Playhead to distPlayhead)
                                            handleCandidates.minByOrNull { it.second }?.first ?: TrimDragTarget.Playhead
                                        }

                                        // Try to detect drag vs tap
                                        var wasDragged = false
                                        var prevX = downX

                                        val dragSuccess = drag(down.id) { change ->
                                            val dx = change.position.x - downX
                                            val dy = change.position.y - downY
                                            if (!wasDragged && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) {
                                                wasDragged = true
                                                dragTarget = target
                                                isDraggingHandle = (target != TrimDragTarget.Playhead)
                                            }

                                            if (wasDragged) {
                                                change.consume()
                                                val dragDelta = change.position.x - prevX
                                                when (target) {
                                                    TrimDragTarget.Start -> {
                                                        startX = (startX + dragDelta).coerceIn(0f, widthPx)
                                                    }
                                                    TrimDragTarget.End -> {
                                                        endX = (endX + dragDelta).coerceIn(0f, widthPx)
                                                    }
                                                    TrimDragTarget.Playhead -> {
                                                        val rawMs = (change.position.x / widthPx) * totalDuration
                                                        val newMs = rawMs.toLong().coerceIn(0L, durationMs)
                                                        previewPositionMs = newMs
                                                        previewPlayer.seekTo(previewPositionMs.toInt())
                                                    }
                                                }
                                            }
                                            prevX = change.position.x
                                        }

                                        // Drag ended or was a tap
                                        dragTarget = null
                                        isDraggingHandle = false

                                        if (!wasDragged) {
                                            // It was a tap - move playhead to tap position
                                            val tapMs = (downX / widthPx) * totalDuration
                                            val newMs = tapMs.toLong().coerceIn(0L, durationMs)
                                            previewPositionMs = newMs
                                            previewPlayer.seekTo(previewPositionMs.toInt())
                                        }
                                    }
                                }
                        ) {}
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

                        // Row 2: START + END (full width, no controls competing)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text("START", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatDuration(range.start.toLong()),
                                    color = themeColors.primary200,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Zinc900.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text("END", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatDuration(range.endInclusive.toLong()),
                                    color = Red200,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1
                                )
                            }
                        }

                        // Row 3: Fade toggles + Play & Reset Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Fade In toggle
                            Box(
                                modifier = Modifier
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
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Fade In",
                                    color = if (fadeInEnabled) Color.White else Zinc500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

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
                                                 if (trimMode == TrimMode.Keep) {
                                                     if (previewPositionMs < start || previewPositionMs >= end) {
                                                          previewPositionMs = start
                                                     }
                                                 } else {
                                                     // Cut Mode
                                                     if (previewPositionMs >= start && previewPositionMs < end) {
                                                          previewPositionMs = end
                                                     } else if (previewPositionMs >= durationMs) {
                                                          previewPositionMs = 0L
                                                     }
                                                 }

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

                            // Fade Out toggle
                            Box(
                                modifier = Modifier
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
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Fade Out",
                                    color = if (fadeOutEnabled) Color.White else Zinc500,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Footer Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Zinc600),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Zinc300)
                        ) {
                            Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Cancel", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                val start = range.start.toLong()
                                val end = range.endInclusive.toLong()
                                val resultDuration = if (trimMode == TrimMode.Keep) end - start else durationMs - (end - start)
                                val fadeDur = (resultDuration / 10).coerceIn(200, 3000)
                                val fadeIn = if (fadeInEnabled) fadeDur else 0L
                                val fadeOut = if (fadeOutEnabled) fadeDur else 0L
                                onConfirm(start, end, false, trimMode == TrimMode.Remove, fadeIn, fadeOut)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc700, contentColor = Color.White),
                            border = BorderStroke(1.dp, Zinc600)
                        ) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Copy", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                val start = range.start.toLong()
                                val end = range.endInclusive.toLong()
                                val resultDuration = if (trimMode == TrimMode.Keep) end - start else durationMs - (end - start)
                                val fadeDur = (resultDuration / 10).coerceIn(200, 3000)
                                val fadeIn = if (fadeInEnabled) fadeDur else 0L
                                val fadeOut = if (fadeOutEnabled) fadeDur else 0L
                                onConfirm(start, end, true, trimMode == TrimMode.Remove, fadeIn, fadeOut)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600, contentColor = Color.White)
                        ) {
                            Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Replace", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            } else if (!playerInitError) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = themeColors.primary500)
                }
            }
        }
    }
}
