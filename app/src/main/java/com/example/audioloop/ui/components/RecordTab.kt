package com.example.audioloop.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RecordTab(
    uiState: AudioLoopUiState,
    onStartRecord: (String, Boolean) -> Unit,
    onStopRecord: () -> Unit
) {
    var mode by remember { mutableStateOf("Speech") }
    val themeColors = uiState.currentTheme.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Speech" to AppIcons.Mic, "Stream" to AppIcons.Stream).forEach { (m, icon) ->
                val active = mode == m
                Surface(
                    onClick = { mode = m },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) themeColors.primary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = m,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Record Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (uiState.isRecording) {
                        Brush.radialGradient(colors = listOf(Red900, Color.Black), radius = 600f)
                    } else {
                        SolidColor(MaterialTheme.colorScheme.surface)
                    }
                )
                .border(
                    width = 2.dp,
                    color = if (uiState.isRecording) Red500 else Red800,
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable {
                    if (uiState.isRecording) {
                        onStopRecord()
                    } else {
                        val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.getDefault())
                        val dateStr = dateFormat.format(java.util.Date())
                        val prefix = if (mode == "Speech") "Speech" else "Stream"
                        val name = "${prefix}_$dateStr"
                        onStartRecord(name, mode == "Stream")
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Record Indicator
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(
                            elevation = if (uiState.isRecording) 12.dp else 0.dp,
                            spotColor = Red500,
                            shape = CircleShape
                        )
                        .background(
                            color = if (uiState.isRecording) Red600 else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (uiState.isRecording) Red400 else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isRecording) {
                        Box(modifier = Modifier.size(20.dp).background(Color.White, RoundedCornerShape(4.dp)))
                    } else {
                        Icon(
                            imageVector = if (mode == "Speech") AppIcons.Mic else AppIcons.Stream,
                            contentDescription = null,
                            tint = Red500,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Text Content
                Column(verticalArrangement = Arrangement.Center) {
                    var recordingDurationSeconds by remember { mutableLongStateOf(0L) }
                    
                    LaunchedEffect(uiState.isRecording) {
                        if (uiState.isRecording) {
                            val startTime = System.currentTimeMillis()
                            while (isActive) {
                                recordingDurationSeconds = (System.currentTimeMillis() - startTime) / 1000
                                delay(1000)
                            }
                        } else {
                            recordingDurationSeconds = 0L
                        }
                    }

                    if (uiState.isRecording) {
                        val hours = recordingDurationSeconds / 3600
                        val minutes = (recordingDurationSeconds % 3600) / 60
                        val seconds = recordingDurationSeconds % 60
                        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                        
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "Recording...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Red400, fontWeight = FontWeight.Medium)
                        )
                    } else {
                        Text(
                            text = "Start Recording",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "$mode mode",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }
        }
    }
}
