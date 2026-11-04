package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChainSequenceTest {
    private fun w(e: String) = ImportedWord(e, "uz-$e", null, null)
    private val words = (0 until 20).map { w("word$it") }

    @Test
    fun targetsForwardAndBackwardStopAtTheEnds() {
        assertEquals(listOf(4, 5, 6), ChainSequence.targets(20, 3, backward = false, length = 3))
        assertEquals(listOf(2, 1, 0), ChainSequence.targets(20, 3, backward = true, length = 10))
        assertEquals(listOf(18, 19), ChainSequence.targets(20, 17, backward = false, length = 5))
        assertTrue(ChainSequence.targets(20, 19, backward = false, length = 5).isEmpty())
    }

    @Test
    fun planFromChosenStartTurnsAroundAtTheEnd() {
        val p = ChainSequence.plan(words, SeqDirection.FORWARD, 5, start = 19, weak = emptyMap())!!
        assertTrue(p.backward)
        assertEquals(listOf(18, 17, 16, 15, 14), p.targets)
        assertEquals(listOf(8, 9), ChainSequence.plan(words, SeqDirection.FORWARD, 2, start = 7, weak = emptyMap())!!.targets)
        assertNull(ChainSequence.plan(words.take(1), SeqDirection.FORWARD, 5, null, emptyMap()))
    }

    @Test
    fun randomRunsAreFullLengthAndWeakLinksComeFirst() {
        repeat(50) { seed ->
            val f = ChainSequence.plan(words, SeqDirection.FORWARD, 5, null, emptyMap(), Random(seed))!!
            assertEquals(5, f.targets.size)
            val b = ChainSequence.plan(words, SeqDirection.BACKWARD, 5, null, emptyMap(), Random(seed))!!
            assertEquals(5, b.targets.size)
            assertTrue(b.backward)
        }
        val weak = mapOf(ChainSequence.linkKey(words[12], words[13]) to 3)
        val hits = (0 until 100).count { seed ->
            val p = ChainSequence.plan(words, SeqDirection.FORWARD, 5, null, weak, Random(seed))!!
            p.anchor <= 12 && 13 in p.targets
        }
        assertTrue("weak link in $hits of 100 runs", hits >= 45)
    }

    @Test
    fun spokenWordsMatchInOrder() {
        val t = listOf(w("price"), w("future"), w("experience"))
        assertEquals(3, ChainSequence.matchSpoken(listOf("Price, future, experience."), t))
        assertEquals(2, ChainSequence.matchSpoken(listOf("price um future banana"), t))
        assertEquals(0, ChainSequence.matchSpoken(listOf("future experience"), t))
        assertEquals(1, ChainSequence.matchSpoken(listOf("nothing", "price"), t))
        assertEquals(1, ChainSequence.matchSpoken(listOf("borrow it"), listOf(w("borrow"), w("lend"))))
        assertEquals(0, ChainSequence.matchSpoken(emptyList(), t))
    }

    @Test
    fun weakLinksGrowAndHeal() {
        var weak = ChainSequence.updateWeak(emptyMap(), "a>b", missed = true)
        weak = ChainSequence.updateWeak(weak, "a>b", missed = true)
        assertEquals(2, weak["a>b"])
        weak = ChainSequence.updateWeak(weak, "a>b", missed = false)
        weak = ChainSequence.updateWeak(weak, "a>b", missed = false)
        assertFalse("a>b" in weak)
        assertEquals("price>future", ChainSequence.linkKey(w("Price"), w("future")))
    }

    @Test
    fun replacingAWordMovesNothingElse() {
        val base = (0 until 10).map { w("b$it") }
        val replaced = WordChain.arrange(base, mapOf(2 to w("mine")), setOf("b2"))
        assertEquals(listOf("b0", "b1", "mine", "b3", "b4", "b5", "b6", "b7", "b8", "b9"), replaced.map { it.english })
        // Auto: the far-end word moves to the place, only the end gets shorter.
        val pick = WordChain.autoPick(base, emptyMap(), emptySet(), base[3])!!
        assertEquals("b9", pick.english)
        val auto = WordChain.arrange(base, mapOf(3 to pick), setOf("b3"))
        assertEquals(listOf("b0", "b1", "b2", "b9", "b4", "b5", "b6", "b7", "b8"), auto.map { it.english })
        assertFalse(WordChain.canUse("B4 ", auto, position = 0))
        assertTrue(WordChain.canUse("b4", auto, position = 4))
        assertTrue(WordChain.canUse("b3", auto, position = 0))
        assertFalse(WordChain.canUse("  ", auto, position = 0))
    }
}
