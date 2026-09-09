package com.onikki.app.domain.prayer

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer-time calculation using the standard sun-angle method (the
 * widely published praytimes.org algorithm). Fajr/Isha angles follow the
 * Muslim World League convention; Asr uses the Hanafi shadow factor (2),
 * matching common practice in Uzbekistan. No network access, per the TZ.
 *
 * NOT numerically verified against a reference table in this session (no
 * JVM/toolchain available here to run it) — spot-check against a known
 * source for your city before relying on it.
 */
object PrayerTimeCalculator {
    private const val FAJR_ANGLE = 18.0
    private const val ISHA_ANGLE = 17.0
    private const val ASR_SHADOW_FACTOR = 2.0 // Hanafi

    data class PrayerTimes(
        val fajr: LocalTime,
        val sunrise: LocalTime,
        val dhuhr: LocalTime,
        val asr: LocalTime,
        val maghrib: LocalTime,
        val isha: LocalTime
    ) {
        /** The five daily prayers in order, Uzbek names, excluding sunrise. */
        fun asOrderedList(): List<Pair<String, LocalTime>> = listOf(
            "Bomdod" to fajr,
            "Peshin" to dhuhr,
            "Asr" to asr,
            "Shom" to maghrib,
            "Xufton" to isha
        )
    }

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        utcOffsetHours: Double
    ): PrayerTimes {
        val d = julianDate(date) - 2451545.0

        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)

        val declination = kotlin.math.asin(sin(e) * sin(l))
        val rightAscension = fixHour(Math.toDegrees(atan2(cos(e) * sin(l), cos(l))) / 15.0)
        val equationOfTime = q / 15.0 - rightAscension

        val latRad = Math.toRadians(latitude)

        fun hourAngle(angleDeg: Double): Double {
            val angleRad = Math.toRadians(angleDeg)
            val cosT = (-sin(angleRad) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))
            return Math.toDegrees(acos(cosT.coerceIn(-1.0, 1.0))) / 15.0
        }

        fun asrHourAngle(): Double {
            val altitude = atan(1.0 / (ASR_SHADOW_FACTOR + tan(abs(latRad - declination))))
            val cosT = (sin(altitude) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))
            return Math.toDegrees(acos(cosT.coerceIn(-1.0, 1.0))) / 15.0
        }

        val dhuhrLocal = 12.0 + utcOffsetHours - longitude / 15.0 - equationOfTime

        return PrayerTimes(
            fajr = toLocalTime(dhuhrLocal - hourAngle(FAJR_ANGLE)),
            sunrise = toLocalTime(dhuhrLocal - hourAngle(0.833)),
            dhuhr = toLocalTime(dhuhrLocal),
            asr = toLocalTime(dhuhrLocal + asrHourAngle()),
            maghrib = toLocalTime(dhuhrLocal + hourAngle(0.833)),
            isha = toLocalTime(dhuhrLocal + hourAngle(ISHA_ANGLE))
        )
    }

    private fun julianDate(date: LocalDate): Double {
        var year = date.year
        var month = date.monthValue
        val day = date.dayOfMonth
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    private fun fixHour(hours: Double): Double {
        var h = hours % 24.0
        if (h < 0) h += 24.0
        return h
    }

    private fun toLocalTime(hoursDecimal: Double): LocalTime {
        val totalMinutes = (fixHour(hoursDecimal) * 60).roundToInt().coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }
}
