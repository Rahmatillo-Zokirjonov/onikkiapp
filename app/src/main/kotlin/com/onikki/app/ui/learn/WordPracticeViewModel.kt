package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.ConfusablePair
import com.onikki.app.data.db.entity.PracticeLog
import com.onikki.app.domain.vocab.ConfusingPairs
import com.onikki.app.domain.vocab.FamilyQuestion
import com.onikki.app.domain.vocab.PairQuestion
import com.onikki.app.domain.vocab.Verdict
import com.onikki.app.domain.vocab.WordFamilies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** A round of confusing pairs or word families: questions, answers so far, the current verdict. */
data class WordRound<Q>(
    val questions: List<Q>,
    val index: Int = 0,
    val verdict: Verdict? = null,
    val typed: String = "",
    val picked: Int = -1,
    val right: Int = 0,
    val done: Boolean = false
) {
    val current: Q? get() = questions.getOrNull(index)
}

class WordPracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as OnIkkiApplication).database.practiceDao()
    private val random = Random(System.nanoTime())
    private val today = LocalDate.now()

    /** The user's own pairs. */
    val customPairs: StateFlow<List<ConfusablePair>> = dao.observePairs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Keys seen lately (oldest first), so a round starts with what hasn't been asked. */
    private val recent: StateFlow<Map<String, List<String>>> = dao.observeSince(today.minusDays(30))
        .map { list -> list.groupBy { it.mode }.mapValues { (_, l) -> l.map { it.itemKey } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _pairs = MutableStateFlow<WordRound<PairQuestion>?>(null)
    val pairs: StateFlow<WordRound<PairQuestion>?> = _pairs

    private val _families = MutableStateFlow<WordRound<FamilyQuestion>?>(null)
    val families: StateFlow<WordRound<FamilyQuestion>?> = _families

    private fun customQuestions() = customPairs.value.map {
        ConfusingPairs.custom(it.id, it.first, it.second, it.firstUz, it.secondUz, it.sentence, it.answer, it.why)
    }

    fun startPairs() {
        val pool = ConfusingPairs.BUILT_IN + customQuestions()
        _pairs.value = WordRound(ConfusingPairs.round(pool, recent.value[MODE_PAIR].orEmpty(), random))
    }

    fun pickPair(option: Int) {
        val r = _pairs.value ?: return
        val q = r.current ?: return
        if (r.verdict != null) return
        val v = if (option == q.answer) Verdict.CORRECT else Verdict.WRONG
        _pairs.value = r.copy(verdict = v, picked = option, right = r.right + if (v == Verdict.CORRECT) 1 else 0)
        log(MODE_PAIR, q.key, v)
    }

    fun nextPair() = _pairs.update { r -> r?.let { if (it.index + 1 >= it.questions.size) it.copy(done = true) else it.copy(index = it.index + 1, verdict = null, picked = -1) } }

    fun closePairs() { _pairs.value = null }

    fun addPair(first: String, second: String, firstUz: String, secondUz: String, sentence: String, answer: Int, why: String): Boolean {
        if (!ConfusingPairs.isValid(first, second, sentence, answer)) return false
        viewModelScope.launch {
            dao.insertPair(ConfusablePair(first = first.trim(), second = second.trim(), firstUz = firstUz.trim(), secondUz = secondUz.trim(), sentence = sentence.trim(), answer = answer, why = why.trim()))
        }
        return true
    }

    fun deletePair(p: ConfusablePair) { viewModelScope.launch { dao.deletePair(p) } }

    fun startFamilies() {
        _families.value = WordRound(WordFamilies.round(recent.value[MODE_FAMILY].orEmpty(), random))
    }

    fun typeFamily(text: String) = _families.update { r -> if (r == null || r.verdict != null) r else r.copy(typed = text.take(40)) }

    fun checkFamily() {
        val r = _families.value ?: return
        val q = r.current ?: return
        if (r.verdict != null || r.typed.isBlank()) return
        val v = WordFamilies.check(q, r.typed)
        _families.value = r.copy(verdict = v, right = r.right + if (v == Verdict.CORRECT) 1 else 0)
        log(MODE_FAMILY, q.key, v)
    }

    fun nextFamily() = _families.update { r -> r?.let { if (it.index + 1 >= it.questions.size) it.copy(done = true) else it.copy(index = it.index + 1, verdict = null, typed = "") } }

    fun closeFamilies() { _families.value = null }

    private fun log(mode: String, key: String, v: Verdict) {
        viewModelScope.launch {
            dao.log(PracticeLog(mode = mode, itemKey = key, score = if (v == Verdict.CORRECT) 100 else 0, at = System.currentTimeMillis(), day = LocalDate.now()))
        }
    }

    companion object {
        const val MODE_PAIR = "PAIR"
        const val MODE_FAMILY = "FAMILY"
    }
}
