package com.littlekrishnaa.barsplayer.lyrics

import com.littlekrishnaa.barsplayer.data.local.dao.LyricsDao
import com.littlekrishnaa.barsplayer.data.local.entity.LyricsCacheEntity
import com.littlekrishnaa.barsplayer.data.model.LyricsTranslationResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
            // 2. Fallback to LRCLIB (multi-step fallback)
            fetchLrcLib(artist, title, durationSec, audioFilePath)
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
                // Fallback to parsed raw lyrics on translation parse error
            }
        }.onFailure {
            // Graceful fallback to original lines without translation
        }

        Pair(parsedLines, translatedResponse)
    }

    suspend fun searchManualLyrics(
        query: String,
        artist: String,
        title: String
    ): Pair<List<SyncedLine>, LyricsTranslationResponse?> = withContext(Dispatchers.IO) {
        val rawLrc = queryLrcLibSearch("q=${urlEncode(query.trim())}")
        if (rawLrc.isNullOrBlank()) return@withContext Pair(emptyList(), null)
        val lines = LrcParser.parse(rawLrc)
        if (lines.isEmpty()) return@withContext Pair(emptyList(), null)

        // Coba terjemahkan juga jika provider aktif
        val provider = settingsManager.getActiveProvider(okHttpClient)
        val request = TranslationRequest(
            artist = artist,
            title = title,
            lines = lines,
            personaPrompt = settingsManager.personaPrompt
        )
        val translationResult = provider.translate(request)
        var translatedResponse: LyricsTranslationResponse? = null
        translationResult.onSuccess { jsonString ->
            try {
                val adapter = moshi.adapter(LyricsTranslationResponse::class.java)
                translatedResponse = adapter.fromJson(jsonString)
            } catch (_: Exception) {}
        }

        Pair(lines, translatedResponse)
    }

    private fun checkLocalLrc(audioFilePath: String): String? {
        try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) return null
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            return if (lrcFile.exists() && lrcFile.canRead()) {
                lrcFile.readText()
            } else null
        } catch (_: Exception) {
            return null
        }
    }

    private fun cleanMetadata(rawArtist: String, rawTitle: String, filePath: String): Pair<String, String> {
        var artist = rawArtist.trim()
        var title = rawTitle.trim()

        if (artist.equals("Unknown", ignoreCase = true) ||
            artist.equals("Unknown Artist", ignoreCase = true) ||
            artist.isBlank()
        ) {
            try {
                val name = File(filePath).nameWithoutExtension
                if (name.contains(" - ")) {
                    val parts = name.split(" - ", limit = 2)
                    artist = parts[0].trim()
                    title = parts[1].trim()
                } else if (title.isBlank() || title.equals("Unknown", ignoreCase = true)) {
                    title = name
                }
            } catch (_: Exception) {}
        }

        title = title.replace(Regex("^[0-9]+[.\\-_\\s]+"), "").trim()
        title = title.replace(Regex("(?i)\\s*\\(feat\\..*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[feat\\..*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[official.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(remaster.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\(audio\\)"), "")
            .trim()

        artist = artist.replace(Regex("(?i)\\s*feat\\..*"), "").trim()

        return Pair(artist, title)
    }

    private fun fetchLrcLib(rawArtist: String, rawTitle: String, durationSec: Int, filePath: String): String? {
        val (artist, title) = cleanMetadata(rawArtist, rawTitle, filePath)
        if (title.isBlank()) return null

        // 1. Coba exact match /api/get
        if (artist.isNotBlank() && !artist.equals("Unknown", true) && durationSec > 0) {
            val result = queryLrcLibGet(artist, title, durationSec)
            if (!result.isNullOrBlank()) return result
        }

        // 2. Coba search terstruktur /api/search?track_name=...&artist_name=...
        if (artist.isNotBlank() && !artist.equals("Unknown", true)) {
            val result = queryLrcLibSearch("track_name=${urlEncode(title)}&artist_name=${urlEncode(artist)}")
            if (!result.isNullOrBlank()) return result
        }

        // 3. Coba search query bebas /api/search?q=...
        val query = if (artist.isNotBlank() && !artist.equals("Unknown", true)) "$artist $title" else title
        val freeSearchResult = queryLrcLibSearch("q=${urlEncode(query)}")
        if (!freeSearchResult.isNullOrBlank()) return freeSearchResult

        // 4. Jika title ada tapi artist bermasalah, cari berdasarkan title saja
        if (artist.isNotBlank() && artist.equals("Unknown", true)) {
            return queryLrcLibSearch("q=${urlEncode(title)}")
        }

        return null
    }

    private fun queryLrcLibGet(artist: String, title: String, durationSec: Int): String? {
        try {
            val url = "https://lrclib.net/api/get?artist_name=${urlEncode(artist)}&track_name=${urlEncode(title)}&duration=$durationSec"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BarsPlayer/1.0.0 (Android; https://github.com/Little-Krishnaa/Bars-Player)")
                .build()
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

    private fun queryLrcLibSearch(params: String): String? {
        try {
            val url = "https://lrclib.net/api/search?$params"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BarsPlayer/1.0.0 (Android; https://github.com/Little-Krishnaa/Bars-Player)")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    var fallbackPlain: String? = null
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val synced = if (item.isNull("syncedLyrics")) null else item.optString("syncedLyrics")
                        if (!synced.isNullOrBlank()) return synced
                        if (fallbackPlain == null && !item.isNull("plainLyrics")) {
                            fallbackPlain = item.optString("plainLyrics")
                        }
                    }
                    return fallbackPlain
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun urlEncode(str: String): String = java.net.URLEncoder.encode(str, "UTF-8")

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
