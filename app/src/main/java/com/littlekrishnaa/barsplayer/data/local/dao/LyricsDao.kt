package com.littlekrishnaa.barsplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.littlekrishnaa.barsplayer.data.local.entity.LyricsCacheEntity

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getLyricsCache(cacheKey: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyricsCache(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteLyricsCache(cacheKey: String)
}
