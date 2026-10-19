package com.onikki.app.domain.vocab

import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.db.entity.WordStatus
import java.time.LocalDate

/** One day of practice (from the answer log). */
data class DayPractice(val day: LocalDate, val xp: Int, val answers: Int, val correct: Int)

data class LearnStats(
    val total: Int = 0,
    val new: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
    val mastered: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    /** Oldest first, exactly 7 days ending today (missing days are 0). */
    val week: List<DayPractice> = emptyList(),
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val dueThisWeek: Int = 0
) {
    /** Words past the first card: learning + review + mastered. */
    val learned: Int get() = learning + review + mastered
    val today: DayPractice? get() = week.lastOrNull()
    val weekXp: Int get() = week.sumOf { it.xp }
    /** 0..100 over the week's answers; null with no answers. */
    val weekAccuracy: Int? get() = week.sumOf { it.answers }.takeIf { it > 0 }?.let { n -> week.sumOf { it.correct } * 100 / n }
}

object VocabStats {
    fun build(words: List<VocabWord>, days: List<DayPractice>, activeDays: Set<LocalDate>, today: LocalDate): LearnStats {
        val byDay = days.associateBy { it.day }
        val week = (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            byDay[d] ?: DayPractice(d, 0, 0, 0)
        }
        val studied = words.filter { it.status != WordStatus.NEW && it.dueDate != null }
        return LearnStats(
            total = words.size,
            new = words.count { it.status == WordStatus.NEW },
            learning = words.count { it.status == WordStatus.LEARNING },
            review = words.count { it.status == WordStatus.REVIEW },
            mastered = words.count { it.status == WordStatus.MASTERED },
            streak = com.onikki.app.domain.notes.Journal.streak(activeDays, today),
            bestStreak = bestStreak(activeDays),
            week = week,
            dueToday = studied.count { !it.dueDate!!.isAfter(today) },
            dueTomorrow = studied.count { it.dueDate == today.plusDays(1) },
            dueThisWeek = studied.count { it.dueDate!!.isAfter(today) && !it.dueDate.isAfter(today.plusDays(7)) }
        )
    }

    /** The longest run of consecutive practice days ever. */
    fun bestStreak(days: Set<LocalDate>): Int {
        var best = 0
        days.forEach { d ->
            if (d.minusDays(1) in days) return@forEach // only start counting at the first day of a run
            var len = 1
            while (d.plusDays(len.toLong()) in days) len++
            best = maxOf(best, len)
        }
        return best
    }
}
