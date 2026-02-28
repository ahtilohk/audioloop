package com.example.audioloop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.ui.theme.*

@Composable
fun PlaybackSettingsCard(
    settingsOpen: Boolean,
    onToggleSettings: () -> Unit,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    selectedLoopCount: Int,
    onLoopCountChange: (Int) -> Unit,
    isShadowing: Boolean,
    onShadowingChange: (Boolean) -> Unit,
    shadowPauseSeconds: Int,
    onShadowPauseChange: (Int) -> Unit,
    selectedSleepMinutes: Int,
    onSleepTimerChange: (Int) -> Unit,
    sleepTimerRemainingMs: Long,
    gapSeconds: Int = 0,
    onGapChange: ((Int) -> Unit)? = null,
    themeColors: AppColorPalette,
    modifier: Modifier = Modifier,
    currentTheme: AppTheme? = null,
    onThemeChange: ((AppTheme) -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Zinc800.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, Zinc600)
    ) {
        Column {
            // Settings Header (Collapsible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSettings() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Settings,
                    contentDescription = null,
                    tint = if (settingsOpen) themeColors.primary else Zinc400,
                    modifier = Modifier.size(20.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playback Settings",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (settingsOpen) themeColors.onPrimaryContainer else Zinc300,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    // Quick preview when collapsed
                    if (!settingsOpen) {
                        val loopText = if (selectedLoopCount == -1) "∞" else "${selectedLoopCount}x"
                        val sleepText = if (sleepTimerRemainingMs > 0L) {
                            val totalSec = (sleepTimerRemainingMs / 1000).toInt()
                            val minutes = totalSec / 60
                            val seconds = totalSec % 60
                            String.format("%02d:%02d", minutes, seconds)
                        } else "Off"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Speed,
                                    contentDescription = "Speed",
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "${String.format("%.2f", selectedSpeed)}x",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            // Loop Count
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Loop,
                                    contentDescription = "Loop Count",
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = loopText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            // Shadowing
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Shadow,
                                    contentDescription = "Shadowing",
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isShadowing) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }

                            // Sleep Timer
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = AppIcons.Sleep,
                                    contentDescription = "Sleep Timer",
                                    tint = themeColors.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = sleepText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = if (settingsOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = if (settingsOpen) themeColors.primary else Zinc500,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = settingsOpen) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(Zinc900.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, Zinc600, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Speed
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Speed:", style = TextStyle(color = Zinc400, fontSize = 13.sp))
                            Text("${String.format("%.2f", selectedSpeed)}x", style = TextStyle(color = themeColors.primary300, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).forEach { s ->
                                val active = selectedSpeed == s
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) themeColors.primary600 else Zinc800)
                                        .clickable { onSpeedChange(s) }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val label = if (s == s.toInt().toFloat()) "${s.toInt()}" else "$s"
                                    Text("${label}x", style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }

                    // Repeats
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeats:", style = TextStyle(color = Zinc400, fontSize = 13.sp))
                            Text(
                                if (selectedLoopCount == -1) "∞" else "${selectedLoopCount}x",
                                style = TextStyle(color = themeColors.primary300, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1, 2, 3, 5, 10, 15, 20).forEach { r ->
                                val active = selectedLoopCount == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (active) themeColors.primary600 else Zinc800)
                                        .clickable { onLoopCountChange(r) }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${r}x", style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                            // ∞ button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedLoopCount == -1) themeColors.primary600 else Zinc800)
                                    .clickable { onLoopCountChange(-1) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("∞", style = TextStyle(color = if (selectedLoopCount == -1) Color.White else Zinc400, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }

                    // Gap support (only shown in Playlist context)
                    if (onGapChange != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Gap between tracks:", style = TextStyle(color = Zinc400, fontSize = 13.sp))
                                Text("${gapSeconds}s", style = TextStyle(color = themeColors.primary300, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0, 1, 2, 3, 5, 10).forEach { gap ->
                                    val active = gapSeconds == gap
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) themeColors.primary600 else Zinc800)
                                            .clickable { onGapChange(gap) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${gap}s", style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }
                    }

                    // Listen & Repeat
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Listen & Repeat", style = TextStyle(color = Zinc300, fontSize = 13.sp))
                            Text("Pause after playback to practice", style = TextStyle(color = Zinc600, fontSize = 10.sp))
                        }
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isShadowing) themeColors.primary600 else Zinc700)
                                .clickable { onShadowingChange(!isShadowing) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .offset(x = if (isShadowing) 22.dp else 2.dp, y = 2.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isShadowing) Icon(AppIcons.Check, contentDescription = null, tint = themeColors.primary600, modifier = Modifier.size(10.dp))
                            }
                        }
                    }

                    if (isShadowing) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Pause duration:", style = TextStyle(color = Zinc400, fontSize = 13.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0 to "Auto", 2 to "2s", 5 to "5s", 10 to "10s").forEach { (secs, label) ->
                                    val active = shadowPauseSeconds == secs
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) themeColors.primary600 else Zinc800)
                                            .clickable { onShadowPauseChange(secs) }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(label, style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }
                    }

                    // Sleep Timer
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sleep Timer:", style = TextStyle(color = Zinc400, fontSize = 13.sp))
                            Text(
                                if (selectedSleepMinutes == 0) "Off" else "${selectedSleepMinutes}m",
                                style = TextStyle(color = themeColors.primary300, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isOffSelected = selectedSleepMinutes == 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isOffSelected) themeColors.primary600 else Zinc800)
                                    .clickable { onSleepTimerChange(0) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Off", style = TextStyle(color = if (isOffSelected) Color.White else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                            }
                            listOf(15, 30, 45, 60).forEach { m ->
                                val isSelected = selectedSleepMinutes == m
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) themeColors.primary600 else Zinc800)
                                        .clickable { onSleepTimerChange(m) }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${m}m", style = TextStyle(color = if (isSelected) Color.White else Zinc400, fontSize = 10.sp, fontWeight = FontWeight.Medium))
                                }
                            }
                        }
                    }

                    if (sleepTimerRemainingMs > 0L) {
                        val totalSec = (sleepTimerRemainingMs / 1000).toInt()
                        val min = totalSec / 60
                        val sec = totalSec % 60
                        val remaining = String.format("%d:%02d", min, sec)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Stops in $remaining",
                                style = TextStyle(color = themeColors.primary300, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                "Cancel",
                                style = TextStyle(color = Zinc500, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                modifier = Modifier.clickable { onSleepTimerChange(0) }
                            )
                        }
                    }

                    // Theme Selector (conditionally show only if callbacks provided)
                    if (onThemeChange != null && currentTheme != null) {
                        Column {
                            HorizontalDivider(color = Zinc700, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                            Text("App Theme:", style = TextStyle(color = Zinc400, fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppTheme.values().forEach { theme ->
                                    val isSelected = currentTheme == theme
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) theme.palette.primary600 else Zinc800)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) theme.palette.primary400 else Zinc600,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onThemeChange(theme) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(theme.palette.primary500)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
