package ee.ahtilohk.audioloop.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import ee.ahtilohk.audioloop.*
import androidx.navigation.NavHostController
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.navigation.compose.*
import ee.ahtilohk.audioloop.ui.navigation.Screen
import ee.ahtilohk.audioloop.ui.components.*
import ee.ahtilohk.audioloop.ui.theme.*

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

    // Handle post-onboarding actions
    LaunchedEffect(uiState.showOnboarding) {
        if (!uiState.showOnboarding && uiState.onboardingPendingAction != null) {
            when (uiState.onboardingPendingAction) {
                "record" -> {
                    navController.navigate(Screen.Record.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                "import" -> {
                    filePickerLauncher.launch("audio/*")
                }
            }
            viewModel.finishOnboarding(null) // Clear the pending action
        }
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
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != tab) {
                        navController.navigate(tab) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = (tab == Screen.Library.route)
                                saveState = (tab != Screen.Library.route)
                            }
                            launchSingleTop = true
                            restoreState = (tab != Screen.Library.route)
                        }
                    }
                })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!isCompact && showNav) {
                    AppNavigationRail(currentTab, onTabSelected = { tab ->
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentRoute != tab) {
                            navController.navigate(tab) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = (tab == Screen.Library.route)
                                    saveState = (tab != Screen.Library.route)
                                }
                                launchSingleTop = true
                                restoreState = (tab != Screen.Library.route)
                            }
                        }
                    })
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
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
                            ee.ahtilohk.audioloop.widget.WidgetStateHelper.updateWidget(context, category = it)
                        },
                        onManageCategoriesClick = { viewModel.setShowCategorySheet(true) }
                    )
                }


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

            // Persistent Now Playing Bar at the bottom (above Nav)
            AnimatedVisibility(
                visible = uiState.playingFileName.isNotEmpty() && !uiState.showOnboarding && !uiState.showPracticeControls && !uiState.showTrimDialog,
                enter = slideInVertically { height -> height } + fadeIn(),
                exit = slideOutVertically { height -> height } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isCompact && showNav) 8.dp else 16.dp) // Avoid overlap with Nav
            ) {
                NowPlayingBar(
                    uiState = uiState,
                    onTogglePlay = { if (uiState.isPaused) viewModel.resumePlaying() else viewModel.pausePlaying() },
                    onOpenPractice = { viewModel.setShowPracticeControls(true) },
                    themeColors = themeColors
                )
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    uiState: AudioLoopUiState,
    onTogglePlay: () -> Unit,
    onOpenPractice: () -> Unit,
    themeColors: AppColorPalette
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(64.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, themeColors.primary.copy(alpha = 0.2f))
    ) {
        Column {
            // Subtle progress line top of bar
            LinearProgressIndicator(
                progress = uiState.currentProgress,
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = themeColors.primary,
                trackColor = Color.Transparent
            )
            
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = uiState.playingFileName.substringBeforeLast(".")
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.currentTimeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(Modifier.width(8.dp))
                
                // Active PRACTICE button
                Button(
                    onClick = onOpenPractice,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary.copy(alpha = 0.1f), contentColor = themeColors.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(AppIcons.Tune, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PRACTICE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp))
                }
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (uiState.isPaused) AppIcons.PlayArrow else AppIcons.Pause, 
                        null, 
                        tint = themeColors.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
