package com.onikki.app.domain.vocab

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** An AI story over a part of the chain: the English text with the chain words in **bold**, and its Uzbek. */
data class ChainStoryText(val english: String, val uzbek: String)

/**
 * Memory helpers for the chain: a story that walks through the words in their order (the "chain
 * method" — a sequence is remembered as a story), and the word reminders through the day. Pure.
 */
object ChainMemory {
    /** One story covers this many chain words. */
    const val STORY_PART = 15
    /** Hours of the day a chain word is shown in a notification. */
    val REMINDER_HOURS = listOf(10, 14, 18, 21)
    private val BOLD = Regex("""\*\*(.+?)\*\*""")

    /** The chain split into story parts: 0..14, 15..29, … (the last one may be shorter). */
    fun parts(size: Int): List<IntRange> = (0 until size step STORY_PART).map { it until minOf(size, it + STORY_PART) }

    /** A stamp of the part's words, so a saved story is dropped when a word in it is replaced. */
    fun signature(words: List<ImportedWord>): String = words.joinToString("|") { WordChain.key(it) }

    /** Splits the AI answer at the "---" line into the story and its translation. */
    fun parse(text: String): ChainStoryText {
        val i = text.indexOf("\n---")
        return if (i < 0) ChainStoryText(text.trim(), "")
        else ChainStoryText(text.substring(0, i).trim(), text.substring(i + 4).trim().trimStart('-').trim())
    }

    /** The bold spans of [story], in order. */
    fun boldWords(story: String): List<String> = BOLD.findAll(story).map { it.groupValues[1] }.toList()

    /** The story's text in pieces: (text, isChainWord) — for highlighting. */
    fun pieces(story: String): List<Pair<String, Boolean>> {
        val out = ArrayList<Pair<String, Boolean>>()
        var at = 0
        BOLD.findAll(story).forEach { m ->
            if (m.range.first > at) out += story.substring(at, m.range.first) to false
            out += m.groupValues[1] to true
            at = m.range.last + 1
        }
        if (at < story.length) out += story.substring(at) to false
        return out
    }

    /**
     * Whether the story names every word, in the chain's order. A bold span may be a form of the word
     * (achieved for achieve).
     */
    fun inOrder(story: String, words: List<ImportedWord>): Boolean {
        val spans = boldWords(story).map(AnswerCheck::letters)
        var i = 0
        for (w in words) {
            val want = AnswerCheck.letters(w.english)
            val stem = want.dropLast(if (want.length > 4) 1 else 0)
            while (i < spans.size && !(spans[i] == want || spans[i].startsWith(stem))) i++
            if (i == spans.size) return false
            i++
        }
        return true
    }

    /** Reminder times from [now] over the next [hours] hours. */
    fun reminderTimes(now: LocalDateTime, hours: Long): List<LocalDateTime> {
        val until = now.plusHours(hours)
        return (0..2).flatMap { d -> REMINDER_HOURS.map { h -> now.toLocalDate().plusDays(d.toLong()).atTime(LocalTime.of(h, 0)) } }
            .filter { it.isAfter(now) && !it.isAfter(until) }
    }

    /**
     * The word for a reminder: the weakest ones take turns (low Leitner box first, then chain order),
     * so each reminder of the day shows a different word.
     */
    fun reminderWord(words: List<ImportedWord>, boxes: Map<String, Int>, date: LocalDate, hour: Int): Int? {
        if (words.isEmpty()) return null
        val weakest = words.indices.sortedWith(compareBy({ boxes[WordChain.key(words[it])] ?: 0 }, { it })).take(12)
        val slot = date.toEpochDay() * REMINDER_HOURS.size + REMINDER_HOURS.indexOf(hour).coerceAtLeast(0)
        return weakest[(slot % weakest.size).toInt()]
    }
}
