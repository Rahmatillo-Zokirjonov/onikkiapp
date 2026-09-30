package com.onikki.app.domain.screentime

/** A foreground change reported by Android's UsageEvents, reduced to what the timeline needs. */
data class UsageEventLite(val packageName: String, val className: String?, val kind: Kind, val time: Long) {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }
}

/** Foreground time for one period: per app, and split into 24 hourly buckets (overall and per app). */
data class UsageTimelineResult(
    val totalMsByApp: Map<String, Long>,
    val hourlyMs: LongArray,
    val hourlyMsByApp: Map<String, LongArray>
) {
    val totalMs: Long get() = totalMsByApp.values.sum()
}

/**
 * Rebuilds real foreground sessions from resume/pause events. Android's daily aggregate
 * (queryUsageStats INTERVAL_DAILY) uses buckets that don't start at midnight and overlap the previous day,
 * so its totals run high; this counts only time inside [start, end).
 *
 * - An app is in the foreground while any of its activities is resumed.
 * - An app already open at [start] (its first event is a pause) counts from [start].
 * - An app still open at the end counts until min([end], [now]).
 * - Screen off closes every open session.
 */
object UsageTimeline {
    private const val HOUR_MS = 3_600_000L

    fun compute(events: List<UsageEventLite>, start: Long, end: Long, now: Long): UsageTimelineResult {
        val totals = HashMap<String, Long>()
        val hourly = LongArray(24)
        val hourlyByApp = HashMap<String, LongArray>()
        val openSince = HashMap<String, Long>()                  // package → session start
        val resumedActivities = HashMap<String, MutableSet<String>>()
        val seen = HashSet<String>()
        val stop = minOf(end, now)

        fun add(pkg: String, from: Long, to: Long) {
            val a = maxOf(from, start)
            val b = minOf(to, stop)
            if (b <= a) return
            totals[pkg] = (totals[pkg] ?: 0L) + (b - a)
            val perApp = hourlyByApp.getOrPut(pkg) { LongArray(24) }
            var t = a
            while (t < b) {
                val hour = ((t - start) / HOUR_MS).toInt().coerceIn(0, 23)
                val hourEnd = start + (hour + 1) * HOUR_MS
                val slice = minOf(b, hourEnd) - t
                hourly[hour] += slice
                perApp[hour] += slice
                t += slice
            }
        }

        fun close(pkg: String, at: Long) {
            openSince.remove(pkg)?.let { add(pkg, it, at) }
            resumedActivities.remove(pkg)
        }

        for (e in events.sortedBy { it.time }) {
            when (e.kind) {
                UsageEventLite.Kind.RESUMED -> {
                    seen += e.packageName
                    // Another app coming to the front ends whatever else was open (single-window phones).
                    openSince.keys.filter { it != e.packageName }.toList().forEach { close(it, e.time) }
                    resumedActivities.getOrPut(e.packageName) { mutableSetOf() } += (e.className ?: "")
                    if (e.packageName !in openSince) openSince[e.packageName] = e.time
                }
                UsageEventLite.Kind.PAUSED -> {
                    if (e.packageName !in seen) {
                        // Open since before the window started.
                        seen += e.packageName
                        add(e.packageName, start, e.time)
                        continue
                    }
                    val set = resumedActivities[e.packageName]
                    set?.remove(e.className ?: "")
                    if (set.isNullOrEmpty()) close(e.packageName, e.time)
                }
                UsageEventLite.Kind.SCREEN_OFF -> openSince.keys.toList().forEach { close(it, e.time) }
            }
        }
        openSince.keys.toList().forEach { close(it, stop) }
        return UsageTimelineResult(totals, hourly, hourlyByApp)
    }
}
