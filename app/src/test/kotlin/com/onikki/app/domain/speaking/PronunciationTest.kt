package com.onikki.app.domain.speaking

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PronunciationTest {
    @Test
    fun perfectReadingIsHundred_caseAndPunctuationIgnored() {
        val r = Pronunciation.score("She went to school by bus.", listOf("she went to school by bus"))
        assertEquals(100, r.percent)
        assertTrue(r.words.all { it.heard })
        assertEquals("bus.", r.words.last().text) // the target's own spelling is shown
    }

    @Test
    fun missedAndExtraWordsDontShiftTheRest() {
        val r = Pronunciation.score("I have never been to London", listOf("I have been to the London"))
        assertEquals(listOf("never"), r.missed)
        assertEquals(83, r.percent)
    }

    @Test
    fun bestAlternativeWins_numbersAndContractionsMatch() {
        val r = Pronunciation.score("I'm twelve years old.", listOf("I am well years old", "I'm 12 years old"))
        assertEquals(100, r.percent)
        assertEquals("I'm 12 years old", r.heardText)
        assertEquals(0, Pronunciation.score("Hello there", emptyList()).percent)
        // A long word one letter off still counts; a short one doesn't.
        assertEquals(100, Pronunciation.score("beautiful", listOf("beautifull")).percent)
        assertEquals(0, Pronunciation.score("ship", listOf("sheep")).percent)
    }

    @Test
    fun roundsStartWithOwnWordsThenGrammar() {
        val words = (1..5).map { VocabWord(id = it.toLong(), english = "w$it", uzbek = "u", status = WordStatus.REVIEW, example = "This is my example number $it.") } +
            VocabWord(id = 9, english = "new", uzbek = "u", example = "A new word sentence here.")
        val round = SpeakingDeck.round("A1", words, emptyMap(), Random(1))
        assertEquals(SpeakingDeck.ROUND, round.size)
        assertEquals(SpeakingDeck.OWN_WORDS, round.count { it.key.startsWith("w-") })
        assertTrue(round.none { it.key == "w-9" }) // never-studied words aren't read yet
        assertTrue(round.drop(SpeakingDeck.OWN_WORDS).all { it.key.startsWith("g-a1-") && it.uzbek != null })
        // Sentences read well go to the back.
        val wellRead = round.associate { it.key to 95 }
        val next = SpeakingDeck.round("A1", words, wellRead, Random(2))
        assertTrue(next.count { it.key in wellRead } < SpeakingDeck.ROUND)
    }
}
