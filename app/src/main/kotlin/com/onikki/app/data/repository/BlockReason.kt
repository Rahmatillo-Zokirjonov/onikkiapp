package com.onikki.app.data.repository

/** Why an app is currently blocked (TZ 3.6 — limit, schedule, or prayer-time buffer). */
sealed class BlockReason {
    data object LimitReached : BlockReason()
    data object WorkHours : BlockReason()
    data class PrayerTime(val prayerName: String) : BlockReason()
}
