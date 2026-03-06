package com.example.audioloop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audioloop.AppIcons
import com.example.audioloop.R
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.ui.PracticeProgressCard

@Composable
fun CoachTab(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    isWide: Boolean = false
) {
    val themeColors = uiState.currentTheme.palette
    val scrollState = rememberScrollState()

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

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CoachContent(uiState, viewModel, themeColors)
                }
                Box(modifier = Modifier.weight(1f)) {
                    CoachAboutSection(themeColors, viewModel)
                }
            }
        } else {
            CoachContent(uiState, viewModel, themeColors)
            CoachAboutSection(themeColors, viewModel)
        }
    }
}

@Composable
private fun CoachContent(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette
) {
    if (uiState.practiceRecommendation.title.isNotEmpty()) {
        PracticeProgressCard(
            weeklyMinutes = uiState.practiceWeeklyMinutes,
            weeklyGoal = uiState.practiceWeeklyGoal,
            streak = uiState.practiceStreak,
            todayMinutes = uiState.practiceTodayMinutes,
            weeklySessions = uiState.practiceWeeklySessions,
            weeklyEdits = uiState.practiceWeeklyEdits,
            recommendation = uiState.practiceRecommendation,
            goalProgress = uiState.practiceGoalProgress,
            themeColors = themeColors,
            onStartRecommended = { viewModel.startRecommendedSession(it) },
            onViewDetails = { viewModel.setShowPracticeStats(true) },
            isExpanded = true,
            onToggleExpanded = { viewModel.toggleSmartCoach() },
            isPlaying = uiState.playingFileName.isNotEmpty(),
            currentSessionElapsedMs = uiState.currentSessionElapsedMs,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoachAboutSection(
    themeColors: com.example.audioloop.ui.theme.AppColorPalette,
    viewModel: AudioLoopViewModel
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = AppIcons.School,
                contentDescription = null,
                tint = themeColors.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.coach_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.coach_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = { viewModel.setShowPracticeStats(true) },
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.coach_open_stats), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

