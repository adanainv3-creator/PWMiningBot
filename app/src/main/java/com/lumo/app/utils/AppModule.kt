package com.lumo.app.utils

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import com.lumo.app.data.local.LumoDatabase
import com.lumo.app.data.local.PlaylistDao
import com.lumo.app.data.local.FavoriteDao
import com.lumo.app.data.local.RecentlyPlayedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LumoDatabase =
        Room.databaseBuilder(context, LumoDatabase::class.java, "lumo_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePlaylistDao(db: LumoDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideFavoriteDao(db: LumoDatabase): FavoriteDao = db.favoriteDao()
    @Provides fun provideRecentlyPlayedDao(db: LumoDatabase): RecentlyPlayedDao = db.recentlyPlayedDao()

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}
