package com.onikki.app.domain.vocab

import java.util.Locale

/** How an answer was judged. ALMOST still counts as passed — the right spelling is shown. */
enum class Verdict { CORRECT, ALMOST, WRONG }

/** A box in the answer: a letter to type, or a fixed character (space, apostrophe, hyphen) shown already. */
data class Slot(val fixed: Char?)

object AnswerCheck {
    private val APOSTROPHES = Regex("[‘’ʻʼ`´]")

    /** Lowercase, one apostrophe kind, single spaces, no end punctuation. */
    fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(APOSTROPHES, "'")
        .replace(Regex("[.!?,;:]+$"), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Only the letters — spaces, apostrophes and hyphens never make an answer wrong. */
    fun letters(text: String): String = normalize(text).filter { it.isLetterOrDigit() }

    /**
     * Accepts any of [answers]. One slip in a word of 5+ letters (two in 9+) is "almost": it passes,
     * and the right spelling is shown so it's learned.
     */
    fun judge(input: String, answers: List<String>): Verdict {
        val given = letters(input)
        if (given.isEmpty()) return Verdict.WRONG
        var best = Int.MAX_VALUE
        var bestLength = 0
        answers.forEach { answer ->
            val target = letters(answer)
            if (target == given) return Verdict.CORRECT
            val d = distance(given, target)
            if (d < best) { best = d; bestLength = target.length }
        }
        val allowed = when {
            bestLength >= 9 -> 2
            bestLength >= 5 -> 1
            else -> 0
        }
        return if (best <= allowed) Verdict.ALMOST else Verdict.WRONG
    }

    /** Letter boxes for [answer]: one per letter, fixed boxes for spaces / apostrophes / hyphens. */
    fun slots(answer: String): List<Slot> = normalize(answer).map { c -> if (c.isLetterOrDigit()) Slot(null) else Slot(c) }

    /** Puts [typed] letters into the boxes; the rest stay empty (null). */
    fun fill(slots: List<Slot>, typed: String): List<Char?> {
        var i = 0
        return slots.map { slot ->
            slot.fixed ?: typed.getOrNull(i++)
        }
    }

    /** The answer as the user wrote it into the boxes (fixed characters included). */
    fun assemble(slots: List<Slot>, typed: String): String = fill(slots, typed).joinToString("") { it?.toString() ?: "" }

    fun letterCount(slots: List<Slot>): Int = slots.count { it.fixed == null }

    /** Levenshtein distance (small strings only). */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
