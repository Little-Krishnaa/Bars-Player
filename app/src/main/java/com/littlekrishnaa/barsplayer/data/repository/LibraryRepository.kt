package com.littlekrishnaa.barsplayer.data.repository

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.littlekrishnaa.barsplayer.data.local.dao.TrackDao
import com.littlekrishnaa.barsplayer.data.local.entity.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao
) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()

    suspend fun scanLocalAudioFiles() = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            val mmr = MediaMetadataRetriever()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val path = cursor.getString(dataCol) ?: ""
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown"
                val album = cursor.getString(albumCol) ?: "Unknown"
                val duration = cursor.getLong(durationCol)
                val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                val dateAdded = cursor.getLong(dateCol)

                // Lossless check (FLAC, WAV, APE, ALAC, DSD)
                val ext = File(path).extension.lowercase()
                val isLossless = ext in setOf("flac", "wav", "ape", "alac", "dsf", "dff", "wv") ||
                        mimeType.contains("flac") || mimeType.contains("wav")

                if (isLossless && File(path).exists()) {
                    var sampleRate = 44100
                    var bitDepth = 16

                    try {
                        mmr.setDataSource(path)
                        val srStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        srStr?.toIntOrNull()?.let { sampleRate = it }
                        val bdStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        bdStr?.toIntOrNull()?.let { bitDepth = it }
                    } catch (_: Exception) {}

                    tracks.add(
                        TrackEntity(
                            id = id.toString(),
                            path = path,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            sampleRate = sampleRate,
                            bitDepth = bitDepth,
                            mimeType = mimeType,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
            mmr.release()
        }

        if (tracks.isNotEmpty()) {
            trackDao.insertTracks(tracks)
        }
    }
}
