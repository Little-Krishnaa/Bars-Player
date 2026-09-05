package com.littlekrishnaa.barsplayer.lyrics

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.littlekrishnaa.barsplayer.lyrics.provider.AnthropicTranslationProvider
import com.littlekrishnaa.barsplayer.lyrics.provider.CustomHttpTranslationProvider
import com.littlekrishnaa.barsplayer.lyrics.provider.OpenAiTranslationProvider
import okhttp3.OkHttpClient

class LyricsSettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_lyrics_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        const val MODE_CUSTOM_HTTP = 0
        const val MODE_ANTHROPIC = 1
        const val MODE_OPENAI = 2

        const val DEFAULT_HIPHOP_PRESET = """
Focus on translating the true meaning and contextual nuance rather than literal word-for-word translation.
Maintain a casual, authentic tone suitable for modern hip-hop culture.
Understand idioms, slang, double entendres, and wordplay.
Use the 'note' field only when a slang term or reference requires critical cultural context.
"""
    }

    var providerMode: Int
        get() = prefs.getInt("provider_mode", MODE_OPENAI)
        set(value) = prefs.edit().putInt("provider_mode", value).apply()

    var customEndpoint: String
        get() = prefs.getString("custom_endpoint", "https://api.openai.com/v1/chat/completions").orEmpty()
        set(value) = prefs.edit().putString("custom_endpoint", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "").orEmpty()
        set(value) = prefs.edit().putString("api_key", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "gpt-4o-mini").orEmpty()
        set(value) = prefs.edit().putString("model_name", value).apply()

    var personaPrompt: String
        get() = prefs.getString("persona_prompt", DEFAULT_HIPHOP_PRESET).orEmpty()
        set(value) = prefs.edit().putString("persona_prompt", value).apply()

    fun getActiveProvider(client: OkHttpClient): TranslationProvider {
        return when (providerMode) {
            MODE_ANTHROPIC -> AnthropicTranslationProvider(client, apiKey, modelName)
            MODE_OPENAI -> OpenAiTranslationProvider(client, apiKey, modelName)
            else -> CustomHttpTranslationProvider(client, customEndpoint, apiKey, modelName)
        }
    }
}
