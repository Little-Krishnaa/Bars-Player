package com.littlekrishnaa.barsplayer.di

import android.content.Context
import androidx.room.Room
import com.littlekrishnaa.barsplayer.data.local.AppDatabase
import com.littlekrishnaa.barsplayer.data.local.dao.LyricsDao
import com.littlekrishnaa.barsplayer.data.local.dao.TrackDao
import com.littlekrishnaa.barsplayer.lyrics.LyricsSettingsManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "bars_player.db"
        ).build()
    }

    @Provides
    fun provideTrackDao(database: AppDatabase): TrackDao = database.trackDao()

    @Provides
    fun provideLyricsDao(database: AppDatabase): LyricsDao = database.lyricsDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsSettingsManager(@ApplicationContext context: Context): LyricsSettingsManager {
        return LyricsSettingsManager(context)
    }
}
