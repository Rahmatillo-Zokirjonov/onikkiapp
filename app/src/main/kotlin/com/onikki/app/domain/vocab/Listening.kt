package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import kotlin.random.Random

/** A short text to listen to; [id] is "a1-1"… for the built-in ones, "ai-<n>" for AI-written ones. */
data class ListeningText(val id: String, val level: String, val title: String, val topic: String, val sentences: List<String>, val ai: Boolean = false) {
    val fullText: String get() = sentences.joinToString(" ")
}

/** A piece of a sentence on screen: plain text, or gap number [gap] whose answer is [text]. */
data class Piece(val text: String, val gap: Int? = null)

/** The text with its gaps: [sentences] of pieces, [answers] by gap number. */
data class GapPlan(val sentences: List<List<Piece>>, val answers: List<String>)

/**
 * Listening with gaps: the user hears the text, then fills the missing words.
 * Gaps prefer words from the user's own list (so listening also trains them), then longer content words;
 * they are spread over the sentences and never two in a row. Pure.
 */
object Listening {
    val LEVELS = listOf("A1", "A2", "B1", "B2")

    fun gapsFor(level: String): Int = when (level) {
        "A1" -> 4
        "A2" -> 5
        "B1" -> 6
        else -> 8
    }

    /** A1 and A2 start with a word bank; B1 and B2 with typing. */
    fun bankByDefault(level: String): Boolean = level == "A1" || level == "A2"

    private val STOP = setOf(
        "the", "and", "but", "for", "are", "was", "were", "has", "have", "had", "his", "her", "him", "she", "they", "them",
        "you", "your", "our", "its", "this", "that", "these", "those", "with", "from", "into", "onto", "than", "then", "there",
        "their", "what", "when", "where", "which", "who", "why", "how", "not", "can", "will", "would", "could", "should", "all",
        "any", "some", "very", "also", "just", "too", "out", "off", "about", "after", "before", "because", "every", "much", "many",
        "does", "did", "been", "being", "here", "only", "more", "most", "each", "other", "over", "under", "again", "she's", "it's"
    )

    /** Splits a sentence into words (letters and inner apostrophes/hyphens) and everything else, keeping spaces. */
    fun split(sentence: String): List<String> = Regex("[A-Za-z]+(?:['-][A-Za-z]+)*|[^A-Za-z]+").findAll(sentence).map { it.value }.toList()

    private fun isWord(token: String) = token.firstOrNull()?.isLetter() == true

    fun plan(text: ListeningText, known: Set<String>, random: Random): GapPlan {
        val tokens = text.sentences.map { split(it) }
        data class Candidate(val s: Int, val t: Int, val word: String, val score: Double)
        val candidates = tokens.flatMapIndexed { s, sentence ->
            val firstWord = sentence.indexOfFirst { isWord(it) }
            sentence.mapIndexedNotNull { t, token ->
                val lower = token.lowercase()
                val usable = isWord(token) && t != firstWord && token.length >= 3 && token[0].isLowerCase() &&
                    '\'' !in token && '-' !in token && lower !in STOP
                if (!usable) null
                else Candidate(s, t, token, (if (AnswerCheck.normalize(lower) in known) 10.0 else 0.0) + token.length * 0.6 + random.nextDouble() * 2)
            }
        }
        val wanted = gapsFor(text.level).coerceAtMost(candidates.size)
        val perSentence = (wanted + text.sentences.size - 1) / text.sentences.size.coerceAtLeast(1) + 1
        val chosen = mutableListOf<Candidate>()
        for (c in candidates.sortedByDescending { it.score }) {
            if (chosen.size >= wanted) break
            if (chosen.count { it.s == c.s } >= perSentence) continue
            if (chosen.any { it.s == c.s && kotlin.math.abs(it.t - c.t) <= 2 }) continue
            if (chosen.any { it.word.equals(c.word, ignoreCase = true) }) continue
            chosen += c
        }
        // Gap numbers follow reading order.
        val ordered = chosen.sortedWith(compareBy({ it.s }, { it.t }))
        val gapAt = ordered.withIndex().associate { (i, c) -> (c.s to c.t) to i }
        val sentences = tokens.mapIndexed { s, sentence ->
            sentence.mapIndexed { t, token -> Piece(token, gapAt[s to t]) }
        }
        return GapPlan(sentences, ordered.map { it.word })
    }

    /** The word bank: the answers plus a couple of look-alike distractors from the same text, shuffled. */
    fun bank(plan: GapPlan, random: Random, extra: Int = 2): List<String> {
        val answers = plan.answers.map { it.lowercase() }.toSet()
        val others = plan.sentences.flatten().filter { it.gap == null && isWord(it.text) && it.text.length >= 4 && it.text[0].isLowerCase() && it.text.lowercase() !in STOP && it.text.lowercase() !in answers }
            .map { it.text.lowercase() }.distinct().shuffled(random).take(extra)
        return (plan.answers + others).shuffled(random)
    }

    fun check(inputs: List<String>, answers: List<String>): List<Verdict> =
        answers.mapIndexed { i, a -> AnswerCheck.judge(inputs.getOrElse(i) { "" }, listOf(a)) }

    fun accuracy(verdicts: List<Verdict>): Int =
        if (verdicts.isEmpty()) 100 else (verdicts.count { it == Verdict.CORRECT } * 100 + verdicts.count { it == Verdict.ALMOST } * 50) / verdicts.size

    /** 5 XP a right gap, 2 for "deyarli", +10 for a clean text. */
    fun xp(verdicts: List<Verdict>): Int =
        verdicts.sumOf { when (it) { Verdict.CORRECT -> 5; Verdict.ALMOST -> 2; Verdict.WRONG -> 0 }.toInt() } +
            if (verdicts.isNotEmpty() && verdicts.all { it == Verdict.CORRECT }) 10 else 0

    /** A starting level from how many words are learned. */
    fun suggestLevel(words: List<VocabWord>): String {
        val learned = words.count { it.status == WordStatus.REVIEW || it.status == WordStatus.MASTERED }
        return when {
            learned < 60 -> "A1"
            learned < 200 -> "A2"
            learned < 500 -> "B1"
            else -> "B2"
        }
    }

    /** Built-in first, then AI-written ones (newest last). */
    fun forLevel(level: String, custom: List<ListeningText>): List<ListeningText> =
        ListeningLibrary.TEXTS.filter { it.level == level } + custom.filter { it.level == level }
}
