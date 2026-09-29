package com.onikki.app.domain.screentime

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.ChallengeDirection
import java.util.Locale

/** One question: show [prompt], expect any of [answers]. */
data class ChallengeQuestion(val word: VocabWord, val prompt: String, val answers: List<String>, val toUzbek: Boolean) {
    val shownAnswer: String get() = answers.joinToString(" / ")
}

/** Pure logic for the unlock challenge: which words to ask and whether an answer counts. */
object VocabChallenge {

    /**
     * Weakest words first (most wrong answers relative to right ones), then the ones not seen for
     * longest — a light spaced-repetition so the challenge actually teaches.
     */
    fun pickQuestions(words: List<VocabWord>, count: Int, direction: ChallengeDirection, seed: Long = System.nanoTime()): List<ChallengeQuestion> {
        if (words.isEmpty()) return emptyList()
        val random = java.util.Random(seed)
        val ranked = words.sortedWith(
            compareByDescending<VocabWord> { it.wrongCount - it.correctCount }
                .thenBy { it.lastAskedAt }
                .thenBy { random.nextInt() }
        )
        // Mix a few random ones in so the same weak words don't always come first.
        val pool = ranked.take((count * 2).coerceAtLeast(count)).shuffled(random)
        return pool.take(count).map { word ->
            val toUzbek = when (direction) {
                ChallengeDirection.EN_UZ -> true
                ChallengeDirection.UZ_EN -> false
                ChallengeDirection.MIXED -> random.nextBoolean()
            }
            if (toUzbek) {
                ChallengeQuestion(word, word.english.trim(), splitAnswers(word.uzbek), toUzbek = true)
            } else {
                ChallengeQuestion(word, word.uzbek.trim(), splitAnswers(word.english), toUzbek = false)
            }
        }
    }

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
