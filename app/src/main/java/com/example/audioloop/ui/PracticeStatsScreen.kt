package com.example.audioloop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.PracticeStatsManager
import com.example.audioloop.CoachEngine
import com.example.audioloop.ui.theme.*
import com.example.audioloop.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeStatsScreen(
    stats: PracticeStatsManager,
    coach: CoachEngine,
    themeColors: AppColorPalette,
    onBack: () -> Unit,
    onStartRecommended: (Int) -> Unit,
    onGoalChange: (Int) -> Unit
) {
    val weeklyMin = stats.weeklyMinutes()
    val goal = stats.weeklyGoalMinutes()
    val streak = stats.streak()
    val todayMin = stats.todayMinutes()
    val sessions = stats.weeklySessions()
    val edits = stats.weeklyEdits()
    val progress = stats.goalProgress()
    val avgDaily = stats.averageDailyMinutes()
    val dailyHistory = stats.dailyMinutesHistory(7)
    val recommendation = coach.recommend()

    var showGoalPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.btn_go_back), tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                stringResource(R.string.label_statistics),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Weekly overview card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.label_weekly_overview),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(12.dp))

                // Big progress number
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${weeklyMin.toInt()}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " / " + stringResource(R.string.label_min, goal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (progress >= 1f) Forest400 else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (progress >= 1f) stringResource(R.string.label_goal_reached) else stringResource(R.string.label_min_remaining, ((1f - progress) * goal).toInt()),
                        color = if (progress >= 1f) Forest400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        stringResource(R.string.label_change_goal),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showGoalPicker = true }
                    )
                }
            }
        }

        // ── Stats grid ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(stringResource(R.string.label_streak), "${streak}d", if (streak >= 3) Sunset400 else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            StatCard(stringResource(R.string.label_today), formatMin(todayMin), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(stringResource(R.string.label_sessions), "$sessions", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            StatCard(stringResource(R.string.label_edits), "$edits", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(stringResource(R.string.label_avg_daily), formatMin(avgDaily), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatCard(stringResource(R.string.settings_theme_label).replace(":", ""), "${(progress * 100).toInt()}%", if (progress >= 1f) Forest400 else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // ── 7-day bar chart ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.label_last_7_days),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(
                    data = dailyHistory,
                    barColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }

        // ── Coach recommendation ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.label_recommendation),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(recommendation.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(recommendation.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onStartRecommended(recommendation.suggestedMinutes) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(recommendation.actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Goal picker dialog ──
    if (showGoalPicker) {
        GoalPickerDialog(
            currentGoal = goal,
            themeColors = themeColors,
            onDismiss = { showGoalPicker = false },
            onConfirm = { newGoal ->
                onGoalChange(newGoal)
                showGoalPicker = false
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp)
        ) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WeeklyBarChart(
    data: List<Pair<String, Float>>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = (data.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, (_, minutes) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Minutes label on top
                if (minutes > 0) {
                    Text(
                        "${minutes.toInt()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(2.dp))
                // Bar
                val fraction = if (maxVal > 0) (minutes / maxVal).coerceIn(0f, 1f) else 0f
                val barHeight = (fraction * 80).coerceAtLeast(if (minutes > 0) 4f else 2f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (minutes > 0) barColor else MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(4.dp))
                // Day label
                Text(
                    dayLabels.getOrElse(index) { "" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun GoalPickerDialog(
    currentGoal: Int,
    themeColors: AppColorPalette,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val options = listOf(30, 60, 90, 120, 150, 180, 240)
    var selected by remember { mutableIntStateOf(currentGoal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.title_weekly_goal), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.msg_goal_question), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected == minutes) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selected = minutes }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMin(minutes.toFloat()), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.label_min_per_day, (minutes / 7)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.btn_save), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun formatMin(minutes: Float): String {
    val m = minutes.toInt()
    return if (m < 60) stringResource(R.string.label_min, m) else stringResource(R.string.label_duration_hour_min, (m / 60), (m % 60))
}

