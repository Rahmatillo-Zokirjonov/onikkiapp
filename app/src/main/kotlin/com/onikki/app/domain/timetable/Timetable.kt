package com.onikki.app.domain.timetable

import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import com.onikki.app.data.db.entity.WeekParity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** What's on now and what comes next today. */
data class NowNext(val current: TimetableEntry?, val next: TimetableEntry?, val minutesLeft: Long?, val minutesUntilNext: Long?)

/** Pure timetable rules: which blocks happen on a day, now/next, grid range, clashes. */
object Timetable {

    fun isOddWeek(date: LocalDate): Boolean = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2 == 1

    fun weekNumber(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun occursOn(entry: TimetableEntry, date: LocalDate): Boolean = when (entry.repeat) {
        TimetableRepeat.DAILY -> true
        TimetableRepeat.ONCE -> entry.date == date
        TimetableRepeat.WEEKLY -> entry.dayOfWeek == date.dayOfWeek.value && when (entry.parity) {
            WeekParity.ALL -> true
            WeekParity.ODD -> isOddWeek(date)
            WeekParity.EVEN -> !isOddWeek(date)
        }
    }

    fun on(entries: List<TimetableEntry>, date: LocalDate): List<TimetableEntry> =
        entries.filter { occursOn(it, date) }.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun nowNext(entries: List<TimetableEntry>, now: LocalDateTime): NowNext {
        val today = on(entries, now.toLocalDate())
        val t = now.toLocalTime()
        val current = today.firstOrNull { !t.isBefore(it.start) && t.isBefore(it.end) }
        val next = today.firstOrNull { it.start.isAfter(t) }
        return NowNext(
            current = current,
            next = next,
            minutesLeft = current?.let { ChronoUnit.MINUTES.between(t, it.end) },
            minutesUntilNext = next?.let { ChronoUnit.MINUTES.between(t, it.start) }
        )
    }

    /** Whole hours the week grid shows: from the earliest start to the latest end, at least 8:00–17:00. */
    fun hourRange(entries: List<TimetableEntry>): IntRange {
        if (entries.isEmpty()) return 8..17
        val first = entries.minOf { it.start.hour }
        val last = entries.maxOf { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour }.coerceAtMost(24)
        return minOf(first, 8)..maxOf(last, 17)
    }

    /** Blocks that overlap [candidate] on at least one shared day (checked over the next 14 days). */
    fun clashes(entries: List<TimetableEntry>, candidate: TimetableEntry, from: LocalDate): List<TimetableEntry> {
        val days = (0 until 14).map { from.plusDays(it.toLong()) }.filter { occursOn(candidate, it) }
        return entries.filter { other ->
            other.id != candidate.id &&
                other.start.isBefore(candidate.end) && candidate.start.isBefore(other.end) &&
                days.any { occursOn(other, it) }
        }
    }

    /** Start times of [entry] between [from] and [to] (inclusive days), for reminders. */
    fun occurrences(entry: TimetableEntry, from: LocalDate, to: LocalDate): List<LocalDateTime> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .filter { occursOn(entry, it) }
            .map { it.atTime(entry.start) }
            .toList()

    fun minutes(from: LocalTime, to: LocalTime): Long = ChronoUnit.MINUTES.between(from, to)

    /** "204" → "204-xona"; anything else ("A-12", "Sport zal") is shown as typed. */
    fun roomLabel(place: String?): String? {
        val p = place?.trim().orEmpty()
        if (p.isEmpty()) return null
        return if (p.all { it.isDigit() }) "$p-xona" else p
    }

    /** The room and colour last used for a title, to prefill when the same lesson is added again. */
    fun lastUsed(entries: List<TimetableEntry>, title: String): TimetableEntry? =
        entries.filter { it.title.equals(title.trim(), ignoreCase = true) }.maxByOrNull { it.id }
}
