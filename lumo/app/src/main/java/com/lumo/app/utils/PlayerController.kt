package com.lumo.app.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lumo.app.data.model.RepeatMode
import com.lumo.app.data.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val duration: Long = 0L,
    val position: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val sleepTimerRemaining: Long = 0L,
    val isSleepTimerActive: Boolean = false,
)

@Singleton
class PlayerController @Inject constructor(
    private val player: ExoPlayer,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var positionJob: Job? = null

    private val songQueue = mutableListOf<Song>()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in songQueue.indices) {
                    _state.update { it.copy(currentSong = songQueue[index], currentIndex = index, position = 0L, duration = player.duration.coerceAtLeast(0L)) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(duration = player.duration.coerceAtLeast(0L)) }
                }
            }
        })
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        songQueue.clear()
        songQueue.addAll(songs)
        val mediaItems = songs.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        _state.update { it.copy(queue = songs.toList(), currentIndex = startIndex, currentSong = songs.getOrNull(startIndex)) }
    }

    fun playSong(song: Song) {
        if (_state.value.queue.isEmpty()) {
            playQueue(listOf(song), 0)
        } else {
            val index = songQueue.indexOfFirst { it.id == song.id }
            if (index >= 0) {
                player.seekToDefaultPosition(index)
                player.play()
                _state.update { it.copy(currentIndex = index, currentSong = song) }
            } else {
                playQueue(listOf(song), 0)
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
    }

    fun skipPrevious() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _state.update { it.copy(position = position) }
    }

    fun toggleShuffle() {
        val newShuffle = !player.shuffleModeEnabled
        player.shuffleModeEnabled = newShuffle
        _state.update { it.copy(isShuffleOn = newShuffle) }
    }

    fun cycleRepeatMode() {
        val current = _state.value.repeatMode
        val next = when (current) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player.repeatMode = when (next) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        _state.update { it.copy(repeatMode = next) }
    }

    fun addToQueue(song: Song) {
        songQueue.add(song)
        player.addMediaItem(MediaItem.fromUri(song.uri))
        _state.update { it.copy(queue = songQueue.toList()) }
    }

    fun removeFromQueue(index: Int) {
        if (index in songQueue.indices) {
            songQueue.removeAt(index)
            player.removeMediaItem(index)
            _state.update { it.copy(queue = songQueue.toList()) }
        }
    }

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _state.update { it.copy(isSleepTimerActive = true, sleepTimerRemaining = durationMs) }
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _state.update { it.copy(sleepTimerRemaining = remaining.coerceAtLeast(0L)) }
            }
            player.pause()
            _state.update { it.copy(isSleepTimerActive = false, sleepTimerRemaining = 0L) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _state.update { it.copy(isSleepTimerActive = false, sleepTimerRemaining = 0L) }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = scope.launch {
            while (isActive) {
                _state.update { it.copy(position = player.currentPosition.coerceAtLeast(0L)) }
                delay(500L)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }
}
