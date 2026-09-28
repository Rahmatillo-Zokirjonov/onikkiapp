package com.onikki.app.ui.finance

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
    onOpenSavings: () -> Unit
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
                cash = state.cashBalance,
                card = state.cardBalance,
                trend = state.balanceTrend
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
private fun BalanceCard(balance: Long, cash: Long, card: Long, trend: List<Long>) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(label = "Naqd", amount = cash, modifier = Modifier.weight(1f))
            MiniStat(label = "Karta", amount = card, modifier = Modifier.weight(1f))
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
private fun FilledSparkline(points: List<Long>, strokeColor: Color, fillColor: Color) {
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
                transactions.forEach { tx -> TransactionRow(tx, onClick = { onEdit(tx) }, showDate = true) }
            }
        }
    }
}

@Composable
fun TransactionRow(transaction: Transaction, onClick: () -> Unit, showDate: Boolean) {
    val colors = LocalOnIkkiColors.current
    val isIncome = transaction.type == TransactionType.KIRIM
    val subtitle = listOfNotNull(
        if (transaction.wallet == Wallet.NAQD) "Naqd" else "Karta",
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
