package com.example.audioloop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.ui.PlaybackSettingsCard
import com.example.audioloop.ui.theme.*

@Composable
fun SettingsTab(
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel
) {
    val themeColors = uiState.currentTheme.palette

    PlaybackSettingsCard(
        settingsOpen = uiState.settingsOpen,
        onToggleSettings = { viewModel.setSettingsOpen(!uiState.settingsOpen) },
        selectedSpeed = uiState.playbackSpeed,
        onSpeedChange = { viewModel.setPlaybackSpeed(it) },
        selectedLoopCount = uiState.loopMode,
        onLoopCountChange = { viewModel.setLoopMode(it) },
        isShadowing = uiState.isShadowingMode,
        onShadowingChange = { viewModel.setShadowingMode(it) },
        shadowPauseSeconds = uiState.shadowPauseSeconds,
        onShadowPauseChange = { viewModel.setShadowPauseSeconds(it) },
        selectedSleepMinutes = uiState.selectedSleepMinutes,
        onSleepTimerChange = { viewModel.setSleepTimer(it) },
        sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
        themeColors = themeColors,
        currentTheme = uiState.currentTheme,
        onThemeChange = { viewModel.changeTheme(it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
