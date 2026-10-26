package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.domain.speaking.Pronunciation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class WordOfHourTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val words = listOf(
        VocabWord(id = 1, english = "apple", uzbek = "olma"),
        VocabWord(id = 2, english = "weather", uzbek = "ob-havo", status = WordStatus.REVIEW, dueDate = today.plusDays(5), stage = 2),
        VocabWord(id = 3, english = "price", uzbek = "narx", status = WordStatus.REVIEW, dueDate = today, stage = 2),
        VocabWord(id = 4, english = "future", uzbek = "kelajak", status = WordStatus.LEARNING, dueDate = today.plusDays(1), unknownSince = 1L, stage = 1)
    )

    @Test
    fun urgentWordsFirst_hardOnTop() {
        // Due (price), on Bilmaganlarim (future), misspelt lately (weather) — hard ones first.
        assertEquals(listOf(2L, 3L, 4L), WordOfHour.candidates(words, today, hardIds = listOf(2L)).map { it.id })
        // Nothing urgent → studied words; nothing studied → any.
        val calm = words.map { it.copy(dueDate = today.plusDays(9), unknownSince = null) }
        assertEquals(listOf(2L, 3L, 4L), WordOfHour.candidates(calm, today, emptyList()).map { it.id })
        assertEquals(listOf(1L), WordOfHour.candidates(listOf(words[0]), today, emptyList()).map { it.id })
        assertNull(WordOfHour.pick(emptyList(), today, emptyList(), 100))
    }

    @Test
    fun aNewWordEveryHour_andOnSkip() {
        val a = WordOfHour.pick(words, today, emptyList(), epochHour = 500)
        val b = WordOfHour.pick(words, today, emptyList(), epochHour = 501)
        val c = WordOfHour.pick(words, today, emptyList(), epochHour = 500, skip = 1)
        assertFalse(a == b)
        assertEquals(b, c)
        assertEquals(a, WordOfHour.pick(words, today, emptyList(), epochHour = 500)) // stable within the hour
    }

    @Test
    fun practiceOfTheWord() {
        val verb = VocabWord(id = 9, english = "break", uzbek = "sindirmoq", pastForm = "broke", participle = "broken", status = WordStatus.REVIEW)
        val ex = WordOfHour.practice(verb, words + verb, Random(1))
        assertEquals(listOf(ExerciseType.LISTEN, ExerciseType.SPELL, ExerciseType.DICTATION, ExerciseType.VERB_PAST), ex.map { it.type })
        assertTrue(WordOfHour.practice(words[0], words, Random(1)).first() is Exercise.Card) // a new word starts with its card
        assertTrue(WordOfHour.practice(words[1], words, Random(1), canListen = false).none { it.type == ExerciseType.DICTATION })
    }

    @Test
    fun wordPronunciationMatch() {
        assertTrue(Pronunciation.saidRight("weather", listOf("whether", "weather")))
        assertTrue(Pronunciation.saidRight("ice cream", listOf("ice cream")))
        assertFalse(Pronunciation.saidRight("weather", listOf("whether")))
        assertFalse(Pronunciation.saidRight("weather", emptyList()))
    }
}
