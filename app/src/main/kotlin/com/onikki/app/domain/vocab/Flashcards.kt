package com.onikki.app.domain.vocab

import kotlin.random.Random

enum class FlashDirection(val label: String) { EN_UZ("EN → UZ"), UZ_EN("UZ → EN"), MIXED("Aralash") }

enum class FlashGrade { AGAIN, HARD, GOOD }

/**
 * One card: [wordId] links it to a learning word (its answers then go into the review schedule);
 * [mnemonic] is the user's own memory hook, shown on the back.
 */
data class FlashCard(val english: String, val uzbek: String, val example: String?, val wordId: Long? = null, val mnemonic: String? = null) {
    val key: String get() = AnswerCheck.normalize(english)
}

/**
 * A flashcard run. [queue] holds the cards still to learn, the first is on screen; a card leaves only
 * with "Bildim". [frontUz] are the cards shown Uzbek side first.
 */
data class FlashSession(
    val queue: List<FlashCard>,
    val total: Int,
    val frontUz: Set<String>,
    val flipped: Boolean = false,
    /** Cards graded at least once. */
    val seen: Set<String> = emptySet(),
    /** Known at the first look. */
    val firstTry: Set<String> = emptySet(),
    /** Cards that got "Bilmadim" / "Qiyin" at least once, in order. */
    val struggled: List<FlashCard> = emptyList()
) {
    val current: FlashCard? get() = queue.firstOrNull()
    val finished: Boolean get() = queue.isEmpty()
    val done: Int get() = total - queue.size
    val currentUzFirst: Boolean get() = current?.key in frontUz
}

/** Pure flashcard logic: the queue ("Bilmadim" comes back soon, "Qiyin" a bit later) and Leitner boxes. */
object Flashcards {
    /** A missed card comes back after this many others; a hard one after more. */
    const val AGAIN_GAP = 3
    const val HARD_GAP = 6
    /** A random session over a big set takes this many cards (weakest first). */
    const val SESSION = 20
    const val MAX_BOX = 5

    fun start(cards: List<FlashCard>, direction: FlashDirection, random: Random = Random.Default): FlashSession {
        val unique = cards.distinctBy { it.key }
        val uz = when (direction) {
            FlashDirection.EN_UZ -> emptySet()
            FlashDirection.UZ_EN -> unique.map { it.key }.toSet()
            FlashDirection.MIXED -> unique.filter { random.nextBoolean() }.map { it.key }.toSet()
        }
        return FlashSession(unique, unique.size, uz)
    }

    fun flip(s: FlashSession): FlashSession = s.copy(flipped = !s.flipped)

    fun grade(s: FlashSession, g: FlashGrade): FlashSession {
        val c = s.current ?: return s
        val rest = s.queue.drop(1)
        val queue = when (g) {
            FlashGrade.GOOD -> rest
            FlashGrade.AGAIN -> rest.toMutableList().also { it.add(minOf(AGAIN_GAP, it.size), c) }
            FlashGrade.HARD -> rest.toMutableList().also { it.add(minOf(HARD_GAP, it.size), c) }
        }
        val first = c.key !in s.seen
        return s.copy(
            queue = queue,
            flipped = false,
            seen = s.seen + c.key,
            firstTry = if (first && g == FlashGrade.GOOD) s.firstTry + c.key else s.firstTry,
            struggled = if (g != FlashGrade.GOOD && s.struggled.none { it.key == c.key }) s.struggled + c else s.struggled
        )
    }

    /** Leitner: a miss sends the card back to box 1, "Qiyin" keeps it, "Bildim" moves it up. 0 = never seen. */
    fun nextBox(box: Int, g: FlashGrade): Int = when (g) {
        FlashGrade.AGAIN -> 1
        FlashGrade.HARD -> box.coerceIn(1, MAX_BOX)
        FlashGrade.GOOD -> (box + 1).coerceAtMost(MAX_BOX)
    }

    /** Weakest first: low boxes (never seen counts as 0), shuffled within a box. */
    fun weakestFirst(cards: List<FlashCard>, boxes: Map<String, Int>, random: Random = Random.Default): List<FlashCard> =
        cards.shuffled(random).sortedBy { boxes[it.key] ?: 0 }

    /** Cards seen and still in the low boxes (1–2), plus any [alsoWeak] keys. */
    fun weak(cards: List<FlashCard>, boxes: Map<String, Int>, alsoWeak: Set<String> = emptySet()): List<FlashCard> =
        cards.filter { (boxes[it.key] ?: 0) in 1..2 || it.key in alsoWeak }
}
