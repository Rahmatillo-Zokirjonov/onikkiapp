package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.data.db.entity.VocabLessonResult
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LearningPathTest {
    private val lesson1 = VocabDeck(id = 2, name = "Lesson 1", sortOrder = 0)
    private val mine = VocabDeck(id = 1, name = "Mening so'zlarim", sortOrder = 1)
    private val starter = VocabImport.parse(VocabImport.LESSON_1).mapIndexed { i, w ->
        VocabWord(id = 100L + i, english = w.english, uzbek = w.uzbek, example = w.example, pastForm = w.pastForm, deckId = 2)
    }
    private val own = (1..5).map { VocabWord(id = it.toLong(), english = "w$it", uzbek = "s$it", deckId = 1, status = WordStatus.REVIEW, stage = 2) }

    private fun result(deck: Long, i: Int) = VocabLessonResult(deck, i, 0, 90, 100, 200)

    @Test
    fun twentyWordsMakeLessonsOfEightEightFour_andSmallTailsMerge() {
        assertEquals(listOf(8, 8, 4), LearningPath.lessonsOf(starter).map { it.size })
        assertEquals(listOf(8, 10), LearningPath.lessonsOf(starter.take(18)).map { it.size })
        // Levels go first: an A1 word added last still lands in lesson 1.
        val withLevel = starter.take(9) + starter[9].copy(id = 999, level = "A1")
        assertEquals(999L, LearningPath.lessonsOf(withLevel).first().first().id)
    }

    @Test
    fun lessonsUnlockOneAfterAnotherAcrossDecks() {
        val start = LearningPath.build(listOf(mine, lesson1), starter + own, emptyList())
        assertEquals(listOf("Lesson 1", "Mening so'zlarim"), start.map { it.deck.name })
        assertEquals(listOf(LessonState.AVAILABLE, LessonState.LOCKED, LessonState.LOCKED), start[0].lessons.map { it.state })
        // Each deck has its own next lesson: own words don't wait for Lesson 1.
        assertEquals(LessonState.AVAILABLE, start[1].lessons[0].state)

        // After its lessons the starter words are studied.
        val studied = starter.map { it.copy(status = WordStatus.REVIEW, stage = 2) }
        val later = LearningPath.build(listOf(mine, lesson1), studied + own, listOf(result(2, 0), result(2, 1), result(2, 2)))
        assertEquals(3, later[0].done)
        assertEquals(LessonState.AVAILABLE, later[1].lessons[0].state)
        assertEquals(1L, LearningPath.nextLesson(later)!!.deckId)
        assertNull(LearningPath.nextLesson(LearningPath.build(listOf(mine), own, listOf(result(1, 0)))))
    }

    @Test
    fun aDoneLessonWithANewWordInItOpensAgain_andNewWordsAreListed() {
        val studied = starter.take(8).map { it.copy(status = WordStatus.REVIEW, stage = 2) }
        val done = LearningPath.build(listOf(lesson1), studied, listOf(result(2, 0)))
        assertEquals(LessonState.DONE, done[0].lessons[0].state)
        // A new word lands in that lesson (a small tail merges into it).
        val withNew = studied + starter[8]
        val reopened = LearningPath.build(listOf(lesson1), withNew, listOf(result(2, 0)))
        assertEquals(LessonState.AVAILABLE, reopened[0].lessons[0].state)
        assertEquals(listOf(starter[8].id), LearningPath.untouched(reopened).map { it.id })
    }

    @Test
    fun aLessonIntroducesNewWordsWithCardsAndStaysWithinFifteen() {
        val words = starter.take(8)
        val plan = LessonPlanner.lesson(words, starter, Random(7))
        assertTrue(plan.size in 10..15)
        // Every new word is introduced by a card before it is asked.
        words.forEach { w ->
            val firstSeen = plan.indexOfFirst { w.id in it.wordIds }
            assertTrue(plan[firstSeen] is Exercise.Card)
        }
        assertTrue(plan.any { it is Exercise.Match })
        assertTrue(plan.any { it.type.hard })
        // Every word gets graded at least once (cards don't grade).
        val graded = plan.filter { it !is Exercise.Card }.flatMap { it.wordIds }.toSet()
        assertEquals(words.map { it.id }.toSet(), graded)
    }

    @Test
    fun reviewAsksEachDueWordOnceAtItsLevel() {
        val due = starter.take(6).map { it.copy(status = WordStatus.REVIEW, stage = 2) }
        val plan = LessonPlanner.review(due, starter, Random(3))
        assertEquals(7, plan.size) // 6 words + one match
        assertTrue(plan.none { it is Exercise.Card })
    }

    @Test
    fun srsMovesOncePerSession_butALaterMissStillCounts() {
        val s = SessionScheduler()
        assertEquals(ReviewGrade.EASY, s.gradeToApply(1, ReviewGrade.EASY))
        assertNull(s.gradeToApply(1, ReviewGrade.EASY))
        assertEquals(ReviewGrade.AGAIN, s.gradeToApply(1, ReviewGrade.AGAIN))
        assertNull(s.gradeToApply(1, ReviewGrade.AGAIN))
        assertEquals(ReviewGrade.HARD, s.gradeToApply(2, ReviewGrade.HARD))
    }
}
