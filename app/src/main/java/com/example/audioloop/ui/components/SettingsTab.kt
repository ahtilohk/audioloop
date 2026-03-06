package com.example.audioloop.ui.components

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.R
import com.example.audioloop.ui.PlaybackSettingsCard
import com.example.audioloop.ui.theme.*
import com.example.audioloop.ThemeMode

@Composable
fun SettingsTab(
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
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 0. Pro Status / Promotion
        if (uiState.isProUser) {
            ProStatusCard(themeColors) { viewModel.setUpgradeSheetVisible(true) }
        } else {
            GoProPromotionCard(themeColors) { viewModel.setUpgradeSheetVisible(true) }
        }

        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )

        // 1. Playback Settings Group
        SettingsGroup(title = stringResource(R.string.settings_playback_title), themeColors = themeColors) {
            PlaybackSettingsCard(
                settingsOpen = true, // Always expanded in Settings Tab
                onToggleSettings = { },
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Visual & Language
        SettingsGroup(title = stringResource(R.string.title_general), themeColors = themeColors) {
            // Language Selector
            LanguageSelector(
                currentLanguage = uiState.appLanguage,
                onLanguageChange = { viewModel.changeLanguage(it) },
                themeColors = themeColors
            )

            Spacer(Modifier.height(16.dp))

            // Theme Mode Selector
            ThemeModeSelector(
                currentMode = uiState.themeMode,
                onModeChange = { viewModel.changeThemeMode(it) },
                themeColors = themeColors
            )

            Spacer(Modifier.height(16.dp))

            // Theme Palette Selector
            ColorThemeSelector(
                currentTheme = uiState.currentTheme,
                onThemeChange = { viewModel.changeTheme(it) },
                themeColors = themeColors
            )

            Spacer(Modifier.height(16.dp))

            // Smart Coach Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.School, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.nav_coach), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Switch(
                    checked = uiState.isSmartCoachEnabled,
                    onCheckedChange = { viewModel.toggleSmartCoachEnabled() },
                    colors = SwitchDefaults.colors(checkedThumbColor = themeColors.primary)
                )
            }
        }

        // 3. Storage & Backup
        SettingsGroup(title = stringResource(R.string.backup_restore), themeColors = themeColors) {
            StorageSettings(
                usePublicStorage = viewModel.getPublicStoragePref(),
                onTogglePublicStorage = { viewModel.setPublicStoragePref(it) },
                onManualScan = { viewModel.triggerPublicStorageImport() },
                themeColors = themeColors
            )


            Spacer(Modifier.height(12.dp))

            BackupQuickAction(
                isLoggedIn = uiState.isBackupSignedIn,
                email = uiState.backupEmail,
                onClick = { viewModel.setShowBackupSheet(true) },
                themeColors = themeColors
            )
        }
        
        // 4. Privacy & Data
        var showClearConfirm by remember { mutableStateOf(false) }
        SettingsGroup(title = stringResource(R.string.settings_privacy_title), themeColors = themeColors) {
            // Data Location info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.Info, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.settings_data_location), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_data_location_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Clear Data button
            OutlinedButton(
                onClick = { showClearConfirm = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Red500.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_clear_data), fontWeight = FontWeight.SemiBold)
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.settings_clear_data)) },
                text = { Text(stringResource(R.string.settings_clear_data_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = { 
                            viewModel.clearAllData()
                            showClearConfirm = false 
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Red500)
                    ) {
                        Text(stringResource(R.string.btn_delete).uppercase(), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text(stringResource(R.string.btn_cancel).uppercase())
                    }
                }
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun SettingsGroup(
    title: String,
    themeColors: AppColorPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                color = themeColors.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        content()
    }
}

@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    themeColors: AppColorPalette
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Language, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_language_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(12.dp))

        // Reuse the language list logic but in a compact way or link to a dialog if too many
        // For now, let's keep it embedded as a scrollable row or flow
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val languages = listOf(
                "en" to "English", "et" to "Eesti", "es" to "Español", "fr" to "Français",
                "de" to "Deutsch", "it" to "Italiano", "pt" to "Português", "ru" to "Русский",
                "fi" to "Suomi", "sv" to "Svenska", "no" to "Norsk", "tr" to "Türkçe",
                "uk" to "Українська", "ja" to "日本語", "ko" to "한국어", "ar" to "العربية",
                "bn" to "বাংলা", "da" to "Dansk", "fil" to "Filipino", "hi" to "हिन्दी",
                "id" to "Bahasa Indonesia", "lt" to "Lietuvių", "lv" to "Latviešu", "nl" to "Nederlands",
                "pl" to "Polski", "vi" to "Tiếng Việt", "zh" to "中文"
            )

            languages.forEach { (code, label) ->
                val isSelected = currentLanguage == code
                Surface(
                    onClick = { 
                        onLanguageChange(code)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) themeColors.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSelected) themeColors.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    themeColors: AppColorPalette
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Settings, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_theme_mode_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    onClick = { 
                        onModeChange(mode)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) themeColors.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSelected) themeColors.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when(mode) {
                                ThemeMode.AUTO -> stringResource(R.string.theme_mode_auto)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                                ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColorThemeSelector(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    themeColors: AppColorPalette
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(AppIcons.Palette, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.settings_theme_label), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTheme.values().forEach { theme ->
                val isSelected = currentTheme == theme
                Box(
                    modifier = Modifier
                        .size(56.dp) // Larger touch targets
                        .clip(CircleShape)
                        .background(theme.palette.primary600)
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable(
                            onClickLabel = stringResource(R.string.a11y_select_theme, theme.name)
                        ) { 
                            onThemeChange(theme)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
            // Add spacer at end to prevent the last item from being flush against the edge
            Spacer(modifier = Modifier.width(32.dp))
        }
    }
}

@Composable
fun StorageSettings(
    usePublicStorage: Boolean,
    onTogglePublicStorage: (Boolean) -> Unit,
    onManualScan: () -> Unit,
    themeColors: AppColorPalette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.title_storage),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = stringResource(R.string.desc_public_storage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = usePublicStorage,
                onCheckedChange = onTogglePublicStorage,
                colors = SwitchDefaults.colors(checkedThumbColor = themeColors.primary)
            )
        }
        
        if (usePublicStorage) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onManualScan,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = themeColors.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_scan_now), style = MaterialTheme.typography.labelLarge, color = themeColors.primary)
            }
        }
    }
}


@Composable
fun BackupQuickAction(
    isLoggedIn: Boolean,
    email: String,
    onClick: () -> Unit,
    themeColors: AppColorPalette
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = themeColors.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(AppIcons.Backup, contentDescription = null, tint = themeColors.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.backup_restore),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = themeColors.primary
                )
                Text(
                    text = if (isLoggedIn) email else stringResource(R.string.msg_backup_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ProStatusCard(themeColors: AppColorPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = themeColors.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, themeColors.primary)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(themeColors.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.label_pro), fontWeight = FontWeight.ExtraBold, color = themeColors.primary, fontSize = 20.sp)
                Text(stringResource(R.string.label_subscription_active), color = themeColors.primary.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = themeColors.primary)
        }
    }
}

@Composable
fun GoProPromotionCard(themeColors: AppColorPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Zinc900
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Zinc900, Zinc800)))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.upgrade_pro_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = stringResource(R.string.label_pro_benefit_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = Zinc400
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.label_upgrade), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

