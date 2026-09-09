package com.onikki.app.ui.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.repository.AppUsageRow
import com.onikki.app.data.repository.BlockRuleFlag
import com.onikki.app.data.repository.ScreenTimeRepository
import com.onikki.app.data.repository.parseBlockRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScreenTimeUiState(
    val hasUsageAccess: Boolean = true,
    val totalMinutesToday: Int = 0,
    val averageMinutesLast7Days: Int = 0,
    val dailyTotalsLast7Days: List<Int> = List(7) { 0 },
    val apps: List<AppUsageRow> = emptyList(),
    val blockedApps: List<AppUsageRow> = emptyList(),
    val reviewedToday: Boolean = false,
    val editingApp: AppUsageRow? = null
)

class ScreenTimeViewModel(private val repository: ScreenTimeRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val editingPackage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { repository.syncToday() }
    }

    val uiState: StateFlow<ScreenTimeUiState> = combine(
        repository.observeOverview(today),
        repository.observeReviewedToday(today),
        editingPackage
    ) { overview, reviewed, editing ->
        val blocked = overview.apps.filter { row ->
            val limit = row.limit
            limit != null &&
                limit.isHarmful &&
                row.minutesUsed >= limit.dailyLimitMinutes &&
                !(parseBlockRules(limit.blockedHours).contains(BlockRuleFlag.UNTIL_DAY_REVIEW) && reviewed)
        }
        ScreenTimeUiState(
            hasUsageAccess = overview.hasUsageAccess,
            totalMinutesToday = overview.totalMinutesToday,
            averageMinutesLast7Days = overview.averageMinutesLast7Days,
            dailyTotalsLast7Days = overview.dailyTotalsLast7Days,
            apps = overview.apps,
            blockedApps = blocked,
            reviewedToday = reviewed,
            editingApp = overview.apps.firstOrNull { it.packageName == editing }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeUiState())

    fun refreshUsage() {
        viewModelScope.launch { repository.syncToday() }
    }

    fun toggleHarmful(row: AppUsageRow) {
        viewModelScope.launch { repository.setHarmful(row.packageName, !(row.limit?.isHarmful ?: false)) }
    }

    fun openLimitEditor(row: AppUsageRow) {
        editingPackage.value = row.packageName
    }

    fun dismissLimitEditor() {
        editingPackage.value = null
    }

    fun saveLimit(dailyLimitMinutes: Int, isHarmful: Boolean, rules: Set<BlockRuleFlag>) {
        val packageName = editingPackage.value ?: return
        viewModelScope.launch {
            repository.saveLimit(packageName, dailyLimitMinutes, isHarmful, rules)
            editingPackage.value = null
        }
    }

    companion object {
        fun factory(repository: ScreenTimeRepository) = viewModelFactory {
            initializer { ScreenTimeViewModel(repository) }
        }
    }
}
