package com.example.audioloop.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.audioloop.*
import androidx.navigation.NavHostController
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.navigation.compose.*
import com.example.audioloop.ui.navigation.Screen
import com.example.audioloop.ui.components.*
import com.example.audioloop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLoopMainScreen(
    context: Context,
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    windowSizeClass: WindowSizeClass,
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    onBackupSignIn: () -> Unit
) {
    val navController = rememberNavController()
    val themeColors = uiState.currentTheme.palette
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri -> viewModel.importFile(uri) }
    }

    // Back handler: return to playlist view to playlist list if we are using the flag (while transitioning)
    // Actually, popBackStack should handle most of this now.
    
    // Dynamic tab based on current route
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: Screen.Library.route
    val currentTab = when {
        currentRoute == Screen.Library.route -> "library"
        currentRoute == Screen.Record.route -> "record"
        currentRoute == Screen.Coach.route -> "coach"
        currentRoute == Screen.Settings.route -> "settings"
        else -> "library"
    }

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val showNav = !uiState.isSelectionMode && !uiState.showCategorySheet && !uiState.showPlaylistSheet && !uiState.showBackupSheet && !uiState.showTrimDialog && uiState.editingPlaylist == null && !uiState.showPlaylistView

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isCompact && showNav) {
                AppNavigationBar(currentTab, onTabSelected = { tab ->
                    navController.navigate(tab) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isCompact && showNav) {
                AppNavigationRail(currentTab, onTabSelected = { tab ->
                    navController.navigate(tab) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
            // Header
            HomeHeader(
                uiState = uiState,
                onSearchClick = { viewModel.setSearchVisible(true) },
                onPlaylistClick = { viewModel.setShowPlaylistSheet(true) }
            )


            // Category Navigation (Library Tab only)
            if (currentTab == "library") {
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)


            // Main Content Area (NavHost)
            Box(modifier = Modifier.weight(1f)) {
                NavHost(navController = navController, startDestination = Screen.Library.route) {
                    composable(Screen.Library.route) {
                        LibraryTab(uiState, viewModel, isWide = !isCompact, onImportClick = { filePickerLauncher.launch("audio/*") }, onNavigate = { navController.navigate(it) })
                    }
                    composable(Screen.Record.route) {
                        RecordTab(uiState, isWide = !isCompact, onStartRecord = { name, isStream -> onStartRecord(name, isStream) }, onStopRecord = onStopRecord)
                    }
                    composable(Screen.Coach.route) {
                        CoachTab(uiState, viewModel, isWide = !isCompact)
                    }
                    composable(Screen.Settings.route) {
                        SettingsTab(uiState, viewModel, isWide = !isCompact)
                    }
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
    }

        // Overlay Sheets & Dialogs
        MainOverlays(uiState, viewModel, themeColors, onBackupSignIn)
    }
}
