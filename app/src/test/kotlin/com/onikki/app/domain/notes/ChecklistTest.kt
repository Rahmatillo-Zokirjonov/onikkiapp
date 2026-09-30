package com.onikki.app.domain.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistTest {
    @Test
    fun plainTextBecomesItems_markersUnderstood() {
        val content = Checklist.fromPlain("Non\n- Sut\n\n2. Tuxum\n[x] Suv\n✓ Choy")
        assertEquals("☐ Non\n☐ Sut\n☐ Tuxum\n☑ Suv\n☑ Choy", content)
        assertEquals(5, Checklist.parse(content).size)
    }

    @Test
    fun toggleFlipsOneItemOnly_andRoundTripsToPlain() {
        val content = "☐ Non\n☑ Sut"
        assertEquals("☑ Non\n☑ Sut", Checklist.toggle(content, 0))
        assertEquals(content, Checklist.toggle(content, 5))
        assertEquals("Non\n✓ Sut", Checklist.toPlain(content))
        assertEquals(content, Checklist.fromPlain(Checklist.toPlain(content)))
    }

    @Test
    fun serializeDropsBlankItems() {
        assertEquals("☐ A", Checklist.serialize(listOf(ChecklistItem("A", false), ChecklistItem("  ", true))))
    }
}
