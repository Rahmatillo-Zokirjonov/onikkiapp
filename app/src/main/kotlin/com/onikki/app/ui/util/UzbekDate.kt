package com.onikki.app.ui.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val monthNamesUz = listOf(
    "yanvar", "fevral", "mart", "aprel", "may", "iyun",
    "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
)

private val weekdayNameMap = mapOf(
    DayOfWeek.MONDAY to "dushanba",
    DayOfWeek.TUESDAY to "seshanba",
    DayOfWeek.WEDNESDAY to "chorshanba",
    DayOfWeek.THURSDAY to "payshanba",
    DayOfWeek.FRIDAY to "juma",
    DayOfWeek.SATURDAY to "shanba",
    DayOfWeek.SUNDAY to "yakshanba"
)

private val weekdayAbbrMap = mapOf(
    DayOfWeek.MONDAY to "Du",
    DayOfWeek.TUESDAY to "Se",
    DayOfWeek.WEDNESDAY to "Ch",
    DayOfWeek.THURSDAY to "Pa",
    DayOfWeek.FRIDAY to "Ju",
    DayOfWeek.SATURDAY to "Sh",
    DayOfWeek.SUNDAY to "Ya"
)

private val monthAbbrUz = listOf("yan", "fev", "mar", "apr", "may", "iyn", "iyl", "avg", "sen", "okt", "noy", "dek")

fun monthNameUz(monthValue: Int): String = monthNamesUz[monthValue - 1]
/** Three-letter month, unique per month (iyun/iyul would both truncate to "iyu"). */
fun monthAbbrUz(monthValue: Int): String = monthAbbrUz[monthValue - 1]
fun weekdayNameUz(dayOfWeek: DayOfWeek): String = weekdayNameMap.getValue(dayOfWeek)
fun weekdayAbbrUz(dayOfWeek: DayOfWeek): String = weekdayAbbrMap.getValue(dayOfWeek)

fun formatFullDateUz(date: LocalDate): String =
    "${date.dayOfMonth}-${monthNameUz(date.monthValue)}, ${weekdayNameUz(date.dayOfWeek)}"

/** "bugun" / "kecha" / "9-sentabr" — for note timestamps and similar. */
fun formatRelativeDateUz(epochMillis: Long): String =
    formatRelativeDateUz(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate())

fun formatRelativeDateUz(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "bugun"
        today.minusDays(1) -> "kecha"
        else -> "${date.dayOfMonth}-${monthNameUz(date.monthValue)}"
    }
}

/** "2s 14d" style — soat/daqiqa, matching the mockup's countdown format. */
fun formatMinutesAsDuration(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}s ${m}d" else "${m}d"
}

/** "02:14:35" live H:MM:SS countdown, for the next-prayer timer. */
fun formatHmsCountdown(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** "Bugun 21:00", "Ertaga 09:00", "3-oktabr 09:00" (year added only when it isn't this year). */
fun formatReminderUz(at: java.time.LocalDateTime, today: LocalDate = LocalDate.now()): String {
    val time = "%02d:%02d".format(at.hour, at.minute)
    val date = at.toLocalDate()
    val day = when (date) {
        today -> "Bugun"
        today.plusDays(1) -> "Ertaga"
        today.minusDays(1) -> "Kecha"
        else -> "${date.dayOfMonth}-${monthNameUz(date.monthValue)}" + if (date.year != today.year) " ${date.year}" else ""
    }
    return "$day $time"
}
