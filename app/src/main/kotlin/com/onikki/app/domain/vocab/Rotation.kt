package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import kotlin.random.Random

/**
 * Picks words for a practice round so the same few don't come back every time: words that need work
 * (unknown, due, misspelt…) fill at most half of the round, the rest come from all the other words;
 * inside each group the least recently asked come first, and words asked in the last [RECENT_MS] go last.
 */
object Rotation {
    const val RECENT_MS = 3 * 60 * 60 * 1000L

    fun pick(words: List<VocabWord>, size: Int, nowMillis: Long, random: Random, needsWork: (VocabWord) -> Boolean = { false }): List<VocabWord> {
        if (words.isEmpty() || size <= 0) return emptyList()
        fun recent(w: VocabWord) = w.lastAskedAt > 0 && nowMillis - w.lastAskedAt < RECENT_MS
        fun order(list: List<VocabWord>) = list.shuffled(random).sortedWith(compareBy({ recent(it) }, { it.lastAskedAt }))
        val urgent = order(words.filter { needsWork(it) && !recent(it) }).take((size + 1) / 2)
        val taken = urgent.map { it.id }.toSet()
        val rest = order(words.filter { it.id !in taken }).take(size - urgent.size)
        return (urgent + rest).shuffled(random)
    }
}
