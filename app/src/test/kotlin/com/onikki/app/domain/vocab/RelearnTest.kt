package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class RelearnTest {
    private val pool = (1..10).map { VocabWord(id = it.toLong(), english = "word$it", uzbek = "soz$it", status = WordStatus.LEARNING, stage = 1) }
    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun aMissedWordComesBackTwoExercisesLater_inANewShape() {
        assertEquals(3, Relearn.position(current = 0, size = 10))
        assertEquals(10, Relearn.position(current = 9, size = 10))
        val w = pool[0]
        assertTrue(Relearn.retryExercise(w, 1, pool, Random(1)) is Exercise.Choose)
        assertTrue(Relearn.retryExercise(w, 2, pool, Random(1)) is Exercise.Listen)
        assertTrue(Relearn.retryExercise(w, 2, pool, Random(1), canListen = false) is Exercise.Choose)
        assertTrue(Relearn.retryExercise(w, 3, pool, Random(1)) is Exercise.TypeIn)
    }

    @Test
    fun queueKeepsRetryFlagsInPlace() {
        val items = pool.take(4).map { Exercise.Card(it) }
        val q = ExerciseQueue(items).insertRetry(3, Exercises.typeIn(pool[0])).insertRetry(1, Exercises.typeIn(pool[1]))
        assertEquals(6, q.items.size)
        assertEquals(setOf(1, 4), q.retries)
        assertTrue(q.items[4] is Exercise.TypeIn)
        assertFalse(q.isRetry(0))
    }

    @Test
    fun missPutsOnList_rightInTwoSessionsTakesOff() {
        val w = pool[0]
        val missed = Relearn.track(w, ReviewGrade.AGAIN, 1000)
        assertEquals(1000L, missed.unknownSince)
        val once = Relearn.track(missed, ReviewGrade.EASY, 2000)
        assertEquals(1000L, once.unknownSince)
        assertEquals(1, once.relearnHits)
        // A miss in between starts the count again.
        val again = Relearn.track(once, ReviewGrade.AGAIN, 3000)
        assertEquals(0, again.relearnHits)
        assertEquals(1000L, again.unknownSince)
        val cleared = Relearn.track(Relearn.track(again, ReviewGrade.HARD, 4000), ReviewGrade.EASY, 5000)
        assertNull(cleared.unknownSince)
        // A word that was never missed is left alone.
        assertEquals(w, Relearn.track(w, ReviewGrade.EASY, 6000))
    }

    @Test
    fun relearnSessionShowsEveryWordAgainThenAsks() {
        val words = pool.take(5)
        val ex = Relearn.session(words, pool, Random(3))
        assertTrue(ex.size <= Relearn.MAX_EXERCISES)
        assertEquals(words.map { it.id }.toSet(), ex.filterIsInstance<Exercise.Card>().map { it.word.id }.toSet())
        // Each card is followed (within its pair) by a question on that word.
        val firstCard = ex.indexOfFirst { it is Exercise.Card }
        assertEquals(0, firstCard)
        assertTrue(ex.any { it is Exercise.Match })
    }

    @Test
    fun sortingKnownSkipsCards_unknownGoesOnTheList() {
        val fresh = VocabWord(id = 50, english = "decide", uzbek = "qaror qilmoq")
        val known = Relearn.markKnown(fresh, today, 1)
        assertEquals(WordStatus.REVIEW, known.status)
        assertEquals(today.plusDays(3), known.dueDate)
        assertTrue(known.stage >= 2)
        val unknown = Relearn.markUnknown(fresh, 7)
        assertEquals(WordStatus.NEW, unknown.status)
        assertEquals(7L, unknown.unknownSince)
        assertEquals(listOf(unknown), Relearn.unknown(listOf(fresh, unknown)))
    }

    @Test
    fun leechClimbsSlower() {
        val normal = pool[0].copy(status = WordStatus.REVIEW, intervalDays = 7, stage = 2)
        val leech = normal.copy(lapses = Relearn.LEECH_LAPSES)
        val n = Srs.review(normal, ReviewGrade.EASY, today, 0).intervalDays
        val l = Srs.review(leech, ReviewGrade.EASY, today, 0).intervalDays
        assertTrue("$l < $n", l < n)
        assertTrue(l > 7)
    }
}
