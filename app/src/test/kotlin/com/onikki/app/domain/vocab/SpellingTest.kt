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

class SpellingTest {
    private fun render(cells: List<DiffCell>) = cells.joinToString("") {
        when (it.kind) {
            CellKind.OK -> it.char.toString()
            CellKind.WRONG -> "[${it.char}]"
            CellKind.MISSING -> "_"
            CellKind.EXTRA -> "(${it.char})"
        }
    }

    @Test
    fun strict_caseAndSpacesIgnored() {
        assertTrue(Spelling.isRight("Weather", listOf("weather")))
        assertTrue(Spelling.isRight(" ice cream ", listOf("icecream")))
        assertFalse(Spelling.isRight("wether", listOf("weather"))) // no "almost" in spelling mode
        assertFalse(Spelling.isRight("", listOf("weather")))
        assertTrue(Spelling.isRight("were", listOf("was", "were")))
    }

    @Test
    fun diffMarksRightWrongMissingAndExtraLetters() {
        assertEquals("price", render(Spelling.diff("price", "price")))
        assertEquals("pri[s]e", render(Spelling.diff("prise", "price")))
        assertEquals("fut_re", render(Spelling.diff("futre", "future")))
        // A doubled letter: one of the two is extra (either is a right reading).
        val doubled = Spelling.diff("weaather", "weather")
        assertEquals(1, doubled.count { it.kind == CellKind.EXTRA && it.char == 'a' })
        assertEquals(7, doubled.count { it.kind == CellKind.OK })
        // Every letter of the target has a cell (right, wrong or "_"), in order.
        val d = Spelling.diff("dengre", "dangerous")
        assertEquals("dangerous".length, d.count { it.kind != CellKind.EXTRA })
        assertTrue(d.any { it.kind == CellKind.MISSING })
        assertEquals("experience", Spelling.closest("expriense", listOf("experience", "exercise")))
    }

    @Test
    fun sentenceDiffWordByWord() {
        val (ok, extraOk) = SentenceDiff.compare("i want a good job in the future", "I want a good job in the future.")
        assertEquals(0, extraOk)
        assertEquals(Verdict.CORRECT, SentenceDiff.verdict(ok, extraOk))

        val (m, extra) = SentenceDiff.compare("I want a god job in future", "I want a good job in the future.")
        assertEquals(CellKind.WRONG, m.first { it.word == "good" }.kind)
        assertEquals("god", m.first { it.word == "good" }.typed)
        assertEquals(CellKind.MISSING, m.first { it.word == "the" }.kind)
        assertEquals(Verdict.WRONG, SentenceDiff.verdict(m, extra))

        val (one, e1) = SentenceDiff.compare("The weather is cold", "The weather is cold today.")
        assertEquals(Verdict.ALMOST, SentenceDiff.verdict(one, e1))
        val (more, e2) = SentenceDiff.compare("The weather is very cold today", "The weather is cold today.")
        assertEquals(1, e2)
        assertEquals(Verdict.ALMOST, SentenceDiff.verdict(more, e2))
    }

    @Test
    fun hardWordsAreTwoMissesInSevenDays() {
        val today = LocalDate.of(2026, 10, 26)
        fun r(id: Long, ex: String, g: ReviewGrade, daysAgo: Long) = ReviewRow(id, ex, g, today.minusDays(daysAgo))
        val rows = listOf(
            r(1, "SPELL", ReviewGrade.AGAIN, 0), r(1, "DICTATION", ReviewGrade.AGAIN, 3), // 2 → hard
            r(2, "SPELL", ReviewGrade.AGAIN, 1), r(2, "SPELL", ReviewGrade.EASY, 0), // 1 → not
            r(3, "SPELL", ReviewGrade.AGAIN, 2), r(3, "SPELL", ReviewGrade.AGAIN, 9), // one is too old
            r(4, "CHOOSE", ReviewGrade.AGAIN, 1), r(4, "CHOOSE", ReviewGrade.AGAIN, 2), // not a writing mode
            r(5, "VERB_PAST", ReviewGrade.AGAIN, 0), r(5, "VERB_PAST", ReviewGrade.AGAIN, 1), r(5, "SPELL", ReviewGrade.AGAIN, 6) // 3 → first
        )
        assertEquals(listOf(5L, 1L), HardSpelling.pick(rows, today))
    }

    @Test
    fun modeStatsThisWeekVsLast() {
        val today = LocalDate.of(2026, 10, 26)
        val rows = List(4) { ReviewRow(1, "SPELL", if (it == 0) ReviewGrade.AGAIN else ReviewGrade.EASY, today) } +
            List(2) { ReviewRow(1, "SPELL", ReviewGrade.AGAIN, today.minusDays(8)) } +
            listOf(ReviewRow(2, "DICTATION", ReviewGrade.HARD, today.minusDays(1)))
        val stats = ModeStats.build(rows, today)
        val spell = stats.first { it.mode == "SPELL" }
        assertEquals("Yozilish", spell.label)
        assertEquals(75, spell.accuracy)
        assertEquals(0, spell.lastWeek)
        assertNull(stats.first { it.mode == "DICTATION" }.lastWeek)
        assertEquals(80 to 0, ModeStats.weekly(rows, today))
    }

    @Test
    fun spellingAMissShortensTheInterval() {
        val known = VocabWord(id = 1, english = "weather", uzbek = "ob-havo", status = WordStatus.REVIEW, intervalDays = 14, stage = 3)
        val after = Srs.review(known, Srs.gradeFor(Verdict.WRONG, false), LocalDate.of(2026, 10, 26), 0)
        assertEquals(1, after.intervalDays)
        assertEquals(LocalDate.of(2026, 10, 27), after.dueDate)
    }

    @Test
    fun newExercises() {
        val w = VocabWord(id = 1, english = "dangerous", uzbek = "xavfli, qo'rqinchli", example = "This road is dangerous at night.")
        val s = Exercises.spell(w)
        assertEquals("xavfli", s.prompt)
        assertEquals("This road is ____ at night.", s.clue)
        assertTrue(s.type.hard)

        val go = VocabWord(id = 2, english = "be", uzbek = "bo'lmoq", pastForm = "was/were", participle = "been")
        assertEquals(listOf("was", "were"), Exercises.verbPast(go, false).answers)
        val back = Exercises.verbPast(go, true)
        assertEquals("was", back.prompt)
        assertEquals(listOf("be"), back.answers)

        assertEquals(DictationMode.SENTENCE, Exercises.dictation(w, DictationMode.SENTENCE).mode)
        assertEquals(DictationMode.WORD, Exercises.dictation(go, DictationMode.SENTENCE).mode) // no example → the word
    }

    @Test
    fun blockerTypedSlotsUseTheWritingModes() {
        val words = (1..6).map {
            VocabWord(id = it.toLong(), english = "word$it", uzbek = "soz$it", status = WordStatus.REVIEW, dueDate = LocalDate.of(2026, 10, 20), stage = 2,
                pastForm = if (it % 2 == 0) "past$it" else null, participle = if (it % 2 == 0) "pp$it" else null)
        }
        val kinds = mutableSetOf<ExerciseType>()
        repeat(40) { seed ->
            val c = BlockerChallenge(words, LocalDate.of(2026, 10, 26), 3, com.onikki.app.data.local.QuestionType.TYPE, Random(seed))
            repeat(6) { c.next()?.let { e -> kinds += e.type } }
        }
        assertTrue(kinds.containsAll(listOf(ExerciseType.TYPE, ExerciseType.SPELL, ExerciseType.DICTATION, ExerciseType.VERB_PAST)))
        assertTrue(kinds.all { it.hard })
    }
}
