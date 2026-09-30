package com.onikki.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.onikki.app.data.db.dao.AppLimitDao
import com.onikki.app.data.db.dao.AppUsageDao
import com.onikki.app.data.db.dao.BlockZoneDao
import com.onikki.app.data.db.dao.DailyReviewDao
import com.onikki.app.data.db.entity.AppLimit
import com.onikki.app.data.db.entity.AppUsage
import com.onikki.app.data.db.entity.BlockZone
import com.onikki.app.data.local.ChallengeSettingsStore
import com.onikki.app.data.local.LocationStore
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import com.onikki.app.domain.screentime.UsageStatsProvider
import com.onikki.app.domain.screentime.ZoneLocationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Extra rule flags stored in AppLimit.blockedHours. WORK_HOURS is legacy (schema ≤4): the v5 migration
 * turned it into a real 09:00–18:00 window, and it is no longer offered or evaluated.
 */
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

private const val PRAYER_BUFFER_MINUTES = 20L

/** A launchable app on the phone, for the "add any app" picker. */
data class InstalledApp(val packageName: String, val label: String)

class ScreenTimeRepository(
    private val context: Context,
    private val appUsageDao: AppUsageDao,
    private val appLimitDao: AppLimitDao,
    private val dailyReviewDao: DailyReviewDao,
    private val blockZoneDao: BlockZoneDao,
    private val locationStore: LocationStore = LocationStore(context),
    private val challengeStore: ChallengeSettingsStore = ChallengeSettingsStore(context)
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

    fun observeRules(): Flow<List<AppLimit>> = appLimitDao.observeAll()

    fun observeZones(): Flow<List<BlockZone>> = blockZoneDao.observeAll()

    suspend fun findRule(packageName: String): AppLimit? = appLimitDao.findByPackage(packageName)

    suspend fun saveRule(rule: AppLimit) = appLimitDao.upsert(rule)

    suspend fun deleteRule(rule: AppLimit) = appLimitDao.delete(rule)

    suspend fun saveZone(zone: BlockZone) {
        if (zone.id == 0L) blockZoneDao.insert(zone) else blockZoneDao.update(zone)
    }

    /** Removes the place and un-links it from every app that used it. */
    suspend fun deleteZone(zone: BlockZone) {
        appLimitDao.getAll().filter { zone.id in it.zoneIdList }.forEach { rule ->
            val remaining = rule.zoneIdList - zone.id
            appLimitDao.upsert(rule.copy(zoneIds = remaining.takeIf { it.isNotEmpty() }?.joinToString(",")))
        }
        blockZoneDao.delete(zone)
    }

    /** Every app with a launcher icon (except On ikki), sorted by name. */
    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { it.first }
            .map { (pkg, label) -> InstalledApp(pkg, label) }
            .sortedBy { it.label.lowercase() }
    }

    fun appLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrNull()

    suspend fun isDayReviewedToday(today: LocalDate): Boolean = dailyReviewDao.findByDate(today) != null

    fun observeReviewedToday(today: LocalDate): Flow<Boolean> =
        dailyReviewDao.observeByDate(today).map { it != null }

    /** The first rule that blocks [rule]'s app right now, or null. Order: window, place, prayer, limit. */
    suspend fun activeBlockReason(rule: AppLimit, now: LocalDateTime): BlockReason? {
        val flags = parseBlockRules(rule.blockedHours)
        val nowTime = now.toLocalTime()

        if (rule.isScheduledBlock(now.toLocalDate(), nowTime)) {
            return BlockReason.Schedule(rule.scheduleStart!!, rule.scheduleEnd!!)
        }

        val zoneIds = rule.zoneIdList
        if (zoneIds.isNotEmpty()) {
            val location = ZoneLocationTracker.locationForZones(context)
            if (location != null) {
                val zones = blockZoneDao.getAll().filter { it.id in zoneIds }
                ZoneLocationTracker.zoneContaining(location, zones)?.let { return BlockReason.Zone(it.name) }
            }
        }

        if (flags.contains(BlockRuleFlag.PRAYER_TIMES)) {
            val city = locationStore.city.first()
            val prayerTimes = PrayerTimeCalculator.calculate(now.toLocalDate(), city.latitude, city.longitude, city.utcOffsetHours)
            val hit = prayerTimes.asOrderedList().firstOrNull { (_, time) ->
                !nowTime.isBefore(time.minusMinutes(PRAYER_BUFFER_MINUTES)) && !nowTime.isAfter(time)
            }
            if (hit != null) return BlockReason.PrayerTime(hit.first)
        }

        if (rule.isHarmful) {
            val usedMinutes = usageStatsProvider.minutesUsedToday(rule.packageName)
            if (usedMinutes >= rule.dailyLimitMinutes) {
                val unlockedByReview = flags.contains(BlockRuleFlag.UNTIL_DAY_REVIEW) && isDayReviewedToday(now.toLocalDate())
                if (!unlockedByReview) return BlockReason.LimitReached
            }
        }
        return null
    }

    /**
     * The single decision point for "what happens when this app opens", shared by the accessibility
     * service (enforcement) and the UI (status). A strict block wins over everything — even a grace
     * period earned by a challenge earlier; otherwise a passed challenge opens the app for a while.
     */
    suspend fun resolveGate(packageName: String, now: LocalDateTime): AppGate {
        if (packageName == context.packageName) return AppGate.Allowed
        val rule = appLimitDao.findByPackage(packageName) ?: return AppGate.Allowed
        val reason = activeBlockReason(rule, now)
        if (reason != null && rule.strictMode) return AppGate.Blocked(reason, strict = true, canChallenge = false)
        if (BlockOverrides.isActive(packageName)) return AppGate.Allowed

        val challenge = challengeStore.settings.first()
        if (reason != null) {
            return AppGate.Blocked(reason, strict = false, canChallenge = challenge.enabled && challenge.unlockBlockedApps)
        }
        if (rule.challengeOnOpen && challenge.enabled) return AppGate.ChallengeRequired
        return AppGate.Allowed
    }
}
