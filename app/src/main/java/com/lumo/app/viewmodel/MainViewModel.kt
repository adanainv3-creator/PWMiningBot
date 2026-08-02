package com.lumo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumo.app.data.model.*
import com.lumo.app.data.repository.MediaRepository
import com.lumo.app.utils.PlayerController
import com.lumo.app.utils.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentlyPlayed: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MediaRepository,
    val playerController: PlayerController,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.state

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Song>> = _searchQuery
        .debounce(300L)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repository.searchSongs(q) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.getFavoriteSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSongs: StateFlow<List<Song>> = repository.getSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = repository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = repository.getArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            combine(
                repository.getRecentlyPlayed(),
                repository.getFavoriteSongs(),
                repository.getAlbums(),
                repository.getArtists(),
                repository.getSongs(),
                repository.getPlaylists(),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                HomeUiState(
                    recentlyPlayed = (values[0] as List<Song>).take(10),
                    favorites = (values[1] as List<Song>).take(10),
                    albums = (values[2] as List<Album>).take(8),
                    artists = (values[3] as List<Artist>).take(8),
                    allSongs = values[4] as List<Song>,
                    playlists = values[5] as List<Playlist>,
                    isLoading = false,
                )
            }.collect { _homeState.value = it }
        }
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        val playQueue = if (queue.isEmpty()) listOf(song) else queue
        val index = playQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playerController.playQueue(playQueue, index)
        viewModelScope.launch { repository.addToRecentlyPlayed(song.id) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { repository.toggleFavorite(songId) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist) }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch { repository.renamePlaylist(id, newName) }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.addSongToPlaylist(playlistId, songId, 0) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { repository.removeSongFromPlaylist(playlistId, songId) }
    }

    fun getSongsForAlbum(albumId: Long): Flow<List<Song>> = repository.getSongsForAlbum(albumId)
    fun getSongsForArtist(name: String): Flow<List<Song>> = repository.getSongsForArtist(name)
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSong>> = repository.getPlaylistSongs(playlistId)
}
