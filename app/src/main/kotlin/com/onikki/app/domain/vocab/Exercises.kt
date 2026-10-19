package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.screentime.VocabChallenge
import kotlin.random.Random

enum class ExerciseType(val label: String, val hard: Boolean = false) {
    CARD("Yangi so'z"),
    CHOOSE("Tanlash"),
    LISTEN("Tinglab tanlash"),
    MATCH("Juftlash"),
    TYPE("Yozish", hard = true),
    CLOZE_CHOICE("Bo'sh joy"),
    CLOZE_TYPE("Bo'sh joyni yozish", hard = true),
    BUILD("Gap yig'ish", hard = true)
}

/** One screen of a lesson. [wordIds] are the words it practises (Match covers five). */
sealed interface Exercise {
    val type: ExerciseType
    val wordIds: List<Long>

    data class Card(val word: VocabWord) : Exercise {
        override val type get() = ExerciseType.CARD
        override val wordIds get() = listOf(word.id)
    }

    /** Uzbek prompt → pick the English word. */
    data class Choose(val word: VocabWord, val prompt: String, val options: List<String>, val answer: Int) : Exercise {
        override val type get() = ExerciseType.CHOOSE
        override val wordIds get() = listOf(word.id)
    }

    /** The English word is spoken → pick its Uzbek meaning. */
    data class Listen(val word: VocabWord, val spoken: String, val options: List<String>, val answer: Int) : Exercise {
        override val type get() = ExerciseType.LISTEN
        override val wordIds get() = listOf(word.id)
    }

    /** Five English words ↔ their five Uzbek meanings, both columns shuffled. */
    data class Match(val words: List<VocabWord>, val left: List<Long>, val right: List<Long>) : Exercise {
        override val type get() = ExerciseType.MATCH
        override val wordIds get() = words.map { it.id }
    }

    /** Uzbek prompt → type the English word into letter boxes. */
    data class TypeIn(val word: VocabWord, val prompt: String, val answers: List<String>) : Exercise {
        override val type get() = ExerciseType.TYPE
        override val wordIds get() = listOf(word.id)
    }

    /** The example sentence with the word blanked: [before] ___ [after]. */
    data class Cloze(
        val word: VocabWord,
        val before: String,
        val after: String,
        val answer: String,
        /** Empty = type it; otherwise pick one. */
        val options: List<String>
    ) : Exercise {
        override val type get() = if (options.isEmpty()) ExerciseType.CLOZE_TYPE else ExerciseType.CLOZE_CHOICE
        override val wordIds get() = listOf(word.id)
    }

    /** Put the example sentence back together from shuffled blocks (plus a couple of extra words). */
    data class Build(val word: VocabWord, val sentence: String, val tokens: List<String>, val bank: List<String>) : Exercise {
        override val type get() = ExerciseType.BUILD
        override val wordIds get() = listOf(word.id)
    }
}

/** A blanked sentence: text before and after the gap, and the word form that fills it. */
data class Blank(val before: String, val after: String, val answer: String)

/** Builds exercises from words (pure; a seeded Random keeps tests stable). */
object Exercises {
    private const val OPTIONS = 4
    private const val MATCH_SIZE = 5

    fun primary(text: String): String = VocabChallenge.splitAnswers(text).first()

    /**
     * Which exercise fits how far a word has got: 0 → card, 1 → recognise (choose / listen),
     * 2 → recall (type), 3 → use it in its sentence (blank / build). Falls back when the example is missing.
     */
    fun forWord(word: VocabWord, stage: Int, pool: List<VocabWord>, random: Random, canListen: Boolean = true): Exercise = when (stage.coerceIn(0, Srs.MAX_STAGE)) {
        0 -> Exercise.Card(word)
        1 -> if (canListen && random.nextInt(3) == 0) listen(word, pool, random) else choose(word, pool, random)
        2 -> typeIn(word)
        else -> {
            val blank = word.example?.let { blank(it, word.english, word.pastForm) }
            val tokens = word.example?.let(::tokens).orEmpty()
            when {
                blank != null && (tokens.size !in 3..10 || random.nextBoolean()) -> cloze(word, blank, pool, random, typed = random.nextBoolean())
                tokens.size in 3..10 -> build(word, pool, random)
                else -> typeIn(word)
            }
        }
    }

    fun choose(word: VocabWord, pool: List<VocabWord>, random: Random): Exercise.Choose {
        val right = word.english.trim()
        val (options, answer) = withDistractors(right, pool.filter { it.id != word.id }.map { it.english.trim() }, random)
        return Exercise.Choose(word, primary(word.uzbek), options, answer)
    }

    fun listen(word: VocabWord, pool: List<VocabWord>, random: Random): Exercise.Listen {
        val right = primary(word.uzbek)
        val (options, answer) = withDistractors(right, pool.filter { it.id != word.id }.map { primary(it.uzbek) }, random)
        return Exercise.Listen(word, word.english.trim(), options, answer)
    }

    fun match(words: List<VocabWord>, random: Random): Exercise.Match? {
        val picked = words.distinctBy { AnswerCheck.letters(it.english) }.distinctBy { AnswerCheck.letters(primary(it.uzbek)) }.take(MATCH_SIZE)
        if (picked.size < 3) return null
        return Exercise.Match(picked, picked.map { it.id }.shuffled(random), picked.map { it.id }.shuffled(random))
    }

    fun typeIn(word: VocabWord): Exercise.TypeIn =
        Exercise.TypeIn(word, primary(word.uzbek), VocabChallenge.splitAnswers(word.english))

    fun cloze(word: VocabWord, blank: Blank, pool: List<VocabWord>, random: Random, typed: Boolean): Exercise.Cloze {
        if (typed) return Exercise.Cloze(word, blank.before, blank.after, blank.answer, emptyList())
        val others = pool.filter { it.id != word.id }.map { it.pastForm?.takeIf { _ -> blank.answer.equals(word.pastForm, true) } ?: it.english.trim() }
        val (options, _) = withDistractors(blank.answer, others, random)
        return Exercise.Cloze(word, blank.before, blank.after, blank.answer, options)
    }

    fun build(word: VocabWord, pool: List<VocabWord>, random: Random): Exercise.Build {
        val sentence = word.example!!.trim()
        val tokens = tokens(sentence)
        val extra = pool.filter { it.id != word.id }.flatMap { w -> w.example?.let(::tokens).orEmpty() }
            .filter { t -> tokens.none { it.equals(t, ignoreCase = true) } }
            .distinctBy { it.lowercase() }
            .shuffled(random)
            .take(2)
        return Exercise.Build(word, sentence, tokens, (tokens + extra).shuffled(random))
    }

    /** The right option plus up to three different wrong ones, shuffled; returns the options and the right index. */
    private fun withDistractors(right: String, candidates: List<String>, random: Random): Pair<List<String>, Int> {
        val wrong = candidates.filter { AnswerCheck.letters(it) != AnswerCheck.letters(right) && it.isNotBlank() }
            .distinctBy { AnswerCheck.letters(it) }
            .shuffled(random)
            .take(OPTIONS - 1)
        val options = (wrong + right).shuffled(random)
        return options to options.indexOf(right)
    }

    /** Words of a sentence, end punctuation dropped ("I", "decided", "to", "study", "English"). */
    fun tokens(sentence: String): List<String> =
        sentence.trim().split(Regex("""\s+""")).map { it.trim('.', ',', '!', '?', ';', ':', '"') }.filter { it.isNotEmpty() }

    /**
     * Finds [english] (or its past form, or a regular -s / -ed / -ing form) in the sentence and blanks it.
     * Null when the word isn't there or is a phrase.
     */
    fun blank(sentence: String, english: String, pastForm: String?): Blank? {
        val base = english.trim().lowercase()
        if (base.contains(' ')) return null
        val forms = buildSet {
            add(base)
            pastForm?.split(',', '/')?.forEach { add(it.trim().lowercase()) }
            add(base + "s"); add(base + "es"); add(base + "ed"); add(base + "d"); add(base + "ing")
            if (base.endsWith("e")) add(base.dropLast(1) + "ing")
            if (base.endsWith("y")) { add(base.dropLast(1) + "ies"); add(base.dropLast(1) + "ied") }
        }
        val match = Regex("""[A-Za-z']+""").findAll(sentence).firstOrNull { it.value.lowercase() in forms } ?: return null
        return Blank(sentence.substring(0, match.range.first), sentence.substring(match.range.last + 1), match.value)
    }

    // ------------------------------------------------------------ checking

    fun checkChoice(selected: Int, answer: Int): Verdict = if (selected == answer) Verdict.CORRECT else Verdict.WRONG

    fun checkTyped(input: String, answers: List<String>): Verdict = AnswerCheck.judge(input, answers)

    /** Same words in the same order; case and punctuation don't matter. */
    fun checkBuild(chosen: List<String>, tokens: List<String>): Verdict =
        if (chosen.map(AnswerCheck::letters) == tokens.map(AnswerCheck::letters)) Verdict.CORRECT else Verdict.WRONG
}
