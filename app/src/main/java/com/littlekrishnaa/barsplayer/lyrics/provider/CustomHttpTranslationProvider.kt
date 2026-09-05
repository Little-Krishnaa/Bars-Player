package com.littlekrishnaa.barsplayer.lyrics.provider

import com.littlekrishnaa.barsplayer.lyrics.PromptBuilder
import com.littlekrishnaa.barsplayer.lyrics.TranslationProvider
import com.littlekrishnaa.barsplayer.lyrics.TranslationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CustomHttpTranslationProvider(
    private val client: OkHttpClient,
    private val endpointUrl: String,
    private val apiKey: String,
    private val modelName: String
) : TranslationProvider {

    override suspend fun translate(request: TranslationRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = PromptBuilder.buildSystemPrompt(request.personaPrompt)
            val userContent = PromptBuilder.buildUserContent(request)

            val jsonBody = JSONObject().apply {
                if (modelName.isNotBlank()) put("model", modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            }

            val httpRequest = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "application/json")
                .apply {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                }
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP Error: ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
