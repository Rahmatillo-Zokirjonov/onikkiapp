package com.onikki.app.data.repository

import com.onikki.app.data.db.dao.CategoryBudgetDao
import com.onikki.app.data.db.dao.TransactionDao
import com.onikki.app.data.db.entity.CategoryBudget
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

class FinanceRepository(
    private val transactionDao: TransactionDao,
    private val categoryBudgetDao: CategoryBudgetDao
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

    fun observeRecentTransactions(limit: Int): Flow<List<Transaction>> = transactionDao.observeRecent(limit)

    suspend fun addTransaction(
        amount: Long,
        type: TransactionType,
        category: String,
        wallet: Wallet,
        date: LocalDate,
        note: String?
    ) {
        transactionDao.insert(
            Transaction(amount = amount, type = type, category = category, wallet = wallet, date = date, note = note)
        )
    }
}
