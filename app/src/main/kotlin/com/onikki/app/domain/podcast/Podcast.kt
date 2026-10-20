package com.onikki.app.domain.podcast

import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Verdict

/** Who says a line: a narrator (null) or a speaker letter in a dialogue ("A", "B"). */
data class PodcastLine(val speaker: String?, val text: String)

enum class QuestionKind(val label: String) { CHOICE("Test"), TRUE_FALSE("To'g'ri / noto'g'ri"), SHORT("Qisqa javob") }

/**
 * A comprehension question. CHOICE: [options] + [answer]; TRUE_FALSE: options "True", "False";
 * SHORT: typed, any of [accepted]. [line] is the transcript line that holds the answer (-1 = whole text).
 */
data class PodcastQuestion(
    val kind: QuestionKind,
    val text: String,
    val options: List<String> = emptyList(),
    val answer: Int = -1,
    val accepted: List<String> = emptyList(),
    val line: Int = -1
) {
    val rightAnswer: String get() = if (kind == QuestionKind.SHORT) accepted.firstOrNull().orEmpty() else options.getOrElse(answer) { "" }
}

data class Podcast(
    val id: String,
    val level: String,
    val minutes: Int,
    val title: String,
    val topic: String,
    val lines: List<PodcastLine>,
    val questions: List<PodcastQuestion>,
    val ai: Boolean = false
) {
    val isDialogue: Boolean get() = lines.any { it.speaker != null }
    val wordCount: Int get() = lines.sumOf { l -> l.text.split(Regex("\\s+")).count { it.isNotBlank() } }
}

object Podcasts {
    val LEVELS = listOf("A1", "A2", "B1", "B2")

    /**
     * Transcript: one line per sentence group, "A: text" for a speaker, plain for narration.
     * Questions, one per line:
     *   C|question|opt;opt;opt;opt|right index|line
     *   TF|statement|T or F|line
     *   S|question|answer~answer|line
     */
    fun parse(id: String, level: String, minutes: Int, title: String, topic: String, transcript: String, questions: String): Podcast {
        val lines = transcript.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
            val m = Regex("^([A-Z]):\\s*(.+)$").find(raw)
            if (m != null) PodcastLine(m.groupValues[1], m.groupValues[2].trim()) else PodcastLine(null, raw)
        }
        val qs = questions.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull(::parseQuestion)
        return Podcast(id, level, minutes, title, topic, lines, qs)
    }

    fun parseQuestion(raw: String): PodcastQuestion? {
        val p = raw.split('|').map { it.trim() }
        return when (p.firstOrNull()) {
            "C" -> if (p.size >= 4) PodcastQuestion(QuestionKind.CHOICE, p[1], p[2].split(';').map { it.trim() }, p[3].toIntOrNull() ?: -1, line = p.getOrNull(4)?.toIntOrNull() ?: -1)
                .takeIf { it.answer in it.options.indices } else null
            "TF" -> if (p.size >= 3) PodcastQuestion(QuestionKind.TRUE_FALSE, p[1], listOf("True", "False"), if (p[2].startsWith("T")) 0 else 1, line = p.getOrNull(3)?.toIntOrNull() ?: -1) else null
            "S" -> if (p.size >= 3) PodcastQuestion(QuestionKind.SHORT, p[1], accepted = p[2].split('~').map { it.trim() }.filter { it.isNotEmpty() }, line = p.getOrNull(3)?.toIntOrNull() ?: -1)
                .takeIf { it.accepted.isNotEmpty() } else null
            else -> null
        }
    }

    /** A short typed answer may be a little longer than needed ("in the park" for "park"). */
    fun check(q: PodcastQuestion, input: String, picked: Int): Verdict = when (q.kind) {
        QuestionKind.CHOICE, QuestionKind.TRUE_FALSE -> if (picked == q.answer) Verdict.CORRECT else Verdict.WRONG
        QuestionKind.SHORT -> {
            val direct = AnswerCheck.judge(input, q.accepted)
            if (direct != Verdict.WRONG) direct
            else {
                val typed = " " + AnswerCheck.normalize(input) + " "
                if (q.accepted.any { a -> AnswerCheck.normalize(a).let { it.length >= 3 && typed.contains(" $it ") } }) Verdict.CORRECT else Verdict.WRONG
            }
        }
    }

    /** "Qancha tushundim": right = 100, "deyarli" = 50, averaged. */
    fun understood(verdicts: List<Verdict>): Int =
        if (verdicts.isEmpty()) 0 else verdicts.sumOf { when (it) { Verdict.CORRECT -> 100; Verdict.ALMOST -> 50; Verdict.WRONG -> 0 }.toInt() } / verdicts.size

    fun xp(verdicts: List<Verdict>, minutes: Int): Int =
        verdicts.sumOf { when (it) { Verdict.CORRECT -> 5; Verdict.ALMOST -> 2; Verdict.WRONG -> 0 }.toInt() } + minutes * 5

    /** Words a podcast of [minutes] should have at the reading speed. */
    fun wordRange(minutes: Int): IntRange = (minutes * 85)..(minutes * 165)

    /** Is the answer of a question actually in its line? (choice / true-false can't be checked this way.) */
    fun answerInLine(p: Podcast, q: PodcastQuestion): Boolean {
        if (q.line !in p.lines.indices) return false
        if (q.kind != QuestionKind.SHORT) return true
        val line = " " + AnswerCheck.normalize(p.lines[q.line].text) + " "
        return q.accepted.any { a -> AnswerCheck.normalize(a).let { it.isNotEmpty() && line.contains(it) } }
    }

    /**
     * An AI podcast cleaned up: only well-formed questions whose evidence line holds the answer are kept.
     * Null when it isn't usable (wrong length, too few lines or questions).
     */
    fun vet(p: Podcast, minutes: Int): Podcast? {
        if (p.lines.size < 4 || p.wordCount !in wordRange(minutes)) return null
        val good = p.questions.filter { q ->
            q.text.isNotBlank() && when (q.kind) {
                QuestionKind.CHOICE -> q.options.size == 4 && q.answer in q.options.indices && q.options.map { it.lowercase() }.toSet().size == 4
                QuestionKind.TRUE_FALSE -> q.answer in 0..1
                QuestionKind.SHORT -> q.accepted.isNotEmpty()
            } && answerInLine(p, q)
        }.take(8)
        return if (good.size >= 5) p.copy(questions = good) else null
    }

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ').trim()

    /** Back into the stored text format (the same one the built-in podcasts use). */
    fun transcriptText(p: Podcast): String = p.lines.joinToString("\n") { l -> (l.speaker?.let { "$it: " } ?: "") + clean(l.text) }

    fun questionsText(p: Podcast): String = p.questions.joinToString("\n") { q ->
        when (q.kind) {
            QuestionKind.CHOICE -> "C|${clean(q.text)}|${q.options.joinToString(";") { clean(it).replace(';', ',') }}|${q.answer}|${q.line}"
            QuestionKind.TRUE_FALSE -> "TF|${clean(q.text)}|${if (q.answer == 0) "T" else "F"}|${q.line}"
            QuestionKind.SHORT -> "S|${clean(q.text)}|${q.accepted.joinToString("~") { clean(it).replace('~', ' ') }}|${q.line}"
        }
    }

    fun forLevel(level: String, custom: List<Podcast> = emptyList()): List<Podcast> =
        (PodcastLibrary.ALL + custom).filter { it.level == level }.sortedWith(compareBy({ it.ai }, { it.minutes }))
}
