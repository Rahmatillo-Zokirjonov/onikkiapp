package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.ReviewGrade
import java.time.LocalDate

/** One letter of a spelling diff: right, a wrong letter typed, a letter left out ("_"), or an extra one. */
enum class CellKind { OK, WRONG, MISSING, EXTRA }

data class DiffCell(val char: Char, val kind: CellKind)

/**
 * Strict spelling for the spelling, verb and dictation modes: case and spaces don't matter, every
 * letter does (no "almost" here — "dengre" is wrong). The diff aligns the typed word with the right one
 * (Levenshtein) so a left-out letter shows as a red "_" in its place.
 */
object Spelling {
    private val APOSTROPHES = Regex("[‘’ʻʼ`´]")

    fun clean(text: String): String = text.lowercase().replace(APOSTROPHES, "'").filter { !it.isWhitespace() }

    fun isRight(input: String, answers: List<String>): Boolean {
        val typed = clean(input)
        return typed.isNotEmpty() && answers.any { clean(it) == typed }
    }

    /** The accepted answer closest to what was typed (for the diff). */
    fun closest(input: String, answers: List<String>): String =
        answers.minByOrNull { AnswerCheck.distance(clean(input), clean(it)) } ?: answers.firstOrNull().orEmpty()

    fun diff(input: String, target: String): List<DiffCell> {
        val a = clean(input)
        val b = clean(target)
        val d = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        val out = ArrayDeque<DiffCell>()
        var i = a.length
        var j = b.length
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i - 1] == b[j - 1] && d[i][j] == d[i - 1][j - 1] -> { out.addFirst(DiffCell(a[i - 1], CellKind.OK)); i--; j-- }
                i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1 -> { out.addFirst(DiffCell(a[i - 1], CellKind.WRONG)); i--; j-- }
                j > 0 && d[i][j] == d[i][j - 1] + 1 -> { out.addFirst(DiffCell(b[j - 1], CellKind.MISSING)); j-- }
                else -> { out.addFirst(DiffCell(a[i - 1], CellKind.EXTRA)); i-- }
            }
        }
        return out.toList()
    }

    fun verdict(input: String, answers: List<String>): Verdict = if (isRight(input, answers)) Verdict.CORRECT else Verdict.WRONG
}

/** A word of a dictated sentence: right, misspelt (with what was typed), or left out. */
data class WordMark(val word: String, val kind: CellKind, val typed: String? = null)

/**
 * Word-by-word comparison of a dictated sentence (case and punctuation ignored): the typed words are
 * aligned with the sentence (LCS); between two matched words, a typed word facing a sentence word is
 * "wrong", a sentence word with nothing facing it is "missing", leftovers are "extra".
 */
object SentenceDiff {
    private fun key(w: String) = w.lowercase().replace(Regex("[‘’ʻʼ`´]"), "'").filter { it.isLetterOrDigit() || it == '\'' }

    fun compare(input: String, target: String): Pair<List<WordMark>, Int> {
        val t = target.trim().split(Regex("\\s+")).filter { key(it).isNotEmpty() }
        val u = input.trim().split(Regex("\\s+")).filter { key(it).isNotEmpty() }
        val tk = t.map(::key)
        val uk = u.map(::key)
        val dp = Array(t.size + 1) { IntArray(u.size + 1) }
        for (i in t.indices.reversed()) for (j in u.indices.reversed()) {
            dp[i][j] = if (tk[i] == uk[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val marks = mutableListOf<WordMark>()
        var extra = 0
        var i = 0
        var j = 0
        val gapT = mutableListOf<Int>()
        val gapU = mutableListOf<Int>()
        fun flush() {
            gapT.forEachIndexed { k, ti ->
                val typed = gapU.getOrNull(k)
                marks += if (typed != null) WordMark(t[ti], CellKind.WRONG, u[typed]) else WordMark(t[ti], CellKind.MISSING)
            }
            extra += (gapU.size - gapT.size).coerceAtLeast(0)
            gapT.clear(); gapU.clear()
        }
        while (i < t.size || j < u.size) {
            when {
                i < t.size && j < u.size && tk[i] == uk[j] -> { flush(); marks += WordMark(t[i], CellKind.OK); i++; j++ }
                j >= u.size || (i < t.size && dp[i + 1][j] >= dp[i][j + 1]) -> { gapT += i; i++ }
                else -> { gapU += j; j++ }
            }
        }
        flush()
        return marks to extra
    }

    /** All right = right; one slip in a sentence of 4+ words = "deyarli"; more = wrong. */
    fun verdict(marks: List<WordMark>, extra: Int): Verdict {
        val errors = marks.count { it.kind != CellKind.OK } + extra
        return when {
            marks.isEmpty() -> Verdict.WRONG
            errors == 0 -> Verdict.CORRECT
            errors == 1 && marks.size >= 4 -> Verdict.ALMOST
            else -> Verdict.WRONG
        }
    }
}

/** One logged answer, as the spelling and stats views read them. */
data class ReviewRow(val wordId: Long, val exercise: String, val grade: ReviewGrade, val day: LocalDate)

/** "Qiyin so'zlar": words misspelt 2+ times in the last 7 days, most misses first. */
object HardSpelling {
    const val DAYS = 7L
    const val MIN_MISSES = 2
    val WRITING = setOf("SPELL", "VERB_PAST", "DICTATION", "TYPE", "DICTATE", "FORMS", "CLOZE_TYPE", "VERB_SPRINT")

    fun pick(rows: List<ReviewRow>, today: LocalDate): List<Long> {
        val from = today.minusDays(DAYS - 1)
        return rows.filter { it.exercise in WRITING && it.grade == ReviewGrade.AGAIN && !it.day.isBefore(from) && !it.day.isAfter(today) }
            .groupingBy { it.wordId }.eachCount()
            .filter { it.value >= MIN_MISSES }
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }
}

/** Accuracy of one practice mode this week and the week before. */
data class ModeStat(val mode: String, val label: String, val answers: Int, val accuracy: Int, val lastWeek: Int?)

object ModeStats {
    /** Exercise names → the mode the user sees. */
    val LABELS = linkedMapOf(
        "SPELL" to "Yozilish",
        "VERB_PAST" to "Noto'g'ri fe'llar",
        "VERB_SPRINT" to "Fe'llar · 60 soniya",
        "DICTATION" to "Diktant",
        "TYPE" to "Yozish (darsda)",
        "CHOOSE" to "Test",
        "LISTEN" to "Tinglab tanlash",
        "MATCH" to "Juftlash",
        "FORMS" to "V2 / V3",
        "FORM_CHOICE" to "V2 / V3 tanlash",
        "TRANSLATE" to "Tarjima",
        "COMPOSE" to "O'z gapi",
        "LISTENING" to "Listening",
        "DICTATE" to "Eshitib yozish",
        "CLOZE_CHOICE" to "Bo'sh joy",
        "CLOZE_TYPE" to "Bo'sh joy yozish",
        "BUILD" to "Gap yig'ish"
    )

    private fun acc(rows: List<ReviewRow>): Int? =
        if (rows.isEmpty()) null else rows.count { it.grade != ReviewGrade.AGAIN } * 100 / rows.size

    /** This week = the last 7 days including today; last week = the 7 before. */
    fun build(rows: List<ReviewRow>, today: LocalDate): List<ModeStat> {
        val weekStart = today.minusDays(6)
        val prevStart = today.minusDays(13)
        val thisWeek = rows.filter { !it.day.isBefore(weekStart) && !it.day.isAfter(today) }
        val prevWeek = rows.filter { !it.day.isBefore(prevStart) && it.day.isBefore(weekStart) }
        return thisWeek.groupBy { it.exercise }.map { (mode, r) ->
            ModeStat(mode, LABELS[mode] ?: mode, r.size, acc(r) ?: 0, acc(prevWeek.filter { it.exercise == mode }))
        }.sortedByDescending { it.answers }
    }

    /** Overall accuracy this week vs last week, for "haftalik o'sish". */
    fun weekly(rows: List<ReviewRow>, today: LocalDate): Pair<Int?, Int?> {
        val weekStart = today.minusDays(6)
        return acc(rows.filter { !it.day.isBefore(weekStart) && !it.day.isAfter(today) }) to
            acc(rows.filter { !it.day.isBefore(today.minusDays(13)) && it.day.isBefore(weekStart) })
    }
}
