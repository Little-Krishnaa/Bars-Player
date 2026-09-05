package com.littlekrishnaa.barsplayer.lyrics

import com.littlekrishnaa.barsplayer.data.local.dao.LyricsDao
import com.littlekrishnaa.barsplayer.data.local.entity.LyricsCacheEntity
import com.littlekrishnaa.barsplayer.data.model.LyricsTranslationResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsPipelineRepository @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val settingsManager: LyricsSettingsManager
) {
    suspend fun getOrFetchLyrics(
        audioFilePath: String,
        artist: String,
        title: String,
        durationSec: Int
    ): Pair<List<SyncedLine>, LyricsTranslationResponse?> = withContext(Dispatchers.IO) {
        // 1. Check local .lrc next to audio file
        val localLrc = checkLocalLrc(audioFilePath)
        val rawLrc = if (!localLrc.isNullOrBlank()) {
            localLrc
        } else {
            // 2. Fallback to LRCLIB
            fetchLrcLib(artist, title, durationSec)
        }

        if (rawLrc.isNullOrBlank()) {
            return@withContext Pair(emptyList(), null)
        }

        val parsedLines = LrcParser.parse(rawLrc)
        if (parsedLines.isEmpty()) {
            return@withContext Pair(emptyList(), null)
        }

        // 3. Check Cache
        val cacheKey = generateHash("$artist-$title-$rawLrc")
        val cached = lyricsDao.getLyricsCache(cacheKey)
        if (cached != null) {
            try {
                val adapter = moshi.adapter(LyricsTranslationResponse::class.java)
                val translation = adapter.fromJson(cached.jsonTranslation)
                return@withContext Pair(parsedLines, translation)
            } catch (_: Exception) {}
        }

        // 4. Translate via LLM Provider
        val provider = settingsManager.getActiveProvider(okHttpClient)
        val request = TranslationRequest(
            artist = artist,
            title = title,
            lines = parsedLines,
            personaPrompt = settingsManager.personaPrompt
        )

        val translationResult = provider.translate(request)
        var translatedResponse: LyricsTranslationResponse? = null

        translationResult.onSuccess { jsonString ->
            try {
                val adapter = moshi.adapter(LyricsTranslationResponse::class.java)
                translatedResponse = adapter.fromJson(jsonString)
                if (translatedResponse != null) {
                    lyricsDao.insertLyricsCache(
                        LyricsCacheEntity(
                            cacheKey = cacheKey,
                            artist = artist,
                            title = title,
                            rawLyrics = rawLrc,
                            jsonTranslation = jsonString,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Exception) {
                // Graceful fallback: show original lyrics without translation
            }
        }

        Pair(parsedLines, translatedResponse)
    }

    private fun checkLocalLrc(audioFilePath: String): String? {
        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) return null
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        return if (lrcFile.exists() && lrcFile.canRead()) {
            lrcFile.readText()
        } else null
    }

    private fun fetchLrcLib(artist: String, title: String, durationSec: Int): String? {
        try {
            val url = "https://lrclib.net/api/get?artist_name=${java.net.URLEncoder.encode(artist, "UTF-8")}&track_name=${java.net.URLEncoder.encode(title, "UTF-8")}&duration=$durationSec"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val synced = if (json.isNull("syncedLyrics")) null else json.optString("syncedLyrics")
                    val plain = if (json.isNull("plainLyrics")) null else json.optString("plainLyrics")
                    return synced ?: plain
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
