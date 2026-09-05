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

class OpenAiTranslationProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val modelName: String = "gpt-4o-mini"
) : TranslationProvider {

    override suspend fun translate(request: TranslationRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = PromptBuilder.buildSystemPrompt(request.personaPrompt)
            val userContent = PromptBuilder.buildUserContent(request)

            val jsonBody = JSONObject().apply {
                put("model", if (modelName.isBlank()) "gpt-4o-mini" else modelName)
                put("response_format", JSONObject().put("type", "json_object"))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
            }

            val httpRequest = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("OpenAI API error: ${response.code}"))
                }
                val resBody = response.body?.string().orEmpty()
                val jsonRes = JSONObject(resBody)
                val content = jsonRes.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
