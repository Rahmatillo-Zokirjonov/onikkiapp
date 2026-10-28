package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import com.onikki.app.data.local.QuestionType
import java.time.LocalDate
import kotlin.random.Random

/**
 * The app-blocker challenge on top of the learning data: [needed] right answers open the app, at least
 * one of them typed. Words come due-first; a miss shows the answer and the next question takes a
 * different word. Pure state machine — the screen asks [next], reports [answer].
 */
class BlockerChallenge(
    words: List<VocabWord>,
    today: LocalDate,
    val needed: Int,
    private val type: QuestionType,
    private val random: Random,
    private val canListen: Boolean = true,
    private val nowMillis: Long = System.currentTimeMillis()
) {
    /**
     * Rotated so every word gets its turn: due words fill up to half, the rest are the words asked
     * longest ago (never-asked first, new words included); words asked in the last hours come last.
     */
    private val order: List<VocabWord> = run {
        val picked = Rotation.pick(words, words.size, nowMillis, random) { Srs.isDue(it, today) }
        // Rotation shuffles its result; keep "needs work, then oldest" as the order for the screen.
        val due = picked.filter { Srs.isDue(it, today) && nowMillis - it.lastAskedAt >= Rotation.RECENT_MS }
            .sortedBy { it.lastAskedAt }.take((needed + 1) / 2)
        val dueIds = due.map { it.id }.toSet()
        due + picked.filter { it.id !in dueIds }.sortedWith(compareBy({ it.lastAskedAt > 0 && nowMillis - it.lastAskedAt < Rotation.RECENT_MS }, { it.lastAskedAt }))
    }
    private var cursor = 0

    var solved = 0
        private set
    var typedDone = false
        private set
    val passed: Boolean get() = solved >= needed && typedDone

    /** The next exercise; a different word each time (cycling only once every word was used). */
    fun next(): Exercise? {
        if (order.isEmpty() || passed) return null
        var word = order[cursor % order.size]
        cursor++
        val remaining = needed - solved
        // The last open slot must be a typed one if none was typed yet.
        val kind = when {
            !typedDone && remaining <= 1 -> QuestionType.TYPE
            type == QuestionType.MIXED -> listOfNotNull(QuestionType.TYPE, QuestionType.CHOICE, QuestionType.LISTEN.takeIf { canListen })
                .let { it[random.nextInt(it.size)] }
            type == QuestionType.LISTEN && !canListen -> QuestionType.CHOICE
            else -> type
        }
        // A new word is only recognised (choice / listen), never typed from nothing; a slot that must be
        // typed takes the next studied word instead.
        if (word.status == WordStatus.NEW) {
            if (kind == QuestionType.TYPE) {
                (0 until order.size).map { order[(cursor + it) % order.size] }.firstOrNull { it.status != WordStatus.NEW }?.let { word = it }
            }
            if (word.status == WordStatus.NEW && order.size >= 4) {
                return if (canListen && random.nextBoolean()) Exercises.listen(word, order, random) else Exercises.choose(word, order, random)
            }
        }
        return when (kind) {
            QuestionType.TYPE -> typed(word)
            QuestionType.LISTEN -> Exercises.listen(word, order, random)
            else -> if (order.size >= 4) Exercises.choose(word, order, random) else Exercises.typeIn(word)
        }
    }

    /** A typed question: plain typing, strict spelling, the verb's past form, or (with sound) dictation. */
    private fun typed(word: VocabWord): Exercise {
        val options = listOfNotNull<() -> Exercise>(
            { Exercises.typeIn(word) },
            { Exercises.spell(word) },
            if (Exercises.isIrregular(word)) ({ Exercises.verbPast(word, reverse = false) }) else null,
            if (canListen) ({ Exercises.dictation(word, DictationMode.WORD) }) else null
        )
        return options[random.nextInt(options.size)]()
    }

    /** Records how the current exercise went. */
    fun answer(exercise: Exercise, verdict: Verdict) {
        if (verdict == Verdict.WRONG) return
        solved++
        if (exercise.type.hard) typedDone = true
    }
}
