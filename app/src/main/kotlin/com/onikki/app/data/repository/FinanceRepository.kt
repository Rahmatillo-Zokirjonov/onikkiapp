@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.data.repository

import com.onikki.app.data.db.dao.AccountDao
import com.onikki.app.data.db.dao.CategoryBudgetDao
import com.onikki.app.data.db.dao.PlannedExpenseDao
import com.onikki.app.data.db.dao.DebtDao
import com.onikki.app.data.db.dao.SavingsGoalDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.Account
import com.onikki.app.data.db.entity.AccountKind
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.entity.RepeatKind
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class AccountBalance(val account: Account, val balance: Long)

/** [fraction] is this category's share of the period's total expense, 0..1. */
data class CategorySlice(val category: String, val amount: Long, val fraction: Float)

data class BudgetProgress(val budget: CategoryBudget, val spent: Long)

private const val MAX_CHART_CATEGORIES = 4
private const val OTHER_CATEGORY_LABEL = "Boshqa"
private const val TOP_CATEGORY_LIMIT = 8

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val categoryBudgetDao: CategoryBudgetDao,
    private val debtDao: DebtDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val accountDao: AccountDao,
    private val plannedExpenseDao: PlannedExpenseDao
) {
    /** Every wallet with its live balance (starting balance + its transactions). */
    fun observeAccountBalances(): Flow<List<AccountBalance>> = combine(
        accountDao.observeAll(),
        accountDao.observeNetByAccount()
    ) { accounts, nets ->
        val netById = nets.associate { it.accountId to it.net }
        accounts.map { AccountBalance(it, it.initialBalance + (netById[it.id] ?: 0L)) }
    }

    suspend fun saveAccount(account: Account) {
        if (account.id == 0L) accountDao.insert(account) else accountDao.update(account)
    }

    /** Refuses (returns false) while transactions still point at the wallet — their history would be orphaned. */
    suspend fun deleteAccount(account: Account): Boolean {
        if (accountDao.transactionCount(account.id) > 0) return false
        accountDao.delete(account)
        return true
    }

    fun observePlannedExpenses(): Flow<List<PlannedExpense>> = plannedExpenseDao.observeAll()

    suspend fun savePlannedExpense(expense: PlannedExpense) {
        if (expense.id == 0L) plannedExpenseDao.insert(expense) else plannedExpenseDao.update(expense)
    }

    suspend fun deletePlannedExpense(expense: PlannedExpense) = plannedExpenseDao.delete(expense)

    suspend fun payPlannedExpense(expense: PlannedExpense, account: Account, amount: Long, date: LocalDate) =
        payPlanned(transactionDao, plannedExpenseDao, expense, account, amount, date)

    /** A repeating expense skipped this time: move to the next period without recording money. */
    suspend fun skipPlannedExpense(expense: PlannedExpense) {
        plannedExpenseDao.update(expense.copy(dueDate = expense.repeat.next(expense.dueDate)))
    }

    /** Top [MAX_CHART_CATEGORIES] expense categories for the range, the rest folded into "Boshqa". */
    fun observeExpenseBreakdown(from: LocalDate, to: LocalDate): Flow<List<CategorySlice>> =
        transactionDao.observeExpenseByCategory(from, to).map { totals ->
            val grandTotal = totals.sumOf { it.total }
            if (totals.isEmpty() || grandTotal <= 0L) {
                return@map emptyList<CategorySlice>()
            }
            val top = totals.take(MAX_CHART_CATEGORIES)
            val otherTotal = totals.drop(MAX_CHART_CATEGORIES).sumOf { it.total }
            val slices = top.map { CategorySlice(it.category, it.total, it.total / grandTotal.toFloat()) }
            if (otherTotal > 0L) {
                slices + CategorySlice(OTHER_CATEGORY_LABEL, otherTotal, otherTotal / grandTotal.toFloat())
            } else {
                slices
            }
        }

    fun observeBudgetProgress(monthStart: LocalDate, today: LocalDate): Flow<List<BudgetProgress>> =
        categoryBudgetDao.observeAll().flatMapLatest { budgets ->
            if (budgets.isEmpty()) {
                flowOf(emptyList<BudgetProgress>())
            } else {
                combine(
                    budgets.map { budget ->
                        transactionDao.observeSpentForCategory(budget.category, monthStart, today)
                            .map { spent -> BudgetProgress(budget, spent) }
                    }
                ) { it.toList() }
            }
        }

    fun observeAllTransactions(): Flow<List<Transaction>> = transactionDao.observeAll()

    /** Most-used categories for [type], most frequent first — feeds the quick-pick chips. */
    fun observeTopCategories(type: TransactionType): Flow<List<String>> =
        transactionDao.observeTopCategories(type, TOP_CATEGORY_LIMIT)

    suspend fun saveTransaction(transaction: Transaction) {
        if (transaction.id == 0L) transactionDao.insert(transaction) else transactionDao.update(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.delete(transaction)

    /** One limit per category: saving a category that already has a limit updates it instead of duplicating. */
    suspend fun saveBudget(existing: CategoryBudget?, category: String, monthlyLimit: Long) {
        if (existing != null) {
            categoryBudgetDao.update(existing.copy(category = category, monthlyLimit = monthlyLimit))
            return
        }
        val sameCategory = categoryBudgetDao.findByCategory(category)
        if (sameCategory != null) {
            categoryBudgetDao.update(sameCategory.copy(monthlyLimit = monthlyLimit))
        } else {
            categoryBudgetDao.insert(CategoryBudget(category = category, monthlyLimit = monthlyLimit))
        }
    }

    suspend fun deleteBudget(budget: CategoryBudget) = categoryBudgetDao.delete(budget)

    fun observeDebts(): Flow<List<Debt>> = debtDao.observeAll()

    suspend fun saveDebt(debt: Debt) {
        if (debt.id == 0L) debtDao.insert(debt) else debtDao.update(debt)
    }

    suspend fun deleteDebt(debt: Debt) = debtDao.delete(debt)

    fun observeSavingsGoals(): Flow<List<SavingsGoal>> = savingsGoalDao.observeAll()

    suspend fun saveSavingsGoal(goal: SavingsGoal) {
        if (goal.id == 0L) savingsGoalDao.insert(goal) else savingsGoalDao.update(goal)
    }

    suspend fun deleteSavingsGoal(goal: SavingsGoal) = savingsGoalDao.delete(goal)

    /** Positive [delta] adds to the goal, negative withdraws; never goes below zero. */
    suspend fun adjustSavings(goal: SavingsGoal, delta: Long) {
        savingsGoalDao.update(goal.copy(currentAmount = (goal.currentAmount + delta).coerceAtLeast(0)))
    }
}

/**
 * Records the payment as a real expense, then either closes a one-time plan or moves a repeating one
 * to its next date. Shared by the Moliya screen and the reminder's "To'landi" action.
 */
suspend fun payPlanned(
    transactionDao: TransactionDao,
    plannedExpenseDao: PlannedExpenseDao,
    expense: PlannedExpense,
    account: Account,
    amount: Long,
    date: LocalDate
) {
    transactionDao.insert(
        Transaction(
            amount = amount,
            type = TransactionType.CHIQIM,
            category = expense.category,
            wallet = account.kind.toWallet(),
            date = date,
            note = expense.title,
            accountId = account.id
        )
    )
    val updated = if (expense.repeat == RepeatKind.NONE) {
        expense.copy(paidDate = date)
    } else {
        // Advance past today so paying a long-overdue monthly bill doesn't leave it still overdue.
        var next = expense.repeat.next(expense.dueDate)
        while (!next.isAfter(date)) next = expense.repeat.next(next)
        expense.copy(dueDate = next)
    }
    plannedExpenseDao.update(updated)
}

fun AccountKind.toWallet(): Wallet = if (this == AccountKind.NAQD) Wallet.NAQD else Wallet.KARTA
