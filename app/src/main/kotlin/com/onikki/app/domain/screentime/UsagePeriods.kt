package com.onikki.app.domain.screentime

import com.onikki.app.data.db.entity.AppUsage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class UsagePeriod(val label: String) { DAY("Kun"), WEEK("Hafta"), MONTH("Oy") }

data class AppTotal(val packageName: String, val appName: String, val minutes: Int, val daysUsed: Int)

data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val total: Int,
    /** One entry per day of the range, oldest first (future days are 0). */
    val dailyTotals: List<Int>,
    val apps: List<AppTotal>,
    /** Days in the range that have any recorded usage — the average is over these, not over gaps. */
    val trackedDays: Int,
    val busiest: Pair<LocalDate, Int>?
) {
    val averagePerDay: Int get() = if (trackedDays == 0) 0 else total / trackedDays
}

/** Pure period maths for the screen-time history (Kun / Hafta / Oy). */
object UsagePeriods {

    fun range(period: UsagePeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        UsagePeriod.DAY -> anchor to anchor
        UsagePeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        UsagePeriod.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    }

    /** The same period [delta] steps away; never past [today]'s period. */
    fun shift(period: UsagePeriod, anchor: LocalDate, delta: Int, today: LocalDate): LocalDate {
        val moved = when (period) {
            UsagePeriod.DAY -> anchor.plusDays(delta.toLong())
            UsagePeriod.WEEK -> anchor.plusWeeks(delta.toLong())
            UsagePeriod.MONTH -> anchor.plusMonths(delta.toLong())
        }
        return if (moved.isAfter(today)) today else moved
    }

    fun isCurrent(period: UsagePeriod, anchor: LocalDate, today: LocalDate): Boolean =
        range(period, anchor) == range(period, today)

    fun summarize(rows: List<AppUsage>, from: LocalDate, to: LocalDate): PeriodSummary {
        val inRange = rows.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val byDay = inRange.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.minutesUsed } }
        val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
        val daily = (0 until days).map { byDay[from.plusDays(it.toLong())] ?: 0 }
        val apps = inRange.groupBy { it.packageName }.map { (pkg, list) ->
            AppTotal(pkg, list.maxByOrNull { it.date }!!.appName, list.sumOf { it.minutesUsed }, list.count { it.minutesUsed > 0 })
        }.filter { it.minutes > 0 }.sortedByDescending { it.minutes }
        return PeriodSummary(
            from = from,
            to = to,
            total = daily.sum(),
            dailyTotals = daily,
            apps = apps,
            trackedDays = byDay.count { it.value > 0 },
            busiest = byDay.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair()
        )
    }

    /** "24,0,3,…" ↔ 24 hourly minute counts (stored per day so past days keep their hourly chart). */
    fun encodeHours(minutes: List<Int>): String = minutes.joinToString(",")

    fun decodeHours(raw: String?): List<Int>? =
        raw?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 24 }
}
