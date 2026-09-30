package com.onikki.app.domain.notes

import com.onikki.app.domain.notes.NoteFormat.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormatTest {
    @Test
    fun spansFindHeadingsBulletsAndInlineStyles() {
        val text = "# Reja\n- **muhim** va *tez*\n~~eski~~"
        val spans = NoteFormat.spans(text)
        fun styled(style: Style) = spans.filter { it.style == style }.map { text.substring(it.start, it.end) }
        assertEquals(listOf("Reja"), styled(Style.H1))
        assertEquals(listOf("muhim"), styled(Style.BOLD))
        assertEquals(listOf("tez"), styled(Style.ITALIC))
        assertEquals(listOf("eski"), styled(Style.STRIKE))
        assertEquals(listOf("-"), styled(Style.BULLET))
        assertTrue(spans.all { it.start >= 0 && it.end <= text.length })
    }

    @Test
    fun boldIsNotReadAsTwoItalics() {
        val spans = NoteFormat.spans("**qalin**")
        assertTrue(spans.none { it.style == Style.ITALIC })
    }

    @Test
    fun plainDropsMarkers() {
        assertEquals("Reja\n• muhim va tez\neski", NoteFormat.plain("# Reja\n- **muhim** va *tez*\n~~eski~~"))
    }

    @Test
    fun toggleWrapWrapsUnwrapsAndInsertsPair() {
        val wrapped = NoteFormat.toggleWrap("salom dunyo", 6, 11, "**")
        assertEquals(NoteFormat.Edit("salom **dunyo**", 8, 13), wrapped)
        assertEquals(NoteFormat.Edit("salom dunyo", 6, 11), NoteFormat.toggleWrap(wrapped.text, wrapped.selStart, wrapped.selEnd, "**"))
        assertEquals(NoteFormat.Edit("a****", 3, 3), NoteFormat.toggleWrap("a", 1, 1, "**"))
    }

    @Test
    fun toggleLinePrefixCoversSelectedLinesAndSwapsOtherPrefixes() {
        val e = NoteFormat.toggleLinePrefix("bir\nikki\nuch", 0, 5, "- ")
        assertEquals("- bir\n- ikki\nuch", e.text)
        assertEquals("bir\nikki\nuch", NoteFormat.toggleLinePrefix(e.text, 2, 7, "- ").text)
        assertEquals("# Sarlavha", NoteFormat.toggleLinePrefix("- Sarlavha", 3, 3, "# ").text)
    }
}
