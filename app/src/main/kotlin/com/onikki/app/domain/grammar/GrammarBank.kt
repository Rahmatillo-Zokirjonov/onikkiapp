package com.onikki.app.domain.grammar

import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.SentenceCheck
import com.onikki.app.domain.vocab.Verdict
import kotlin.random.Random

/** The four grammar exercise kinds. */
enum class GrammarKind(val label: String, val code: Char) {
    CHOICE("Test", 'C'),
    GAP("Bo'sh joy", 'G'),
    FIX("Xatoni tuzating", 'F'),
    TRANSFORM("Gapni o'zgartiring", 'T')
}

/**
 * One grammar exercise. [prompt] is the sentence shown (with "___" for CHOICE/GAP, the wrong one for FIX,
 * the source for TRANSFORM); [hint] the base word for GAP or the instruction for TRANSFORM.
 */
data class GrammarExercise(
    val topicId: String,
    val kind: GrammarKind,
    val prompt: String,
    val hint: String? = null,
    val options: List<String> = emptyList(),
    val answer: Int = -1,
    val answers: List<String> = emptyList(),
    val why: String = ""
) {
    /** The answer to show after a miss. */
    val rightAnswer: String
        get() = when (kind) {
            GrammarKind.CHOICE -> prompt.replace("___", options.getOrElse(answer) { "" })
            GrammarKind.GAP -> prompt.replace("___", answers.firstOrNull().orEmpty())
            GrammarKind.FIX, GrammarKind.TRANSFORM -> answers.firstOrNull().orEmpty()
        }

    /** Same exercise, whatever its source (for de-duplicating AI ones against the built-in ones). */
    val key: String get() = kind.code + ":" + SentenceCheck.words(prompt).joinToString(" ")
}

/** A result of one answer, scored 100 / 50 / 0. */
fun Verdict.score(): Int = when (this) {
    Verdict.CORRECT -> 100
    Verdict.ALMOST -> 50
    Verdict.WRONG -> 0
}

/** Topic progress: the last [WINDOW] answers decide mastery. */
data class TopicProgress(val attempts: Int, val recentAverage: Int, val mastered: Boolean) {
    /** Answers counted in the window (at most [GrammarBank.WINDOW]). */
    val counted: Int get() = minOf(attempts, GrammarBank.WINDOW)
}

object GrammarBank {
    const val WINDOW = 20
    const val MASTERY = 85
    const val SESSION = 10

    /** Parses one topic's built-in lines (format in GrammarDrillsA). Bad lines are skipped. */
    fun parse(topicId: String, block: String): List<GrammarExercise> = block.lines().mapNotNull { raw ->
        val line = raw.trim()
        if (line.length < 3 || line[1] != '|') return@mapNotNull null
        val p = line.split('|')
        fun alts(s: String) = s.split('~').map { it.trim() }.filter { it.isNotEmpty() }
        when (line[0]) {
            'C' -> if (p.size >= 5) GrammarExercise(topicId, GrammarKind.CHOICE, p[1], options = p[2].split(';').map { it.trim() }, answer = p[3].trim().toIntOrNull() ?: -1, why = p[4])
                .takeIf { it.answer in it.options.indices && "___" in it.prompt } else null
            'G' -> if (p.size >= 5) GrammarExercise(topicId, GrammarKind.GAP, p[1], hint = p[2].ifBlank { null }, answers = alts(p[3]), why = p[4])
                .takeIf { it.answers.isNotEmpty() && "___" in it.prompt } else null
            'F' -> if (p.size >= 4) GrammarExercise(topicId, GrammarKind.FIX, p[1], answers = alts(p[2]), why = p[3]).takeIf { it.answers.isNotEmpty() } else null
            'T' -> if (p.size >= 5) GrammarExercise(topicId, GrammarKind.TRANSFORM, p[2], hint = p[1], answers = alts(p[3]), why = p[4]).takeIf { it.answers.isNotEmpty() } else null
            else -> null
        }
    }

    private val builtIn: Map<String, List<GrammarExercise>> by lazy {
        (GrammarDrillsA.ALL + GrammarDrillsB.ALL).mapValues { (id, block) -> parse(id, block) }
    }

    fun builtIn(topicId: String): List<GrammarExercise> = builtIn[topicId].orEmpty()

    /**
     * A practice round: up to [SESSION] exercises, kinds mixed (test and gap first, then fixing and
     * transforming), the least-recently-seen first so repeats come last.
     */
    fun session(pool: List<GrammarExercise>, recentKeys: List<String>, random: Random, size: Int = SESSION): List<GrammarExercise> {
        val unique = pool.distinctBy { it.key }
        val seenRank = recentKeys.withIndex().associate { (i, k) -> k to i } // later = more recent
        val ordered = unique.shuffled(random).sortedBy { seenRank[it.key] ?: -1 }
        val picked = ordered.take(size)
        return picked.sortedBy { it.kind.ordinal + random.nextDouble() * 1.5 }
    }

    /** Checks a typed or picked answer. */
    fun check(ex: GrammarExercise, input: String, picked: Int = -1): Verdict = when (ex.kind) {
        GrammarKind.CHOICE -> if (picked == ex.answer) Verdict.CORRECT else Verdict.WRONG
        GrammarKind.GAP -> AnswerCheck.judge(input, ex.answers)
        GrammarKind.FIX -> when {
            // The mistake is only punctuation (a missing comma): compare with punctuation.
            ex.answers.any { SentenceCheck.words(it) == SentenceCheck.words(ex.prompt) } ->
                if (ex.answers.any { punctuated(it) == punctuated(input) }) Verdict.CORRECT else Verdict.WRONG
            SentenceCheck.words(input) == SentenceCheck.words(ex.prompt) -> Verdict.WRONG
            else -> best(input, ex.answers)
        }
        GrammarKind.TRANSFORM -> best(input, ex.answers)
    }

    /** Lowercase, single spaces, no space around commas, no end mark — commas still count. */
    private fun punctuated(s: String): String =
        s.lowercase().replace('’', '\'').replace(Regex("\\s+"), " ").replace(Regex("\\s*,\\s*"), ",").trim().trimEnd('.', '!', '?')

    private fun best(input: String, answers: List<String>): Verdict =
        answers.map { SentenceCheck.compare(input, it) }.minByOrNull { it.ordinal } ?: Verdict.WRONG

    /** Scores oldest first → progress; mastered when the last [WINDOW] average ≥ [MASTERY] over a full window. */
    fun progress(scores: List<Int>): TopicProgress {
        val recent = scores.takeLast(WINDOW)
        val avg = if (recent.isEmpty()) 0 else recent.sum() / recent.size
        return TopicProgress(scores.size, avg, recent.size >= WINDOW && avg >= MASTERY)
    }

    /**
     * Is an exercise usable: the right shape for its kind, and its own answers pass its own check
     * (a FIX sentence must also fail as written)? AI-written ones are kept only when this holds.
     */
    fun valid(ex: GrammarExercise): Boolean {
        if (ex.prompt.isBlank() || ex.prompt.length > 220) return false
        return when (ex.kind) {
            GrammarKind.CHOICE -> "___" in ex.prompt && ex.options.size == 4 && ex.answer in ex.options.indices &&
                ex.options.map { it.trim().lowercase() }.toSet().size == 4
            GrammarKind.GAP -> "___" in ex.prompt && ex.answers.isNotEmpty() && ex.answers.all { check(ex, it) == Verdict.CORRECT }
            GrammarKind.FIX -> ex.answers.isNotEmpty() && check(ex, ex.prompt) == Verdict.WRONG && ex.answers.all { check(ex, it) == Verdict.CORRECT }
            GrammarKind.TRANSFORM -> !ex.hint.isNullOrBlank() && ex.answers.isNotEmpty() && ex.answers.all { check(ex, it) == Verdict.CORRECT }
        }
    }

    fun encode(ex: GrammarExercise): String = org.json.JSONObject()
        .put("kind", ex.kind.name)
        .put("prompt", ex.prompt)
        .put("hint", ex.hint ?: "")
        .put("options", org.json.JSONArray(ex.options))
        .put("answer", ex.answer)
        .put("answers", org.json.JSONArray(ex.answers))
        .put("why", ex.why)
        .toString()

    fun decode(topicId: String, json: String): GrammarExercise? = try {
        val o = org.json.JSONObject(json)
        fun list(name: String) = o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()
        GrammarExercise(
            topicId = topicId,
            kind = GrammarKind.valueOf(o.getString("kind")),
            prompt = o.optString("prompt").trim(),
            hint = o.optString("hint").trim().ifBlank { null },
            options = list("options"),
            answer = o.optInt("answer", -1),
            answers = list("answers"),
            why = o.optString("why").trim()
        )
    } catch (e: Exception) {
        null
    }

    /** XP for a grammar answer. */
    fun xp(v: Verdict): Int = when (v) {
        Verdict.CORRECT -> 6
        Verdict.ALMOST -> 3
        Verdict.WRONG -> 0
    }
}
