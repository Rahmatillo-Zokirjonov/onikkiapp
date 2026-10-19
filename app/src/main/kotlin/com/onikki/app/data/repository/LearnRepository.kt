package com.onikki.app.data.repository

import android.content.Context
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.ReviewSource
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.data.db.entity.VocabReview
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Srs
import com.onikki.app.domain.vocab.VocabImport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Result of one answer: the word as saved after scheduling, and the XP earned. */
data class AnswerOutcome(val word: VocabWord, val xp: Int)

/** Every answer from lessons, reviews and the app blocker goes through here, so SRS and stats stay in one place. */
class LearnRepository(private val db: AppDatabase) {
    private val words = db.vocabWordDao()
    private val reviews = db.vocabReviewDao()

    suspend fun recordAnswer(
        wordId: Long,
        grade: ReviewGrade,
        source: ReviewSource,
        exercise: String,
        hardExercise: Boolean,
        /** False: log the answer (and its XP) without moving the word's schedule — see SessionScheduler. */
        schedule: Boolean = true,
        nowMillis: Long = System.currentTimeMillis()
    ): AnswerOutcome? {
        val word = words.findById(wordId) ?: return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val updated = if (schedule) Srs.review(word, grade, today, nowMillis) else word.copy(lastAskedAt = nowMillis)
        val xp = Srs.xpFor(grade, hardExercise)
        words.update(updated)
        reviews.insert(VocabReview(wordId = wordId, at = nowMillis, day = today, grade = grade, xp = xp, source = source, exercise = exercise))
        return AnswerOutcome(updated, xp)
    }
}

/**
 * Makes sure the decks exist and the starter "Lesson 1" deck is in once — on upgrades and on fresh installs alike.
 * Words added without a deck (older screens) are adopted by "Mening so'zlarim".
 */
object VocabSeeder {
    private const val PREFS = "vocab_seed"
    private const val KEY_LESSON_1 = "lesson1_v1"
    const val MY_WORDS = "Mening so'zlarim"

    suspend fun ensure(context: Context, db: AppDatabase) {
        val decks = db.vocabDeckDao()
        val wordDao = db.vocabWordDao()
        val all = decks.getAll()
        val myDeck = all.firstOrNull { it.name == MY_WORDS }?.id ?: decks.insert(VocabDeck(name = MY_WORDS, sortOrder = 1))
        wordDao.adoptOrphans(myDeck)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LESSON_1, false)) return
        val lessonDeck = all.firstOrNull { it.name == "Lesson 1" }?.id ?: decks.insert(VocabDeck(name = "Lesson 1", sortOrder = 0))
        val existing = wordDao.getAll().associateBy { AnswerCheck.normalize(it.english) }
        VocabImport.parse(VocabImport.LESSON_1).forEach { w ->
            val known = existing[AnswerCheck.normalize(w.english)]
            if (known == null) {
                wordDao.insert(VocabWord(english = w.english, uzbek = w.uzbek, example = w.example, pastForm = w.pastForm, level = "A2", deckId = lessonDeck))
            } else if (known.example == null || known.pastForm == null) {
                // Already in the user's list: just give it the example / past form.
                wordDao.update(known.copy(example = known.example ?: w.example, pastForm = known.pastForm ?: w.pastForm))
            }
        }
        prefs.edit().putBoolean(KEY_LESSON_1, true).apply()
    }
}
