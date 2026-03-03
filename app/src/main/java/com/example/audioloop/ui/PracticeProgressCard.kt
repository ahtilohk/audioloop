package com.example.audioloop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.CoachEngine
import com.example.audioloop.ui.theme.*

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
    isExpanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = goalProgress,
        animationSpec = tween(durationMillis = 800),
        label = "goalProgress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        themeColors.primary900.copy(alpha = 0.8f),
                        themeColors.primary900.copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                1.dp,
                themeColors.primary700.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        // ── Header row: title + chevron + "view details" ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SMART COACH",
                    style = TextStyle(
                        color = themeColors.primary300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                )
                Spacer(Modifier.width(4.dp))
                val chevronRotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 250),
                    label = "chevron"
                )
                Icon(
                    AppIcons.ChevronDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation),
                    tint = themeColors.primary400
                )
            }

            // Collapsed: show compact summary; Expanded: show "View details"
            if (!isExpanded) {
                Text(
                    "${(goalProgress * 100).toInt()}% · ${formatMinutes(weeklyMinutes)} / ${formatMinutes(weeklyGoal.toFloat())}",
                    style = TextStyle(
                        color = Zinc400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            } else {
                Text(
                    "View details",
                    style = TextStyle(
                        color = themeColors.primary400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.clickable { onViewDetails() }
                )
            }
        }

        // ── Expandable content ──
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(animationSpec = tween(200))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                // ── Progress bar + weekly minutes ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.size(56.dp),
                            color = if (goalProgress >= 1f) Forest400 else themeColors.primary400,
                            trackColor = Zinc800,
                            strokeWidth = 5.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            "${(goalProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${formatMinutes(weeklyMinutes)} / ${formatMinutes(weeklyGoal.toFloat())}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "this week",
                            color = Zinc500,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Stats pills row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(
                        label = "Streak",
                        value = "${streak}d",
                        color = if (streak >= 3) Sunset400 else Zinc400,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Today",
                        value = formatMinutes(todayMinutes),
                        color = themeColors.primary400,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Sessions",
                        value = "$weeklySessions",
                        color = Zinc400,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Edits",
                        value = "$weeklyEdits",
                        color = Zinc400,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── Coach recommendation + CTA ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(themeColors.primary800.copy(alpha = 0.6f))
                        .padding(12.dp)
                ) {
                    Text(
                        recommendation.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        recommendation.subtitle,
                        color = Zinc400,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onStartRecommended(recommendation.suggestedMinutes) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            AppIcons.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            recommendation.actionLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
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
            .clip(RoundedCornerShape(10.dp))
            .background(Zinc800.copy(alpha = 0.6f))
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
            color = Zinc500,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatMinutes(minutes: Float): String {
    val m = minutes.toInt()
    return if (m < 60) "${m} min" else "${m / 60}h ${m % 60}m"
}
