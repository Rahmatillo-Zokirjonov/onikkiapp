package com.onikki.app.ui.learn

import androidx.lifecycle.ViewModel
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.ChainPath
import com.onikki.app.domain.vocab.ChoiceQuestion
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.PathStep
import com.onikki.app.domain.vocab.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** "O'rganish yo'li" for one day's words: where the user is and the state of the current step. */
data class PathState(
    val day: Int,
    val words: List<ImportedWord>,
    val earlier: List<ImportedWord>,
    val step: PathStep = PathStep.CARDS,
    // Choosing: a missed question comes again at the end.
    val choices: List<ChoiceQuestion> = emptyList(),
    val picked: String? = null,
    // Matching: English on the left in order, Uzbek on the right in [matchOrder].
    val matchWords: List<ImportedWord> = emptyList(),
    val matchOrder: List<Int> = emptyList(),
    val selected: Int? = null,
    val matched: Set<Int> = emptySet(),
    val wrongPair: Pair<Int, Int>? = null,
    // Listening: a missed word comes again at the end.
    val listen: List<ImportedWord> = emptyList(),
    val input: String = "",
    val verdict: Verdict? = null,
    val mistakes: Int = 0
) {
    val choice: ChoiceQuestion? get() = choices.firstOrNull()
    val heard: ImportedWord? get() = listen.firstOrNull()
    val matchDone: Boolean get() = matchWords.isNotEmpty() && matched.size == matchWords.size
}

class ChainPathViewModel : ViewModel() {
    private val _state = MutableStateFlow<PathState?>(null)
    val state: StateFlow<PathState?> = _state

    /** Starts the path on [words] (day [day]); [earlier] are the chain words before them. */
    fun start(day: Int, words: List<ImportedWord>, earlier: List<ImportedWord>) {
        if (words.isEmpty()) return
        _state.value = PathState(day, words, earlier)
    }

    /** On to the next step, setting it up. */
    fun next() = _state.update { s ->
        s ?: return@update null
        when (s.step) {
            PathStep.CARDS -> s.copy(step = PathStep.CHOOSE, choices = ChainPath.choices(s.words, s.earlier), picked = null)
            PathStep.CHOOSE -> {
                val words = ChainPath.matchWords(s.words, s.earlier)
                s.copy(step = PathStep.MATCH, matchWords = words, matchOrder = ChainPath.matchOrder(words.size), matched = emptySet(), selected = null)
            }
            PathStep.MATCH -> s.copy(step = PathStep.LISTEN, listen = s.words.shuffled(), input = "", verdict = null)
            PathStep.LISTEN, PathStep.WRITE -> s.copy(step = PathStep.WRITE)
        }
    }

    fun pick(option: String) = _state.update { s ->
        if (s == null || s.picked != null || s.choice == null) s else s.copy(picked = option)
    }

    /** After a pick: a miss comes back at the end (with the options shuffled anew). */
    fun nextChoice() {
        val s = _state.value ?: return
        val q = s.choice ?: return
        val picked = s.picked ?: return
        val rest = s.choices.drop(1)
        val right = ChainPath.isRight(q, picked)
        val choices = if (right) rest else rest + ChainPath.choice(q.answer, s.words + s.earlier)
        _state.value = s.copy(choices = choices, picked = null, mistakes = s.mistakes + if (right) 0 else 1)
        if (choices.isEmpty()) next()
    }

    fun selectLeft(i: Int) = _state.update { s -> if (s == null || i in s.matched) s else s.copy(selected = i, wrongPair = null) }

    /** Tapping an Uzbek meaning: matched if it belongs to the selected English word. */
    fun selectRight(wordIndex: Int) = _state.update { s ->
        val left = s?.selected ?: return@update s
        if (wordIndex in s.matched) s
        else if (wordIndex == left) s.copy(matched = s.matched + left, selected = null, wrongPair = null)
        else s.copy(wrongPair = left to wordIndex, selected = null, mistakes = s.mistakes + 1)
    }

    fun type(text: String) = _state.update { s -> if (s == null || s.verdict != null) s else s.copy(input = text.take(40)) }

    fun checkListen() = _state.update { s ->
        val w = s?.heard ?: return@update s
        if (s.input.isBlank() || s.verdict != null) s
        else {
            val v = AnswerCheck.judge(s.input, listOf(w.english))
            s.copy(verdict = v, mistakes = s.mistakes + if (v == Verdict.WRONG) 1 else 0)
        }
    }

    /** After a listening answer: a miss comes again at the end. */
    fun nextListen() {
        val s = _state.value ?: return
        val w = s.heard ?: return
        val v = s.verdict ?: return
        val rest = s.listen.drop(1)
        val listen = if (v == Verdict.WRONG) rest + w else rest
        _state.value = s.copy(listen = listen, input = "", verdict = null)
        if (listen.isEmpty()) next()
    }

    fun close() { _state.value = null }
}
