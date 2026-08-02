package com.lumo.app

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.MoreExecutors
import com.lumo.app.service.LumoPlaybackService
import com.lumo.app.ui.components.MiniPlayerBar
import com.lumo.app.ui.navigation.LumoNavGraph
import com.lumo.app.ui.navigation.Screen
import com.lumo.app.ui.theme.*
import com.lumo.app.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Rounded.HomeWork, Icons.Rounded.Home),
    BottomNavItem(Screen.Songs, "Library", Icons.Rounded.LibraryMusic),
    BottomNavItem(Screen.Search, "Search", Icons.Rounded.Search),
    BottomNavItem(Screen.Favorites, "Favorites", Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
    BottomNavItem(Screen.Playlists, "Playlists", Icons.Rounded.QueueMusic),
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handled via state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionToken = SessionToken(this, ComponentName(this, LumoPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({}, MoreExecutors.directExecutor())

        requestPermissions()

        setContent {
            LumoTheme {
                LumoApp()
            }
        }
    }

    private fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

@Composable
fun LumoApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route != Screen.NowPlaying.route
    val showMiniPlayer = playerState.currentSong != null &&
            currentDestination?.route != Screen.NowPlaying.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DeepBlack,
        bottomBar = {
            if (showBottomBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepBlack)
                        .navigationBarsPadding(),
                ) {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
                    ) {
                        MiniPlayerBar(
                            state = playerState,
                            onPlayerClick = { navController.navigate(Screen.NowPlaying.route) },
                            onPlayPause = { viewModel.playerController.togglePlayPause() },
                            onSkipNext = { viewModel.playerController.skipNext() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    LumoBottomBar(
                        items = bottomNavItems,
                        currentDestination = currentDestination,
                        onItemClick = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        LumoNavGraph(navController = navController, paddingValues = paddingValues)
    }
}

@Composable
fun LumoBottomBar(
    items: List<BottomNavItem>,
    currentDestination: androidx.navigation.NavDestination?,
    onItemClick: (Screen) -> Unit,
) {
    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = TextSecondary,
        tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp),
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(item.screen) },
                icon = {
                    Box(contentAlignment = Alignment.Center) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonGreenGlow),
                            )
                        }
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) NeonGreen else TextTertiary,
                        )
                    }
                },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) NeonGreen else TextTertiary,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}
