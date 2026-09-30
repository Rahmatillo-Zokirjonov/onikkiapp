package com.onikki.app.domain.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ClaudeResult {
    data class Success(val text: String) : ClaudeResult()
    data class Error(val message: String) : ClaudeResult()
}

/** One turn of a conversation; only the visible text is kept (no thinking blocks are replayed). */
data class ChatTurn(val fromUser: Boolean, val text: String)

/**
 * Minimal raw-HTTP client for the Claude Messages API — the only module in
 * this app that touches the network (TZ 3.7/4).
 *
 * Deliberately not the official `anthropic-java` SDK: resolving it for this
 * project's dependency tree pulled in Jackson, Apache HttpClient5, and
 * JSON-schema/Swagger generator tooling built for backend services — verified
 * with a real `gradle :app:dependencies` run, not assumed. That's a poor fit
 * for a mobile client sending a few short requests a day. OkHttp (already a
 * lightweight, standard Android HTTP client) plus `org.json` (built into
 * Android, no extra dependency) cover these non-streaming calls with far less weight.
 *
 * Claude Opus 5.5 always thinks (effort is the only control) and the thinking counts toward
 * `max_tokens`, so limits here are sized for thinking + reply, and replies are read by block type.
 */
class ClaudeApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Single question → answer. */
    suspend fun sendMessage(systemPrompt: String, userMessage: String, maxTokens: Int = DEFAULT_MAX_TOKENS): ClaudeResult =
        send(systemPrompt, listOf(ChatTurn(fromUser = true, text = userMessage)), maxTokens = maxTokens)

    /**
     * @param turns the conversation, oldest first, starting and ending with a user turn.
     * @param jsonSchema when set, the reply is constrained to JSON matching it (structured outputs).
     */
    suspend fun send(
        systemPrompt: String,
        turns: List<ChatTurn>,
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        effort: String = "low",
        jsonSchema: JSONObject? = null
    ): ClaudeResult = withContext(Dispatchers.IO) {
        try {
            val outputConfig = JSONObject().put("effort", effort)
            if (jsonSchema != null) outputConfig.put("format", JSONObject().put("type", "json_schema").put("schema", jsonSchema))
            val messages = JSONArray()
            turns.forEach { turn -> messages.put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", turn.text)) }
            val requestJson = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", maxTokens)
                .put("system", systemPrompt)
                .put("output_config", outputConfig)
                .put("messages", messages)

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
                    val message = runCatching { JSONObject(bodyText).getJSONObject("error").getString("message") }
                        .getOrDefault("HTTP ${response.code}")
                    return@use ClaudeResult.Error(friendlyError(response.code, message))
                }
                val body = JSONObject(bodyText)
                val content = body.optJSONArray("content")
                val text = buildString {
                    for (i in 0 until (content?.length() ?: 0)) {
                        val block = content!!.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
                when (body.optString("stop_reason")) {
                    "refusal" -> ClaudeResult.Error("AI bu so'rovga javob bermadi")
                    // A cut-off JSON reply can't be parsed; a cut-off text reply is still worth showing.
                    "max_tokens" -> if (jsonSchema != null || text.isBlank()) ClaudeResult.Error("Javob juda uzun bo'lib ketdi, qayta urinib ko'ring") else ClaudeResult.Success(text)
                    else -> if (text.isBlank()) ClaudeResult.Error("Bo'sh javob qaytdi") else ClaudeResult.Success(text)
                }
            }
        } catch (e: IOException) {
            ClaudeResult.Error(e.message ?: "Tarmoq xatosi")
        } catch (e: JSONException) {
            ClaudeResult.Error("Javobni o'qib bo'lmadi")
        }
    }

    private fun friendlyError(code: Int, message: String): String = when (code) {
        401 -> "API kalit noto'g'ri — Sozlamalarda tekshiring"
        403 -> "Bu kalitga ruxsat yo'q: $message"
        429 -> "Juda ko'p so'rov — biroz kutib qayta urining"
        in 500..599 -> "Anthropic serveri band — keyinroq urinib ko'ring"
        else -> message
    }

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-opus-5-5"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
