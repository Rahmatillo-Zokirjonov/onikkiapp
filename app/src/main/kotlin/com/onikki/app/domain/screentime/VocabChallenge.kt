package com.onikki.app.domain.screentime

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.ChallengeDirection
import com.onikki.app.data.local.QuestionType
import com.onikki.app.domain.vocab.AnswerCheck
import com.onikki.app.domain.vocab.Verdict
import java.util.Locale

enum class QuestionKind { TYPE, CHOICE, LISTEN }

/**
 * One question: show [prompt], expect any of [answers]. CHOICE offers [options] (one is right);
 * LISTEN plays the English word and expects it typed (the prompt is its Uzbek meaning, as a hint).
 */
data class ChallengeQuestion(
    val word: VocabWord,
    val prompt: String,
    val answers: List<String>,
    val toUzbek: Boolean,
    val kind: QuestionKind = QuestionKind.TYPE,
    val options: List<String> = emptyList()
) {
    val shownAnswer: String get() = answers.joinToString(" / ")
    /** The spelling used for the letter boxes. */
    val primaryAnswer: String get() = answers.first()
}

/** Pure logic for the unlock challenge: which words to ask and whether an answer counts. */
object VocabChallenge {

    /**
     * Weakest words first (most wrong answers relative to right ones), then the ones not seen for
     * longest — a light spaced-repetition so the challenge actually teaches.
     */
    fun pickQuestions(
        words: List<VocabWord>,
        count: Int,
        direction: ChallengeDirection,
        seed: Long = System.nanoTime(),
        type: QuestionType = QuestionType.TYPE,
        canListen: Boolean = true
    ): List<ChallengeQuestion> {
        if (words.isEmpty()) return emptyList()
        val random = java.util.Random(seed)
        val ranked = words.sortedWith(
            compareByDescending<VocabWord> { it.wrongCount - it.correctCount }
                .thenBy { it.lastAskedAt }
                .thenBy { random.nextInt() }
        )
        // Mix a few random ones in so the same weak words don't always come first.
        val pool = ranked.take((count * 2).coerceAtLeast(count)).shuffled(random)
        // Four options need three other words to borrow wrong answers from.
        val canChoose = words.size >= 4
        return pool.take(count).map { word ->
            val kind = when (type) {
                QuestionType.TYPE -> QuestionKind.TYPE
                QuestionType.CHOICE -> if (canChoose) QuestionKind.CHOICE else QuestionKind.TYPE
                QuestionType.LISTEN -> if (canListen) QuestionKind.LISTEN else QuestionKind.TYPE
                QuestionType.MIXED -> listOfNotNull(QuestionKind.TYPE, QuestionKind.CHOICE.takeIf { canChoose }, QuestionKind.LISTEN.takeIf { canListen })
                    .let { it[random.nextInt(it.size)] }
            }
            if (kind == QuestionKind.LISTEN) {
                return@map ChallengeQuestion(word, word.uzbek.trim(), splitAnswers(word.english), toUzbek = false, kind = kind)
            }
            val toUzbek = when (direction) {
                ChallengeDirection.EN_UZ -> true
                ChallengeDirection.UZ_EN -> false
                ChallengeDirection.MIXED -> random.nextBoolean()
            }
            val base = if (toUzbek) {
                ChallengeQuestion(word, word.english.trim(), splitAnswers(word.uzbek), toUzbek = true, kind = kind)
            } else {
                ChallengeQuestion(word, word.uzbek.trim(), splitAnswers(word.english), toUzbek = false, kind = kind)
            }
            if (kind != QuestionKind.CHOICE) base else {
                val wrong = words.filter { it.id != word.id }
                    .map { other -> splitAnswers(if (toUzbek) other.uzbek else other.english).first() }
                    .filter { AnswerCheck.letters(it) != AnswerCheck.letters(base.primaryAnswer) }
                    .distinct()
                    .shuffled(random)
                    .take(3)
                base.copy(options = (wrong + base.primaryAnswer).shuffled(random))
            }
        }
    }

    /** Lenient check: case, spaces and apostrophes never matter; one slip in a longer word passes as ALMOST. */
    fun judge(input: String, question: ChallengeQuestion): Verdict = AnswerCheck.judge(input, question.answers)

    fun splitAnswers(raw: String): List<String> =
        raw.split(",", "/", ";").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(raw.trim()) }

    fun isCorrect(input: String, question: ChallengeQuestion): Boolean {
        val given = normalize(input)
        return given.isNotEmpty() && question.answers.any { normalize(it) == given }
    }

    fun phraseMatches(input: String, phrase: String): Boolean = normalize(input) == normalize(phrase)

    /**
     * Case, extra spaces, end punctuation and the many apostrophe look-alikes (o' / o‘ / oʻ / o`) don't
     * matter — Uzbek keyboards produce all of them.
     */
    fun normalize(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace(Regex("[‘’ʻʼ`´]"), "'")
        .replace(Regex("[.!?,;:]+$"), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /** Parses pasted lines like "apple - olma", "apple = olma", "apple\tolma" or "apple: olma". */
    fun parseBulk(text: String): List<Pair<String, String>> = text.lines().mapNotNull { line ->
        val parts = line.split(Regex("""\s+[-–—=]\s+|\t|\s*[:=]\s*"""), limit = 2)
        if (parts.size < 2) return@mapNotNull null
        val english = parts[0].trim()
        val uzbek = parts[1].trim()
        if (english.isEmpty() || uzbek.isEmpty()) null else english to uzbek
    }

    /** A starter list so the challenge works out of the box; the user edits or replaces it. */
    val STARTER_WORDS: List<Pair<String, String>> = listOf(
        "apple" to "olma", "book" to "kitob", "water" to "suv", "house" to "uy", "friend" to "do'st",
        "time" to "vaqt", "work" to "ish", "school" to "maktab", "family" to "oila", "bread" to "non",
        "money" to "pul", "city" to "shahar", "road" to "yo'l", "sun" to "quyosh", "moon" to "oy",
        "tree" to "daraxt", "door" to "eshik", "window" to "deraza", "teacher" to "o'qituvchi", "student" to "talaba",
        "beautiful" to "chiroyli", "strong" to "kuchli", "fast" to "tez", "slow" to "sekin", "big" to "katta",
        "small" to "kichik", "to learn" to "o'rganmoq", "to read" to "o'qimoq", "to write" to "yozmoq", "to help" to "yordam bermoq"
    )
}
