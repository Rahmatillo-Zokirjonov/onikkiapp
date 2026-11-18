package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.notifications.ReminderScheduler
import com.onikki.app.domain.vocab.ChainMemory
import com.onikki.app.domain.vocab.ChainStoryText
import com.onikki.app.domain.vocab.ImportedWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryState(
    /** Index of the part (0 = words 1–15). */
    val part: Int = 0,
    val story: ChainStoryText? = null,
    /** The story names every word in the chain's order. */
    val inOrder: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val notify: Boolean = false
)

/** The chain's memory helpers: the AI story over a part of the chain, and the word reminders. */
class ChainStoryViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LearnPrefs(application)
    private val ai = AiRepository(application, (application as OnIkkiApplication).database)

    private val _state = MutableStateFlow(StoryState(notify = prefs.chainNotify()))
    val state: StateFlow<StoryState> = _state

    /** Shows part [part] of [learned] (a saved story if its words haven't changed). */
    fun show(part: Int, learned: List<ImportedWord>) {
        val words = words(part, learned)
        val saved = prefs.chainStory(part * ChainMemory.STORY_PART, ChainMemory.signature(words))
        val story = saved?.let(ChainMemory::parse)
        _state.update { it.copy(part = part, story = story, inOrder = story == null || ChainMemory.inOrder(story.english, words), error = null) }
    }

    /** Asks the AI for a story over the part's words, in their order. */
    fun generate(learned: List<ImportedWord>) {
        val part = _state.value.part
        val words = words(part, learned)
        if (words.isEmpty() || _state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = ai.writeChainStory(words.map { it.english })) {
                is AiOutcome.Ok -> {
                    prefs.setChainStory(part * ChainMemory.STORY_PART, ChainMemory.signature(words), r.value)
                    val story = ChainMemory.parse(r.value)
                    _state.update { it.copy(loading = false, story = story, inOrder = ChainMemory.inOrder(story.english, words)) }
                }
                is AiOutcome.Failed -> _state.update { it.copy(loading = false, error = r.message) }
                AiOutcome.NoKey -> _state.update { it.copy(loading = false, error = "AI kaliti kiritilmagan — AI yordamchi bo'limida kalitni saqlang") }
                AiOutcome.Offline -> _state.update { it.copy(loading = false, error = "Internet kerak (hikoyani AI tuzadi)") }
            }
        }
    }

    private fun words(part: Int, learned: List<ImportedWord>): List<ImportedWord> =
        ChainMemory.parts(learned.size).getOrNull(part)?.let { r -> learned.slice(r) }.orEmpty()

    fun setNotify(on: Boolean) {
        prefs.setChainNotify(on)
        _state.update { it.copy(notify = on) }
        viewModelScope.launch { runCatching { ReminderScheduler(getApplication()).resync() } }
    }
}
