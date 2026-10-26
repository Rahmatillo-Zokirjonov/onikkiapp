package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import java.time.LocalDate

/**
 * The home-screen widget's word: one per hour, picked from the words that need it most — due today,
 * misspelt lately ("qiyin") or on "Bilmaganlarim"; otherwise any studied word; otherwise any word.
 * [skip] moves to the next one (the widget's ↻ button). Pure and deterministic for a given hour.
 */
object WordOfHour {
    fun candidates(words: List<VocabWord>, today: LocalDate, hardIds: Collection<Long>): List<VocabWord> {
        val hard = hardIds.toSet()
        val urgent = words.filter { Srs.isDue(it, today) || it.id in hard || it.unknownSince != null }
            .sortedWith(compareBy<VocabWord>({ if (it.id in hard) 0 else 1 }, { it.dueDate ?: LocalDate.MAX }, { it.id }))
        if (urgent.isNotEmpty()) return urgent
        val studied = words.filter { it.status != WordStatus.NEW }.sortedBy { it.id }
        return studied.ifEmpty { words.sortedBy { it.id } }
    }

    /** [epochHour] = current time in hours; each hour (and each skip) gives the next word in the list. */
    fun pick(words: List<VocabWord>, today: LocalDate, hardIds: Collection<Long>, epochHour: Long, skip: Int = 0): VocabWord? {
        val list = candidates(words, today, hardIds)
        if (list.isEmpty()) return null
        val i = ((epochHour + skip) % list.size).toInt().let { if (it < 0) it + list.size else it }
        return list[i]
    }

    /** A short practice for the widget's word: recognise it, spell it, then hear and write it. */
    fun practice(word: VocabWord, pool: List<VocabWord>, random: kotlin.random.Random, canListen: Boolean = true): List<Exercise> = buildList {
        if (word.status == WordStatus.NEW) add(Exercise.Card(word))
        add(if (canListen) Exercises.listen(word, pool, random) else Exercises.choose(word, pool, random))
        add(Exercises.spell(word))
        if (canListen) add(Exercises.dictation(word, DictationMode.WORD))
        if (Exercises.isIrregular(word)) add(Exercises.verbPast(word, reverse = false))
    }
}
