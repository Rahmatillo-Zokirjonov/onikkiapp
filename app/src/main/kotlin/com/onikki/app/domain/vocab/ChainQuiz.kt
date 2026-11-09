package com.onikki.app.domain.vocab

import kotlin.random.Random

enum class ChainQuestionKind { NEXT, PREVIOUS, AT_POSITION, POSITION_OF }

/**
 * A question about the order of the chain. [given] is the word shown (NEXT / PREVIOUS / POSITION_OF),
 * [target] the index of the word asked for; POSITION_OF expects [target] + 1 as a number.
 */
data class ChainQuestion(val kind: ChainQuestionKind, val given: Int?, val target: Int) {
    val numeric: Boolean get() = kind == ChainQuestionKind.POSITION_OF
}

/**
 * The app-blocker's chain questions: [needed] right answers over the learned chain [words]. Weak links
 * (from the sequence practice) are asked more; a miss shows the answer and the next question is a new
 * one. Pure state machine like BlockerChallenge.
 */
class ChainQuiz(
    private val words: List<ImportedWord>,
    val needed: Int,
    private val weak: Map<String, Int>,
    private val random: Random
) {
    var solved = 0
        private set
    val passed: Boolean get() = solved >= needed
    private val used = HashSet<Pair<ChainQuestionKind, Int>>()

    fun next(): ChainQuestion? {
        if (words.size < ChainSequence.MIN_WORDS || passed) return null
        repeat(20) {
            val q = candidate()
            if (used.add(q.kind to q.target)) return q
        }
        return candidate()
    }

    private fun candidate(): ChainQuestion {
        val size = words.size
        val weakLinks = (0 until size - 1).flatMap { i ->
            listOf(
                ChainQuestion(ChainQuestionKind.NEXT, i, i + 1) to (weak[ChainSequence.linkKey(words[i], words[i + 1])] ?: 0),
                ChainQuestion(ChainQuestionKind.PREVIOUS, i + 1, i) to (weak[ChainSequence.linkKey(words[i + 1], words[i])] ?: 0)
            )
        }.filter { it.second > 0 }
        if (weakLinks.isNotEmpty() && random.nextInt(100) < 50) {
            val top = weakLinks.maxOf { it.second }
            return weakLinks.filter { it.second == top }.random(random).first
        }
        return when (ChainQuestionKind.entries[random.nextInt(4)]) {
            ChainQuestionKind.NEXT -> random.nextInt(size - 1).let { ChainQuestion(ChainQuestionKind.NEXT, it, it + 1) }
            ChainQuestionKind.PREVIOUS -> (1 + random.nextInt(size - 1)).let { ChainQuestion(ChainQuestionKind.PREVIOUS, it, it - 1) }
            ChainQuestionKind.AT_POSITION -> ChainQuestion(ChainQuestionKind.AT_POSITION, null, random.nextInt(size))
            ChainQuestionKind.POSITION_OF -> random.nextInt(size).let { ChainQuestion(ChainQuestionKind.POSITION_OF, it, it) }
        }
    }

    fun word(i: Int): ImportedWord = words[i]

    /** The answer as shown after a miss. */
    fun answerText(q: ChainQuestion): String = if (q.numeric) "${q.target + 1}-o'rin" else words[q.target].english

    fun judge(input: String, q: ChainQuestion): Verdict =
        if (q.numeric) {
            if (input.filter(Char::isDigit).toIntOrNull() == q.target + 1) Verdict.CORRECT else Verdict.WRONG
        } else ChainSequence.judge(input, words[q.target], words)

    /** Records the answer; for a next / previous question returns the link to update in the weak list. */
    fun answer(q: ChainQuestion, verdict: Verdict): String? {
        if (verdict != Verdict.WRONG) solved++
        return q.given?.takeIf { q.kind == ChainQuestionKind.NEXT || q.kind == ChainQuestionKind.PREVIOUS }
            ?.let { ChainSequence.linkKey(words[it], words[q.target]) }
    }

    val size: Int get() = words.size
}
