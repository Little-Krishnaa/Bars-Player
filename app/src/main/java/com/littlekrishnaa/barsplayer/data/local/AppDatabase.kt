package com.littlekrishnaa.barsplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.littlekrishnaa.barsplayer.data.local.dao.LyricsDao
import com.littlekrishnaa.barsplayer.data.local.dao.PlaylistDao
import com.littlekrishnaa.barsplayer.data.local.dao.TrackDao
import com.littlekrishnaa.barsplayer.data.local.entity.LyricsCacheEntity
import com.littlekrishnaa.barsplayer.data.local.entity.PlaylistEntity
import com.littlekrishnaa.barsplayer.data.local.entity.PlaylistTrackEntity
import com.littlekrishnaa.barsplayer.data.local.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        LyricsCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun playlistDao(): PlaylistDao
}
