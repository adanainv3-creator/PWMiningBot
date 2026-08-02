package com.lumo.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lumo.app.data.model.Album
import com.lumo.app.data.model.Artist
import com.lumo.app.data.model.Song
import com.lumo.app.ui.components.*
import com.lumo.app.ui.navigation.Screen
import com.lumo.app.ui.theme.*
import com.lumo.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.homeState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                color = NeonGreen,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item { HomeHeader(navController = navController) }

                if (state.recentlyPlayed.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recently Played",
                            actionLabel = "See all",
                            onActionClick = { navController.navigate(Screen.Songs.route) }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.recentlyPlayed, key = { it.id }) { song ->
                                SongCard(
                                    song = song,
                                    isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                    onClick = { viewModel.playSong(song, state.recentlyPlayed) }
                                )
                            }
                        }
                    }
                }

                if (state.favorites.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Favorites",
                            actionLabel = "See all",
                            onActionClick = { navController.navigate(Screen.Favorites.route) }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.favorites, key = { it.id }) { song ->
                                SongCard(
                                    song = song,
                                    isPlaying = playerState.currentSong?.id == song.id && playerState.isPlaying,
                                    onClick = { viewModel.playSong(song, state.favorites) }
                                )
                            }
                        }
                    }
                }

                if (state.albums.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Albums",
                            actionLabel = "See all",
                            onActionClick = { navController.navigate(Screen.Songs.route) }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { navController.navigate(Screen.AlbumDetail.createRoute(album.id)) }
                                )
                            }
                        }
                    }
                }

                if (state.artists.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Artists",
                            actionLabel = "See all",
                            onActionClick = { navController.navigate(Screen.Songs.route) }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.artists, key = { it.id }) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    onClick = { navController.navigate(Screen.ArtistDetail.createRoute(artist.name)) }
                                )
                            }
                        }
                    }
                }

                if (state.allSongs.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "No Music Found",
                            subtitle = "Add music files to your device to get started",
                            modifier = Modifier.padding(top = 64.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "LUMO",
                style = MaterialTheme.typography.headlineLarge,
                color = NeonGreen,
            )
            Text(
                text = "Your music, elevated.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                Icon(Icons.Rounded.Search, "Search", tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                Icon(Icons.Rounded.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(130.dp)) {
            AlbumArtwork(
                uri = song.artworkUri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingBarsIndicator()
                }
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = DeepBlack, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPlaying) NeonGreen else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        AlbumArtwork(
            uri = album.artworkUri,
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElevatedBlack, CardBlack)
                    )
                )
                .border(2.dp, DividerGray, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = TextTertiary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${artist.songCount} songs",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
