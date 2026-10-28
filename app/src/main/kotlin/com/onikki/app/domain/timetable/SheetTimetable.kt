package com.onikki.app.domain.timetable

import java.time.LocalTime

/** One lesson read from the sheet. [key] = day + slot, so a moved/changed lesson can be told from a new one. */
data class SheetLesson(
    val dayOfWeek: Int,
    val slot: Int,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val teacher: String?,
    val room: String?
) {
    val key: String get() = "$dayOfWeek-$slot"
}

/** A tab of a spreadsheet ("1-kurs ingliz tili jadvali"). */
data class SheetTab(val gid: String, val name: String)

/**
 * A university timetable published as a Google Sheet: row 1 = days (each spanning its slot columns),
 * row 2 = slots ("1. 08:00 - 09:20"), then per group a block of four rows — subject, teacher, room,
 * groups — the first one starting with the group's name ("26-202 (UZ)"). Pure parsing and diffing.
 */
object SheetTimetable {
    private val DAYS = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4, "friday" to 5, "saturday" to 6, "sunday" to 7,
        "dushanba" to 1, "seshanba" to 2, "chorshanba" to 3, "payshanba" to 4, "juma" to 5, "shanba" to 6, "yakshanba" to 7
    )
    private val SLOT = Regex("""(\d+)\.?\s*(\d{1,2})[:.](\d{2})\s*[-–]\s*(\d{1,2})[:.](\d{2})""")
    /** A share link ("…/d/<id>/edit?gid=<gid>#gid=<gid>") → sheet id and tab gid; null if it isn't a sheet link. */
    fun ids(shareLink: String): Pair<String, String?>? {
        val id = Regex("""/spreadsheets/d/([A-Za-z0-9_-]{20,})""").find(shareLink)?.groupValues?.get(1) ?: return null
        return id to Regex("""[#?&]gid=(\d+)""").find(shareLink)?.groupValues?.get(1)
    }

    fun csvUrl(sheetId: String, gid: String?): String =
        "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv" + (gid?.let { "&gid=$it" } ?: "")

    /** A share link → its CSV export URL; null if it isn't a sheet link. */
    fun csvUrl(shareLink: String): String? = ids(shareLink)?.let { (id, gid) -> csvUrl(id, gid) }

    /** The tabs listed in the sheet's public "htmlview" page ({name: "…", pageUrl: "…gid=N"}). */
    fun tabs(html: String): List<SheetTab> =
        Regex("""\{name: "((?:[^"\\]|\\.)*)", pageUrl: "[^"]*?gid=(\d+)""").findAll(html).map { m ->
            val name = m.groupValues[1].replace(Regex("""\\x([0-9a-fA-F]{2})""")) { it.groupValues[1].toInt(16).toChar().toString() }
                .replace("\\/", "/").replace("\\\"", "\"")
            SheetTab(m.groupValues[2], name)
        }.distinctBy { it.gid }.toList()

    /** RFC 4180 CSV: quoted cells may hold commas, quotes ("") and line breaks. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    /** The group names in the sheet, in order ("26-202 (UZ)", "Beginner 2"): every labelled row below the headers. */
    fun groups(rows: List<List<String>>): List<String> =
        rows.drop(2).map { it.firstOrNull().orEmpty().trim() }.filter { it.isNotEmpty() && !it.equals("class", true) }.distinct()

    /**
     * Groups whose lessons list [academicGroup] in their last row — on the English tab, the English groups
     * that have students of "26-202" ("26-202 (20)"), so the user's can be suggested.
     */
    fun groupsWith(rows: List<List<String>>, academicGroup: String): List<String> {
        val key = academicGroup.trim().substringBefore(' ').lowercase()
        if (key.isEmpty()) return emptyList()
        return groups(rows).filter { g ->
            val start = rows.indexOfFirst { it.firstOrNull().orEmpty().trim() == g }
            val block = rows.drop(start).take(4)
            block.size == 4 && block[3].drop(1).any { it.lowercase().contains(key) } && !g.lowercase().startsWith(key)
        }
    }

    /** Day and slot of every column (null for the label column or an unreadable header). */
    private fun columns(rows: List<List<String>>): List<Triple<Int, Int, Pair<LocalTime, LocalTime>>?> {
        val dayRow = rows.getOrNull(0).orEmpty()
        val slotRow = rows.getOrNull(1).orEmpty()
        var day: Int? = null
        return (0 until maxOf(dayRow.size, slotRow.size)).map { col ->
            DAYS[dayRow.getOrNull(col).orEmpty().trim().lowercase()]?.let { day = it }
            val m = SLOT.find(slotRow.getOrNull(col).orEmpty())
            val d = day
            if (col == 0 || d == null || m == null) null
            else {
                val (n, h1, m1, h2, m2) = m.destructured
                runCatching { Triple(d, n.toInt(), LocalTime.of(h1.toInt(), m1.toInt()) to LocalTime.of(h2.toInt(), m2.toInt())) }.getOrNull()
            }
        }
    }

    /** The lessons of [group] (matched by its name, so "26-202" finds "26-202 (UZ)"). */
    fun lessons(rows: List<List<String>>, group: String): List<SheetLesson> {
        val want = group.trim().lowercase()
        val start = rows.indexOfFirst { r -> r.firstOrNull().orEmpty().trim().lowercase().let { it == want || it.startsWith(want) } }
        if (start < 2) return emptyList()
        // The block is the group's row and the unlabelled rows under it (teacher, room, groups).
        val block = mutableListOf(rows[start])
        var i = start + 1
        while (i < rows.size && rows[i].firstOrNull().orEmpty().isBlank() && block.size < 4) { block += rows[i]; i++ }
        val cols = columns(rows)
        return cols.mapIndexedNotNull { col, info ->
            val (day, slot, time) = info ?: return@mapIndexedNotNull null
            val subject = block[0].getOrNull(col).orEmpty().trim().replace(Regex("\\s+"), " ")
            if (subject.isEmpty()) return@mapIndexedNotNull null
            fun line(k: Int) = block.getOrNull(k)?.getOrNull(col)?.trim()?.replace(Regex("\\s+"), " ")?.ifEmpty { null }
            SheetLesson(day, slot, time.first, time.second, subject, line(1), line(2))
        }.sortedWith(compareBy({ it.dayOfWeek }, { it.slot }))
    }

    /** What changed between two syncs, in words for the notification ("Seshanba 11:00: Study Skills → …"). */
    fun changes(old: List<SheetLesson>, new: List<SheetLesson>): List<String> {
        val o = old.associateBy { it.key }
        val n = new.associateBy { it.key }
        fun at(l: SheetLesson) = "${DAY_UZ[l.dayOfWeek - 1]} %02d:%02d".format(l.start.hour, l.start.minute)
        val out = mutableListOf<String>()
        (o.keys + n.keys).sortedWith(compareBy({ it.substringBefore('-').toInt() }, { it.substringAfter('-').toInt() })).forEach { k ->
            val a = o[k]
            val b = n[k]
            when {
                a == null && b != null -> out += "＋ ${at(b)}: ${b.subject}" + (b.room?.let { " ($it)" } ?: "")
                a != null && b == null -> out += "✕ ${at(a)}: ${a.subject} olib tashlandi"
                a != null && b != null && a != b -> {
                    val what = buildList {
                        if (a.subject != b.subject) add("${a.subject} → ${b.subject}")
                        if (a.room != b.room) add("xona ${a.room ?: "—"} → ${b.room ?: "—"}")
                        if (a.teacher != b.teacher) add("o'qituvchi: ${b.teacher ?: "—"}")
                        if (a.start != b.start || a.end != b.end) add("vaqt o'zgardi")
                    }
                    out += "✎ ${at(b)}: " + what.joinToString(", ")
                }
            }
        }
        return out
    }

    private val DAY_UZ = listOf("Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba", "Yakshanba")
}
