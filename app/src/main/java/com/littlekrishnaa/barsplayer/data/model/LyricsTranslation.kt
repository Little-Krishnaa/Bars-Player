package com.littlekrishnaa.barsplayer.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LyricsTranslationResponse(
    @Json(name = "lines")
    val lines: List<TranslatedLine>
)

@JsonClass(generateAdapter = true)
data class TranslatedLine(
    @Json(name = "time_ms")
    val timeMs: Long,
    @Json(name = "original")
    val original: String,
    @Json(name = "translated")
    val translated: String,
    @Json(name = "note")
    val note: String? = null
)
