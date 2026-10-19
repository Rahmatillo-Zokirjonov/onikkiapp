package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExercisesTest {
    private val lesson = VocabImport.parse(VocabImport.LESSON_1).mapIndexed { i, w ->
        VocabWord(id = i + 1L, english = w.english, uzbek = w.uzbek, example = w.example, pastForm = w.pastForm)
    }
    private fun word(en: String) = lesson.first { it.english == en }

    @Test
    fun blanksFindPastAndRegularForms() {
        assertEquals(Blank("I ", " to study English.", "decided"), Exercises.blank("I decided to study English.", "decide", "decided"))
        assertEquals("forgot", Exercises.blank("I forgot my phone at home.", "forget", "forgot")!!.answer)
        assertEquals("arrived", Exercises.blank("The bus arrived at 8 am.", "arrive", null)!!.answer)
        assertEquals("spent", Exercises.blank("I spent two hours on homework.", "spend", "spent")!!.answer)
        assertNull(Exercises.blank("I like tea.", "coffee", null))
        assertNull(Exercises.blank("Look for it.", "look for", null))
        // Every Lesson 1 sentence contains its word.
        assertTrue(lesson.all { Exercises.blank(it.example!!, it.english, it.pastForm) != null })
    }

    @Test
    fun choiceOptionsAreFourDistinctWithTheAnswer() {
        val ex = Exercises.choose(word("improve"), lesson, Random(1))
        assertEquals(4, ex.options.size)
        assertEquals(4, ex.options.map { it.lowercase() }.toSet().size)
        assertEquals("improve", ex.options[ex.answer])
        assertEquals("yaxshilamoq", ex.prompt)
        val listen = Exercises.listen(word("spend"), lesson, Random(2))
        assertEquals("sarflamoq", listen.options[listen.answer]) // first meaning only
    }

    @Test
    fun matchTakesFiveDistinctPairs() {
        val m = Exercises.match(lesson.take(7), Random(3))!!
        assertEquals(5, m.words.size)
        assertEquals(m.left.toSet(), m.right.toSet())
        assertNull(Exercises.match(lesson.take(2), Random(3)))
    }

    @Test
    fun buildBlocksAndCheck() {
        val b = Exercises.build(word("decide"), lesson, Random(4))
        assertEquals(listOf("I", "decided", "to", "study", "English"), b.tokens)
        assertTrue(b.bank.containsAll(b.tokens))
        assertEquals(b.tokens.size + 2, b.bank.size)
        assertEquals(Verdict.CORRECT, Exercises.checkBuild(listOf("i", "Decided", "to", "study", "english"), b.tokens))
        assertEquals(Verdict.WRONG, Exercises.checkBuild(listOf("I", "to", "decided", "study", "English"), b.tokens))
    }

    @Test
    fun stageDecidesTheExercise() {
        val w = word("remember")
        assertTrue(Exercises.forWord(w, 0, lesson, Random(5)) is Exercise.Card)
        assertTrue(Exercises.forWord(w, 1, lesson, Random(5), canListen = false) is Exercise.Choose)
        assertTrue(Exercises.forWord(w, 2, lesson, Random(5)) is Exercise.TypeIn)
        val late = Exercises.forWord(w, 3, lesson, Random(5))
        assertTrue(late is Exercise.Cloze || late is Exercise.Build)
        // No example sentence → typing instead.
        assertTrue(Exercises.forWord(w.copy(example = null), 3, lesson, Random(5)) is Exercise.TypeIn)
        assertTrue(ExerciseType.TYPE.hard && !ExerciseType.CHOOSE.hard)
    }
}
