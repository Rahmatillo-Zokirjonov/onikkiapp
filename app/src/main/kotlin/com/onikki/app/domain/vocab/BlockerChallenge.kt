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
    private val canListen: Boolean = true
) {
    /** Due words first, then studied ones by how soon they're due, then new ones as a last resort. */
    private val order: List<VocabWord> = run {
        val due = Srs.dueQueue(words, today)
        val dueIds = due.map { it.id }.toSet()
        val studied = words.filter { it.id !in dueIds && it.status != WordStatus.NEW }
            .sortedWith(compareBy<VocabWord> { it.dueDate ?: LocalDate.MAX }.thenByDescending { it.lapses + it.wrongCount })
        val fresh = words.filter { it.status == WordStatus.NEW }.shuffled(random)
        due + studied + fresh
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
        val word = order[cursor % order.size]
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
