package com.example.audioloop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.audioloop.*
import com.example.audioloop.ui.components.*
import com.example.audioloop.ui.*
import androidx.navigation.compose.*

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Record : Screen("record")
    object Coach : Screen("coach")
    object Settings : Screen("settings")
    object PlaylistView : Screen("playlist_view/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_view/$playlistId"
    }
}

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    uiState: AudioLoopUiState,
    viewModel: AudioLoopViewModel,
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        composable(Screen.Library.route) {
            // We might need to wrap current tabs in their own composables if they aren't already
            LibraryTab(uiState, viewModel, onImportClick = {})
        }
        composable(Screen.Record.route) {
            RecordTab(uiState, onStartRecord = { name, isStream -> onStartRecord(name, isStream) }, onStopRecord = onStopRecord)
        }
        composable(Screen.Coach.route) {
            CoachTab(uiState, viewModel)
        }
        composable(Screen.Settings.route) {
            SettingsTab(uiState, viewModel)
        }
        composable(Screen.PlaylistView.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            val playlist = uiState.playlists.find { it.id == playlistId }
            if (playlist != null) {
                PlaylistViewScreen(
                    playlist = playlist,
                    playingFileName = uiState.playingFileName,
                    currentIteration = uiState.currentPlaylistIteration,
                    isPaused = uiState.isPaused,
                    allRecordings = viewModel.getAllRecordings(),
                    themeColors = uiState.currentTheme.palette,
                    onBack = { navController.popBackStack() },
                    onPause = { viewModel.pausePlaying() },
                    onResume = { viewModel.resumePlaying() },
                    onStop = { 
                        viewModel.stopPlaying()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
