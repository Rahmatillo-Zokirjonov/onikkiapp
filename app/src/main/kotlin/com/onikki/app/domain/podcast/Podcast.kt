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

    fun forLevel(level: String, custom: List<Podcast> = emptyList()): List<Podcast> =
        (PodcastLibrary.ALL + custom).filter { it.level == level }.sortedWith(compareBy({ it.ai }, { it.minutes }))
}
