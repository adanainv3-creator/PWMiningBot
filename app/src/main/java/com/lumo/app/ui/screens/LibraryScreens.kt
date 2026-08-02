package com.lumo.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.lumo.app.data.model.Album
import com.lumo.app.data.model.Playlist
import com.lumo.app.ui.components.*
import com.lumo.app.ui.navigation.Screen
import com.lumo.app.ui.theme.*
import com.lumo.app.viewmodel.MainViewModel

@Composable
fun SongListScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        LumoTopBar(
            title = "All Songs",
            onBackClick = { navController.popBackStack() },
            actions = {
                Text("${songs.size}", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                Spacer(Modifier.width(16.dp))
            }
        )
        if (songs.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.MusicNote,
                title = "No Songs",
                subtitle = "No music files found on your device.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(songs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id,
                        onSongClick = { viewModel.playSong(song, songs) },
                        onMoreClick = {},
                    )
                    HorizontalDivider(
                        color = DividerGray.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 80.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        Spacer(Modifier.statusBarsPadding())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search songs, artists, albums…", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                trailingIcon = {
                    AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Rounded.Clear, null, tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ElevatedBlack,
                    unfocusedContainerColor = CardBlack,
                    focusedIndicatorColor = NeonGreen,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeonGreen,
                ),
                shape = RoundedCornerShape(16.dp),
            )
        }

        if (query.isBlank()) {
            EmptyStateView(
                icon = Icons.Rounded.Search,
                title = "Search Your Library",
                subtitle = "Find songs, artists, and albums",
                modifier = Modifier.fillMaxSize(),
            )
        } else if (results.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.SearchOff,
                title = "No Results",
                subtitle = "Try a different search term",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Text(
                        "${results.size} results for \"$query\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                items(results, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id,
                        onSongClick = { viewModel.playSong(song, results) },
                        onMoreClick = {},
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        LumoTopBar(title = "Favorites", onBackClick = { navController.popBackStack() })

        if (favorites.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.FavoriteBorder,
                title = "No Favorites Yet",
                subtitle = "Tap the heart on any song to save it here",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(CrimsonRed, CrimsonRedDim))),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Liked Songs", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${favorites.size} songs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            IconButton(onClick = { viewModel.playSong(favorites.first(), favorites) }) {
                                Icon(Icons.Rounded.PlayCircleFilled, "Play all", tint = NeonGreen, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                }
                items(favorites, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id,
                        onSongClick = { viewModel.playSong(song, favorites) },
                        onMoreClick = {},
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Playlists",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            )
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Rounded.Add, "New Playlist", tint = NeonGreen)
            }
        }

        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.QueueMusic,
                title = "No Playlists",
                subtitle = "Create your first playlist to organize your music",
                modifier = Modifier.fillMaxSize(),
                action = {
                    NeonGlowButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create Playlist", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        onClick = { navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) },
                        onDelete = { viewModel.deletePlaylist(playlist) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
            title = { Text("New Playlist", color = TextPrimary) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Playlist name", color = TextTertiary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ElevatedBlack,
                        unfocusedContainerColor = CardBlack,
                        focusedIndicatorColor = NeonGreen,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonGreen,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.createPlaylist(newPlaylistName.trim())
                        showCreateDialog = false
                        newPlaylistName = ""
                    }
                }) { Text("Create", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardBlack,
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ElevatedBlack, CardBlack)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.QueueMusic, null, tint = TextTertiary, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Playlist", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, null, tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = ElevatedBlack,
            ) {
                DropdownMenuItem(
                    text = { Text("Delete", color = CrimsonRed) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = CrimsonRed) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlist = playlists.find { it.id == playlistId }
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val playlistSongRefs by viewModel.getPlaylistSongs(playlistId).collectAsStateWithLifecycle(emptyList())
    val playlistSongs = playlistSongRefs.mapNotNull { ref -> allSongs.find { it.id == ref.songId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        LumoTopBar(title = playlist?.name ?: "Playlist", onBackClick = { navController.popBackStack() })
        if (playlistSongs.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.QueueMusic,
                title = "Empty Playlist",
                subtitle = "Add songs from your library",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(playlistSongs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        isPlaying = playerState.currentSong?.id == song.id,
                        onSongClick = { viewModel.playSong(song, playlistSongs) },
                        onMoreClick = {},
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val songs by viewModel.getSongsForAlbum(albumId).collectAsStateWithLifecycle(emptyList())
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val album = albums.find { it.id == albumId }
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AlbumArtwork(
                    uri = album?.artworkUri,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x66080A0E), DeepBlack),
                            )
                        )
                )
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
                        .align(Alignment.TopStart),
                ) {
                    Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = TextPrimary)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp),
                ) {
                    Text(album?.name ?: "Album", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(album?.artist ?: "", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                    Text("${songs.size} songs${if ((album?.year ?: 0) > 0) " • ${album?.year}" else ""}", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeonGlowButton(
                    onClick = { if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play All")
                }
                OutlinedButton(
                    onClick = { viewModel.playerController.toggleShuffle(); if (songs.isNotEmpty()) viewModel.playSong(songs.random(), songs) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, DividerGray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
        }

        items(songs, key = { it.id }) { song ->
            SongListItem(
                song = song,
                isPlaying = playerState.currentSong?.id == song.id,
                onSongClick = { viewModel.playSong(song, songs) },
                onMoreClick = {},
            )
        }
    }
}

@Composable
fun ArtistScreen(
    artistName: String,
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val songs by viewModel.getSongsForArtist(artistName).collectAsStateWithLifecycle(emptyList())
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(colors = listOf(ElevatedBlack, DeepBlack))
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CardBlack)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Person, null, tint = TextTertiary, modifier = Modifier.size(40.dp))
            }
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopStart),
            ) {
                Icon(Icons.Rounded.ArrowBackIosNew, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Text(artistName, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${songs.size} songs", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }

        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(songs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isPlaying = playerState.currentSong?.id == song.id,
                    onSongClick = { viewModel.playSong(song, songs) },
                    onMoreClick = {},
                )
            }
        }
    }
}
