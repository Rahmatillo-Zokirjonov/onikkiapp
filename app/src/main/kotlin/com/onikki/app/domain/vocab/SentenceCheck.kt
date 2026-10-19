package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord

/** What the AI (or the offline check) said about a sentence the user wrote. */
data class SentenceVerdict(val verdict: Verdict, val corrected: String?, val note: String)

/**
 * Checking typed sentences without a network: a translation against its answer (word by word,
 * contractions expanded, one slip allowed), and an own sentence for "uses the word, long enough".
 */
object SentenceCheck {
    private val CONTRACTIONS = mapOf(
        "i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are", "he's" to "he is",
        "she's" to "she is", "it's" to "it is", "that's" to "that is", "there's" to "there is", "don't" to "do not",
        "doesn't" to "does not", "didn't" to "did not", "can't" to "can not", "cannot" to "can not", "won't" to "will not",
        "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not", "i've" to "i have",
        "we've" to "we have", "they've" to "they have", "i'll" to "i will", "you'll" to "you will", "i'd" to "i would",
        "haven't" to "have not", "hasn't" to "has not", "couldn't" to "could not", "shouldn't" to "should not", "let's" to "let us"
    )

    /** Lowercase words, apostrophes unified, contractions expanded, punctuation gone. */
    fun words(sentence: String): List<String> =
        sentence.lowercase().replace('’', '\'').replace('`', '\'')
            .split(Regex("[^a-z0-9']+")).map { it.trim('\'') }.filter { it.isNotEmpty() }
            .flatMap { (CONTRACTIONS[it] ?: it).split(' ') }

    /** Same words = right; one word missing, extra, or a little misspelt = "deyarli". */
    fun compare(input: String, answer: String): Verdict {
        val a = words(input)
        val b = words(answer)
        if (a.isEmpty()) return Verdict.WRONG
        if (a == b) return Verdict.CORRECT
        val d = wordDistance(a, b)
        val spelling = if (a.size == b.size) a.zip(b).sumOf { (x, y) -> AnswerCheck.distance(x, y) } else Int.MAX_VALUE
        return if (d <= 1 && b.size >= 3 || spelling <= 2 && b.size >= 3) Verdict.ALMOST else Verdict.WRONG
    }

    private fun wordDistance(a: List<String>, b: List<String>): Int {
        val dp = IntArray(b.size + 1) { it }
        for (i in 1..a.size) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.size) {
                val cur = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
        }
        return dp[b.size]
    }

    /** Any form of the word: base, V2, V3, -s / -ed / -ing. */
    fun usesWord(sentence: String, word: VocabWord): Boolean {
        val text = sentence.trim()
        if (text.isEmpty()) return false
        val english = word.english.trim().lowercase()
        if (english.contains(' ')) return words(text).joinToString(" ").contains(words(english).joinToString(" "))
        return Exercises.blank(text, english, word.pastForm, word.participle) != null
    }

    const val MIN_WORDS = 4

    /** Without AI: only what can be checked for sure. Grammar is not judged. */
    fun offline(sentence: String, word: VocabWord): SentenceVerdict = when {
        !usesWord(sentence, word) -> SentenceVerdict(Verdict.WRONG, null, "Gapda \"${word.english}\" so'zi (yoki uning shakli) yo'q.")
        words(sentence).size < MIN_WORDS -> SentenceVerdict(Verdict.WRONG, null, "Gap juda qisqa — kamida $MIN_WORDS ta so'z yozing.")
        else -> SentenceVerdict(Verdict.CORRECT, null, "So'z gapda ishlatilgan. Grammatikani AI tekshirishi uchun API kalit qo'shing.")
    }
}
