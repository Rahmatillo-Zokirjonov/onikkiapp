package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.ListeningAttempt
import com.onikki.app.data.db.entity.ListeningCustomText
import com.onikki.app.data.db.entity.ReviewSource
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.data.repository.LearnRepository
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.DictEntry
import com.onikki.app.domain.vocab.EnglishDictionary
import com.onikki.app.domain.vocab.GapPlan
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.Listening
import com.onikki.app.domain.vocab.ListeningText
import com.onikki.app.domain.vocab.Srs
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.ui.vocabulary.DictionaryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

data class ListeningHomeState(
    val level: String = "A1",
    val suggested: String = "A1",
    val texts: List<ListeningText> = emptyList(),
    /** Best accuracy per text id. */
    val best: Map<String, Int> = emptyMap(),
    val doneToday: Int = 0
)

enum class ListenPhase { LISTEN, FILL, RESULT }

data class ListenSession(
    val text: ListeningText,
    val plan: GapPlan,
    val bank: List<String>,
    val bankMode: Boolean,
    val phase: ListenPhase = ListenPhase.LISTEN,
    val inputs: List<String> = List(plan.answers.size) { "" },
    /** The gap a bank word goes into. */
    val selected: Int = 0,
    val verdicts: List<Verdict> = emptyList(),
    val xp: Int = 0,
    val accuracy: Int = 0
) {
    val filled: Int get() = inputs.count { it.isNotBlank() }
    /** Bank words not placed yet (a word used twice in the bank stays until both are placed). */
    val bankLeft: List<String> get() {
        val left = bank.toMutableList()
        inputs.forEach { v -> left.indexOfFirst { it.equals(v, ignoreCase = true) }.takeIf { it >= 0 }?.let { left.removeAt(it) } }
        return left
    }
}

sealed interface AiWriteState {
    data object Idle : AiWriteState
    data object Loading : AiWriteState
    data class Failed(val message: String) : AiWriteState
    data object NeedsKey : AiWriteState
}

class ListeningViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.listeningDao()
    private val repository = LearnRepository(db)
    private val ai = AiRepository(application, db)
    private val prefs = LearnPrefs(application)
    private val random = Random(System.nanoTime())
    private val today = LocalDate.now()
    private var words: List<VocabWord> = emptyList()
    private var dictionary: EnglishDictionary? = null

    private val level = MutableStateFlow(prefs.listenLevel())

    init {
        viewModelScope.launch { dictionary = runCatching { DictionaryStore.get(application) }.getOrNull() }
    }

    val home: StateFlow<ListeningHomeState> = combine(
        level,
        dao.observeTexts(),
        dao.observeAttempts(),
        db.vocabWordDao().observeInPathOrder()
    ) { picked, custom, attempts, all ->
        words = all
        val suggested = Listening.suggestLevel(all)
        val current = picked ?: suggested
        ListeningHomeState(
            level = current,
            suggested = suggested,
            texts = Listening.forLevel(current, custom.map { it.toText() }),
            best = attempts.groupBy { it.textId }.mapValues { (_, list) -> list.maxOf { it.accuracy } },
            doneToday = attempts.count { it.day == today }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningHomeState())

    private val _session = MutableStateFlow<ListenSession?>(null)
    val session: StateFlow<ListenSession?> = _session

    private val _aiState = MutableStateFlow<AiWriteState>(AiWriteState.Idle)
    val aiState: StateFlow<AiWriteState> = _aiState

    fun setLevel(l: String) {
        prefs.setListenLevel(l)
        level.value = l
    }

    fun open(text: ListeningText) {
        val known = words.flatMap { listOfNotNull(it.english, it.pastForm) }.map { AnswerCheck.normalize(it) }.toSet()
        val plan = Listening.plan(text, known, random)
        _session.value = ListenSession(text, plan, Listening.bank(plan, random), Listening.bankByDefault(text.level))
    }

    fun startFill() = _session.update { it?.copy(phase = ListenPhase.FILL) }

    fun toggleMode() = _session.update { it?.copy(bankMode = !it.bankMode) }

    fun select(gap: Int) = _session.update { s ->
        if (s == null) null
        // Tapping a filled gap in bank mode takes its word back.
        else if (s.bankMode && s.inputs[gap].isNotBlank()) s.copy(inputs = s.inputs.toMutableList().also { it[gap] = "" }, selected = gap)
        else s.copy(selected = gap)
    }

    fun setInput(gap: Int, value: String) = _session.update { s ->
        s?.copy(inputs = s.inputs.toMutableList().also { it[gap] = value.take(30) }, selected = gap)
    }

    /** A bank word goes into the selected gap, then the next empty one is selected. */
    fun pick(word: String) = _session.update { s ->
        if (s == null) return@update null
        val target = if (s.inputs.getOrNull(s.selected).isNullOrBlank()) s.selected else s.inputs.indexOfFirst { it.isBlank() }
        if (target < 0) return@update s
        val inputs = s.inputs.toMutableList().also { it[target] = word }
        val next = (inputs.indices.firstOrNull { it > target && inputs[it].isBlank() } ?: inputs.indexOfFirst { it.isBlank() }).let { if (it < 0) target else it }
        s.copy(inputs = inputs, selected = next)
    }

    fun check() {
        val s = _session.value ?: return
        val verdicts = Listening.check(s.inputs, s.plan.answers)
        val xp = Listening.xp(verdicts)
        _session.value = s.copy(phase = ListenPhase.RESULT, verdicts = verdicts, xp = xp, accuracy = Listening.accuracy(verdicts))
        viewModelScope.launch {
            // Gaps that are words from the user's list move those words' schedule (a miss puts them on "Bilmaganlarim").
            val byForm = words.flatMap { w -> listOfNotNull(w.english, w.pastForm).map { AnswerCheck.normalize(it) to w } }.toMap()
            var vocabXp = 0
            s.plan.answers.forEachIndexed { i, answer ->
                val w = byForm[AnswerCheck.normalize(answer)] ?: return@forEachIndexed
                if (w.status == WordStatus.NEW && verdicts[i] == Verdict.WRONG) return@forEachIndexed
                val outcome = repository.recordAnswer(w.id, Srs.gradeFor(verdicts[i], false), ReviewSource.LISTENING, "LISTENING", hardExercise = !s.bankMode)
                vocabXp += outcome?.xp ?: 0
            }
            dao.insertAttempt(
                ListeningAttempt(textId = s.text.id, level = s.text.level, at = System.currentTimeMillis(), day = LocalDate.now(), accuracy = Listening.accuracy(verdicts), xp = (xp - vocabXp).coerceAtLeast(0))
            )
        }
    }

    /** Same text, the same gaps, empty again. */
    fun retry() = _session.update { s -> s?.copy(phase = ListenPhase.FILL, inputs = List(s.plan.answers.size) { "" }, selected = 0, verdicts = emptyList(), xp = 0) }

    /** Same text, other words missing. */
    fun newGaps() { _session.value?.text?.let(::open) }

    fun next() {
        val s = _session.value ?: return
        val list = home.value.texts
        val i = list.indexOfFirst { it.id == s.text.id }
        val nextText = list.getOrNull(i + 1) ?: list.firstOrNull { home.value.best[it.id] == null } ?: list.firstOrNull()
        if (nextText != null) open(nextText) else close()
    }

    fun close() { _session.value = null }

    /** AI writes a fresh text at the current level with words the user is learning. */
    fun writeWithAi() {
        val lvl = home.value.level
        val learning = words.filter { it.status == WordStatus.LEARNING || it.unknownSince != null }.shuffled(random) +
            words.filter { it.status == WordStatus.REVIEW }.shuffled(random)
        _aiState.value = AiWriteState.Loading
        viewModelScope.launch {
            _aiState.value = when (val r = ai.writeListening(lvl, learning.map { it.english }.distinct().take(15))) {
                is AiOutcome.Ok -> {
                    val id = dao.insertText(ListeningCustomText(level = lvl, title = r.value.first, body = r.value.second.joinToString("\n"), createdAt = System.currentTimeMillis()))
                    open(ListeningCustomText(id, lvl, r.value.first, r.value.second.joinToString("\n"), 0).toText())
                    AiWriteState.Idle
                }
                is AiOutcome.Failed -> AiWriteState.Failed(r.message)
                AiOutcome.Offline -> AiWriteState.Failed("Internet yo'q")
                AiOutcome.NoKey -> AiWriteState.NeedsKey
            }
        }
    }

    fun dismissAi() { _aiState.value = AiWriteState.Idle }

    fun saveKey(key: String) {
        viewModelScope.launch {
            ai.saveKey(key)
            writeWithAi()
        }
    }

    fun lookup(english: String): DictEntry? = dictionary?.lookup(english)

    fun isInList(english: String): Boolean = words.any { AnswerCheck.normalize(it.english) == AnswerCheck.normalize(english) }

    /** A word from the text → "Mening so'zlarim", with its sentence as the example. */
    fun addWord(english: String, uzbek: String, example: String?, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val r = repository.importWords(repository.myWordsDeckId(), listOf(ImportedWord(english.trim().lowercase(), uzbek.trim(), example, null)))
            onDone(if (r.added > 0) "\"$english\" qo'shildi" else "\"$english\" ro'yxatda bor edi")
        }
    }

    private fun ListeningCustomText.toText() =
        ListeningText("ai-$id", level, title, "AI matn", body.lines().filter { it.isNotBlank() }, ai = true)
}
