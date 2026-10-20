package com.onikki.app.domain.speaking

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.domain.grammar.Grammar
import kotlin.random.Random

/** A sentence to read aloud; [key] identifies it in the history ("w-12", "g-a1-be-0"). */
data class SpeakItem(val key: String, val english: String, val uzbek: String?, val word: String? = null)

/**
 * Read-aloud rounds: the user's own words' example sentences first (unknown and young ones), then the
 * grammar examples of the level. Sentences read well lately (80%+) go to the back.
 */
object SpeakingDeck {
    const val ROUND = 8
    const val OWN_WORDS = 3

    fun round(level: String, words: List<VocabWord>, bestByKey: Map<String, Int>, random: Random, size: Int = ROUND): List<SpeakItem> {
        val own = words
            .filter { it.status != WordStatus.NEW && !it.example.isNullOrBlank() && it.example.split(' ').size in 3..14 }
            .sortedWith(compareBy<VocabWord>({ if (it.unknownSince != null) 0 else 1 }, { it.stage }, { random.nextInt() }))
            .map { SpeakItem("w-${it.id}", it.example!!.trim(), it.exampleUz, it.english) }
            .sortedBy { (bestByKey[it.key] ?: 0) >= 80 }
            .take(OWN_WORDS)
        val grammar = Grammar.forLevel(level).flatMap { t ->
            t.examples.mapIndexed { i, e -> SpeakItem("g-${t.id}-$i", e.english, e.uzbek) }
        }.shuffled(random).sortedBy { (bestByKey[it.key] ?: 0) >= 80 }
        return (own + grammar).distinctBy { it.english.lowercase() }.take(size)
    }
}
