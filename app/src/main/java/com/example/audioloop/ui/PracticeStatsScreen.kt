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
            .background(themeColors.primary900.copy(alpha = 0.4f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = "Tagasi", tint = Color.White)
            }
            Text(
                "Statistika",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Weekly overview card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "NÄDALA ÜLEVAADE",
                    color = themeColors.primary400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(12.dp))

                // Big progress number
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${weeklyMin.toInt()}",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        " / $goal min",
                        color = Zinc500,
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
                    color = if (progress >= 1f) Forest400 else themeColors.primary500,
                    trackColor = Zinc800,
                    strokeCap = StrokeCap.Round
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (progress >= 1f) "Eesmärk saavutatud!" else "${((1f - progress) * goal).toInt()} min jäänud",
                        color = if (progress >= 1f) Forest400 else Zinc500,
                        fontSize = 12.sp
                    )
                    Text(
                        "Muuda eesmärki",
                        color = themeColors.primary400,
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Streak", "${streak}d", if (streak >= 3) Sunset400 else Zinc400, Modifier.weight(1f))
            StatCard("Täna", formatMin(todayMin), themeColors.primary400, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Sessioonid", "$sessions", Zinc400, Modifier.weight(1f))
            StatCard("Muudatused", "$edits", Zinc400, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Kesk. päevas", formatMin(avgDaily), themeColors.primary300, Modifier.weight(1f))
            StatCard("Progress", "${(progress * 100).toInt()}%", if (progress >= 1f) Forest400 else themeColors.primary400, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // ── 7-day bar chart ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "VIIMASED 7 PÄEVA",
                    color = themeColors.primary400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(12.dp))
                WeeklyBarChart(
                    data = dailyHistory,
                    barColor = themeColors.primary500,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "SOOVITUS",
                    color = themeColors.primary400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(recommendation.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(recommendation.subtitle, color = Zinc400, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onStartRecommended(recommendation.suggestedMinutes) },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                    shape = RoundedCornerShape(10.dp),
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
        colors = CardDefaults.cardColors(containerColor = Zinc900),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp)
        ) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(label, color = Zinc500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
    val dayLabels = listOf("E", "T", "K", "N", "R", "L", "P") // Mon-Sun in Estonian

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
                        color = Zinc500,
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
                        .background(if (minutes > 0) barColor else Zinc800)
                )
                Spacer(Modifier.height(4.dp))
                // Day label
                Text(
                    dayLabels.getOrElse(index) { "" },
                    color = Zinc500,
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
        containerColor = Zinc900,
        title = {
            Text("Nädala eesmärk", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Mitu minutit nädalas soovid harjutada?", color = Zinc400, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected == minutes) themeColors.primary800.copy(alpha = 0.6f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected == minutes) themeColors.primary600 else Zinc700,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selected = minutes }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatMin(minutes.toFloat()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "~${(minutes / 7)} min/päev",
                            color = Zinc500,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600)
            ) {
                Text("Salvesta", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tühista", color = Zinc400)
            }
        }
    )
}

private fun formatMin(minutes: Float): String {
    val m = minutes.toInt()
    return if (m < 60) "${m} min" else "${m / 60}h ${m % 60}m"
}
