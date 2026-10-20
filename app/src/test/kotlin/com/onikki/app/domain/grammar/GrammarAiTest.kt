package com.onikki.app.domain.grammar

import com.onikki.app.domain.ai.AiPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarAiTest {
    private val reply = """
        {"exercises": [
          {"kind":"test","prompt":"She ____ to work every day.","instruction":"","options":["go","goes","going","gone"],"answer_index":1,"answers":[],"why":"she → goes"},
          {"kind":"gap","prompt":"They ___ (live) in Tashkent.","instruction":"live","options":[],"answer_index":-1,"answers":["live"],"why":"they bilan -s yo'q"},
          {"kind":"fix","prompt":"He don’t like coffee.","instruction":"","options":[],"answer_index":-1,"answers":["He doesn’t like coffee.","He does not like coffee."],"why":"he → doesn't"},
          {"kind":"transform","prompt":"I play chess.","instruction":"Inkor qiling","options":[],"answer_index":-1,"answers":["I don't play chess.","I do not play chess."],"why":"don't + fe'l"},
          {"kind":"test","prompt":"No gap here.","instruction":"","options":["a","b","c","d"],"answer_index":0,"answers":[],"why":""},
          {"kind":"fix","prompt":"He likes coffee.","instruction":"","options":[],"answer_index":-1,"answers":["He likes coffee."],"why":""},
          {"kind":"test","prompt":"I ___ happy.","instruction":"","options":["am","am","is","are"],"answer_index":0,"answers":[],"why":""},
          {"kind":"poem","prompt":"x","instruction":"","options":[],"answer_index":0,"answers":[],"why":""}
        ]}
    """.trimIndent()

    @Test
    fun parsesNormalisesAndKeepsOnlyValidOnes() {
        val parsed = AiPlans.parseGrammar(reply, "a1-present-simple")
        assertEquals(7, parsed.size) // the unknown kind is dropped while parsing
        assertEquals("She ___ to work every day.", parsed[0].prompt) // long gap → ___
        assertEquals("He doesn't like coffee.", parsed[2].answers[0]) // curly apostrophe → '
        val valid = parsed.filter(GrammarBank::valid)
        assertEquals(listOf(GrammarKind.CHOICE, GrammarKind.GAP, GrammarKind.FIX, GrammarKind.TRANSFORM), valid.map { it.kind })
        assertTrue(AiPlans.parseGrammar("nonsense", "x").isEmpty())
    }

    @Test
    fun cacheRoundTrip() {
        val ex = GrammarBank.builtIn("b1-passive").first { it.kind == GrammarKind.TRANSFORM }
        val back = GrammarBank.decode(ex.topicId, GrammarBank.encode(ex))
        assertEquals(ex, back)
        assertNull(GrammarBank.decode("x", "{bad"))
        assertFalse(GrammarBank.valid(ex.copy(answers = emptyList())))
    }

    @Test
    fun everyBuiltInExerciseIsValid() {
        Grammar.TOPICS.flatMap { GrammarBank.builtIn(it.id) }.forEach { assertTrue(it.prompt, GrammarBank.valid(it)) }
    }
}
