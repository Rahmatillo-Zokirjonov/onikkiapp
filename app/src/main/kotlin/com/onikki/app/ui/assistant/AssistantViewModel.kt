package com.onikki.app.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.ai.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A bubble; [failed] questions stay visible (with a retry) but are never sent back as history. */
data class ChatMessage(val fromUser: Boolean, val text: String, val failed: Boolean = false)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val needsKey: Boolean = false,
    val error: String? = null
)

val ASSISTANT_SUGGESTIONS = listOf(
    "Bugun nimaga e'tibor beray?",
    "Qaysi maqsadim orqada qolyapti?",
    "Bu oy pulim qayerga ketdi?",
    "Ertangi kunimni rejalashtirib ber",
    "Odatlarimni qanday yaxshilasam bo'ladi?"
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AiRepository(application, (application as OnIkkiApplication).database)
    private val file = File(application.filesDir, "ai_chat.json")
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state

    init {
        viewModelScope.launch { _state.update { it.copy(messages = load()) } }
    }

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _state.value.isThinking) return
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.failed } + ChatMessage(true, question), isThinking = true, error = null) }
        viewModelScope.launch {
            val history = _state.value.messages.filterNot { it.failed }.map { ChatTurn(it.fromUser, it.text) }
            when (val result = repository.chat(history)) {
                is AiOutcome.Ok -> _state.update { it.copy(messages = it.messages + ChatMessage(false, result.value), isThinking = false) }
                AiOutcome.NoKey -> fail(question, null, needsKey = true)
                AiOutcome.Offline -> fail(question, "Internet yo'q")
                is AiOutcome.Failed -> fail(question, result.message)
            }
            save(_state.value.messages)
        }
    }

    /** The unanswered question is marked failed so it isn't sent again as a dangling user turn. */
    private fun fail(question: String, error: String?, needsKey: Boolean = false) = _state.update { s ->
        val messages = s.messages.dropLast(1) + ChatMessage(true, question, failed = true)
        s.copy(messages = messages, isThinking = false, error = error, needsKey = needsKey)
    }

    fun retry() {
        val failed = _state.value.messages.lastOrNull { it.failed } ?: return
        send(failed.text)
    }

    fun saveKey(key: String) {
        viewModelScope.launch {
            repository.saveKey(key)
            _state.update { it.copy(needsKey = false) }
            retry()
        }
    }

    fun dismissKey() = _state.update { it.copy(needsKey = false) }

    fun clear() {
        _state.update { AssistantUiState() }
        viewModelScope.launch { save(emptyList()) }
    }

    private suspend fun load(): List<ChatMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ChatMessage(o.getBoolean("u"), o.getString("t"))
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun save(messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        messages.filterNot { it.failed }.takeLast(MAX_SAVED).forEach { array.put(JSONObject().put("u", it.fromUser).put("t", it.text)) }
        runCatching { file.writeText(array.toString()) }
    }

    private companion object {
        const val MAX_SAVED = 60
    }
}
