package com.lumo.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumo.app.data.model.RepeatMode
import com.lumo.app.ui.components.*
import com.lumo.app.ui.navigation.Screen
import com.lumo.app.ui.theme.*
import com.lumo.app.viewmodel.MainViewModel

@Composable
fun NowPlayingScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val song = state.currentSong

    if (song == null) {
        EmptyStateView(
            icon = Icons.Rounded.MusicOff,
            title = "Nothing Playing",
            subtitle = "Pick a song to start listening",
            modifier = Modifier
                .fillMaxSize()
                .background(DeepBlack),
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "artwork_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(song.artworkUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .alpha(0.18f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC080A0E), DeepBlack, DeepBlack),
                        startY = 0f,
                        endY = 600f,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = TextSecondary, modifier = Modifier.size(28.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = TextTertiary, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp))
                }
                IconButton(onClick = { navController.navigate(Screen.Queue.route) }) {
                    Icon(Icons.Rounded.QueueMusic, "Queue", tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .shadow(32.dp, CircleShape, spotColor = NeonGreenGlow),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(CircleShape)
                        .background(CardBlack)
                        .border(2.dp, Brush.sweepGradient(listOf(NeonGreen, NeonGreenGlow, DeepBlack, NeonGreenGlow, NeonGreen)), CircleShape)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(song.artworkUri).crossfade(true).build(),
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .rotate(if (state.isPlaying) rotation else rotation),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DeepBlack)
                        .border(2.dp, DividerGray, CircleShape)
                )
            }

            Spacer(Modifier.height(32.dp))

            WaveformVisualizer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 32.dp),
                isPlaying = state.isPlaying,
                accentColor = NeonGreen,
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleFavorite(song.id) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElevatedBlack),
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) CrimsonRed else TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Slider(
                    value = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f,
                    onValueChange = { viewModel.playerController.seekTo((it * state.duration).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonGreen,
                        activeTrackColor = NeonGreen,
                        inactiveTrackColor = DividerGray,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatDuration(state.position), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(formatDuration(state.duration), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { viewModel.playerController.toggleShuffle() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        "Shuffle",
                        tint = if (state.isShuffleOn) NeonGreen else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                IconButton(
                    onClick = { viewModel.playerController.skipPrevious() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.SkipPrevious, "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(if (state.isPlaying) 16.dp else 0.dp, CircleShape, spotColor = NeonGreenGlow)
                        .clip(CircleShape)
                        .background(
                            if (state.isPlaying)
                                Brush.radialGradient(listOf(NeonGreen, NeonGreenDim))
                            else
                                Brush.radialGradient(listOf(ElevatedBlack, CardBlack))
                        )
                        .clickable { viewModel.playerController.togglePlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            scaleIn(tween(150)) + fadeIn(tween(150)) togetherWith
                            scaleOut(tween(150)) + fadeOut(tween(150))
                        },
                        label = "play_pause"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isPlaying) DeepBlack else TextPrimary,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.playerController.skipNext() },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.SkipNext, "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                val repeatIcon = when (state.repeatMode) {
                    RepeatMode.OFF -> Icons.Rounded.Repeat
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    RepeatMode.ALL -> Icons.Rounded.Repeat
                }
                IconButton(
                    onClick = { viewModel.playerController.cycleRepeatMode() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        repeatIcon,
                        "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) NeonGreen else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomActionButton(Icons.Rounded.Equalizer, "EQ") { navController.navigate(Screen.Equalizer.route) }
                BottomActionButton(Icons.Rounded.Timer, "Sleep") { navController.navigate(Screen.SleepTimer.route) }
                BottomActionButton(Icons.Rounded.Share, "Share") { }
                BottomActionButton(Icons.Rounded.PlaylistAdd, "Add to playlist") { }
            }
        }
    }
}

@Composable
private fun BottomActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ElevatedBlack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}
