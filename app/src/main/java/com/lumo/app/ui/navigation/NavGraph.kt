package com.lumo.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.lumo.app.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Songs : Screen("songs")
    object Search : Screen("search")
    object Favorites : Screen("favorites")
    object Playlists : Screen("playlists")
    object NowPlaying : Screen("now_playing")
    object Queue : Screen("queue")
    object Equalizer : Screen("equalizer")
    object SleepTimer : Screen("sleep_timer")
    object Settings : Screen("settings")
    object About : Screen("about")
    object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/${artistName}"
    }
    object PlaylistDetail : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
}

@Composable
fun LumoNavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(paddingValues),
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
            fadeIn(tween(300))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) +
            fadeOut(tween(150))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
            fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) +
            fadeOut(tween(150))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Screen.Songs.route) {
            SongListScreen(navController = navController)
        }
        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }
        composable(Screen.Favorites.route) {
            FavoritesScreen(navController = navController)
        }
        composable(Screen.Playlists.route) {
            PlaylistsScreen(navController = navController)
        }
        composable(Screen.NowPlaying.route) {
            NowPlayingScreen(navController = navController)
        }
        composable(Screen.Queue.route) {
            QueueScreen(navController = navController)
        }
        composable(Screen.Equalizer.route) {
            EqualizerScreen(navController = navController)
        }
        composable(Screen.SleepTimer.route) {
            SleepTimerScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.About.route) {
            AboutScreen(navController = navController)
        }
        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStack ->
            AlbumDetailScreen(
                albumId = backStack.arguments?.getLong("albumId") ?: 0L,
                navController = navController
            )
        }
        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStack ->
            ArtistScreen(
                artistName = backStack.arguments?.getString("artistName") ?: "",
                navController = navController
            )
        }
        composable(
            route = Screen.PlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStack ->
            PlaylistDetailScreen(
                playlistId = backStack.arguments?.getLong("playlistId") ?: 0L,
                navController = navController
            )
        }
    }
}
