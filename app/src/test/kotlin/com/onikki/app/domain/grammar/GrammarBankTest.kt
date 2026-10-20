package com.onikki.app.domain.grammar

import com.onikki.app.domain.vocab.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GrammarBankTest {
    @Test
    fun everyTopicHasTwoOfEachKind_andAllLinesParse() {
        assertEquals(Grammar.TOPICS.map { it.id }.toSet(), (GrammarDrillsA.ALL.keys + GrammarDrillsB.ALL.keys))
        Grammar.TOPICS.forEach { t ->
            val raw = (GrammarDrillsA.ALL + GrammarDrillsB.ALL).getValue(t.id).lines().count { it.isNotBlank() }
            val ex = GrammarBank.builtIn(t.id)
            assertEquals(t.id, raw, ex.size)
            GrammarKind.entries.forEach { k -> assertEquals("${t.id} $k", 2, ex.count { it.kind == k }) }
            assertEquals(t.id, ex.size, ex.map { it.key }.toSet().size)
        }
    }

    @Test
    fun builtInAnswersPassTheirOwnCheck_andTheWrongSentenceFails() {
        Grammar.TOPICS.flatMap { GrammarBank.builtIn(it.id) }.forEach { ex ->
            when (ex.kind) {
                GrammarKind.CHOICE -> {
                    assertEquals(ex.prompt, Verdict.CORRECT, GrammarBank.check(ex, "", ex.answer))
                    assertEquals(ex.prompt, 4, ex.options.size)
                }
                else -> ex.answers.forEach { a -> assertEquals("${ex.prompt} → $a", Verdict.CORRECT, GrammarBank.check(ex, a)) }
            }
            if (ex.kind == GrammarKind.FIX) assertEquals(ex.prompt, Verdict.WRONG, GrammarBank.check(ex, ex.prompt))
        }
    }

    @Test
    fun checkingIsForgivingWhereItShouldBe() {
        val gap = GrammarBank.builtIn("a2-present-perfect").first { it.kind == GrammarKind.GAP && "already" in it.prompt }
        assertEquals(Verdict.CORRECT, GrammarBank.check(gap, "have seen"))
        assertEquals(Verdict.CORRECT, GrammarBank.check(gap, "'ve seen"))
        assertEquals(Verdict.WRONG, GrammarBank.check(gap, "saw"))
        val fix = GrammarBank.builtIn("a1-be").first { it.kind == GrammarKind.FIX && it.prompt.startsWith("He") }
        assertEquals(Verdict.CORRECT, GrammarBank.check(fix, "he is my best friend"))
    }

    @Test
    fun masteryNeedsTwentyAnswersAtEightyFivePercent() {
        assertFalse(GrammarBank.progress(List(19) { 100 }).mastered)
        assertTrue(GrammarBank.progress(List(20) { 100 }).mastered)
        // Old mistakes fall out of the window.
        assertTrue(GrammarBank.progress(List(10) { 0 } + List(20) { if (it < 3) 50 else 100 }).mastered)
        val p = GrammarBank.progress(List(17) { 100 } + List(3) { 0 })
        assertEquals(85, p.recentAverage)
        assertTrue(p.mastered)
        assertFalse(GrammarBank.progress(List(16) { 100 } + List(4) { 0 }).mastered)
    }

    @Test
    fun sessionsMixKinds_andPutRecentlySeenLast() {
        val pool = GrammarBank.builtIn("b1-passive")
        val s = GrammarBank.session(pool, emptyList(), Random(1), size = 8)
        assertEquals(8, s.size)
        assertEquals(GrammarKind.entries.toSet(), s.map { it.kind }.toSet())
        val recent = pool.take(6).map { it.key }
        val next = GrammarBank.session(pool, recent, Random(2), size = 2)
        assertTrue(next.none { it.key in recent })
    }
}
