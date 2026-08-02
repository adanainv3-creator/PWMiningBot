package com.lumo.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.lumo.app.data.local.FavoriteDao
import com.lumo.app.data.local.PlaylistDao
import com.lumo.app.data.local.RecentlyPlayedDao
import com.lumo.app.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val recentlyPlayedDao: RecentlyPlayedDao,
) {

    private val artworkUri = Uri.parse("content://media/external/audio/albumart")

    fun getSongs(): Flow<List<Song>> = combine(
        flow { emit(querySongs()) }.flowOn(Dispatchers.IO),
        favoriteDao.getAllIds()
    ) { songs, favoriteIds ->
        songs.map { it.copy(isFavorite = it.id in favoriteIds) }
    }

    private fun querySongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 30000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        albumId = albumId,
                        duration = cursor.getLong(durationCol),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        artworkUri = ContentUris.withAppendedId(artworkUri, albumId),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        size = cursor.getLong(sizeCol),
                    )
                )
            }
        }
        return songs
    }

    fun getAlbums(): Flow<List<Album>> = flow {
        emit(queryAlbums())
    }.flowOn(Dispatchers.IO)

    private fun queryAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.FIRST_YEAR,
        )
        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection, null, null, "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.FIRST_YEAR)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                albums.add(
                    Album(
                        id = id,
                        name = cursor.getString(nameCol) ?: "Unknown Album",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        songCount = cursor.getInt(songCountCol),
                        artworkUri = ContentUris.withAppendedId(artworkUri, id),
                        year = cursor.getInt(yearCol),
                    )
                )
            }
        }
        return albums
    }

    fun getArtists(): Flow<List<Artist>> = flow {
        emit(queryArtists())
    }.flowOn(Dispatchers.IO)

    private fun queryArtists(): List<Artist> {
        val artists = mutableListOf<Artist>()
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
        )
        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection, null, null, "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val albumCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
            val trackCountCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            while (cursor.moveToNext()) {
                artists.add(
                    Artist(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol) ?: "Unknown Artist",
                        albumCount = cursor.getInt(albumCountCol),
                        songCount = cursor.getInt(trackCountCol),
                    )
                )
            }
        }
        return artists
    }

    fun getSongsForAlbum(albumId: Long): Flow<List<Song>> = flow {
        emit(querySongs().filter { it.albumId == albumId }.sortedBy { it.trackNumber })
    }.flowOn(Dispatchers.IO)

    fun getSongsForArtist(artistName: String): Flow<List<Song>> = flow {
        emit(querySongs().filter { it.artist == artistName })
    }.flowOn(Dispatchers.IO)

    fun getFavoriteSongs(): Flow<List<Song>> = combine(
        favoriteDao.getAll(),
        flow { emit(querySongs()) }.flowOn(Dispatchers.IO)
    ) { favorites, allSongs ->
        val favIds = favorites.map { it.songId }.toSet()
        allSongs.filter { it.id in favIds }.map { it.copy(isFavorite = true) }
    }

    fun getRecentlyPlayed(): Flow<List<Song>> = combine(
        recentlyPlayedDao.getAll(),
        flow { emit(querySongs()) }.flowOn(Dispatchers.IO)
    ) { recent, allSongs ->
        val songMap = allSongs.associateBy { it.id }
        recent.mapNotNull { songMap[it.songId] }
    }

    suspend fun toggleFavorite(songId: Long) = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavorite(songId)
        if (isFav.toString() == "true") {
            favoriteDao.remove(songId)
        } else {
            favoriteDao.add(FavoriteSong(songId))
        }
    }

    suspend fun addToRecentlyPlayed(songId: Long) = withContext(Dispatchers.IO) {
        recentlyPlayedDao.insert(RecentlyPlayed(songId))
        recentlyPlayedDao.trimOldEntries()
    }

    fun getPlaylists(): Flow<List<Playlist>> = playlistDao.getAll()

    suspend fun createPlaylist(name: String): Long = playlistDao.insert(Playlist(name = name))

    suspend fun deletePlaylist(playlist: Playlist) = playlistDao.delete(playlist)

    suspend fun renamePlaylist(id: Long, newName: String) {
        val playlist = playlistDao.getById(id) ?: return
        playlistDao.update(playlist.copy(name = newName, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long, position: Int) {
        playlistDao.addSongToPlaylist(PlaylistSong(playlistId = playlistId, songId = songId, position = position))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSong>> =
        playlistDao.getSongsForPlaylist(playlistId)

    fun searchSongs(query: String): Flow<List<Song>> = flow {
        val q = query.lowercase()
        emit(querySongs().filter { song ->
            song.title.lowercase().contains(q) ||
            song.artist.lowercase().contains(q) ||
            song.album.lowercase().contains(q)
        })
    }.flowOn(Dispatchers.IO)
}
