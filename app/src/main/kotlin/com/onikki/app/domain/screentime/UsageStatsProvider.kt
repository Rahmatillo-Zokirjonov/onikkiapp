package com.onikki.app.domain.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class AppUsageSnapshot(val packageName: String, val minutesUsed: Int)

/**
 * Thin wrapper over Android's UsageStatsManager (TZ 3.6). Requires the
 * PACKAGE_USAGE_STATS special permission, which the user must grant by hand
 * in Settings > Special app access > Usage access — there is no runtime
 * permission dialog for this.
 *
 * NOT exercised on a real device in this session (no Android toolchain
 * available here) — verify usage numbers look sane once running for real.
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

    /** Aggregated per-app foreground time for [date], in whole minutes. Empty without permission. */
    fun queryUsageForDate(date: LocalDate): List<AppUsageSnapshot> {
        if (!hasUsageAccess()) return emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return emptyList()
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map { AppUsageSnapshot(it.packageName, (it.totalTimeInForeground / 60_000L).toInt()) }
    }

    /** Live minutes used today for one package — used for real-time blocking decisions. */
    fun minutesUsedToday(packageName: String): Int =
        queryUsageForDate(LocalDate.now()).firstOrNull { it.packageName == packageName }?.minutesUsed ?: 0
}
