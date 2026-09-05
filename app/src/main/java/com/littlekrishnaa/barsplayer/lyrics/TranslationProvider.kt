package com.littlekrishnaa.barsplayer.lyrics

data class TranslationRequest(
    val artist: String,
    val title: String,
    val year: String? = null,
    val region: String? = null,
    val lines: List<SyncedLine>,
    val personaPrompt: String
)

interface TranslationProvider {
    suspend fun translate(request: TranslationRequest): Result<String>
}
