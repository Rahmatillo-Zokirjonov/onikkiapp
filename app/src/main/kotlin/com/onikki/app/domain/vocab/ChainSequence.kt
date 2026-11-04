package com.onikki.app.domain.vocab

import kotlin.random.Random

enum class SeqDirection { FORWARD, BACKWARD, MIXED }

/** One run: starting at [anchor], the words at [targets] (indices into the learned chain) are said in order. */
data class SeqPlan(val anchor: Int, val backward: Boolean, val targets: List<Int>)

/**
 * "Ketma-ketlik": saying the learned chain in order from any point, forwards or backwards. Links the
 * user stumbles on ("price → ?") are remembered as weak and runs start near them more often. Pure.
 */
object ChainSequence {
    const val MIN_WORDS = 2
    /** How often a run is placed over a weak link (percent), when there is one. */
    private const val WEAK_SHARE = 60

    /** A link in its direction: "price>future" forwards is a different link from "future>price". */
    fun linkKey(from: ImportedWord, to: ImportedWord): String = WordChain.key(from) + ">" + WordChain.key(to)

    /** Up to [length] words after (or before) [anchor], as far as the chain goes. */
    fun targets(size: Int, anchor: Int, backward: Boolean, length: Int): List<Int> =
        if (backward) (anchor - 1 downTo maxOf(0, anchor - length)).toList()
        else (anchor + 1..minOf(size - 1, anchor + length)).toList()

    /** A run over [words]; [start] fixes the first word (index), otherwise it's chosen (weak links first). */
    fun plan(
        words: List<ImportedWord>,
        direction: SeqDirection,
        length: Int,
        start: Int?,
        weak: Map<String, Int>,
        random: Random = Random.Default
    ): SeqPlan? {
        val size = words.size
        if (size < MIN_WORDS) return null
        var backward = when (direction) {
            SeqDirection.FORWARD -> false
            SeqDirection.BACKWARD -> true
            SeqDirection.MIXED -> random.nextBoolean()
        }
        val anchor = start?.coerceIn(0, size - 1) ?: pickAnchor(words, backward, length, weak, random)
        // From the last word there is nothing ahead (from the first, nothing behind): turn around.
        if (targets(size, anchor, backward, length).isEmpty()) backward = !backward
        return SeqPlan(anchor, backward, targets(size, anchor, backward, length))
    }

    private fun pickAnchor(words: List<ImportedWord>, backward: Boolean, length: Int, weak: Map<String, Int>, random: Random): Int {
        val size = words.size
        val step = if (backward) -1 else 1
        val from = if (backward) 1 until size else 0 until size - 1
        val weakest = from.map { it to (weak[linkKey(words[it], words[it + step])] ?: 0) }.filter { it.second > 0 }
        if (weakest.isNotEmpty() && random.nextInt(100) < WEAK_SHARE) {
            val top = weakest.maxOf { it.second }
            val link = weakest.filter { it.second == top }.random(random).first
            // Start a little before the weak link, so it's said in the flow of the chain.
            val offset = random.nextInt(minOf(3, length.coerceAtLeast(1)))
            return (link - step * offset).coerceIn(0, size - 1)
        }
        return if (backward) random.nextInt(minOf(length, size - 1), size)
        else random.nextInt(0, maxOf(1, size - length))
    }

    /**
     * A typed answer; a small slip counts (it's the order being trained), the spelling is shown — but
     * another chain word written exactly (advise for advice) is the wrong word, not a slip.
     */
    fun judge(input: String, target: ImportedWord, chain: List<ImportedWord> = emptyList()): Verdict {
        val v = AnswerCheck.judge(input, listOf(target.english))
        if (v != Verdict.ALMOST) return v
        val given = AnswerCheck.letters(input)
        return if (chain.any { AnswerCheck.letters(it.english) == given }) Verdict.WRONG else v
    }

    /**
     * Spoken answer: how many of [targets] were said, in order, from the start — several words may come
     * in one breath. A filler word between them is allowed. The best of the recognizer's [alternatives].
     */
    fun matchSpoken(alternatives: List<String>, targets: List<ImportedWord>): Int =
        alternatives.maxOfOrNull { alt -> matchOne(tokens(alt), targets) } ?: 0

    private fun tokens(text: String) = AnswerCheck.normalize(text).split(" ").map(AnswerCheck::letters).filter { it.isNotEmpty() }

    private fun matchOne(heard: List<String>, targets: List<ImportedWord>): Int {
        var cursor = 0
        var n = 0
        for (t in targets) {
            val want = tokens(t.english)
            if (want.isEmpty()) break
            val at = (cursor..minOf(cursor + 1, heard.size - want.size)).firstOrNull { i ->
                want.indices.all { j -> same(heard[i + j], want[j]) }
            } ?: break
            cursor = at + want.size
            n++
        }
        return n
    }

    private fun same(heard: String, want: String) = heard == want || AnswerCheck.judge(heard, listOf(want)) != Verdict.WRONG

    /** A miss makes the link weaker (+1); getting it right heals it (−1, gone at 0). */
    fun updateWeak(weak: Map<String, Int>, key: String, missed: Boolean): Map<String, Int> {
        val n = (weak[key] ?: 0) + if (missed) 1 else -1
        return if (n <= 0) weak - key else weak + (key to minOf(n, 9))
    }
}
