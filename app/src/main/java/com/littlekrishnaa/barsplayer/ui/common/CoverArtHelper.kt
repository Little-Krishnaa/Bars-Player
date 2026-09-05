package com.littlekrishnaa.barsplayer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverArtCache {
    private val memoryCache = LruCache<String, Bitmap>(64)

    suspend fun loadCover(context: Context, path: String): Bitmap? = withContext(Dispatchers.IO) {
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) return@withContext cached

        val mmr = MediaMetadataRetriever()
        try {
            if (path.startsWith("content://")) {
                mmr.setDataSource(context, Uri.parse(path))
            } else {
                val file = File(path)
                if (file.exists()) {
                    mmr.setDataSource(path)
                }
            }
            val artBytes = mmr.embeddedPicture
            if (artBytes != null) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
                if (bitmap != null) {
                    memoryCache.put(path, bitmap)
                    return@withContext bitmap
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {}
        }
        null
    }
}

@Composable
fun rememberCoverArt(path: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path) {
        if (!path.isNullOrBlank()) {
            val bmp = CoverArtCache.loadCover(context, path)
            bitmap = bmp?.asImageBitmap()
        } else {
            bitmap = null
        }
    }
    return bitmap
}
