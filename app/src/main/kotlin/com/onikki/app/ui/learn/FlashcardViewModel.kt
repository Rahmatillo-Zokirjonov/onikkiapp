package com.onikki.app.ui.learn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.ReviewSource
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.data.repository.LearnRepository
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.ExerciseType
import com.onikki.app.domain.vocab.FlashCard
import com.onikki.app.domain.vocab.FlashDirection
import com.onikki.app.domain.vocab.FlashGrade
import com.onikki.app.domain.vocab.FlashSession
import com.onikki.app.domain.vocab.Flashcards
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.Srs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Which dictionary words a flashcard run takes. */
enum class FlashSource(val label: String) { DUE("Takrorlash vaqti kelgan"), WEAK("Qiyin so'zlar"), ALL("Barcha so'zlar") }

/** The chain's flashcard sets. */
enum class ChainFlash { TODAY, REVIEW, CHAIN_ORDER, CHAIN_MIXED, WEAK }

data class FlashSetup(
    val decks: List<VocabDeck> = emptyList(),
    val deckId: Long? = null,
    val counts: Map<FlashSource, Int> = emptyMap(),
    val boxes: Map<String, Int> = emptyMap()
)

/** Flashcards for the word chain and for the dictionary; answers move Leitner boxes and the review schedule. */
class FlashcardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val prefs = LearnPrefs(application)
    private val repository = LearnRepository(db)

    private val _session = MutableStateFlow<FlashSession?>(null)
    val session: StateFlow<FlashSession?> = _session

    private val _direction = MutableStateFlow(FlashDirection.EN_UZ)
    val direction: StateFlow<FlashDirection> = _direction

    private val _setup = MutableStateFlow(FlashSetup())
    val setup: StateFlow<FlashSetup> = _setup

    private val _boxes = MutableStateFlow(prefs.flashBoxes())
    val boxes: StateFlow<Map<String, Int>> = _boxes

    /** The cards of the last run, for "yana bir bor". */
    private var lastCards: List<FlashCard> = emptyList()
    /** Words already put into the review schedule in this run (once per word, like lessons). */
    private val scheduled = HashSet<String>()
    /** The chain set being run (to mark the day's review done when it ends). */
    private var running: ChainFlash? = null

    private val _reviewedToday = MutableStateFlow(prefs.chainReviewedOn() == LocalDate.now())
    val reviewedToday: StateFlow<Boolean> = _reviewedToday

    fun setDirection(d: FlashDirection) { _direction.value = d }

    // ------------------------------------------------------------ the chain

    fun chainCount(kind: ChainFlash, today: List<ImportedWord>, learned: List<ImportedWord>, review: List<ImportedWord>): Int = when (kind) {
        ChainFlash.TODAY -> today.size
        ChainFlash.REVIEW -> review.size
        ChainFlash.CHAIN_ORDER -> learned.size
        ChainFlash.CHAIN_MIXED -> minOf(learned.size, Flashcards.SESSION)
        ChainFlash.WEAK -> Flashcards.weak(learned.map(::card), _boxes.value, chainWeakWords()).size
    }

    fun startChain(kind: ChainFlash, today: List<ImportedWord>, learned: List<ImportedWord>, review: List<ImportedWord>) {
        val all = learned.map(::card)
        val cards = when (kind) {
            ChainFlash.TODAY -> today.map(::card)
            ChainFlash.REVIEW -> review.map(::card)
            ChainFlash.CHAIN_ORDER -> all
            ChainFlash.CHAIN_MIXED -> Flashcards.weakestFirst(all, _boxes.value).take(Flashcards.SESSION)
            ChainFlash.WEAK -> Flashcards.weakestFirst(Flashcards.weak(all, _boxes.value, chainWeakWords()), _boxes.value).take(Flashcards.SESSION)
        }
        if (cards.isEmpty()) return
        begin(cards, shuffle = kind == ChainFlash.TODAY || kind == ChainFlash.REVIEW)
        running = kind
    }

    /** Any chain words as cards (the learning path's first step). */
    fun startWords(words: List<ImportedWord>) {
        if (words.isEmpty()) return
        begin(words.map(::card), shuffle = true)
    }

    /** Words the sequence practice found weak (the word after a weak link). */
    private fun chainWeakWords(): Set<String> = prefs.chainWeak().keys.map { it.substringAfter('>') }.toSet()

    private fun card(w: ImportedWord) = FlashCard(w.english, w.uzbek, w.example)

    // ------------------------------------------------------------ the dictionary

    fun loadSetup(deckId: Long? = _setup.value.deckId) {
        viewModelScope.launch {
            val decks = db.vocabDeckDao().getAll()
            val words = words(deckId)
            val boxes = prefs.flashBoxes()
            _boxes.value = boxes
            _setup.value = FlashSetup(
                decks = decks,
                deckId = deckId,
                counts = FlashSource.entries.associateWith { pick(it, words, boxes).size },
                boxes = boxes
            )
        }
    }

    fun startVocab(source: FlashSource) {
        viewModelScope.launch {
            val cards = pick(source, words(_setup.value.deckId), prefs.flashBoxes())
            if (cards.isNotEmpty()) begin(cards, shuffle = false)
        }
    }

    private suspend fun words(deckId: Long?): List<VocabWord> =
        db.vocabWordDao().getAll().filter { deckId == null || it.deckId == deckId }

    private fun pick(source: FlashSource, words: List<VocabWord>, boxes: Map<String, Int>): List<FlashCard> {
        val today = LocalDate.now()
        val cards = when (source) {
            FlashSource.DUE -> words.filter { Srs.isDue(it, today) }.sortedBy { it.dueDate }
            FlashSource.WEAK -> words.filter {
                it.status != WordStatus.NEW && (it.wrongCount > it.correctCount || it.lapses > 0 || (boxes[AnswerCheck.normalize(it.english)] ?: 0) in 1..2)
            }
            FlashSource.ALL -> words
        }.map { FlashCard(it.english, it.uzbek.trim(), it.example, it.id) }
        return (if (source == FlashSource.DUE) cards else Flashcards.weakestFirst(cards, boxes)).take(Flashcards.SESSION)
    }

    // ------------------------------------------------------------ the run

    private fun begin(cards: List<FlashCard>, shuffle: Boolean) {
        lastCards = cards
        scheduled.clear()
        running = null
        _session.value = Flashcards.start(if (shuffle) cards.shuffled() else cards, _direction.value)
        // Chain words are in the "Study Skills" deck too: link them, so their answers count there as well.
        if (cards.any { it.wordId == null }) viewModelScope.launch {
            val ids = db.vocabWordDao().getAll().associate { AnswerCheck.normalize(it.english) to it.id }
            val link = { c: FlashCard -> if (c.wordId != null) c else c.copy(wordId = ids[c.key]) }
            lastCards = lastCards.map(link)
            _session.update { s -> s?.copy(queue = s.queue.map(link), struggled = s.struggled.map(link)) }
        }
    }

    fun flip() = _session.update { s -> s?.let(Flashcards::flip) }

    fun grade(g: FlashGrade) {
        val s = _session.value ?: return
        val c = s.current ?: return
        val next = Flashcards.grade(s, g)
        _session.value = next
        if (next.finished && running == ChainFlash.REVIEW) {
            prefs.setChainReviewedOn(LocalDate.now())
            _reviewedToday.value = true
        }
        val boxes = _boxes.value.let { it + (c.key to Flashcards.nextBox(it[c.key] ?: 0, g)) }
        _boxes.value = boxes
        prefs.setFlashBoxes(boxes)
        val id = c.wordId ?: return
        if (!scheduled.add(c.key)) return
        viewModelScope.launch {
            repository.recordAnswer(
                wordId = id,
                grade = when (g) { FlashGrade.AGAIN -> ReviewGrade.AGAIN; FlashGrade.HARD -> ReviewGrade.HARD; FlashGrade.GOOD -> ReviewGrade.EASY },
                source = ReviewSource.REVIEW,
                exercise = ExerciseType.CARD.name,
                hardExercise = false
            )
        }
    }

    /** Again: the same cards, or only the ones that were hard. */
    fun again(onlyStruggled: Boolean) {
        val cards = if (onlyStruggled) _session.value?.struggled.orEmpty() else lastCards
        if (cards.isEmpty()) return
        val keep = lastCards
        begin(cards, shuffle = true)
        lastCards = keep
    }

    fun close() {
        _session.value = null
        loadSetup()
    }
}
