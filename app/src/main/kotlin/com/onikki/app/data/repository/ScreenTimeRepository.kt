package com.onikki.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.onikki.app.data.db.dao.AppLimitDao
import com.onikki.app.data.db.dao.AppUsageDao
import com.onikki.app.data.db.dao.DailyReviewDao
import com.onikki.app.data.db.entity.AppLimit
import com.onikki.app.data.db.entity.AppUsage
import com.onikki.app.domain.prayer.DefaultLocation
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import com.onikki.app.domain.screentime.UsageStatsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt

/** Schedule-based blocking rules a user can attach to an app limit (TZ 3.6). */
enum class BlockRuleFlag { WORK_HOURS, PRAYER_TIMES, UNTIL_DAY_REVIEW }

fun parseBlockRules(raw: String?): Set<BlockRuleFlag> =
    raw?.split(",")
        ?.mapNotNull { token -> runCatching { BlockRuleFlag.valueOf(token) }.getOrNull() }
        ?.toSet()
        ?: emptySet()

fun encodeBlockRules(rules: Set<BlockRuleFlag>): String? =
    rules.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }

data class AppUsageRow(
    val packageName: String,
    val appName: String,
    val minutesUsed: Int,
    val limit: AppLimit?
)

data class ScreenTimeOverview(
    val totalMinutesToday: Int,
    val averageMinutesLast7Days: Int,
    val dailyTotalsLast7Days: List<Int>,
    val apps: List<AppUsageRow>,
    val hasUsageAccess: Boolean
)

private val WORK_START: LocalTime = LocalTime.of(9, 0)
private val WORK_END: LocalTime = LocalTime.of(18, 0)
private const val PRAYER_BUFFER_MINUTES = 20L

class ScreenTimeRepository(
    private val context: Context,
    private val appUsageDao: AppUsageDao,
    private val appLimitDao: AppLimitDao,
    private val dailyReviewDao: DailyReviewDao
) {
    private val usageStatsProvider = UsageStatsProvider(context)

    fun hasUsageAccess(): Boolean = usageStatsProvider.hasUsageAccess()

    /** Pulls today's usage from UsageStatsManager into Room, resolving display names. No-op without permission. */
    suspend fun syncToday() {
        if (!hasUsageAccess()) return
        val today = LocalDate.now()
        usageStatsProvider.queryUsageForDate(today).forEach { snapshot ->
            if (snapshot.packageName == context.packageName) return@forEach
            val label = resolveAppName(snapshot.packageName) ?: return@forEach
            appUsageDao.upsert(
                AppUsage(
                    packageName = snapshot.packageName,
                    appName = label,
                    date = today,
                    minutesUsed = snapshot.minutesUsed
                )
            )
        }
    }

    private fun resolveAppName(packageName: String): String? = try {
        val pm = context.packageManager
        // Skip packages with no launcher entry (services/components) — keep the list to real apps.
        if (pm.getLaunchIntentForPackage(packageName) == null) {
            null
        } else {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun observeOverview(today: LocalDate): Flow<ScreenTimeOverview> {
        val weekStart = today.minusDays(6)
        return combine(
            appUsageDao.observeByDate(today),
            appUsageDao.observeDailyTotals(weekStart, today),
            appLimitDao.observeAll()
        ) { todayUsage, dailyTotals, limits ->
            val limitByPackage = limits.associateBy { it.packageName }
            val totalsByDate = dailyTotals.associate { it.date to it.total }
            val last7 = (0..6).map { offset -> totalsByDate[weekStart.plusDays(offset.toLong())] ?: 0 }
            val average = if (last7.size > 1) last7.dropLast(1).average().roundToInt() else 0
            ScreenTimeOverview(
                totalMinutesToday = last7.last(),
                averageMinutesLast7Days = average,
                dailyTotalsLast7Days = last7,
                apps = todayUsage.map { usage ->
                    AppUsageRow(usage.packageName, usage.appName, usage.minutesUsed, limitByPackage[usage.packageName])
                },
                hasUsageAccess = hasUsageAccess()
            )
        }
    }

    suspend fun setHarmful(packageName: String, isHarmful: Boolean) {
        val existing = appLimitDao.findByPackage(packageName)
        appLimitDao.upsert(
            existing?.copy(isHarmful = isHarmful)
                ?: AppLimit(packageName = packageName, dailyLimitMinutes = 30, isHarmful = isHarmful)
        )
    }

    suspend fun saveLimit(packageName: String, dailyLimitMinutes: Int, isHarmful: Boolean, rules: Set<BlockRuleFlag>) {
        appLimitDao.upsert(
            AppLimit(
                packageName = packageName,
                dailyLimitMinutes = dailyLimitMinutes,
                isHarmful = isHarmful,
                blockedHours = encodeBlockRules(rules)
            )
        )
    }

    suspend fun isDayReviewedToday(today: LocalDate): Boolean = dailyReviewDao.findByDate(today) != null

    fun observeReviewedToday(today: LocalDate): Flow<Boolean> =
        dailyReviewDao.observeByDate(today).map { it != null }

    /**
     * The single source of truth for "is this app blocked right now", shared by the
     * accessibility service (real-time enforcement) and the UI (status display).
     */
    suspend fun resolveBlockReason(packageName: String, now: LocalDateTime): BlockReason? {
        if (packageName == context.packageName) return null
        if (BlockOverrides.isActive(packageName)) return null

        val limit = appLimitDao.findByPackage(packageName) ?: return null
        val rules = parseBlockRules(limit.blockedHours)
        val nowTime = now.toLocalTime()

        if (rules.contains(BlockRuleFlag.WORK_HOURS) && !nowTime.isBefore(WORK_START) && nowTime.isBefore(WORK_END)) {
            return BlockReason.WorkHours
        }

        if (rules.contains(BlockRuleFlag.PRAYER_TIMES)) {
            val prayerTimes = PrayerTimeCalculator.calculate(
                now.toLocalDate(), DefaultLocation.LATITUDE, DefaultLocation.LONGITUDE, DefaultLocation.UTC_OFFSET_HOURS
            )
            val hit = prayerTimes.asOrderedList().firstOrNull { (_, time) ->
                !nowTime.isBefore(time.minusMinutes(PRAYER_BUFFER_MINUTES)) && !nowTime.isAfter(time)
            }
            if (hit != null) return BlockReason.PrayerTime(hit.first)
        }

        if (limit.isHarmful) {
            val usedMinutes = usageStatsProvider.minutesUsedToday(packageName)
            if (usedMinutes >= limit.dailyLimitMinutes) {
                val unlockedByReview = rules.contains(BlockRuleFlag.UNTIL_DAY_REVIEW) && isDayReviewedToday(now.toLocalDate())
                if (!unlockedByReview) return BlockReason.LimitReached
            }
        }

        return null
    }
}
