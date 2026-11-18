package com.onikki.app.domain.vocab

import kotlin.random.Random

/** The steps of learning a day's words, easiest first; the last one is the day's check. */
enum class PathStep(val label: String) {
    CARDS("Kartalar"),
    CHOOSE("Tanlash"),
    MATCH("Juftlash"),
    LISTEN("Eshitib yozish"),
    WRITE("Xotiradan yozish")
}

/** Pick the English word for [answer]'s meaning out of [options]. */
data class ChoiceQuestion(val answer: ImportedWord, val options: List<String>)

/**
 * "O'rganish yo'li" for the chain: a day's new words go from recognising (cards, choosing, matching)
 * to producing (writing what is heard, then from memory). Also which earlier days come back for spaced
 * review. Pure.
 */
object ChainPath {
    /** Days before today whose words are reviewed: 1, 3 and 7 days ago (spaced repetition). */
    val REVIEW_GAPS = listOf(1, 3, 7)
    /** The matching board is filled up to this many pairs with earlier chain words. */
    const val MATCH_PAIRS = 5

    fun reviewDays(today: Int): List<Int> = REVIEW_GAPS.map { today - it }.filter { it >= 1 }

    /** One question per word; three wrong options from [others] (or the curated list when short). */
    fun choices(words: List<ImportedWord>, others: List<ImportedWord>, random: Random = Random.Default): List<ChoiceQuestion> =
        words.shuffled(random).map { choice(it, words + others, random) }

    fun choice(word: ImportedWord, others: List<ImportedWord>, random: Random = Random.Default): ChoiceQuestion {
        val key = WordChain.key(word)
        val wrong = (others + WordChain.WORDS).map { it.english }
            .filter { AnswerCheck.normalize(it) != key }
            .distinctBy(AnswerCheck::normalize)
            .shuffled(random)
            .take(3)
        return ChoiceQuestion(word, (wrong + word.english).shuffled(random))
    }

    fun isRight(q: ChoiceQuestion, picked: String): Boolean = AnswerCheck.normalize(picked) == WordChain.key(q.answer)

    /** The words to match: the day's words, then the latest earlier ones up to [MATCH_PAIRS]. */
    fun matchWords(words: List<ImportedWord>, earlier: List<ImportedWord>): List<ImportedWord> {
        val keys = words.map(WordChain::key).toSet()
        return (words + earlier.asReversed().filter { WordChain.key(it) !in keys }).distinctBy(WordChain::key).take(maxOf(words.size, MATCH_PAIRS))
    }

    /** The right column's order: a shuffle that (with 2+ words) never leaves every pair side by side. */
    fun matchOrder(size: Int, random: Random = Random.Default): List<Int> {
        if (size < 2) return List(size) { it }
        var order: List<Int>
        do order = (0 until size).shuffled(random) while (order == (0 until size).toList())
        return order
    }
}
