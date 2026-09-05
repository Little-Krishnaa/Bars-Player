package com.littlekrishnaa.barsplayer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String, // URI or content path hash
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val mimeType: String,
    val dateAdded: Long
)
