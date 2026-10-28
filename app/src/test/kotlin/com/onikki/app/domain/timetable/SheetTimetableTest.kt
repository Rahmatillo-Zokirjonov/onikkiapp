package com.onikki.app.domain.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SheetTimetableTest {
    // Same shape as the university sheet: days spanning slot columns, 4 rows per group.
    private val csv = listOf(
        ",Monday,,Tuesday,",
        "Class,1. 08:00 - 09:20,2. 09:30 - 10:50,1. 08:00 - 09:20,2. 09:30 - 10:50",
        "26-201 (UZ),Math,,Physics,",
        ",Teacher A,,Teacher B,",
        ",101 Room,,102 Lab,",
        ",26-201 (UZ),,26-201 (UZ),",
        "26-202 (UZ),\"Web Development\",\"History, Modern\",,Study Skills",
        ",Teacher C,\"Teacher D\",,Teacher E",
        ",227 Room,234 Lecture Room,,227 Room",
        ",26-202 (UZ),\"26-201 (UZ), 26-202 (UZ)\",,26-202 (UZ)"
    ).joinToString("\r\n")

    @Test
    fun linkToCsv() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/15WaaKu0J5bfssa3Z5whWSCl1tJ7HeQlz3xa6AgyM0ic/export?format=csv&gid=121259130",
            SheetTimetable.csvUrl("https://docs.google.com/spreadsheets/d/15WaaKu0J5bfssa3Z5whWSCl1tJ7HeQlz3xa6AgyM0ic/edit?gid=121259130#gid=121259130")
        )
        assertNull(SheetTimetable.csvUrl("https://example.com/x"))
    }

    @Test
    fun csvWithQuotesCommasAndLineBreaks() {
        val rows = SheetTimetable.parseCsv("a,\"b, c\",\"say \"\"hi\"\"\"\n\"two\nlines\",x")
        assertEquals(listOf("a", "b, c", "say \"hi\""), rows[0])
        assertEquals(listOf("two\nlines", "x"), rows[1])
    }

    @Test
    fun groupsAndLessons() {
        val rows = SheetTimetable.parseCsv(csv)
        assertEquals(listOf("26-201 (UZ)", "26-202 (UZ)"), SheetTimetable.groups(rows))
        val l = SheetTimetable.lessons(rows, "26-202")
        assertEquals(3, l.size)
        assertEquals(SheetLesson(1, 1, LocalTime.of(8, 0), LocalTime.of(9, 20), "Web Development", "Teacher C", "227 Room"), l[0])
        assertEquals("History, Modern", l[1].subject)
        assertEquals(LocalTime.of(9, 30), l[1].start)
        assertEquals(2, l[2].dayOfWeek) // Tuesday, slot 2
        assertEquals("2-2", l[2].key)
        assertTrue(SheetTimetable.lessons(rows, "99-999").isEmpty())
    }

    @Test
    fun changesInWords() {
        val rows = SheetTimetable.parseCsv(csv)
        val old = SheetTimetable.lessons(rows, "26-202")
        val new = old.map { if (it.key == "1-1") it.copy(room = "220 Lab") else it }.filter { it.key != "2-2" } +
            SheetLesson(3, 1, LocalTime.of(8, 0), LocalTime.of(9, 20), "Programming", null, "133")
        val c = SheetTimetable.changes(old, new)
        assertEquals(3, c.size)
        assertTrue(c[0].contains("Dushanba 08:00") && c[0].contains("227 Room → 220 Lab"))
        assertTrue(c.any { it.startsWith("✕") && it.contains("Study Skills") })
        assertTrue(c.any { it.startsWith("＋") && it.contains("Chorshanba") })
        assertTrue(SheetTimetable.changes(old, old).isEmpty())
    }
}
