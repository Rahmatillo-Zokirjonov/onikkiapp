package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.data.repository.LearnRepository
import com.onikki.app.domain.vocab.ChainDay
import com.onikki.app.domain.vocab.ChainSequence
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.SeqDirection
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
    val streak: Int = 0,
    /** First day the word list can't fill (more words are needed from then on). */
    val shortDay: Int? = null,
    /** Every word reached so far (days 1..today), in chain order — what the sequence practice uses. */
    val learnedWords: List<ImportedWord> = emptyList(),
    val weakLinks: Int = 0,
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

/** Sequence practice settings; [start] null = a random point (weak links first). */
data class SeqConfig(
    val direction: SeqDirection = SeqDirection.FORWARD,
    val length: Int = 10,
    val start: Int? = null,
    val voice: Boolean = false
)

data class SeqAnswer(val target: Int, val given: String, val verdict: Verdict, val hinted: Boolean) {
    val ok: Boolean get() = verdict != Verdict.WRONG && !hinted
}

/** A running sequence: from [anchor], say the words at [targets] one after another. */
data class SeqSession(
    val words: List<ImportedWord>,
    val anchor: Int,
    val backward: Boolean,
    val targets: List<Int>,
    val voice: Boolean,
    val answers: List<SeqAnswer> = emptyList(),
    val input: String = "",
    val hinted: Boolean = false,
    val heard: String? = null
) {
    val step: Int get() = answers.size
    val finished: Boolean get() = step >= targets.size
    /** The word the next one is said from: the start, then each answer's right word. */
    val prompt: ImportedWord get() = words[if (step == 0) anchor else targets[step - 1]]
    val current: ImportedWord? get() = targets.getOrNull(step)?.let(words::get)
    val right: Int get() = answers.count { it.ok }
    val last: SeqAnswer? get() = answers.lastOrNull()
}

/** Replacing the word at [position] (0-based in the chain). */
data class ChainEdit(val position: Int, val old: ImportedWord, val error: String? = null)

class WordChainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val prefs = LearnPrefs(application)
    private val repository = LearnRepository(db)
    private var base: List<ImportedWord> = WordChain.pool(emptyList())
    /** [base] with the user's replacements. */
    private var chain: List<ImportedWord> = base

    private val _state = MutableStateFlow(ChainState())
    val state: StateFlow<ChainState> = _state

    private val _check = MutableStateFlow<ChainCheck?>(null)
    val check: StateFlow<ChainCheck?> = _check

    private val _seqConfig = MutableStateFlow(SeqConfig())
    val seqConfig: StateFlow<SeqConfig> = _seqConfig

    private val _seq = MutableStateFlow<SeqSession?>(null)
    val seq: StateFlow<SeqSession?> = _seq

    private val _edit = MutableStateFlow<ChainEdit?>(null)
    val edit: StateFlow<ChainEdit?> = _edit

    init {
        if (prefs.chainStart() == null) prefs.setChainStart(LocalDate.now())
        refresh()
        // The dictionary (by frequency) continues the list after the curated words.
        viewModelScope.launch {
            base = ChainWords.base(application)
            refresh()
        }
    }

    fun refresh() {
        chain = WordChain.arrange(base, prefs.chainOverrides(), prefs.chainRemoved())
        val start = prefs.chainStart() ?: LocalDate.now()
        val today = LocalDate.now()
        val done = prefs.chainDone()
        val n = WordChain.dayNumber(start, today)
        val learned = chain.take(WordChain.totalUpTo(n))
        _state.value = ChainState(
            start, n, WordChain.days(start, today, done, chain), done, WordChain.streak(done, n),
            shortDay = WordChain.firstShortDay(start, chain), learnedWords = learned,
            weakLinks = prefs.chainWeak().size, loaded = true
        )
        _seqConfig.update { c -> c.copy(start = c.start?.takeIf { it < learned.size }) }
        // Words reached so far also go into the "Study Skills" deck, so lessons and reviews keep them fresh.
        if (n > 0) viewModelScope.launch {
            repository.importWords(repository.createDeck(WordChain.DECK), learned)
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

    // ------------------------------------------------------------ day check

    /** Write day [day]'s words from memory (today by default; a missed day can be caught up). */
    fun startCheck(day: Int = _state.value.today) {
        val words = WordChain.wordsFor(day, chain)
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

    // ------------------------------------------------------------ sequence practice

    fun setSeqConfig(transform: (SeqConfig) -> SeqConfig) = _seqConfig.update(transform)

    fun startSeq() {
        val words = _state.value.learnedWords
        val c = _seqConfig.value
        val plan = ChainSequence.plan(words, c.direction, c.length, c.start, prefs.chainWeak()) ?: return
        _seq.value = SeqSession(words, plan.anchor, plan.backward, plan.targets, c.voice)
    }

    fun seqType(text: String) = _seq.update { s -> s?.copy(input = text.take(40)) }

    fun seqHint() = _seq.update { s -> s?.copy(hinted = true) }

    /** The typed word for the current step. */
    fun seqSubmit() {
        val s = _seq.value ?: return
        val target = s.current ?: return
        if (s.input.isBlank()) return
        answer(s, listOf(SeqAnswer(s.targets[s.step], s.input.trim(), ChainSequence.judge(s.input, target, s.words), s.hinted)), heard = null)
    }

    /** "Bilmayman": shows the word, counts as a miss. */
    fun seqGiveUp() {
        val s = _seq.value ?: return
        if (s.current == null) return
        answer(s, listOf(SeqAnswer(s.targets[s.step], "", Verdict.WRONG, s.hinted)), heard = null)
    }

    /** What the microphone heard: every word said in order moves on; nothing matching = a miss. */
    fun seqHeard(alternatives: List<String>) {
        val s = _seq.value ?: return
        val left = s.targets.drop(s.step)
        if (left.isEmpty()) return
        val said = alternatives.firstOrNull().orEmpty()
        val n = ChainSequence.matchSpoken(alternatives, left.map(s.words::get))
        val answers = if (n == 0) listOf(SeqAnswer(left.first(), said, Verdict.WRONG, s.hinted))
        else left.take(n).mapIndexed { i, t -> SeqAnswer(t, s.words[t].english, Verdict.CORRECT, s.hinted && i == 0) }
        answer(s, answers, heard = said)
    }

    private fun answer(s: SeqSession, new: List<SeqAnswer>, heard: String?) {
        var weak = prefs.chainWeak()
        var from = s.prompt
        new.forEach { a ->
            val to = s.words[a.target]
            weak = ChainSequence.updateWeak(weak, ChainSequence.linkKey(from, to), missed = !a.ok)
            from = to
        }
        prefs.setChainWeak(weak)
        _seq.value = s.copy(answers = s.answers + new, input = "", hinted = false, heard = heard)
        _state.update { it.copy(weakLinks = weak.size) }
    }

    fun closeSeq() { _seq.value = null }

    // ------------------------------------------------------------ replacing words

    fun startEdit(position: Int) {
        val old = chain.getOrNull(position) ?: return
        _edit.value = ChainEdit(position, old)
    }

    fun closeEdit() { _edit.value = null }

    /** Puts the user's own word at the edited place; the old word is taken out of the chain. */
    fun saveOwn(english: String, uzbek: String, example: String) {
        val e = _edit.value ?: return
        val en = english.trim()
        val uz = uzbek.trim()
        when {
            en.isEmpty() || uz.isEmpty() -> { _edit.value = e.copy(error = "Inglizchasi va o'zbekchasini yozing"); return }
            !WordChain.canUse(en, chain, e.position) -> { _edit.value = e.copy(error = "\"$en\" zanjirda allaqachon bor"); return }
        }
        replace(e, ImportedWord(en, uz, example.trim().ifEmpty { null }, null))
    }

    /** Puts a word not met yet (from the far end of the list) at the edited place. */
    fun autoReplace() {
        val e = _edit.value ?: return
        val overrides = prefs.chainOverrides()
        val removed = prefs.chainRemoved()
        val pick = WordChain.autoPick(base, overrides, removed, e.old)
        if (pick == null) { _edit.value = e.copy(error = "Ro'yxatda bo'sh so'z qolmadi — o'zingiz yozing"); return }
        replace(e, pick)
    }

    private fun replace(e: ChainEdit, word: ImportedWord) {
        prefs.setChainOverrides(prefs.chainOverrides() + (e.position to word))
        prefs.setChainRemoved(prefs.chainRemoved() + WordChain.key(e.old) - WordChain.key(word))
        _edit.value = null
        refresh()
    }
}
