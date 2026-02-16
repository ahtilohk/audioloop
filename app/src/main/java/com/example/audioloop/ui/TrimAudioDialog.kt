package com.example.audioloop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
    onConfirm: (start: Long, end: Long, replace: Boolean, removeSelection: Boolean) -> Unit,
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
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(24.dp), // More modern rounding
            border = BorderStroke(1.dp, Zinc800),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp), // Higher elevation
            modifier = Modifier.fillMaxWidth()
        ) {
            if (playerReady) {
                Column(modifier = Modifier.padding(24.dp)) {
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
                            border = BorderStroke(1.dp, Zinc800)
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
                            .border(1.dp, Zinc800, RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp)
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

                        LaunchedEffect(isPreviewPlaying, trimMode, selectionStartMs, selectionEndMs) {
                             if (isPreviewPlaying) {
                                  while (isActive && previewPlayer.isPlaying) {
                                      val currentMs = previewPlayer.currentPosition.toLong()
                                      previewPositionMs = currentMs
                                      
                                      if (trimMode == TrimMode.Keep) {
                                          // Keep Mode: Stop if we start exceeding selection
                                          if (currentMs >= selectionEndMs) {
                                                previewPlayer.pause()
                                                previewPlayer.seekTo(selectionStartMs.toInt())
                                                previewPositionMs = selectionStartMs.toLong()
                                                isPreviewPlaying = false
                                          }
                                      } else {
                                          // Remove Mode: Skip the selection STRICTLY
                                          if (currentMs >= selectionStartMs && currentMs < selectionEndMs) {
                                               val seekPos = (selectionEndMs + 20).toInt().coerceAtMost(durationMs.toInt())
                                               previewPlayer.seekTo(seekPos)
                                               previewPositionMs = seekPos.toLong()
                                               // Crucial: wait for seek to take effect so currentMs doesn't trigger again
                                               delay(100) 
                                          }
                                      }
                                      delay(30)
                                  }
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
                            
                            // Start Handle
                            drawRoundRect(
                                color = themeColors.primary500,
                                topLeft = Offset(startX - handleBarWidth/2, handleY),
                                size = Size(handleBarWidth, handleH),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // End Handle
                            drawRoundRect(
                                color = Red500,
                                topLeft = Offset(endX - handleBarWidth/2, handleY),
                                size = Size(handleBarWidth, handleH),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            
                            // Playhead
                             val playheadX = if (totalDuration > 0f) {
                                ((previewPositionMs.toFloat() / totalDuration) * size.width).coerceIn(0f, size.width)
                            } else 0f
                            drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, size.height), strokeWidth = 2.dp.toPx())
                         }
                        
                         // Touch Logic
                         Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { offset ->
                                        val tapMs = (offset.x / widthPx) * totalDuration
                                        var newMs = tapMs.toLong().coerceIn(0L, durationMs)
                                        
                                        // Enforce Cut Mode constraints
                                        if (trimMode == TrimMode.Remove) {
                                            if (newMs >= selectionStartMs && newMs < selectionEndMs) {
                                                newMs = selectionEndMs.toLong()
                                            }
                                        }
                                        
                                        previewPositionMs = newMs
                                        previewPlayer.seekTo(previewPositionMs.toInt())
                                    })
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                             val touchX = offset.x
                                             val distStart = kotlin.math.abs(touchX - startX)
                                             val distEnd = kotlin.math.abs(touchX - endX)
                                             
                                             // Hit test handles with priority
                                             if (distStart < handleHitWidth && distStart <= distEnd) {
                                                 dragTarget = TrimDragTarget.Start
                                             } else if (distEnd < handleHitWidth) {
                                                 dragTarget = TrimDragTarget.End
                                             } else {
                                                 dragTarget = TrimDragTarget.Playhead
                                                 // Snap playhead immediately on drag start if hitting body
                                                 val newMs = (touchX / widthPx) * totalDuration
                                                 previewPositionMs = newMs.toLong().coerceIn(0L, durationMs)
                                                 previewPlayer.seekTo(previewPositionMs.toInt())
                                             }
                                             isDraggingHandle = (dragTarget != TrimDragTarget.Playhead)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val touchX = change.position.x
                                            
                                            when(dragTarget) {
                                                TrimDragTarget.Start -> {
                                                     startX = (startX + dragAmount.x).coerceIn(0f, widthPx)
                                                }
                                                TrimDragTarget.End -> {
                                                     endX = (endX + dragAmount.x).coerceIn(0f, widthPx)
                                                }
                                                TrimDragTarget.Playhead -> {
                                                     val rawMs = (touchX / widthPx) * totalDuration
                                                     var newMs = rawMs.toLong().coerceIn(0L, durationMs)
                                                     
                                                     // Enforce Cut Mode constraints
                                                     if (trimMode == TrimMode.Remove) {
                                                         if (newMs >= selectionStartMs && newMs < selectionEndMs) {
                                                             newMs = selectionEndMs.toLong()
                                                         }
                                                     }
                                                     
                                                     previewPositionMs = newMs
                                                     previewPlayer.seekTo(previewPositionMs.toInt())
                                                }
                                                null -> {}
                                            }
                                        },
                                        onDragEnd = { 
                                            dragTarget = null 
                                            isDraggingHandle = false
                                        }
                                    )
                                }
                        ) {}
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Time Displays - digital & projected
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Position
                        Column {
                            Text("CURRENT", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                formatDuration(previewPositionMs),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        }

                        // Projected Duration
                        Column(horizontalAlignment = Alignment.End) {
                            val start = range.start.toLong()
                            val end = range.endInclusive.toLong()
                            val projectedDuration = if (trimMode == TrimMode.Keep) {
                                (end - start).coerceAtLeast(0L)
                            } else {
                                (durationMs - (end - start)).coerceAtLeast(0L)
                            }
                            
                            Text("NEW LENGTH", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                formatDuration(projectedDuration),
                                color = themeColors.primary200,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selection Range Displays
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("START", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                formatDuration(range.start.toLong()),
                                color = themeColors.primary200,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                        
                        // Center Play Control
                         Box(
                            modifier = Modifier
                                .size(48.dp) // Slightly smaller
                                .shadow(8.dp, CircleShape)
                                .background(if (isPreviewPlaying) themeColors.primary500 else Zinc800, CircleShape)
                                .border(1.dp, Zinc700, CircleShape)
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text("END", color = Zinc500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                formatDuration(range.endInclusive.toLong()),
                                color = Red200,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Zinc800)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Footer Actions
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                         OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Zinc700),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Zinc200)
                        ) {
                            Text("Cancel", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                        
                         Button(
                            onClick = { 
                                val start = range.start.toLong()
                                val end = range.endInclusive.toLong()
                                onConfirm(start, end, false, trimMode == TrimMode.Remove) 
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc700, contentColor = Color.White)
                        ) {
                            Text("Save Copy", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                        
                        Button(
                            onClick = { 
                                val start = range.start.toLong()
                                val end = range.endInclusive.toLong()
                                onConfirm(start, end, true, trimMode == TrimMode.Remove) 
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600, contentColor = Color.White)
                        ) {
                            Text("Replace", maxLines = 1, style = MaterialTheme.typography.labelLarge)
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
