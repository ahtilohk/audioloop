package com.example.audioloop.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.ui.components.*
import com.example.audioloop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLoopMainScreen(
    context: Context,
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    onBackupSignIn: () -> Unit
) {
    val themeColors = uiState.currentTheme.palette
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri -> viewModel.importFile(uri) }
    }

    // Back handler: return from playlist view to playlist list
    BackHandler(enabled = uiState.showPlaylistView) {
        viewModel.closePlaylistView()
        viewModel.setShowPlaylistSheet(true)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!uiState.isSelectionMode && !uiState.showCategorySheet && !uiState.showPlaylistSheet && !uiState.showBackupSheet && !uiState.showTrimDialog && uiState.editingPlaylist == null && !uiState.showPlaylistView) {
                AppNavigationBar(uiState.currentNavTab, onTabSelected = { viewModel.setNavTab(it) })
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(innerPadding)
        ) {
            // Header
            HomeHeader(
                uiState = uiState,
                onSearchClick = { viewModel.setSearchVisible(true) },
                onPlaylistClick = { viewModel.setShowPlaylistSheet(true) }
            )


            // Category Navigation (Library Tab only)
            if (uiState.currentNavTab == "library") {
                CategoryTabs(
                    categories = uiState.categories,
                    currentCategory = uiState.currentCategory,
                    onCategoryChange = { 
                        viewModel.changeCategory(it)
                        com.example.audioloop.widget.WidgetStateHelper.updateWidget(context, category = it)
                    },
                    onManageCategoriesClick = { viewModel.setShowCategorySheet(true) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))


            // Main Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentNavTab) {
                    "library" -> LibraryTab(uiState, viewModel, onImportClick = { filePickerLauncher.launch("audio/*") })
                    "record" -> RecordTab(uiState, onStartRecord = { name, isStream -> onStartRecord(name, isStream) }, onStopRecord = onStopRecord)
                    "coach" -> CoachTab(uiState, viewModel)
                    "settings" -> SettingsTab(uiState, viewModel)
                }


                // Category Management Overlay
                if (uiState.showCategorySheet) {
                    CategoryManagementSheet(
                        categories = uiState.categories,
                        currentCategory = uiState.currentCategory,
                        onSelect = { 
                            viewModel.changeCategory(it)
                            viewModel.setShowCategorySheet(false)
                        },
                        onAdd = { viewModel.addCategory(it) },
                        onRename = { old, new -> viewModel.renameCategory(old, new) },
                        onDelete = { viewModel.deleteCategory(it) },
                        onReorder = { viewModel.reorderCategories(it) },
                        onClose = { viewModel.setShowCategorySheet(false) },
                        themeColors = themeColors
                    )
                }
            }
        }

        // Overlay Sheets & Dialogs
        MainOverlays(uiState, viewModel, themeColors, onBackupSignIn)
    }
}
