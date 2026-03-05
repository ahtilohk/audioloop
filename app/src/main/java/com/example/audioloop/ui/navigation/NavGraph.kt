package com.example.audioloop.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.AudioLoopUiState
import com.example.audioloop.ui.screens.*

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
            LibraryScreen(uiState, viewModel)
        }
        composable(Screen.Record.route) {
            RecordScreen(uiState, onStartRecord, onStopRecord)
        }
        composable(Screen.Coach.route) {
            CoachScreen(uiState, viewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(uiState, viewModel)
        }
        composable(Screen.PlaylistView.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            PlaylistViewScreen(playlistId, uiState, viewModel, onClose = { navController.popBackStack() })
        }
    }
}
