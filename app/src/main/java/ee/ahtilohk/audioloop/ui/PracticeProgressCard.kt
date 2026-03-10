package ee.ahtilohk.audioloop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.CoachEngine
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.ui.theme.*

@Composable
fun PracticeProgressCard(
    weeklyMinutes: Float,
    weeklyGoal: Int,
    streak: Int,
    todayMinutes: Float,
    weeklySessions: Int,
    weeklyEdits: Int,
    recommendation: CoachEngine.Recommendation,
    goalProgress: Float,
    themeColors: AppColorPalette,
    onStartRecommended: (Int) -> Unit,
    onViewDetails: () -> Unit,
    isExpanded: Boolean = false,
    onToggleExpanded: () -> Unit = {},
    isPlaying: Boolean = false,
    currentSessionElapsedMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    // -- Collapsed bar (always visible, always compact) --
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                1.dp,
                themeColors.primary700.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onToggleExpanded() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.label_smart_coach),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                AppIcons.ChevronDown,
                contentDescription = "Open",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Right side: live timer when playing, or static summary
        if (isPlaying) {
            val totalTodayMs = (todayMinutes * 60_000L).toLong() + currentSessionElapsedMs
            Text(
                formatHhMmSs(totalTodayMs),
                style = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            )
        } else {
            val context = LocalContext.current
            Text(
                "${(goalProgress * 100).toInt()}%  ·  ${formatMinutes(weeklyMinutes, context)} / ${formatMinutes(weeklyGoal.toFloat(), context)}",
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    // -- Expanded content as dialog overlay --
    if (isExpanded) {
        SmartCoachDialog(
            weeklyMinutes = weeklyMinutes,
            weeklyGoal = weeklyGoal,
            streak = streak,
            todayMinutes = todayMinutes,
            weeklySessions = weeklySessions,
            weeklyEdits = weeklyEdits,
            recommendation = recommendation,
            goalProgress = goalProgress,
            themeColors = themeColors,
            onStartRecommended = onStartRecommended,
            onViewDetails = onViewDetails,
            onDismiss = onToggleExpanded,
            isPlaying = isPlaying,
            currentSessionElapsedMs = currentSessionElapsedMs
        )
    }
}

@Composable
private fun SmartCoachDialog(
    weeklyMinutes: Float,
    weeklyGoal: Int,
    streak: Int,
    todayMinutes: Float,
    weeklySessions: Int,
    weeklyEdits: Int,
    recommendation: CoachEngine.Recommendation,
    goalProgress: Float,
    themeColors: AppColorPalette,
    onStartRecommended: (Int) -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    isPlaying: Boolean,
    currentSessionElapsedMs: Long
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, themeColors.primary700.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            // -- Header --
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.label_smart_coach),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                )
                Text(
                    stringResource(R.string.btn_view_details),
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.clickable {
                        onDismiss()
                        onViewDetails()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // -- Progress ring + weekly minutes --
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { goalProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(56.dp),
                        color = if (goalProgress >= 1f) Forest400 else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 5.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        "${(goalProgress * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val context = LocalContext.current
                    Text(
                        "${formatMinutes(weeklyMinutes, context)} / ${formatMinutes(weeklyGoal.toFloat(), context)}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.label_this_week),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                if (isPlaying) {
                    val totalTodayMs = (todayMinutes * 60_000L).toLong() + currentSessionElapsedMs
                    Text(
                        formatHhMmSs(totalTodayMs),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // -- Stats pills --
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill(stringResource(R.string.label_streak), "${streak}d", if (streak >= 3) Sunset400 else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                StatPill(stringResource(R.string.label_today), formatMinutes(todayMinutes, LocalContext.current), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatPill(stringResource(R.string.label_sessions), "$weeklySessions", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
                StatPill(stringResource(R.string.label_edits), "$weeklyEdits", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            // -- Coach recommendation + CTA --
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp)
            ) {
                Text(
                    recommendation.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    recommendation.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onStartRecommended(recommendation.suggestedMinutes)
                        if (!isPlaying) onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (isPlaying) AppIcons.Stop else AppIcons.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPlaying) stringResource(R.string.state_playing) else recommendation.actionLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            value,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Format milliseconds as hh:mm:ss */
private fun formatHhMmSs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatMinutes(minutes: Float, context: android.content.Context): String {
    val m = minutes.toInt()
    return if (m < 60) context.getString(R.string.label_min, m)
    else "${m / 60}${context.getString(R.string.label_hour_short)} ${m % 60}${context.getString(R.string.label_minute_short)}"
}

