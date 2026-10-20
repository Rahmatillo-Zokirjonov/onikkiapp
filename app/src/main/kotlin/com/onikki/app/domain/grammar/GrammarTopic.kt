package com.onikki.app.domain.grammar

/** An English example and its Uzbek translation. */
data class GrammarExample(val english: String, val uzbek: String)

/**
 * One grammar topic: [title] is the English name ("Present Simple"), [titleUz] the Uzbek one,
 * [formula] the pattern at a glance, [explanation] short Uzbek paragraphs ("• " lines are bullets).
 */
data class GrammarTopic(
    val id: String,
    val level: String,
    val title: String,
    val titleUz: String,
    val formula: String,
    val explanation: String,
    val examples: List<GrammarExample>
)

internal fun topic(id: String, level: String, title: String, titleUz: String, formula: String, explanation: String, vararg examples: Pair<String, String>) =
    GrammarTopic(id, level, title, titleUz, formula, explanation.trimIndent(), examples.map { GrammarExample(it.first, it.second) })

object Grammar {
    val LEVELS = listOf("A1", "A2", "B1", "B2")

    val TOPICS: List<GrammarTopic> by lazy { GrammarA.TOPICS + GrammarB.TOPICS }

    fun forLevel(level: String): List<GrammarTopic> = TOPICS.filter { it.level == level }

    fun byId(id: String): GrammarTopic? = TOPICS.firstOrNull { it.id == id }
}
