package com.onikki.app.ui.finance

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.onikki.app.data.repository.AccountBalance
import com.onikki.app.data.db.entity.PlannedExpense
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.TagChip
import com.onikki.app.ui.components.TagVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatFullDateUz
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatSom
import com.onikki.app.ui.util.monthNameUz
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val ScreenPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 84.dp)

@Composable
private fun EmptyText(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(text = text, color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
}

@Composable
private fun SubScreenFrame(fabOnClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        content()
        AddFab(onClick = fabOnClick, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }
}

/** Two-line total block used in the debt and transaction summaries. */
@Composable
private fun TotalBlock(label: String, amount: Long, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = modifier) {
        Text(text = label, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "${formatSom(amount)} so'm",
            color = colors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

// ---------------------------------------------------------------- Barcha tranzaksiyalar

private enum class TransactionFilter(val label: String) { ALL("Hammasi"), EXPENSE("Chiqim"), INCOME("Kirim") }

@Composable
fun AllTransactionsScreen(
    transactions: List<Transaction>,
    accounts: List<AccountBalance>,
    onBack: () -> Unit,
    onOpenSheet: (FinanceSheet) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var filter by rememberSaveable { mutableStateOf(TransactionFilter.ALL) }
    var accountFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val visible = when (filter) {
        TransactionFilter.ALL -> transactions
        TransactionFilter.EXPENSE -> transactions.filter { it.type == TransactionType.CHIQIM }
        TransactionFilter.INCOME -> transactions.filter { it.type == TransactionType.KIRIM }
    }.filter { accountFilter == null || it.accountId == accountFilter }
    val names = accounts.associate { it.account.id to it.account.name }
    val byDay = visible.groupBy { it.date }

    SubScreenFrame(fabOnClick = { onOpenSheet(FinanceSheet.TransactionEdit(null)) }) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SubScreenHeader(title = "Tranzaksiyalar", onBack = onBack) }
            item {
                OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
                    Row {
                        TotalBlock(
                            label = "Kirim",
                            amount = visible.filter { it.type == TransactionType.KIRIM }.sumOf { it.amount },
                            modifier = Modifier.weight(1f)
                        )
                        TotalBlock(
                            label = "Chiqim",
                            amount = visible.filter { it.type == TransactionType.CHIQIM }.sumOf { it.amount },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionFilter.entries.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                    }
                }
            }
            if (accounts.size > 1) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = accountFilter == null, onClick = { accountFilter = null }, label = { Text("Barcha hamyonlar") })
                        accounts.forEach { item ->
                            FilterChip(
                                selected = accountFilter == item.account.id,
                                onClick = { accountFilter = if (accountFilter == item.account.id) null else item.account.id },
                                label = { Text(accountLabel(item.account)) }
                            )
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item { EmptyText("Bu bo'limda tranzaksiya yo'q") }
            }
            byDay.forEach { (date, dayTransactions) ->
                item(key = "day-$date") {
                    val net = dayTransactions.sumOf { if (it.type == TransactionType.KIRIM) it.amount else -it.amount }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dayHeader(date).uppercase(),
                            color = colors.text.muted(0.45f),
                            fontSize = 10.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(
                            text = (if (net >= 0) "+ " else "− ") + formatSom(kotlin.math.abs(net)),
                            color = colors.text.muted(0.45f),
                            fontSize = 11.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                }
                items(dayTransactions, key = { it.id }) { tx ->
                    TransactionRow(
                        tx,
                        accountName = names[tx.accountId],
                        onClick = { onOpenSheet(FinanceSheet.TransactionEdit(tx)) },
                        showDate = false
                    )
                }
            }
        }
    }
}

private fun dayHeader(date: LocalDate): String {
    val relative = formatRelativeDateUz(date)
    val full = formatFullDateUz(date)
    return if (relative == "bugun" || relative == "kecha") "$relative · $full" else full
}

// ---------------------------------------------------------------- Qarz-nasiya

@Composable
fun DebtsScreen(
    debts: List<Debt>,
    totals: DebtTotals,
    onBack: () -> Unit,
    onOpenSheet: (FinanceSheet) -> Unit,
    onToggleStatus: (Debt) -> Unit
) {
    var showClosed by rememberSaveable { mutableStateOf(false) }
    val visible = debts.filter { (it.status == DebtStatus.YOPILGAN) == showClosed }
    val today = LocalDate.now()

    SubScreenFrame(fabOnClick = { onOpenSheet(FinanceSheet.DebtEdit(null)) }) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SubScreenHeader(title = "Qarz-nasiya", onBack = onBack) }
            item {
                OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
                    Row {
                        TotalBlock(label = "Menga qarzdor", amount = totals.owedToMe, modifier = Modifier.weight(1f))
                        TotalBlock(label = "Men qarzdorman", amount = totals.iOwe, modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showClosed, onClick = { showClosed = false }, label = { Text("Ochiq") })
                    FilterChip(selected = showClosed, onClick = { showClosed = true }, label = { Text("Yopilgan") })
                }
            }
            if (visible.isEmpty()) {
                item { EmptyText(if (showClosed) "Yopilgan qarzlar yo'q" else "Ochiq qarz yo'q — + bilan qo'shing") }
            }
            items(visible, key = { it.id }) { debt ->
                DebtCard(
                    debt = debt,
                    today = today,
                    onClick = { onOpenSheet(FinanceSheet.DebtEdit(debt)) },
                    onToggleStatus = { onToggleStatus(debt) }
                )
            }
        }
    }
}

@Composable
private fun DebtCard(debt: Debt, today: LocalDate, onClick: () -> Unit, onToggleStatus: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val isOpen = debt.status == DebtStatus.OCHIQ
    val overdue = isOpen && debt.dueDate != null && debt.dueDate.isBefore(today)
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), gap = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = debt.personName,
                color = if (isOpen) colors.text else colors.text.muted(0.5f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatSom(debt.amount)} so'm",
                color = if (isOpen) colors.text else colors.text.muted(0.5f),
                fontSize = 15.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(
                text = if (debt.direction == DebtDirection.MENGA_QARZDOR) "Menga qarzdor" else "Men qarzdorman",
                variant = TagVariant.NEUTRAL
            )
            if (overdue) TagChip(text = "Muddati o'tdi", variant = TagVariant.OUTLINE)
            Text(
                text = dueText(debt, today),
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isOpen) "Yopish" else "Qayta ochish",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onToggleStatus).padding(4.dp)
            )
        }
    }
}

private fun dueText(debt: Debt, today: LocalDate): String {
    val due = debt.dueDate ?: return "muddatsiz"
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        debt.status == DebtStatus.YOPILGAN -> "${due.dayOfMonth}-${monthNameUz(due.monthValue)}"
        days < 0 -> "${-days} kun o'tdi"
        days == 0L -> "bugun"
        days == 1L -> "ertaga"
        else -> "${due.dayOfMonth}-${monthNameUz(due.monthValue)}gacha"
    }
}

// ---------------------------------------------------------------- Jamg'arma

@Composable
fun SavingsScreen(goals: List<SavingsGoal>, onBack: () -> Unit, onOpenSheet: (FinanceSheet) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    SubScreenFrame(fabOnClick = { onOpenSheet(FinanceSheet.GoalEdit(null)) }) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SubScreenHeader(title = "Jamg'arma", onBack = onBack) }
            if (goals.isNotEmpty()) {
                item {
                    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
                        Row {
                            TotalBlock(label = "Yig'ildi", amount = goals.sumOf { it.currentAmount }, modifier = Modifier.weight(1f))
                            TotalBlock(label = "Maqsad", amount = goals.sumOf { it.targetAmount }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Noutbuk, to'y, mashina — nima uchun yig'yapsiz? + bilan birinchi maqsadni qo'shing.",
                        color = colors.text.muted(0.55f),
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            items(goals, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    today = today,
                    onEdit = { onOpenSheet(FinanceSheet.GoalEdit(goal)) },
                    onAdjust = { onOpenSheet(FinanceSheet.GoalAdjust(goal)) }
                )
            }
        }
    }
}

@Composable
private fun GoalCard(goal: SavingsGoal, today: LocalDate, onEdit: () -> Unit, onAdjust: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val ratio = if (goal.targetAmount <= 0) 0f else (goal.currentAmount.toFloat() / goal.targetAmount).coerceIn(0f, 1f)
    val reached = goal.currentAmount >= goal.targetAmount
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = goal.name,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            if (reached) {
                TagChip(text = "Yetildi", variant = TagVariant.ACCENT)
            } else {
                Text(
                    text = "${(ratio * 100).toInt()}%",
                    color = colors.accent,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        LinearProgressTrack(progress = ratio, trackColor = colors.neutral800, progressColor = colors.accent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${formatSom(goal.currentAmount)} / ${formatSom(goal.targetAmount)} so'm",
                    color = colors.text.muted(0.7f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
                goalPaceText(goal, today)?.let {
                    Text(
                        text = it,
                        color = colors.text.muted(0.5f),
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "+ Pul qo'shish",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onAdjust).padding(4.dp)
            )
        }
    }
}

/** "15-dekabrgacha · oyiga ~250 000 so'm" — how much to set aside monthly to hit the deadline. */
private fun goalPaceText(goal: SavingsGoal, today: LocalDate): String? {
    val deadline = goal.deadline ?: return null
    val remaining = goal.targetAmount - goal.currentAmount
    val dateLabel = "${deadline.dayOfMonth}-${monthNameUz(deadline.monthValue)}gacha"
    if (remaining <= 0) return dateLabel
    if (deadline.isBefore(today)) return "Muddat o'tgan · ${formatSom(remaining)} so'm qoldi"
    val months = ((ChronoUnit.DAYS.between(today, deadline) + 29) / 30).coerceAtLeast(1)
    return "$dateLabel · oyiga ~${formatSom(remaining / months)} so'm"
}

// ---------------------------------------------------------------- Rejali xarajatlar

@Composable
fun PlannedExpensesScreen(
    planned: List<PlannedExpense>,
    onBack: () -> Unit,
    onOpenSheet: (FinanceSheet) -> Unit
) {
    val today = LocalDate.now()
    val upcoming = planned.filter { it.paidDate == null }
    val paid = planned.filter { it.paidDate != null }.sortedByDescending { it.paidDate }
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

    SubScreenFrame(fabOnClick = { onOpenSheet(FinanceSheet.PlannedEdit(null)) }) {
        LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SubScreenHeader(title = "Kelgusi pullar", onBack = onBack) }
            item {
                val thisMonth = upcoming.filter { !it.dueDate.isAfter(monthEnd) }
                OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
                    Row {
                        TotalBlock(
                            label = "Shu oy olinadi",
                            amount = thisMonth.filter { it.isIncome }.sumOf { it.amount },
                            modifier = Modifier.weight(1f)
                        )
                        TotalBlock(
                            label = "Shu oy to'lanadi",
                            amount = thisMonth.filter { !it.isIncome }.sumOf { it.amount },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (planned.isEmpty()) {
                item { EmptyText("Hali reja yo'q. + bilan to'lanadigan yoki olinadigan pulni qo'shing — eslatma o'zi keladi.") }
            }
            items(upcoming, key = { it.id }) { expense ->
                OnIkkiCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    PlannedRow(
                        expense = expense,
                        today = today,
                        onClick = { onOpenSheet(FinanceSheet.PlannedEdit(expense)) },
                        onPay = { onOpenSheet(FinanceSheet.PlannedPay(expense)) }
                    )
                }
            }
            if (paid.isNotEmpty()) {
                item { Text(text = "YAKUNLANGANLAR", color = LocalOnIkkiColors.current.text.muted(0.45f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(top = 8.dp)) }
                items(paid, key = { "paid-${it.id}" }) { expense ->
                    PlannedRow(expense = expense, today = today, onClick = { onOpenSheet(FinanceSheet.PlannedEdit(expense)) }, onPay = null)
                }
            }
        }
    }
}
