package com.onikki.app.domain.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class AppUsageSnapshot(val packageName: String, val minutesUsed: Int)

/**
 * Screen time from Android's usage data (needs the "Usage access" special permission, granted by hand).
 *
 * Totals come from [UsageTimeline] over raw foreground events, not from queryUsageStats' daily
 * aggregates: those buckets don't start at midnight and overlap yesterday, so they over-count, and they
 * can't say *when* in the day the time was spent. The timeline also gives the hourly chart.
 */
class UsageStatsProvider(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Foreground time for [date] (midnight to midnight, up to now), per app and per hour. */
    fun timeline(date: LocalDate): UsageTimelineResult {
        val empty = UsageTimelineResult(emptyMap(), LongArray(24), emptyMap())
        if (!hasUsageAccess()) return empty
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (now <= start) return empty
        cached?.let { (key, at, result) -> if (key == date && now - at < CACHE_MS) return result }

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Start an hour early so an app already open at midnight shows its pause/resume pair.
        val events = runCatching { manager.queryEvents(start - 3_600_000L, minOf(end, now)) }.getOrNull() ?: return empty
        val list = ArrayList<UsageEventLite>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventLite.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEventLite.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventLite.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            list += UsageEventLite(e.packageName.orEmpty(), e.className, kind, e.timeStamp)
        }
        val result = UsageTimeline.compute(list, start, end, now)
        cached = Triple(date, now, result)
        return result
    }

    /** Per-app foreground time for [date], in whole minutes. Empty without permission. */
    fun queryUsageForDate(date: LocalDate): List<AppUsageSnapshot> =
        timeline(date).totalMsByApp
            .filter { it.value >= 60_000L && it.key.isNotEmpty() }
            .map { (pkg, ms) -> AppUsageSnapshot(pkg, (ms / 60_000L).toInt()) }

    /** Live minutes used today for one package — used for real-time blocking decisions. */
    fun minutesUsedToday(packageName: String): Int =
        ((timeline(LocalDate.now()).totalMsByApp[packageName] ?: 0L) / 60_000L).toInt()

    companion object {
        /** Opening several apps in a row shouldn't re-read the whole day's events each time. */
        private const val CACHE_MS = 20_000L
        @Volatile private var cached: Triple<LocalDate, Long, UsageTimelineResult>? = null
    }
}
