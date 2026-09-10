@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import com.onikki.app.data.repository.BudgetProgress
import com.onikki.app.data.repository.CategorySlice
import com.onikki.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class MoneyPeriod(val label: String) {
    WEEK("Hafta"),
    MONTH("Oy"),
    YEAR("Yil")
}

data class FinanceUiState(
    val period: MoneyPeriod = MoneyPeriod.MONTH,
    val balance: Long = 0,
    val cashBalance: Long = 0,
    val cardBalance: Long = 0,
    val expenseTotal: Long = 0,
    val expenseSlices: List<CategorySlice> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val isAddSheetOpen: Boolean = false
)

private data class PeriodExpense(val period: MoneyPeriod, val slices: List<CategorySlice>)

private fun periodRange(period: MoneyPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    MoneyPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
    MoneyPeriod.MONTH -> today.withDayOfMonth(1) to today
    MoneyPeriod.YEAR -> today.withDayOfYear(1) to today
}

class FinanceViewModel(private val financeRepository: FinanceRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val period = MutableStateFlow(MoneyPeriod.MONTH)
    private val isAddSheetOpen = MutableStateFlow(false)

    private val periodExpenseFlow = period.flatMapLatest { p ->
        val (from, to) = periodRange(p, today)
        financeRepository.observeExpenseBreakdown(from, to).map { slices -> PeriodExpense(p, slices) }
    }

    val uiState: StateFlow<FinanceUiState> = combine(
        periodExpenseFlow,
        financeRepository.observeBalances(),
        financeRepository.observeBudgetProgress(today.withDayOfMonth(1), today),
        financeRepository.observeRecentTransactions(8),
        isAddSheetOpen
    ) { periodExpense, balances, budgets, recent, sheetOpen ->
        FinanceUiState(
            period = periodExpense.period,
            balance = balances.total,
            cashBalance = balances.cash,
            cardBalance = balances.card,
            expenseTotal = periodExpense.slices.sumOf { it.amount },
            expenseSlices = periodExpense.slices,
            budgets = budgets,
            recentTransactions = recent,
            isAddSheetOpen = sheetOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    fun selectPeriod(newPeriod: MoneyPeriod) {
        period.value = newPeriod
    }

    fun openAddSheet() {
        isAddSheetOpen.value = true
    }

    fun dismissAddSheet() {
        isAddSheetOpen.value = false
    }

    fun addTransaction(amount: Long, type: TransactionType, category: String, wallet: Wallet, note: String?) {
        if (amount <= 0 || category.isBlank()) return
        viewModelScope.launch {
            financeRepository.addTransaction(amount, type, category, wallet, today, note)
            isAddSheetOpen.value = false
        }
    }

    companion object {
        fun factory(financeRepository: FinanceRepository) = viewModelFactory {
            initializer { FinanceViewModel(financeRepository) }
        }
    }
}
