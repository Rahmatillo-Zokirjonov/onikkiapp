@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.data.repository

import com.onikki.app.data.db.dao.CategoryBudgetDao
import com.onikki.app.data.db.dao.DebtDao
import com.onikki.app.data.db.dao.SavingsGoalDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.Debt
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

data class WalletBalances(val total: Long, val cash: Long, val card: Long)

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
    private val savingsGoalDao: SavingsGoalDao
) {
    fun observeBalances(): Flow<WalletBalances> = combine(
        transactionDao.observeBalance(),
        transactionDao.observeWalletBalance(Wallet.NAQD),
        transactionDao.observeWalletBalance(Wallet.KARTA)
    ) { total, cash, card -> WalletBalances(total, cash, card) }

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
