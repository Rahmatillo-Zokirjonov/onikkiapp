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

    @Test
    fun tabsGroupsWithStudentsAndIds() {
        val html = """x {name: "1-kurs 1-semestr", pageUrl: "https://docs.google.com/x/htmlview/sheet?headers=true&gid=121259130"}, {name: "Ingliz tili guruhlari ro\x27yxati", pageUrl: "https://x?gid=368002780"}"""
        assertEquals(listOf(SheetTab("121259130", "1-kurs 1-semestr"), SheetTab("368002780", "Ingliz tili guruhlari ro'yxati")), SheetTimetable.tabs(html))
        assertEquals("abcdefghijklmnopqrstuvwxyz" to "5", SheetTimetable.ids("https://docs.google.com/spreadsheets/d/abcdefghijklmnopqrstuvwxyz/edit#gid=5"))
        val english = SheetTimetable.parseCsv(listOf(
            ",Tuesday,",
            "Class,1. 08:00 - 09:20,2. 09:30 - 10:50",
            "Beginner 1,English,",
            ",T1,",
            ",230 Room,",
            ",26-201 (20),",
            "Beginner 2,,English",
            ",,T2",
            ",,231 Room",
            ",,\"26-202 (18), 26-205 (2)\""
        ).joinToString("\n"))
        assertEquals(listOf("Beginner 1", "Beginner 2"), SheetTimetable.groups(english))
        assertEquals(listOf("Beginner 2"), SheetTimetable.groupsWith(english, "26-202 (UZ)"))
        assertEquals("231 Room", SheetTimetable.lessons(english, "Beginner 2").single().room)
    }

    @Test
    fun studentIdFindsCourseAndGroups() {
        val list = SheetTimetable.parseCsv(listOf(
            "Ingliz tili guruhlari ro'yxati,,,,,,",
            "№,Kurs,Ingliz tili guruhi,Daraja,F.I.O,Talaba ID,Akademik guruh",
            "1,1-kurs,Beginner 1,A1 (Beginner),Student A,260001,26-201",
            "2,,,A1 (Beginner),Student B,260002,26-202",
            "3,,Beginner 2,A1 (Beginner),Student C,260003,26-202",
            "4,2-kurs,Pre-Int 1,A2,Student D,250004,25-101"
        ).joinToString("\n"))
        assertEquals(StudentGroups("1-kurs", "Beginner 1", "A1 (Beginner)", "26-202"), SheetTimetable.findStudent(list, "260002")) // carried down
        assertEquals("Beginner 2", SheetTimetable.findStudent(list, " 260003 ")?.englishGroup)
        assertEquals("2-kurs", SheetTimetable.findStudent(list, "250004")?.course)
        assertNull(SheetTimetable.findStudent(list, "111"))
        val tabs = listOf(
            SheetTab("1", "1-kurs 1-semestr"), SheetTab("2", "2-kurs 1-semestr"), SheetTab("3", "Ingliz tili guruhlari ro'yxati"),
            SheetTab("4", "1-kurs ingliz tili jadvali"), SheetTab("5", "2-kurs ingliz tili jadvali")
        )
        assertEquals("1", SheetTimetable.courseTab(tabs, "1-kurs", english = false)?.gid)
        assertEquals("5", SheetTimetable.courseTab(tabs, "2-kurs", english = true)?.gid)
        assertEquals("3", SheetTimetable.studentListTab(tabs)?.gid)
    }
}
