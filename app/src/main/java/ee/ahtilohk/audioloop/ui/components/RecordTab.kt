package ee.ahtilohk.audioloop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.AudioLoopUiState
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RecordTab(
    uiState: AudioLoopUiState,
    isWide: Boolean = false,
    onStartRecord: (String, Boolean) -> Unit,
    onStopRecord: () -> Unit
) {
    var mode by remember { mutableStateOf("speech") }
    val themeColors = uiState.currentTheme.palette
    val haptic = LocalHapticFeedback.current
    val speechLabel = stringResource(R.string.mode_speech)
    val streamLabel = stringResource(R.string.mode_stream)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Mode Selector (Top)
        Row(
            modifier = Modifier
                .width(280.dp)
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("speech" to (speechLabel to AppIcons.Mic), "stream" to (streamLabel to AppIcons.Stream)).forEach { (key, labelAndIcon) ->
                val (label, icon) = labelAndIcon
                val active = mode == key
                Surface(
                    onClick = { 
                        mode = key
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(22.dp),
                    color = if (active) themeColors.primary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Large Premium Record Button
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(
                    elevation = if (uiState.isRecording) 30.dp else 12.dp,
                    shape = CircleShape,
                    spotColor = if (uiState.isRecording) Red500 else themeColors.primary,
                    ambientColor = if (uiState.isRecording) Red900 else themeColors.primary900
                )
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (uiState.isRecording) listOf(Red600, Red900) else listOf(themeColors.primary, themeColors.primary900)
                    )
                )
                .clickable(
                    onClickLabel = if (uiState.isRecording) stringResource(R.string.a11y_stop_recording) else stringResource(R.string.a11y_start_recording)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (uiState.isRecording) {
                        onStopRecord()
                    } else {
                        val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.getDefault())
                        val dateStr = dateFormat.format(java.util.Date())
                        val prefix = if (mode == "speech") speechLabel else streamLabel
                        val name = "${prefix}_$dateStr"
                        onStartRecord(name, mode == "stream")
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Animated pulse/rings when recording
            if (uiState.isRecording) {
                RecordingRings()
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (uiState.isRecording) AppIcons.Stop else (if (mode == "speech") AppIcons.Mic else AppIcons.Stream),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (uiState.isRecording) {
                    RecordingTimer()
                } else {
                    Text(
                        text = stringResource(R.string.record_start).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Context info (Bottom)
        Text(
            text = if (uiState.isRecording) stringResource(R.string.record_recording) else stringResource(R.string.record_mode_format, if (mode == "speech") speechLabel else streamLabel),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = if (uiState.isRecording) Red400 else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun RecordingRings() {
    val infiniteTransition = rememberInfiniteTransition(label = "rings")
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "ring1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "ring1_alpha"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .graphicsLayer {
                scaleX = ring1Scale
                scaleY = ring1Scale
                alpha = ring1Alpha
            }
            .border(4.dp, Red500, CircleShape)
    )
}

@Composable
fun RecordingTimer() {
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (isActive) {
            seconds = (System.currentTimeMillis() - startTime) / 1000
            delay(1000)
        }
    }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    Text(
        text = "%02d:%02d:%02d".format(h, m, s),
        style = MaterialTheme.typography.headlineMedium.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    )

}

