package com.onikki.app.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabImportTest {
    @Test
    fun lessonOneHasTwentyWordsWithExamplesAndPastForms() {
        val words = VocabImport.parse(VocabImport.LESSON_1)
        assertEquals(20, words.size)
        assertEquals(ImportedWord("decide", "qaror qilmoq", "I decided to study English.", "decided"), words[0])
        assertEquals("spent", words.first { it.english == "spend" }.pastForm)
        assertEquals("sarflamoq, o'tkazmoq", words.first { it.english == "spend" }.uzbek)
        assertNull(words.first { it.english == "remember" }.pastForm)
        assertEquals(20, words.count { it.example != null })
    }

    @Test
    fun formatsAndJunk() {
        assertEquals(ImportedWord("apple", "olma", null, null), VocabImport.parseLine("apple | olma"))
        assertEquals(ImportedWord("apple", "olma", null, null), VocabImport.parseLine("apple - olma"))
        assertEquals(ImportedWord("go", "bormoq", "I go home.", "went"), VocabImport.parseLine("  go (went) |bormoq| I go home. "))
        assertNull(VocabImport.parseLine("# izoh"))
        assertNull(VocabImport.parseLine("faqat bir so'z"))
        assertNull(VocabImport.parseLine(" | olma"))
        // Duplicates (by spelling) are dropped.
        assertEquals(1, VocabImport.parse("Apple | olma\napple | olma 2").size)
    }
}
