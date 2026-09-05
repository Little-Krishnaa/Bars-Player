package com.littlekrishnaa.barsplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey
    val cacheKey: String, // hash(artist + title + raw_lyrics)
    val artist: String,
    val title: String,
    val rawLyrics: String,
    val jsonTranslation: String,
    val timestamp: Long
)
