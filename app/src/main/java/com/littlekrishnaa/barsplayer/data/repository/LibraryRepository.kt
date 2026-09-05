package com.littlekrishnaa.barsplayer.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
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

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bars_library_folders", Context.MODE_PRIVATE)

    fun getSavedFolderUris(): Set<String> {
        return prefs.getStringSet("folder_uris", emptySet()) ?: emptySet()
    }

    fun saveFolderUri(treeUri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: Exception) {}

        val current = getSavedFolderUris().toMutableSet()
        current.add(treeUri.toString())
        prefs.edit().putStringSet("folder_uris", current).apply()
    }

    fun removeFolderUri(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}

        val current = getSavedFolderUris().toMutableSet()
        current.remove(uriString)
        prefs.edit().putStringSet("folder_uris", current).apply()
    }

    suspend fun scanAll() = withContext(Dispatchers.IO) {
        scanLocalAudioFiles()
        val folderUris = getSavedFolderUris()
        for (uriString in folderUris) {
            try {
                scanDocumentFolder(Uri.parse(uriString))
            } catch (_: Exception) {}
        }
    }

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

        // Filter: durasi minimal 3 detik agar sound effect/notifikasi terfilter,
        // namun tidak membatasi flag IS_MUSIC yang sering luput saat transfer USB
        val selection = "${MediaStore.Audio.Media.DURATION} >= 3000"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                val mmr = MediaMetadataRetriever()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown"
                    val album = cursor.getString(albumCol) ?: "Unknown"
                    val duration = cursor.getLong(durationCol)
                    val mimeType = cursor.getString(mimeCol) ?: "audio/*"
                    val dateAdded = cursor.getLong(dateCol)

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val ext = if (rawPath.isNotEmpty()) File(rawPath).extension.lowercase() else ""
                    val isAudio = ext in setOf("flac", "wav", "ape", "alac", "dsf", "dff", "wv", "aiff", "m4a", "mp3", "ogg") ||
                            mimeType.startsWith("audio/")

                    if (isAudio) {
                        var sampleRate = 44100
                        var bitDepth = 16

                        // Coba extract metadata via contentUri (paling kompatibel dengan Scoped Storage Android 10+)
                        try {
                            mmr.setDataSource(context, contentUri)
                            val srStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                            srStr?.toIntOrNull()?.let { sampleRate = it }
                            val bdStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                            bdStr?.toIntOrNull()?.let { bitDepth = it }
                        } catch (_: Exception) {
                            if (rawPath.isNotEmpty() && File(rawPath).exists()) {
                                try {
                                    mmr.setDataSource(rawPath)
                                    val srStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                                    srStr?.toIntOrNull()?.let { sampleRate = it }
                                    val bdStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                                    bdStr?.toIntOrNull()?.let { bitDepth = it }
                                } catch (_: Exception) {}
                            }
                        }

                        // Jika path file ada dan dapat diakses, pakai path; jika tidak, gunakan contentUri string
                        val finalPlayPath = if (rawPath.isNotEmpty() && File(rawPath).exists()) {
                            rawPath
                        } else {
                            contentUri.toString()
                        }

                        tracks.add(
                            TrackEntity(
                                id = id.toString(),
                                path = finalPlayPath,
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
        } catch (_: Exception) {}

        if (tracks.isNotEmpty()) {
            trackDao.insertTracks(tracks)
        }
    }

    suspend fun scanDocumentFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val mmr = MediaMetadataRetriever()
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDocumentTreeRecursive(treeUri, docId, tracks, mmr)
            if (tracks.isNotEmpty()) {
                trackDao.insertTracks(tracks)
            }
        } catch (_: Exception) {
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }
    }

    private fun scanDocumentTreeRecursive(
        treeUri: Uri,
        parentDocId: String,
        outTracks: MutableList<TrackEntity>,
        mmr: MediaMetadataRetriever
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val displayName = cursor.getString(nameCol) ?: "Unknown"
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val lastMod = if (modCol >= 0) cursor.getLong(modCol) else System.currentTimeMillis()

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        scanDocumentTreeRecursive(treeUri, docId, outTracks, mmr)
                    } else {
                        val ext = displayName.substringAfterLast('.', "").lowercase()
                        val isAudio = ext in setOf("flac", "wav", "ape", "alac", "dsf", "dff", "wv", "aiff", "m4a", "mp3", "ogg") ||
                                mimeType.startsWith("audio/")

                        if (isAudio) {
                            val itemUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            var title = displayName.substringBeforeLast('.')
                            var artist = "Unknown Artist"
                            var album = "Unknown Album"
                            var duration = 0L
                            var sampleRate = 44100
                            var bitDepth = 16

                            try {
                                mmr.setDataSource(context, itemUri)
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                                    if (it.isNotBlank()) title = it
                                }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                                    if (it.isNotBlank()) artist = it
                                }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                                    if (it.isNotBlank()) album = it
                                }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                                    duration = it
                                }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let {
                                    sampleRate = it
                                }
                                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let {
                                    bitDepth = it
                                }
                            } catch (_: Exception) {}

                            outTracks.add(
                                TrackEntity(
                                    id = itemUri.toString().hashCode().toString(),
                                    path = itemUri.toString(),
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    durationMs = duration,
                                    sampleRate = sampleRate,
                                    bitDepth = bitDepth,
                                    mimeType = mimeType.ifEmpty { "audio/*" },
                                    dateAdded = lastMod / 1000
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
