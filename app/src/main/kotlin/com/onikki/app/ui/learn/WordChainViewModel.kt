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
    val streak: Int = 0
) {
    val todayDay: ChainDay? get() = days.getOrNull(today - 1)
    val finishedAll: Boolean get() = days.isNotEmpty() && days.all { it.number in done }
}

/** Reciting today's chain from memory, position by position. */
data class ChainCheck(
    val day: Int,
    val chain: List<ImportedWord>,
    val inputs: List<String> = List(chain.size) { "" },
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

    private val _state = MutableStateFlow(ChainState())
    val state: StateFlow<ChainState> = _state

    private val _check = MutableStateFlow<ChainCheck?>(null)
    val check: StateFlow<ChainCheck?> = _check

    init {
        if (prefs.chainStart() == null) prefs.setChainStart(LocalDate.now())
        refresh()
    }

    fun refresh() {
        val start = prefs.chainStart() ?: LocalDate.now()
        val today = LocalDate.now()
        val done = prefs.chainDone()
        val n = WordChain.dayNumber(start, today)
        _state.value = ChainState(start, n, WordChain.days(start, today, done), done, WordChain.streak(done, n))
        // Words reached so far also go into the "Study Skills" deck, so lessons and reviews keep them fresh.
        if (n > 0) viewModelScope.launch {
            repository.importWords(repository.createDeck(WordChain.DECK), WordChain.chain(n))
        }
    }

    /** Moves the start by [days] (for a task that began earlier or later); finished days are kept by number. */
    fun shiftStart(days: Long) {
        val current = prefs.chainStart() ?: LocalDate.now()
        val next = current.plusDays(days)
        if (next.isBefore(WordChain.earliestStart) || next.isAfter(LocalDate.now())) return
        prefs.setChainStart(next)
        refresh()
    }

    fun startCheck() {
        val n = _state.value.today
        if (n <= 0) return
        _check.value = ChainCheck(n, WordChain.chain(n))
    }

    fun type(i: Int, text: String) = _check.update { c ->
        if (c == null || c.checked) c else c.copy(inputs = c.inputs.toMutableList().also { it[i] = text.take(40) })
    }

    fun hint(i: Int) = _check.update { c -> c?.copy(hinted = c.hinted + i) }

    fun submit() {
        val c = _check.value ?: return
        val verdicts = WordChain.check(c.inputs, c.chain)
        _check.value = c.copy(verdicts = verdicts)
        // A position recalled only with its Uzbek hint doesn't count as from memory.
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
