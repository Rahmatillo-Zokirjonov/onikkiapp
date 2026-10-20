package com.onikki.app.domain.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTest {
    @Test
    fun twelveTopicsPerLevel_uniqueIds_fullContent() {
        Grammar.LEVELS.forEach { l -> assertEquals(l, 12, Grammar.forLevel(l).size) }
        assertEquals(48, Grammar.TOPICS.map { it.id }.toSet().size)
        Grammar.TOPICS.forEach { t ->
            assertTrue(t.id, t.id.startsWith(t.level.lowercase() + "-"))
            assertTrue(t.id, t.title.isNotBlank() && t.titleUz.isNotBlank() && t.formula.isNotBlank())
            val lines = t.explanation.lines().filter { it.isNotBlank() }
            assertTrue(t.id, lines.size >= 3)
            // trimIndent took the code indentation away.
            assertTrue(t.id, lines.none { it.startsWith(" ") && !it.startsWith("  ") })
            assertEquals(t.id, 3, t.examples.size)
            t.examples.forEach { assertTrue(t.id, it.english.isNotBlank() && it.uzbek.isNotBlank()) }
        }
        assertEquals("Present Simple", Grammar.byId("a1-present-simple")?.title)
    }
}
