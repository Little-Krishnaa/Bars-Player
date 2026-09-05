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

class AnthropicTranslationProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val modelName: String = "claude-3-5-haiku-20241022"
) : TranslationProvider {

    override suspend fun translate(request: TranslationRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = PromptBuilder.buildSystemPrompt(request.personaPrompt)
            val userContent = PromptBuilder.buildUserContent(request)

            val jsonBody = JSONObject().apply {
                put("model", if (modelName.isBlank()) "claude-3-5-haiku-20241022" else modelName)
                put("max_tokens", 4096)
                put("system", systemPrompt)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            }

            val httpRequest = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Anthropic API error: ${response.code}"))
                }
                val resBody = response.body?.string().orEmpty()
                val jsonRes = JSONObject(resBody)
                val text = jsonRes.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
