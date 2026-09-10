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

fun monthNameUz(monthValue: Int): String = monthNamesUz[monthValue - 1]
fun weekdayNameUz(dayOfWeek: DayOfWeek): String = weekdayNameMap.getValue(dayOfWeek)
fun weekdayAbbrUz(dayOfWeek: DayOfWeek): String = weekdayAbbrMap.getValue(dayOfWeek)

fun formatFullDateUz(date: LocalDate): String =
    "${date.dayOfMonth}-${monthNameUz(date.monthValue)}, ${weekdayNameUz(date.dayOfWeek)}"

/** "bugun" / "kecha" / "9-sentabr" — for note timestamps and similar. */
fun formatRelativeDateUz(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
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
