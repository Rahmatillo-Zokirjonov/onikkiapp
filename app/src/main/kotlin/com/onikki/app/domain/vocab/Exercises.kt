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
    BUILD("Gap yig'ish", hard = true),
    FORM_CHOICE("Fe'l shaklini tanlash"),
    FORMS("V2 va V3 ni yozish", hard = true),
    DICTATE("Eshitib yozish", hard = true),
    TRANSLATE("Gapni tarjima qilish", hard = true),
    COMPOSE("So'zni gapda ishlatish", hard = true),
    SPELL("Yozilish", hard = true),
    VERB_PAST("Fe'lning o'tgan shakli", hard = true),
    DICTATION("Diktant", hard = true)
}

/** Dictation levels: (a) the word, (b) the word then its example, (c) only the example. */
enum class DictationMode(val label: String) { WORD("Bitta so'z"), WORD_SENTENCE("So'z + misol gap"), SENTENCE("Faqat misol gap") }

/** Which verb form an exercise asks for. */
enum class VerbForm(val label: String, val hint: String) {
    PAST("V2", "Past Simple — o'tgan zamon"),
    PARTICIPLE("V3", "Past Participle — 3-shakl")
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

    /** An irregular verb: base form + meaning → pick its V2 or V3 among look-alikes ("goed", "gone"…). */
    data class FormChoice(val word: VocabWord, val form: VerbForm, val options: List<String>, val answer: Int) : Exercise {
        override val type get() = ExerciseType.FORM_CHOICE
        override val wordIds get() = listOf(word.id)
    }

    /** The Uzbek translation of the example → type the English sentence. */
    data class Translate(val word: VocabWord, val uzbek: String, val answer: String) : Exercise {
        override val type get() = ExerciseType.TRANSLATE
        override val wordIds get() = listOf(word.id)
    }

    /**
     * Spelling: the Uzbek meaning (and the example with the word hidden) → write the English word,
     * strictly letter by letter; a miss is rewritten three times.
     */
    data class Spell(val word: VocabWord, val prompt: String, val clue: String?, val answers: List<String>) : Exercise {
        override val type get() = ExerciseType.SPELL
        override val wordIds get() = listOf(word.id)
    }

    /** Irregular verb: base → past (V2), or with [reverse] past → base. */
    data class VerbPast(val word: VocabWord, val reverse: Boolean, val prompt: String, val answers: List<String>) : Exercise {
        override val type get() = ExerciseType.VERB_PAST
        override val wordIds get() = listOf(word.id)
    }

    /** Dictation: the text is only heard; [sentence] is the example for the sentence levels. */
    data class Dictation(val word: VocabWord, val mode: DictationMode, val sentence: String?) : Exercise {
        override val type get() = ExerciseType.DICTATION
        override val wordIds get() = listOf(word.id)
        val answers: List<String> get() = VocabChallenge.splitAnswers(word.english)
    }

    /** Write an own sentence with the word; checked by the AI when there is a key, else offline. */
    data class Compose(val word: VocabWord) : Exercise {
        override val type get() = ExerciseType.COMPOSE
        override val wordIds get() = listOf(word.id)
    }

    /** The English word is spoken → type what you heard (letter boxes). */
    data class Dictate(val word: VocabWord, val answers: List<String>) : Exercise {
        override val type get() = ExerciseType.DICTATE
        override val wordIds get() = listOf(word.id)
    }

    /** An irregular verb: type both V2 and V3. */
    data class Forms(val word: VocabWord, val past: List<String>, val participle: List<String>) : Exercise {
        override val type get() = ExerciseType.FORMS
        override val wordIds get() = listOf(word.id)
    }

    /**
     * Put the example sentence back together from shuffled blocks (plus a couple of extra words): heard,
     * or — when [translation] is set — from its Uzbek translation.
     */
    data class Build(val word: VocabWord, val sentence: String, val tokens: List<String>, val bank: List<String>, val translation: String? = null) : Exercise {
        override val type get() = ExerciseType.BUILD
        override val wordIds get() = listOf(word.id)
    }
}

/** An irregular verb shown in its base form → pick its V2 or V3. */
data class FormChoiceData(val form: VerbForm, val options: List<String>, val answer: Int)

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
    fun forWord(word: VocabWord, stage: Int, pool: List<VocabWord>, random: Random, canListen: Boolean = true): Exercise {
        // Irregular verbs: about half of their questions are about the forms.
        val s = stage.coerceIn(0, Srs.MAX_STAGE)
        if (isIrregular(word) && s >= 1 && random.nextInt(2) == 0) {
            return if (s == 1) formChoice(word, pool, random) else forms(word)
        }
        return plainForWord(word, s, pool, random, canListen)
    }

    private fun plainForWord(word: VocabWord, stage: Int, pool: List<VocabWord>, random: Random, canListen: Boolean): Exercise = when (stage) {
        0 -> Exercise.Card(word)
        1 -> if (canListen && random.nextInt(3) == 0) listen(word, pool, random) else choose(word, pool, random)
        2 -> if (canListen && random.nextInt(3) == 0) dictate(word) else typeIn(word)
        else -> {
            val blank = word.example?.let { blank(it, word.english, word.pastForm, word.participle) }
            val tokens = word.example?.let(::tokens).orEmpty()
            val translatable = word.exampleUz != null && tokens.size in 3..12
            when {
                // With a translation, a third of the sentence questions go Uzbek → English.
                translatable && random.nextInt(3) == 0 -> if (random.nextBoolean()) build(word, pool, random, translated = true) else translate(word)
                blank != null && (tokens.size !in 3..10 || random.nextBoolean()) -> cloze(word, blank, pool, random, typed = random.nextBoolean())
                tokens.size in 3..10 -> build(word, pool, random)
                else -> typeIn(word)
            }
        }
    }

    /** Has both irregular forms (V2 and V3). */
    fun isIrregular(word: VocabWord): Boolean = !word.pastForm.isNullOrBlank() && !word.participle.isNullOrBlank()

    /** The wrong "regular" form learners make up: go → goed, make → maked, try → tried. */
    fun regularized(base: String): String {
        val b = base.trim().lowercase()
        return when {
            b.endsWith("e") -> b + "d"
            b.endsWith("y") && b.length > 2 && b[b.length - 2] !in "aeiou" -> b.dropLast(1) + "ied"
            else -> b + "ed"
        }
    }

    fun formChoice(word: VocabWord, pool: List<VocabWord>, random: Random, form: VerbForm? = null): Exercise.FormChoice {
        val f = form ?: if (random.nextBoolean()) VerbForm.PAST else VerbForm.PARTICIPLE
        val past = primary(word.pastForm.orEmpty())
        val participle = primary(word.participle.orEmpty())
        val right = if (f == VerbForm.PAST) past else participle
        val other = if (f == VerbForm.PAST) participle else past
        // Look-alikes first (the other form, the made-up regular one, the base), then other verbs' forms.
        val near = listOf(other, regularized(word.english), word.english.trim().lowercase(), past + "ed")
            .filter { AnswerCheck.letters(it) != AnswerCheck.letters(right) }
            .distinctBy { AnswerCheck.letters(it) }
        val far = pool.filter { it.id != word.id && isIrregular(it) }.map { primary(if (f == VerbForm.PAST) it.pastForm!! else it.participle!!) }
        val wrong = (near.take(2) + (near.drop(2) + far).shuffled(random))
            .filter { AnswerCheck.letters(it) != AnswerCheck.letters(right) }
            .distinctBy { AnswerCheck.letters(it) }
            .take(OPTIONS - 1)
        val options = (wrong + right).shuffled(random)
        return Exercise.FormChoice(word, f, options, options.indexOf(right))
    }

    fun forms(word: VocabWord): Exercise.Forms =
        Exercise.Forms(word, VocabChallenge.splitAnswers(word.pastForm.orEmpty()), VocabChallenge.splitAnswers(word.participle.orEmpty()))

    /** Both forms right = right; one "deyarli" = deyarli; any wrong = wrong. */
    fun checkForms(past: String, participle: String, ex: Exercise.Forms): Pair<Verdict, Verdict> =
        checkTyped(past, ex.past) to checkTyped(participle, ex.participle)

    fun combine(a: Verdict, b: Verdict): Verdict = if (a.ordinal >= b.ordinal) a else b

    /** A verb drill: each verb's forms, as a choice while it's new, typed once it's known. */
    fun verbDrill(verbs: List<VocabWord>, pool: List<VocabWord>, random: Random): List<Exercise> =
        verbs.filter(::isIrregular).map { w -> if (w.stage >= 2) forms(w) else formChoice(w, pool, random) }

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

    /** The example with the word replaced by a gap, so it hints at use without giving the spelling away. */
    fun clueFor(word: VocabWord): String? {
        val example = word.example ?: return null
        val b = blank(example, word.english, word.pastForm, word.participle) ?: return null
        return b.before + "_".repeat(4) + b.after
    }

    fun spell(word: VocabWord): Exercise.Spell =
        Exercise.Spell(word, primary(word.uzbek), clueFor(word), VocabChallenge.splitAnswers(word.english))

    fun verbPast(word: VocabWord, reverse: Boolean): Exercise.VerbPast =
        if (reverse) Exercise.VerbPast(word, true, primary(word.pastForm.orEmpty()), VocabChallenge.splitAnswers(word.english))
        else Exercise.VerbPast(word, false, word.english.trim(), VocabChallenge.splitAnswers(word.pastForm.orEmpty()))

    /** Falls back to the word alone when there's no example (or it's too long to write down). */
    fun dictation(word: VocabWord, mode: DictationMode): Exercise.Dictation {
        val sentence = word.example?.trim()?.takeIf { it.split(' ').size in 2..14 }
        return Exercise.Dictation(word, if (sentence == null) DictationMode.WORD else mode, sentence)
    }

    fun dictate(word: VocabWord): Exercise.Dictate = Exercise.Dictate(word, VocabChallenge.splitAnswers(word.english))

    fun typeIn(word: VocabWord): Exercise.TypeIn =
        Exercise.TypeIn(word, primary(word.uzbek), VocabChallenge.splitAnswers(word.english))

    fun cloze(word: VocabWord, blank: Blank, pool: List<VocabWord>, random: Random, typed: Boolean): Exercise.Cloze {
        if (typed) return Exercise.Cloze(word, blank.before, blank.after, blank.answer, emptyList())
        val others = pool.filter { it.id != word.id }.map { it.pastForm?.takeIf { _ -> blank.answer.equals(word.pastForm, true) } ?: it.english.trim() }
        val (options, _) = withDistractors(blank.answer, others, random)
        return Exercise.Cloze(word, blank.before, blank.after, blank.answer, options)
    }

    fun translate(word: VocabWord): Exercise.Translate = Exercise.Translate(word, word.exampleUz!!.trim(), word.example!!.trim())

    /**
     * "Gap tuzish": per word, its sentence from Uzbek (blocks while it's young, typed later), then the
     * word in an own sentence. Words without a translated example only get the own sentence.
     */
    fun sentencePractice(words: List<VocabWord>, pool: List<VocabWord>, random: Random): List<Exercise> = words.flatMap { w ->
        val tokens = w.example?.let(::tokens).orEmpty()
        listOfNotNull(
            if (w.exampleUz != null && tokens.size in 3..12) (if (w.stage >= 3) translate(w) else build(w, pool, random, translated = true)) else null,
            Exercise.Compose(w)
        )
    }

    fun build(word: VocabWord, pool: List<VocabWord>, random: Random, translated: Boolean = false): Exercise.Build {
        val sentence = word.example!!.trim()
        val tokens = tokens(sentence)
        val extra = pool.filter { it.id != word.id }.flatMap { w -> w.example?.let(::tokens).orEmpty() }
            .filter { t -> tokens.none { it.equals(t, ignoreCase = true) } }
            .distinctBy { it.lowercase() }
            .shuffled(random)
            .take(2)
        return Exercise.Build(word, sentence, tokens, (tokens + extra).shuffled(random), word.exampleUz?.trim()?.takeIf { translated })
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
    fun blank(sentence: String, english: String, pastForm: String?, participle: String? = null): Blank? {
        val base = english.trim().lowercase()
        if (base.contains(' ')) return null
        val forms = buildSet {
            add(base)
            pastForm?.split(',', '/')?.forEach { add(it.trim().lowercase()) }
            participle?.split(',', '/')?.forEach { add(it.trim().lowercase()) }
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
