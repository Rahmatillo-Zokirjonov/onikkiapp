package com.onikki.app.ui.finance

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.FinanceRepository

private enum class FinanceDestination { OVERVIEW, TRANSACTIONS, DEBTS, SAVINGS, PLANNED }

/** The Moliya tab: overview plus its sub-screens, sharing one ViewModel and one sheet host. */
@Composable
fun FinanceSectionRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val repository = remember {
        val db = app.database
        FinanceRepository(
            db.transactionDao(), db.categoryBudgetDao(), db.debtDao(), db.savingsGoalDao(),
            db.accountDao(), db.plannedExpenseDao()
        )
    }
    val viewModel: FinanceViewModel = viewModel(factory = FinanceViewModel.factory(repository))
    val state by viewModel.uiState.collectAsState()
    var destination by rememberSaveable { mutableStateOf(FinanceDestination.OVERVIEW) }
    val backToOverview = { destination = FinanceDestination.OVERVIEW }

    BackHandler(enabled = destination != FinanceDestination.OVERVIEW, onBack = backToOverview)

    when (destination) {
        FinanceDestination.OVERVIEW -> FinanceScreen(
            state = state,
            onSelectPeriod = viewModel::selectPeriod,
            onOpenSheet = viewModel::openSheet,
            onOpenAllTransactions = { destination = FinanceDestination.TRANSACTIONS },
            onOpenDebts = { destination = FinanceDestination.DEBTS },
            onOpenSavings = { destination = FinanceDestination.SAVINGS },
            onOpenPlanned = { destination = FinanceDestination.PLANNED }
        )
        FinanceDestination.PLANNED -> PlannedExpensesScreen(
            planned = state.planned,
            onBack = backToOverview,
            onOpenSheet = viewModel::openSheet
        )
        FinanceDestination.TRANSACTIONS -> AllTransactionsScreen(
            transactions = state.transactions,
            accounts = state.accounts,
            onBack = backToOverview,
            onOpenSheet = viewModel::openSheet
        )
        FinanceDestination.DEBTS -> DebtsScreen(
            debts = state.debts,
            totals = state.debtTotals,
            onBack = backToOverview,
            onOpenSheet = viewModel::openSheet,
            onToggleStatus = viewModel::toggleDebtStatus
        )
        FinanceDestination.SAVINGS -> SavingsScreen(
            goals = state.goals,
            onBack = backToOverview,
            onOpenSheet = viewModel::openSheet
        )
    }

    when (val sheet = state.sheet) {
        null -> Unit
        is FinanceSheet.TransactionEdit -> TransactionSheet(
            transaction = sheet.transaction,
            accounts = state.accounts,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            onDismiss = viewModel::dismissSheet,
            onSave = { amount, type, category, account, date, note ->
                viewModel.saveTransaction(sheet.transaction, amount, type, category, account, date, note)
            },
            onDelete = { sheet.transaction?.let(viewModel::deleteTransaction) }
        )
        is FinanceSheet.BudgetEdit -> BudgetSheet(
            budget = sheet.budget,
            expenseCategories = state.expenseCategories,
            onDismiss = viewModel::dismissSheet,
            onSave = { category, limit -> viewModel.saveBudget(sheet.budget, category, limit) },
            onDelete = { sheet.budget?.let(viewModel::deleteBudget) }
        )
        is FinanceSheet.DebtEdit -> DebtSheet(
            debt = sheet.debt,
            onDismiss = viewModel::dismissSheet,
            onSave = { name, amount, direction, dueDate, status ->
                viewModel.saveDebt(sheet.debt, name, amount, direction, dueDate, status)
            },
            onDelete = { sheet.debt?.let(viewModel::deleteDebt) }
        )
        is FinanceSheet.GoalEdit -> GoalSheet(
            goal = sheet.goal,
            onDismiss = viewModel::dismissSheet,
            onSave = { name, target, current, deadline -> viewModel.saveGoal(sheet.goal, name, target, current, deadline) },
            onDelete = { sheet.goal?.let(viewModel::deleteGoal) }
        )
        is FinanceSheet.AccountEdit -> AccountSheet(
            account = sheet.account,
            errorFromDelete = sheet.error,
            onDismiss = viewModel::dismissSheet,
            onSave = { name, kind, digits, initial -> viewModel.saveAccount(sheet.account, name, kind, digits, initial) },
            onDelete = { sheet.account?.let(viewModel::deleteAccount) }
        )
        is FinanceSheet.PlannedEdit -> PlannedExpenseSheet(
            expense = sheet.expense,
            accounts = state.accounts,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            onDismiss = viewModel::dismissSheet,
            onSave = { type, title, amount, category, accountId, dueDate, repeat, remind, daysBefore, time ->
                viewModel.savePlanned(sheet.expense, type, title, amount, category, accountId, dueDate, repeat, remind, daysBefore, time)
            },
            onDelete = { sheet.expense?.let(viewModel::deletePlanned) }
        )
        is FinanceSheet.PlannedPay -> PlannedPaySheet(
            expense = sheet.expense,
            accounts = state.accounts,
            onDismiss = viewModel::dismissSheet,
            onPay = { account, amount, date -> viewModel.payPlanned(sheet.expense, account, amount, date) },
            onSkip = { viewModel.skipPlanned(sheet.expense) }
        )
        is FinanceSheet.GoalAdjust -> GoalAdjustSheet(
            goal = sheet.goal,
            onDismiss = viewModel::dismissSheet,
            onAdjust = { delta -> viewModel.adjustGoal(sheet.goal, delta) }
        )
    }
}
