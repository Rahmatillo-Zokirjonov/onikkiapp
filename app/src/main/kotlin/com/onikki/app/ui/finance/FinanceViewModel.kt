@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import com.onikki.app.data.repository.BudgetProgress
import com.onikki.app.data.repository.CategorySlice
import com.onikki.app.data.repository.FinanceRepository
import com.onikki.app.data.repository.WalletBalances
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

/** Which bottom sheet is open. Carries the entity being edited, or null for "new". */
sealed interface FinanceSheet {
    data class TransactionEdit(val transaction: Transaction?) : FinanceSheet
    data class BudgetEdit(val budget: CategoryBudget?) : FinanceSheet
    data class DebtEdit(val debt: Debt?) : FinanceSheet
    data class GoalEdit(val goal: SavingsGoal?) : FinanceSheet
    data class GoalAdjust(val goal: SavingsGoal) : FinanceSheet
}

data class DebtTotals(val owedToMe: Long = 0, val iOwe: Long = 0, val openCount: Int = 0)

data class FinanceUiState(
    val period: MoneyPeriod = MoneyPeriod.MONTH,
    val balance: Long = 0,
    val cashBalance: Long = 0,
    val cardBalance: Long = 0,
    /** End-of-day balance for the last 30 days, oldest first. */
    val balanceTrend: List<Long> = emptyList(),
    val expenseTotal: Long = 0,
    val expenseSlices: List<CategorySlice> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val debtTotals: DebtTotals = DebtTotals(),
    val goals: List<SavingsGoal> = emptyList(),
    val expenseCategories: List<String> = emptyList(),
    val incomeCategories: List<String> = emptyList(),
    val sheet: FinanceSheet? = null
)

/** Common categories from the product plan, filling quick-pick chips before the user has history. */
private val DEFAULT_EXPENSE_CATEGORIES =
    listOf("Oziq-ovqat", "Transport", "Uy", "Kommunal", "Kiyim", "Ta'lim", "Sog'liq", "Mehmon/marosim")
private val DEFAULT_INCOME_CATEGORIES = listOf("Maosh", "Qo'shimcha daromad", "Sovg'a")
private const val CATEGORY_CHIP_COUNT = 8

private fun withDefaults(used: List<String>, defaults: List<String>): List<String> =
    (used + defaults).distinctBy { it.lowercase() }.take(CATEGORY_CHIP_COUNT)

private fun periodRange(period: MoneyPeriod, today: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
    MoneyPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
    MoneyPeriod.MONTH -> today.withDayOfMonth(1) to today
    MoneyPeriod.YEAR -> today.withDayOfYear(1) to today
}

/** Open debts first (soonest due first, undated last), closed debts after. */
private fun sortDebts(debts: List<Debt>): List<Debt> =
    debts.sortedWith(
        compareBy<Debt> { it.status == DebtStatus.YOPILGAN }
            .thenBy { it.dueDate == null }
            .thenBy { it.dueDate }
    )

private const val TREND_DAYS = 30

/** Walks back from today's balance, undoing each day's net change to get each day's closing balance. */
private fun balanceTrend(transactions: List<Transaction>, currentBalance: Long, today: LocalDate): List<Long> {
    if (transactions.isEmpty()) return emptyList()
    val netByDay = transactions.groupBy { it.date }.mapValues { (_, day) ->
        day.sumOf { if (it.type == TransactionType.KIRIM) it.amount else -it.amount }
    }
    val futureNet = netByDay.filterKeys { it.isAfter(today) }.values.sum()
    var running = currentBalance - futureNet
    val points = ArrayList<Long>(TREND_DAYS)
    for (daysAgo in 0 until TREND_DAYS) {
        points.add(running)
        running -= netByDay[today.minusDays(daysAgo.toLong())] ?: 0L
    }
    return points.reversed()
}

private data class Overview(
    val period: MoneyPeriod,
    val slices: List<CategorySlice>,
    val balances: WalletBalances,
    val budgets: List<BudgetProgress>
)

private data class Lists(
    val transactions: List<Transaction>,
    val debts: List<Debt>,
    val goals: List<SavingsGoal>,
    val expenseCategories: List<String>,
    val incomeCategories: List<String>
)

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val period = MutableStateFlow(MoneyPeriod.MONTH)
    private val sheet = MutableStateFlow<FinanceSheet?>(null)

    private val overviewFlow = combine(
        period.flatMapLatest { p ->
            val (from, to) = periodRange(p, today)
            repository.observeExpenseBreakdown(from, to).map { p to it }
        },
        repository.observeBalances(),
        repository.observeBudgetProgress(today.withDayOfMonth(1), today)
    ) { (p, slices), balances, budgets -> Overview(p, slices, balances, budgets) }

    private val listsFlow = combine(
        repository.observeAllTransactions(),
        repository.observeDebts(),
        repository.observeSavingsGoals(),
        repository.observeTopCategories(TransactionType.CHIQIM),
        repository.observeTopCategories(TransactionType.KIRIM)
    ) { transactions, debts, goals, expenseCats, incomeCats ->
        Lists(
            transactions = transactions,
            debts = sortDebts(debts),
            goals = goals,
            expenseCategories = withDefaults(expenseCats, DEFAULT_EXPENSE_CATEGORIES),
            incomeCategories = withDefaults(incomeCats, DEFAULT_INCOME_CATEGORIES)
        )
    }

    val uiState: StateFlow<FinanceUiState> = combine(overviewFlow, listsFlow, sheet) { overview, lists, openSheet ->
        val openDebts = lists.debts.filter { it.status == DebtStatus.OCHIQ }
        FinanceUiState(
            period = overview.period,
            balance = overview.balances.total,
            cashBalance = overview.balances.cash,
            cardBalance = overview.balances.card,
            balanceTrend = balanceTrend(lists.transactions, overview.balances.total, today),
            expenseTotal = overview.slices.sumOf { it.amount },
            expenseSlices = overview.slices,
            budgets = overview.budgets,
            transactions = lists.transactions,
            debts = lists.debts,
            debtTotals = DebtTotals(
                owedToMe = openDebts.filter { it.direction == DebtDirection.MENGA_QARZDOR }.sumOf { it.amount },
                iOwe = openDebts.filter { it.direction == DebtDirection.MEN_QARZDORMAN }.sumOf { it.amount },
                openCount = openDebts.size
            ),
            goals = lists.goals,
            expenseCategories = lists.expenseCategories,
            incomeCategories = lists.incomeCategories,
            sheet = openSheet
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    fun selectPeriod(newPeriod: MoneyPeriod) {
        period.value = newPeriod
    }

    fun openSheet(target: FinanceSheet) {
        sheet.value = target
    }

    fun dismissSheet() {
        sheet.value = null
    }

    private fun launchAndClose(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            sheet.value = null
        }
    }

    fun saveTransaction(
        existing: Transaction?,
        amount: Long,
        type: TransactionType,
        category: String,
        wallet: Wallet,
        date: LocalDate,
        note: String?
    ) {
        if (amount <= 0 || category.isBlank()) return
        val updated = (existing ?: Transaction(amount = amount, type = type, category = category, wallet = wallet, date = date))
            .copy(amount = amount, type = type, category = category.trim(), wallet = wallet, date = date, note = note)
        launchAndClose { repository.saveTransaction(updated) }
    }

    fun deleteTransaction(transaction: Transaction) = launchAndClose { repository.deleteTransaction(transaction) }

    fun saveBudget(existing: CategoryBudget?, category: String, monthlyLimit: Long) {
        if (category.isBlank() || monthlyLimit <= 0) return
        launchAndClose { repository.saveBudget(existing, category.trim(), monthlyLimit) }
    }

    fun deleteBudget(budget: CategoryBudget) = launchAndClose { repository.deleteBudget(budget) }

    fun saveDebt(
        existing: Debt?,
        personName: String,
        amount: Long,
        direction: DebtDirection,
        dueDate: LocalDate?,
        status: DebtStatus
    ) {
        if (personName.isBlank() || amount <= 0) return
        val updated = (existing ?: Debt(personName = personName, amount = amount, direction = direction, dueDate = dueDate))
            .copy(personName = personName.trim(), amount = amount, direction = direction, dueDate = dueDate, status = status)
        launchAndClose { repository.saveDebt(updated) }
    }

    /** Quick open/closed toggle straight from the debt list, without opening the sheet. */
    fun toggleDebtStatus(debt: Debt) {
        viewModelScope.launch {
            val next = if (debt.status == DebtStatus.OCHIQ) DebtStatus.YOPILGAN else DebtStatus.OCHIQ
            repository.saveDebt(debt.copy(status = next))
        }
    }

    fun deleteDebt(debt: Debt) = launchAndClose { repository.deleteDebt(debt) }

    fun saveGoal(existing: SavingsGoal?, name: String, targetAmount: Long, currentAmount: Long, deadline: LocalDate?) {
        if (name.isBlank() || targetAmount <= 0) return
        val updated = (existing ?: SavingsGoal(name = name, targetAmount = targetAmount, deadline = deadline))
            .copy(name = name.trim(), targetAmount = targetAmount, currentAmount = currentAmount, deadline = deadline)
        launchAndClose { repository.saveSavingsGoal(updated) }
    }

    fun deleteGoal(goal: SavingsGoal) = launchAndClose { repository.deleteSavingsGoal(goal) }

    fun adjustGoal(goal: SavingsGoal, delta: Long) {
        if (delta == 0L) return
        launchAndClose { repository.adjustSavings(goal, delta) }
    }

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { FinanceViewModel(repository) }
        }
    }
}
