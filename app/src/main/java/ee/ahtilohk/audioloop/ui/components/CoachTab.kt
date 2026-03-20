package ee.ahtilohk.audioloop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.AudioLoopUiState
import ee.ahtilohk.audioloop.AudioLoopViewModel
import ee.ahtilohk.audioloop.ui.PracticeProgressCard
import ee.ahtilohk.audioloop.ui.theme.*

@Composable
fun CoachTab(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    isWide: Boolean = false
) {
    val themeColors = uiState.currentTheme.palette
    val scrollState = rememberScrollState()
    
    val stats = viewModel.practiceStats
    val coach = viewModel.coachEngine

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
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_coach),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.align(Alignment.Start)
        )

        // 1. Practice Recommendation Card (Primary Action)
        CoachRecommendationCard(recommendation, themeColors) { viewModel.startRecommendedSession(it) }

        // 2. Weekly Overview & Progress
        WeeklyOverviewCard(weeklyMin, goal, progress, themeColors) { showGoalPicker = true }

        // 3. Stats Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.label_streak), "${streak}d", if (streak >= 3) Sunset400 else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            StatCard(stringResource(R.string.label_today), formatMin(todayMin), themeColors.primary, Modifier.weight(1f))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.label_sessions), "$sessions", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            StatCard(stringResource(R.string.label_avg_daily), formatMin(avgDaily), themeColors.primary, Modifier.weight(1f))
        }

        // 4. 7-Day Chart
        WeeklyChartCard(dailyHistory, themeColors.primary)

        if (showGoalPicker) {
            GoalPickerDialog(
                currentGoal = goal,
                themeColors = themeColors,
                onDismiss = { showGoalPicker = false },
                onConfirm = { 
                    viewModel.setPracticeGoal(it)
                    showGoalPicker = false 
                }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CoachRecommendationCard(
    recommendation: ee.ahtilohk.audioloop.CoachRecommendation,
    themeColors: AppColorPalette,
    onStart: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(AppIcons.School, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.label_recommendation).uppercase(),
                    color = themeColors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(recommendation.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(recommendation.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onStart(recommendation.suggestedMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(recommendation.actionLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun WeeklyOverviewCard(
    weeklyMin: Float,
    goal: Int,
    progress: Float,
    themeColors: AppColorPalette,
    onChangeGoal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.label_weekly_overview).uppercase(),
                color = themeColors.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text("${weeklyMin.toInt()}", color = MaterialTheme.colorScheme.onSurface, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text(" / " + stringResource(R.string.label_min, goal), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = if (progress >= 1f) Forest400 else themeColors.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (progress >= 1f) stringResource(R.string.label_goal_reached) else stringResource(R.string.label_min_remaining, ((1f - progress) * goal).toInt()),
                    color = if (progress >= 1f) Forest400 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    stringResource(R.string.label_change_goal),
                    color = themeColors.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onChangeGoal() }
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeeklyChartCard(data: List<Pair<String, Float>>, barColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.label_last_7_days).uppercase(),
                color = barColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(20.dp))
            
            val maxVal = (data.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
            val dayLabels = listOf(
                stringResource(R.string.day_m), stringResource(R.string.day_t), stringResource(R.string.day_w),
                stringResource(R.string.day_th), stringResource(R.string.day_f), stringResource(R.string.day_s),
                stringResource(R.string.day_su)
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                data.forEachIndexed { index, (_, minutes) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        val fraction = if (maxVal > 0) (minutes / maxVal).coerceIn(0f, 1f) else 0f
                        val barHeight = (fraction * 80).coerceAtLeast(if (minutes > 0) 4f else 2f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (minutes > 0) barColor else MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(dayLabels.getOrElse(index) { "" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
        title = { Text(stringResource(R.string.title_weekly_goal), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.msg_goal_question), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                options.forEach { minutes ->
                    Surface(
                        onClick = { selected = minutes },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected == minutes) themeColors.primary.copy(alpha = 0.1f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (selected == minutes) themeColors.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatMin(minutes.toFloat()), fontWeight = FontWeight.Bold, color = if (selected == minutes) themeColors.primary else MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(R.string.label_min_per_day, (minutes / 7)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected) }, colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary)) {
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


