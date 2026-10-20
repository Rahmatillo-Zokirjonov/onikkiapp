package com.onikki.app.domain.speaking

import com.onikki.app.domain.ai.AiPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OralTest {
    @Test
    fun sixPromptsPerLevel_withHints() {
        listOf("A1", "A2", "B1", "B2").forEach { l -> assertEquals(6, OralPrompts.forLevel(l).size) }
        assertEquals(24, OralPrompts.ALL.map { it.id }.toSet().size)
        assertTrue(OralPrompts.ALL.all { it.hints.size >= 4 && it.questionUz.isNotBlank() })
    }

    @Test
    fun offlineCheckRewardsLengthVarietyAndPhrases() {
        val p = OralPrompts.ALL.first { it.id == "oa1-1" }
        val short = OralCheck.offline(p, "my family is big", 4)
        val good = OralCheck.offline(
            p,
            "there are five people in my family my father is a teacher and my mother is a doctor I have one brother and one sister we live in Namangan I love my family very much",
            40
        )
        assertTrue("${short.overall} < ${good.overall}", short.overall < good.overall)
        assertEquals(100, good.length)
        assertTrue(good.vocabulary >= 70)
        assertNull(good.grammar)
        assertFalse(good.byAi)
        assertTrue(short.feedback.contains("qisqa"))
        assertTrue(OralCheck.xp(good, 40) > OralCheck.xp(short, 4))
    }

    @Test
    fun aiRatingParsing() {
        val r = AiPlans.parseOral(
            """{"grammar":72,"vocabulary":65,"relevance":90,"overall":75,"feedback":"Yaxshi!","corrected":"There are five people in my family.","mistakes":[{"wrong":"he have","right":"he has","why":"he → has"},{"wrong":"same","right":"same","why":""}]}""",
            length = 80
        )!!
        assertEquals(75, r.overall)
        assertEquals(72, r.grammar)
        assertEquals(80, r.length)
        assertEquals(1, r.mistakes.size) // identical "mistake" dropped
        assertTrue(r.byAi)
        assertNull(AiPlans.parseOral("{}", 50))
        assertNull(AiPlans.parseOral("junk", 50))
    }
}
