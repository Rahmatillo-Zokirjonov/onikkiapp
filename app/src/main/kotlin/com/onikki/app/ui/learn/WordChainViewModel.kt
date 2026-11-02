package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.data.repository.LearnRepository
import com.onikki.app.domain.vocab.ChainDay
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.domain.vocab.WordChain
import com.onikki.app.ui.vocabulary.DictionaryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChainState(
    val start: LocalDate = LocalDate.now(),
    val today: Int = 1,
    val days: List<ChainDay> = emptyList(),
    val done: Set<Int> = emptySet(),
    val streak: Int = 0,
    /** First day the word list can't fill (more words are needed from then on). */
    val shortDay: Int? = null,
    val loaded: Boolean = false
) {
    val todayDay: ChainDay? get() = days.getOrNull(today - 1)
    val finishedAll: Boolean get() = days.isNotEmpty() && days.all { it.number in done }
    val learned: Int get() = done.sumOf { it }
}

/** Writing a day's words from memory, in order (the Uzbek meaning is the prompt). */
data class ChainCheck(
    val day: Int,
    val words: List<ImportedWord>,
    val inputs: List<String> = List(words.size) { "" },
    val hinted: Set<Int> = emptySet(),
    val verdicts: List<Verdict> = emptyList()
) {
    val checked: Boolean get() = verdicts.isNotEmpty()
    val right: Int get() = verdicts.count { it == Verdict.CORRECT }
}

class WordChainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val prefs = LearnPrefs(application)
    private val repository = LearnRepository(db)
    private var pool: List<ImportedWord> = WordChain.pool(emptyList())

    private val _state = MutableStateFlow(ChainState())
    val state: StateFlow<ChainState> = _state

    private val _check = MutableStateFlow<ChainCheck?>(null)
    val check: StateFlow<ChainCheck?> = _check

    init {
        if (prefs.chainStart() == null) prefs.setChainStart(LocalDate.now())
        refresh()
        // The dictionary (by frequency) continues the list after the curated words.
        viewModelScope.launch {
            val dict = runCatching { DictionaryStore.get(application) }.getOrNull()
            if (dict != null) {
                pool = WordChain.pool(dict.popularMissing(emptySet(), 10_000).map { ImportedWord(it.english, it.uzbek, null, null) })
                refresh()
            }
        }
    }

    fun refresh() {
        val start = prefs.chainStart() ?: LocalDate.now()
        val today = LocalDate.now()
        val done = prefs.chainDone()
        val n = WordChain.dayNumber(start, today)
        _state.value = ChainState(
            start, n, WordChain.days(start, today, done, pool), done, WordChain.streak(done, n),
            shortDay = WordChain.firstShortDay(start, pool), loaded = true
        )
        // Words reached so far also go into the "Study Skills" deck, so lessons and reviews keep them fresh.
        if (n > 0) viewModelScope.launch {
            repository.importWords(repository.createDeck(WordChain.DECK), (1..n).flatMap { WordChain.wordsFor(it, pool) })
        }
    }

    /** Moves the start by [days] (for a task that began earlier or later); finished days are kept by number. */
    fun shiftStart(days: Long) {
        val current = prefs.chainStart() ?: LocalDate.now()
        val next = current.plusDays(days)
        if (next.isBefore(LocalDate.now().minusDays(120)) || next.isAfter(LocalDate.now())) return
        prefs.setChainStart(next)
        refresh()
    }

    /** Write day [day]'s words from memory (today by default; a missed day can be caught up). */
    fun startCheck(day: Int = _state.value.today) {
        val words = WordChain.wordsFor(day, pool)
        if (day <= 0 || words.isEmpty()) return
        _check.value = ChainCheck(day, words)
    }

    fun type(i: Int, text: String) = _check.update { c ->
        if (c == null || c.checked) c else c.copy(inputs = c.inputs.toMutableList().also { it[i] = text.take(40) })
    }

    fun hint(i: Int) = _check.update { c -> c?.copy(hinted = c.hinted + i) }

    fun submit() {
        val c = _check.value ?: return
        val verdicts = WordChain.check(c.inputs, c.words)
        _check.value = c.copy(verdicts = verdicts)
        // A word recalled only with its hint doesn't count as from memory.
        if (WordChain.passed(verdicts) && c.hinted.isEmpty()) {
            prefs.setChainDone(prefs.chainDone() + c.day)
            refresh()
        }
    }

    /** Try again: keeps what was right, clears the rest. */
    fun retry() = _check.update { c ->
        c?.copy(inputs = c.inputs.mapIndexed { i, s -> if (c.verdicts.getOrNull(i) == Verdict.CORRECT) s else "" }, verdicts = emptyList(), hinted = emptySet())
    }

    fun closeCheck() { _check.value = null }
}
