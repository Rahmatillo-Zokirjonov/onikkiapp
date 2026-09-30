@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.entity.AppLimit
import com.onikki.app.data.db.entity.BlockZone
import com.onikki.app.data.repository.AppUsageRow
import com.onikki.app.data.repository.BlockReason
import com.onikki.app.data.repository.InstalledApp
import com.onikki.app.data.repository.ScreenTimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.onikki.app.domain.screentime.PeriodSummary
import com.onikki.app.domain.screentime.UsagePeriod
import com.onikki.app.domain.screentime.UsagePeriods
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** An app under control, with the reason it's blocked right now (null = open). */
data class ControlledApp(val rule: AppLimit, val label: String, val minutesToday: Int, val blockedNow: BlockReason?)

data class ScreenTimeUiState(
    val hasUsageAccess: Boolean = true,
    val totalMinutesToday: Int = 0,
    val averageMinutesLast7Days: Int = 0,
    val dailyTotalsLast7Days: List<Int> = List(7) { 0 },
    val apps: List<AppUsageRow> = emptyList(),
    val controlled: List<ControlledApp> = emptyList(),
    val zones: List<BlockZone> = emptyList(),
    val reviewedToday: Boolean = false,
    /** Minutes of screen time in each hour today (24 values). */
    val hourlyMinutes: List<Int> = List(24) { 0 },
    val hourlyByApp: Map<String, List<Int>> = emptyMap()
)

/** Kun / Hafta / Oy history: the chosen period, its numbers, and the one before for comparison. */
data class UsageHistoryState(
    val period: UsagePeriod = UsagePeriod.DAY,
    val anchor: LocalDate = LocalDate.now(),
    val summary: PeriodSummary? = null,
    /** DAY only: minutes per hour, or null when that day has no hourly record. */
    val hours: List<Int>? = null,
    /** Average per tracked day in the previous period (null = nothing recorded then). */
    val previousAverage: Int? = null,
    val isCurrent: Boolean = true
)

class ScreenTimeViewModel(private val repository: ScreenTimeRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val blockedNow = MutableStateFlow<Map<String, BlockReason?>>(emptyMap())
    private val hourly = MutableStateFlow<Pair<List<Int>, Map<String, List<Int>>>>(List(24) { 0 } to emptyMap())

    private val _installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    /** Loaded on first use of the picker (querying every package isn't free). */
    val installedApps: StateFlow<List<InstalledApp>?> = _installedApps

    init {
        refreshUsage()
        // Re-evaluate "blocked right now" whenever the rules change.
        viewModelScope.launch {
            repository.observeRules().collect { rules ->
                val now = LocalDateTime.now()
                blockedNow.value = rules.associate { it.packageName to repository.activeBlockReason(it, now) }
            }
        }
    }

    val uiState: StateFlow<ScreenTimeUiState> = combine(
        repository.observeOverview(today),
        repository.observeRules(),
        repository.observeZones(),
        repository.observeReviewedToday(today),
        combine(blockedNow, hourly) { b, h -> b to h }
    ) { overview, rules, zones, reviewed, (blocked, hourlyData) ->
        val usage = overview.apps.associateBy { it.packageName }
        ScreenTimeUiState(
            hasUsageAccess = overview.hasUsageAccess,
            totalMinutesToday = overview.totalMinutesToday,
            averageMinutesLast7Days = overview.averageMinutesLast7Days,
            dailyTotalsLast7Days = overview.dailyTotalsLast7Days,
            apps = overview.apps,
            controlled = rules.map { rule ->
                ControlledApp(
                    rule = rule,
                    label = usage[rule.packageName]?.appName ?: rule.appName ?: repository.appLabel(rule.packageName) ?: rule.packageName,
                    minutesToday = usage[rule.packageName]?.minutesUsed ?: 0,
                    blockedNow = blocked[rule.packageName]
                )
            }.sortedBy { it.label.lowercase() },
            zones = zones,
            reviewedToday = reviewed,
            hourlyMinutes = hourlyData.first,
            hourlyByApp = hourlyData.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeUiState())

    private val selection = MutableStateFlow(UsagePeriod.DAY to today)

    val history: StateFlow<UsageHistoryState> = selection.flatMapLatest { (period, anchor) ->
        val (from, to) = UsagePeriods.range(period, anchor)
        val (prevFrom, prevTo) = UsagePeriods.range(period, UsagePeriods.shift(period, anchor, -1, today))
        val hoursFlow: Flow<List<Int>?> = when {
            period != UsagePeriod.DAY -> flowOf(null)
            anchor == today -> hourly.map { it.first }
            else -> repository.observeStoredHours(anchor)
        }
        combine(
            repository.observeUsageRange(from, to),
            repository.observeUsageRange(prevFrom, prevTo),
            hoursFlow
        ) { rows, prevRows, hours ->
            val previous = UsagePeriods.summarize(prevRows, prevFrom, prevTo)
            UsageHistoryState(
                period = period,
                anchor = anchor,
                summary = UsagePeriods.summarize(rows, from, to),
                hours = hours,
                previousAverage = previous.averagePerDay.takeIf { previous.trackedDays > 0 },
                isCurrent = UsagePeriods.isCurrent(period, anchor, today)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageHistoryState())

    fun setPeriod(period: UsagePeriod) {
        // Switching keeps the day you were looking at inside the new period.
        selection.update { (_, anchor) -> period to anchor }
    }

    fun shiftPeriod(delta: Int) {
        selection.update { (period, anchor) -> period to UsagePeriods.shift(period, anchor, delta, today) }
    }

    fun openDay(date: LocalDate) {
        if (!date.isAfter(today)) selection.value = UsagePeriod.DAY to date
    }

    fun refreshUsage() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncToday()
            repository.backfillHistory()
            val t = repository.timeline(today)
            fun toMinutes(ms: LongArray) = ms.map { (it / 60_000L).toInt() }
            hourly.value = toMinutes(t.hourlyMs) to t.hourlyMsByApp.mapValues { toMinutes(it.value) }
        }
    }

    fun loadInstalledApps() {
        if (_installedApps.value != null) return
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) { repository.installedApps() }
        }
    }

    fun labelFor(packageName: String): String = repository.appLabel(packageName) ?: packageName

    suspend fun ruleFor(packageName: String): AppLimit? = repository.findRule(packageName)

    fun saveRule(rule: AppLimit, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveRule(rule)
            onDone()
        }
    }

    fun deleteRule(rule: AppLimit, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRule(rule)
            onDone()
        }
    }

    fun saveZone(zone: BlockZone) {
        viewModelScope.launch { repository.saveZone(zone) }
    }

    fun deleteZone(zone: BlockZone) {
        viewModelScope.launch { repository.deleteZone(zone) }
    }

    companion object {
        fun factory(repository: ScreenTimeRepository) = viewModelFactory {
            initializer { ScreenTimeViewModel(repository) }
        }
    }
}
