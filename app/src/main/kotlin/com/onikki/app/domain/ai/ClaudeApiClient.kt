package com.onikki.app.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ClaudeResult {
    data class Success(val text: String) : ClaudeResult()
    data class Error(val message: String) : ClaudeResult()
}

/**
 * Minimal raw-HTTP client for the Claude Messages API — the only module in
 * this app that touches the network (TZ 3.7/4).
 *
 * Deliberately not the official `anthropic-java` SDK: resolving it for this
 * project's dependency tree pulled in Jackson, Apache HttpClient5, and
 * JSON-schema/Swagger generator tooling built for backend services — verified
 * with a real `gradle :app:dependencies` run, not assumed. That's a poor fit
 * for a mobile client sending one short request a day. OkHttp (already a
 * lightweight, standard Android HTTP client) plus `org.json` (built into
 * Android, no extra dependency) cover this single-endpoint, non-streaming
 * call with far less weight.
 */
class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(systemPrompt: String, userMessage: String): ClaudeResult =
        withContext(Dispatchers.IO) {
            try {
                val requestJson = JSONObject().apply {
                    put("model", MODEL)
                    put("max_tokens", MAX_TOKENS)
                    put("system", systemPrompt)
                    put("output_config", JSONObject().put("effort", "low"))
                    put(
                        "messages",
                        JSONArray().put(JSONObject().put("role", "user").put("content", userMessage))
                    )
                }

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", API_VERSION)
                    .addHeader("content-type", "application/json")
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = runCatching {
                            JSONObject(bodyText).getJSONObject("error").getString("message")
                        }.getOrDefault("HTTP ${response.code}")
                        return@use ClaudeResult.Error(message)
                    }
                    val content = JSONObject(bodyText).optJSONArray("content")
                    val text = buildString {
                        for (i in 0 until (content?.length() ?: 0)) {
                            val block = content!!.getJSONObject(i)
                            if (block.optString("type") == "text") append(block.optString("text"))
                        }
                    }
                    if (text.isBlank()) ClaudeResult.Error("Bo'sh javob qaytdi") else ClaudeResult.Success(text)
                }
            } catch (e: IOException) {
                ClaudeResult.Error(e.message ?: "Tarmoq xatosi")
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-opus-5-5"
        private const val MAX_TOKENS = 1024
    }
}
