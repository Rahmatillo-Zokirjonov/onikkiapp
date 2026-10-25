package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IrregularVerbsTest {
    private val verbs = IrregularVerbs.ALL.flatMap { (level, text) ->
        VocabImport.parse(text).map { level to it }
    }.mapIndexed { i, (level, v) ->
        VocabWord(id = i + 1L, english = v.english, uzbek = v.uzbek, example = v.example, pastForm = v.pastForm, participle = v.participle, level = level)
    }

    @Test
    fun allVerbsWithBothForms() {
        assertEquals(101, verbs.size)
        assertEquals(101, verbs.map { it.english }.toSet().size)
        assertTrue(verbs.all(Exercises::isIrregular))
        val be = verbs.first { it.english == "be" }
        assertEquals("was/were", be.pastForm)
        assertEquals("been", be.participle)
    }

    @Test
    fun importReadsBothForms_andOldOneFormStillWorks() {
        val go = VocabImport.parseLine("go (went, gone) | bormoq | She went home.")!!
        assertEquals("go", go.english)
        assertEquals("went", go.pastForm)
        assertEquals("gone", go.participle)
        val decide = VocabImport.parseLine("decide (decided) | qaror qilmoq")!!
        assertEquals("decided", decide.pastForm)
        assertNull(decide.participle)
    }

    @Test
    fun formChoiceOffersLookAlikes() {
        val go = verbs.first { it.english == "go" }
        repeat(20) { seed ->
            val ex = Exercises.formChoice(go, verbs, Random(seed), VerbForm.PAST)
            assertEquals("went", ex.options[ex.answer])
            assertEquals(4, ex.options.size)
            assertEquals(4, ex.options.map { it.lowercase() }.toSet().size)
            // The other form and the made-up regular one are always there to trap guesses.
            assertTrue("gone" in ex.options && "goed" in ex.options)
        }
        assertEquals("maked", Exercises.regularized("make"))
        assertEquals("tried", Exercises.regularized("try"))
    }

    @Test
    fun formsAreCheckedTogether() {
        val be = Exercises.forms(verbs.first { it.english == "be" })
        assertEquals(Verdict.CORRECT to Verdict.CORRECT, Exercises.checkForms("were", "been", be))
        // Short words must be exact; a long one may be one letter off.
        assertEquals(Verdict.WRONG, Exercises.checkForms("was", "ben", be).second)
        val forget = Exercises.forms(verbs.first { it.english == "forget" })
        val (a, b) = Exercises.checkForms("forgot", "forgoten", forget)
        assertEquals(Verdict.ALMOST, Exercises.combine(a, b))
        assertEquals(Verdict.WRONG, Exercises.combine(Verdict.CORRECT, Verdict.WRONG))
    }

    @Test
    fun verbsBlankTheirParticipleToo() {
        val blank = Exercises.blank("I have forgotten his name.", "forget", "forgot", "forgotten")
        assertEquals("forgotten", blank?.answer)
    }

    @Test
    fun drillShapesAndDictation() {
        val drill = Exercises.verbDrill(verbs.take(3).mapIndexed { i, v -> v.copy(stage = i) }, verbs, Random(1))
        assertTrue(drill[0] is Exercise.FormChoice && drill[1] is Exercise.FormChoice && drill[2] is Exercise.Forms)
        val d = Exercises.dictate(verbs[0])
        assertEquals(listOf("be"), d.answers)
        assertTrue(d.type.hard)
        assertFalse(Exercises.isIrregular(VocabWord(english = "apple", uzbek = "olma")))
    }
}
