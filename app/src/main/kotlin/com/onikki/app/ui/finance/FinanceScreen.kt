package com.onikki.app.ui.finance

import java.time.temporal.ChronoUnit
import java.time.LocalDate
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import com.onikki.app.data.repository.AccountBalance
import com.onikki.app.data.db.entity.RepeatKind
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.entity.Account
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import com.onikki.app.data.repository.BudgetProgress
import com.onikki.app.data.repository.CategorySlice
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatCompactSom
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatSom
import kotlin.math.roundToInt

@Composable
fun FinanceScreen(
    state: FinanceUiState,
    onSelectPeriod: (MoneyPeriod) -> Unit,
    onOpenSheet: (FinanceSheet) -> Unit,
    onOpenAllTransactions: () -> Unit,
    onOpenDebts: () -> Unit,
    onOpenSavings: () -> Unit,
    onOpenPlanned: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                // Extra bottom space so the floating "+" never covers the last transaction row.
                .padding(top = 14.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Moliya", color = colors.text, style = OnIkkiType.screenTitle)
                PeriodSegmentedControl(selected = state.period, onSelect = onSelectPeriod)
            }

            BalanceCard(
                balance = state.balance,
                accounts = state.accounts,
                trend = state.balanceTrend,
                onEditAccount = { onOpenSheet(FinanceSheet.AccountEdit(it)) },
                onAddAccount = { onOpenSheet(FinanceSheet.AccountEdit(null)) }
            )
            PlannedSummaryCard(
                planned = state.upcomingPlanned,
                onOpenAll = onOpenPlanned,
                onAdd = { onOpenSheet(FinanceSheet.PlannedEdit(null)) },
                onPay = { onOpenSheet(FinanceSheet.PlannedPay(it)) }
            )
            ExpenseBreakdownCard(total = state.expenseTotal, slices = state.expenseSlices)
            BudgetLimitsCard(
                budgets = state.budgets,
                onAdd = { onOpenSheet(FinanceSheet.BudgetEdit(null)) },
                onEdit = { onOpenSheet(FinanceSheet.BudgetEdit(it)) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebtSummaryCard(totals = state.debtTotals, onClick = onOpenDebts, modifier = Modifier.weight(1f))
                SavingsSummaryCard(goals = state.goals, onClick = onOpenSavings, modifier = Modifier.weight(1f))
            }
            RecentTransactionsSection(
                transactions = state.transactions.take(RECENT_TRANSACTION_COUNT),
                accountName = state::accountName,
                onOpenAll = onOpenAllTransactions,
                onEdit = { onOpenSheet(FinanceSheet.TransactionEdit(it)) }
            )
        }

        AddFab(
            onClick = { onOpenSheet(FinanceSheet.TransactionEdit(null)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)
        )
    }
}

private const val RECENT_TRANSACTION_COUNT = 6

@Composable
private fun PeriodSegmentedControl(selected: MoneyPeriod, onSelect: (MoneyPeriod) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.medium)) {
        MoneyPeriod.entries.forEach { p ->
            val isSelected = p == selected
            Box(
                modifier = Modifier
                    .clickable { onSelect(p) }
                    .padding(2.dp)
                    .let {
                        if (isSelected) it.border(BorderStroke(1.dp, colors.accent), OnIkkiShapes.medium) else it
                    }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = p.label,
                    color = if (isSelected) colors.accent else colors.text.muted(0.7f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    balance: Long,
    accounts: List<AccountBalance>,
    trend: List<Long>,
    onEditAccount: (Account) -> Unit,
    onAddAccount: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Umumiy balans",
                    color = colors.text.muted(0.55f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                    Text(text = formatSom(balance), color = colors.text, style = OnIkkiType.amountLarge)
                    Text(
                        text = " so'm",
                        color = colors.text.muted(0.55f),
                        fontSize = 14.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            // Only drawn once there's real movement to show — a flat or empty line says nothing.
            if (trend.distinct().size > 1) {
                FilledSparkline(points = trend, strokeColor = colors.accent, fillColor = colors.accent900)
            }
        }
        // Every wallet (cash + each card); tap one to edit it, "+ Hamyon" to add a card.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.forEach { item ->
                MiniStat(
                    label = accountLabel(item.account),
                    amount = item.balance,
                    modifier = Modifier.width(128.dp).clickable { onEditAccount(item.account) }
                )
            }
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .background(colors.background, OnIkkiShapes.medium)
                    .clickable(onClick = onAddAccount)
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "+ Hamyon", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, amount: Long, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .background(colors.background, OnIkkiShapes.medium)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(text = label, color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = formatSom(amount),
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun FilledSparkline(points: List<Long>, strokeColor: Color, fillColor: Color) {
    Canvas(modifier = Modifier.size(width = 92.dp, height = 40.dp)) {
        val stroke = 2.dp.toPx()
        val min = points.min()
        val range = (points.max() - min).coerceAtLeast(1L).toFloat()
        val stepX = size.width / (points.size - 1)
        val usableHeight = size.height - stroke * 2
        val offsets = points.mapIndexed { index, value ->
            Offset(index * stepX, stroke + usableHeight * (1f - (value - min) / range))
        }
        val line = Path().apply {
            offsets.forEachIndexed { index, o -> if (index == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(fill, color = fillColor)
        drawPath(line, color = strokeColor, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ExpenseBreakdownCard(total: Long, slices: List<CategorySlice>) {
    val colors = LocalOnIkkiColors.current
    // Warm accent is reserved for streak/budget-warning/day-rating (TZ), so unlike the mockup's
    // pixel colors, this chart never uses it — the 4th slice gets an extra accent-ramp step instead.
    val sliceColors = listOf(colors.accent500, colors.accent700, colors.accent400, colors.accent300, colors.neutral600)
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        if (slices.isEmpty()) {
            Text(
                text = "Bu davrda xarajat yo'q",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DonutChart(slices = slices, sliceColors = sliceColors, trackColor = colors.neutral800) {
                    Text(
                        text = "Chiqim",
                        color = colors.neutral500,
                        fontSize = 10.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                    Text(
                        text = formatCompactSom(total),
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slices.forEachIndexed { index, slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(sliceColors.getOrElse(index) { colors.neutral600 }, RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = slice.category,
                                color = colors.text,
                                fontSize = 12.sp,
                                fontFamily = OnIkkiFontFamily,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(slice.fraction * 100).roundToInt()}%",
                                color = colors.text.muted(0.6f),
                                fontSize = 12.sp,
                                fontFamily = OnIkkiFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    slices: List<CategorySlice>,
    sliceColors: List<Color>,
    trackColor: Color,
    size: Dp = 112.dp,
    strokeWidth: Dp = 15.dp,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = sliceColors.getOrElse(index) { trackColor },
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun FinanceSectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onAction).padding(4.dp)
            )
        }
    }
}

@Composable
private fun BudgetLimitsCard(
    budgets: List<BudgetProgress>,
    onAdd: () -> Unit,
    onEdit: (CategoryBudget) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column {
        FinanceSectionHeader(title = "Budjet limitlari", actionLabel = "+ Limit", onAction = onAdd)
        if (budgets.isEmpty()) {
            OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd), gap = 4.dp) {
                Text(
                    text = "Kategoriya uchun oylik limit belgilang",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "Limitga yaqinlashganda shu yerda ogohlantiramiz.",
                    color = colors.text.muted(0.55f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            return@Column
        }
        OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 12.dp) {
            budgets.forEach { progress ->
                val limit = progress.budget.monthlyLimit
                val ratio = if (limit <= 0) 0f else progress.spent.toFloat() / limit.toFloat()
                val isWarning = limit > 0 && progress.spent >= (limit * 0.9)
                val over = progress.spent - limit
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onEdit(progress.budget) },
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = progress.budget.category,
                            color = colors.text.muted(0.8f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(
                            text = "${formatSom(progress.spent)} / ${formatSom(limit)}",
                            color = colors.text.muted(0.55f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                    LinearProgressTrack(
                        progress = ratio.coerceIn(0f, 1f),
                        trackColor = colors.neutral800,
                        progressColor = if (isWarning) colors.warmAccent else colors.accent
                    )
                    if (isWarning) {
                        Text(
                            text = if (over > 0) "Limitdan ${formatSom(over)} so'm oshdi" else "Limitga ${formatSom(-over)} so'm qoldi",
                            color = colors.warmAccent,
                            fontSize = 11.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtSummaryCard(totals: DebtTotals, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = modifier.clickable(onClick = onClick), gap = 4.dp) {
        Text(text = "Qarz-nasiya", color = colors.accent, style = OnIkkiType.kicker)
        if (totals.openCount == 0) {
            Text(text = "Ochiq qarz yo'q", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        } else {
            MiniLine(label = "Menga", amount = totals.owedToMe)
            MiniLine(label = "Mendan", amount = totals.iOwe)
        }
    }
}

@Composable
private fun SavingsSummaryCard(goals: List<SavingsGoal>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = modifier.clickable(onClick = onClick), gap = 4.dp) {
        Text(text = "Jamg'arma", color = colors.accent, style = OnIkkiType.kicker)
        if (goals.isEmpty()) {
            Text(text = "Maqsad qo'shing", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        } else {
            val saved = goals.sumOf { it.currentAmount }
            val target = goals.sumOf { it.targetAmount }
            MiniLine(label = "Yig'ildi", amount = saved)
            LinearProgressTrack(
                progress = if (target <= 0) 0f else (saved.toFloat() / target).coerceIn(0f, 1f),
                trackColor = colors.neutral800,
                progressColor = colors.accent,
                height = 4.dp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MiniLine(label: String, amount: Long) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        Text(text = formatCompactSom(amount), color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun RecentTransactionsSection(
    transactions: List<Transaction>,
    accountName: (Long) -> String?,
    onOpenAll: () -> Unit,
    onEdit: (Transaction) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column {
        FinanceSectionHeader(title = "Oxirgi tranzaksiyalar", actionLabel = "Barchasi", onAction = onOpenAll)
        if (transactions.isEmpty()) {
            Text(
                text = "Hali tranzaksiya yo'q — pastdagi + tugmasi bilan qo'shing",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                transactions.forEach { tx ->
                    TransactionRow(tx, accountName = accountName(tx.accountId), onClick = { onEdit(tx) }, showDate = true)
                }
            }
        }
    }
}

@Composable
fun TransactionRow(transaction: Transaction, accountName: String?, onClick: () -> Unit, showDate: Boolean) {
    val colors = LocalOnIkkiColors.current
    val isIncome = transaction.type == TransactionType.KIRIM
    val subtitle = listOfNotNull(
        accountName ?: if (transaction.wallet == Wallet.NAQD) "Naqd" else "Karta",
        if (showDate) formatRelativeDateUz(transaction.date) else null,
        transaction.note
    ).joinToString(" · ")
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(colors.surface, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = transaction.category.take(1).uppercase(),
                color = colors.accent300,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.category, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = subtitle,
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1
            )
        }
        Text(
            text = (if (isIncome) "+ " else "− ") + formatSom(transaction.amount),
            color = if (isIncome) colors.accent else colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

/** "Karta •• 1234" for cards with digits, else the wallet's name. */
fun accountLabel(account: Account): String =
    account.lastDigits?.let { "${account.name} •• $it" } ?: account.name

@Composable
private fun PlannedSummaryCard(
    planned: List<PlannedExpense>,
    onOpenAll: () -> Unit,
    onAdd: () -> Unit,
    onPay: (PlannedExpense) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val dueThisMonth = planned.filter { !it.dueDate.isAfter(monthEnd) }
    Column {
        FinanceSectionHeader(
            title = "Kelgusi pullar",
            actionLabel = if (planned.isEmpty()) "+ Qo'shish" else "Barchasi",
            onAction = if (planned.isEmpty()) onAdd else onOpenAll
        )
        if (planned.isEmpty()) {
            Text(
                text = "To'lashingiz (ijara, internet, kredit) yoki olishingiz kerak bo'lgan pullarni (maosh, qarz qaytishi) belgilang — o'sha kuni eslatamiz.",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
            return@Column
        }
        OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
            if (dueThisMonth.isNotEmpty()) {
                val outgoing = dueThisMonth.filter { !it.isIncome }.sumOf { it.amount }
                val incoming = dueThisMonth.filter { it.isIncome }.sumOf { it.amount }
                Text(
                    text = "Oy oxirigacha: " + listOfNotNull(
                        incoming.takeIf { it > 0 }?.let { "+ ${formatSom(it)}" },
                        outgoing.takeIf { it > 0 }?.let { "− ${formatSom(it)}" }
                    ).joinToString(" · ") + " so'm",
                    color = colors.text.muted(0.55f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            planned.take(3).forEach { expense -> PlannedRow(expense, today, onClick = onOpenAll, onPay = { onPay(expense) }) }
        }
    }
}

@Composable
fun PlannedRow(expense: PlannedExpense, today: LocalDate, onClick: () -> Unit, onPay: (() -> Unit)?) {
    val colors = LocalOnIkkiColors.current
    val days = ChronoUnit.DAYS.between(today, expense.dueDate)
    val overdue = expense.paidDate == null && days < 0
    val whenText = when {
        expense.paidDate != null -> "${if (expense.isIncome) "Olingan" else "To'langan"} · ${formatRelativeDateUz(expense.paidDate)}"
        days < 0 -> "${-days} kun kechikdi"
        days == 0L -> "Bugun"
        days == 1L -> "Ertaga"
        days < 7 -> "$days kundan keyin"
        else -> formatRelativeDateUz(expense.dueDate)
    }
    val repeat = if (expense.repeat == RepeatKind.NONE) "" else " · ${expense.repeat.label.lowercase()}"
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = expense.title, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
            Text(
                text = whenText + repeat,
                // Warm accent is reserved for warnings — an overdue payment is one.
                color = if (overdue || days == 0L) colors.warmAccent else colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(
            text = (if (expense.isIncome) "+ " else "− ") + formatSom(expense.amount),
            color = if (expense.isIncome) colors.accent else colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        if (onPay != null && expense.paidDate == null) {
            Text(
                text = expense.doneLabel,
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier
                    .border(BorderStroke(1.dp, colors.accent700), OnIkkiShapes.small)
                    .clickable(onClick = onPay)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}
