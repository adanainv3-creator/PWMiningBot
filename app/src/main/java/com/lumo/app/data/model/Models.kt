package com.lumo.app.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val uri: Uri,
    val artworkUri: Uri?,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val isFavorite: Boolean = false,
    val dateAdded: Long = 0L,
    val size: Long = 0L,
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: Uri?,
    val year: Int = 0,
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val artworkUri: Uri? = null,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val coverArtUri: String? = null,
)

@Entity(tableName = "playlist_songs")
data class PlaylistSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class FavoriteSong(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recently_played")
data class RecentlyPlayed(
    @PrimaryKey val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
)

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>,
)

enum class RepeatMode { OFF, ONE, ALL }
enum class SortOrder { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }
enum class SortDirection { ASC, DESC }
