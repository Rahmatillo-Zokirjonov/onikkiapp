package com.onikki.app.domain.speaking

import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.SentenceCheck

/** One word of the target sentence and whether it was heard. */
data class SpokenWord(val text: String, val heard: Boolean)

/** How a read-aloud went: per word, and the share of words heard. */
data class PronunciationResult(val words: List<SpokenWord>, val percent: Int, val heardText: String) {
    val missed: List<String> get() = words.filter { !it.heard }.map { it.text }
}

/**
 * Read-aloud scoring: the recognizer's best guesses are aligned word by word with the target
 * (longest common subsequence), so an extra or missed word doesn't shift the rest. Numbers match
 * as digits or words, and a long word one letter off still counts (recognizers spell oddly).
 */
object Pronunciation {
    private val NUMBERS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"
    )

    /** Normalised word for matching: lower case, contractions expanded (via SentenceCheck), numbers as words. */
    fun key(word: String): String {
        val w = word.lowercase().trim('\'')
        return w.toIntOrNull()?.let { NUMBERS.getOrNull(it) } ?: w
    }

    private fun same(a: String, b: String): Boolean =
        a == b || (a.length >= 5 && b.length >= 5 && AnswerCheck.distance(a, b) <= 1)

    /** Which target words appear, in order, in what was heard. */
    fun align(target: List<String>, heard: List<String>): BooleanArray {
        val t = target.map(::key)
        val h = heard.map(::key)
        val dp = Array(t.size + 1) { IntArray(h.size + 1) }
        for (i in t.indices.reversed()) for (j in h.indices.reversed()) {
            dp[i][j] = if (same(t[i], h[j])) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val hit = BooleanArray(t.size)
        var i = 0
        var j = 0
        while (i < t.size && j < h.size) {
            when {
                same(t[i], h[j]) -> { hit[i] = true; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        return hit
    }

    /**
     * Scores [candidates] (the recognizer's alternatives) against [target] and keeps the best.
     * The shown words keep the target's own spelling and punctuation.
     */
    fun score(target: String, candidates: List<String>): PronunciationResult {
        val shown = target.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        // Matching words for each shown word (a contraction like "I'm" expands to two).
        val perShown = shown.map { SentenceCheck.words(it) }
        val flat = perShown.flatten()
        var best = BooleanArray(flat.size)
        var bestText = candidates.firstOrNull().orEmpty()
        candidates.forEach { c ->
            val hit = align(flat, SentenceCheck.words(c))
            if (hit.count { it } > best.count { it }) { best = hit; bestText = c }
        }
        var k = 0
        val words = shown.mapIndexed { i, s ->
            val n = perShown[i].size
            val heard = n == 0 || (k until k + n).all { best[it] }
            k += n
            SpokenWord(s, heard)
        }
        val percent = if (flat.isEmpty()) 0 else best.count { it } * 100 / flat.size
        return PronunciationResult(words, percent, bestText)
    }

    /** A single word (or short phrase) said right: every word of it heard in one of the alternatives. */
    fun saidRight(target: String, candidates: List<String>): Boolean = candidates.isNotEmpty() && score(target, candidates).percent == 100

    /** XP for a read sentence by its best score. */
    fun xp(percent: Int): Int = when {
        percent >= 90 -> 8
        percent >= 70 -> 5
        percent >= 50 -> 2
        else -> 0
    }
}
