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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import com.onikki.app.data.repository.BudgetProgress
import com.onikki.app.data.repository.CategorySlice
import com.onikki.app.data.repository.FinanceRepository
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatCompactSom
import com.onikki.app.ui.util.formatSom
import kotlin.math.roundToInt

@Composable
fun FinanceRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val financeRepository = remember {
        FinanceRepository(app.database.transactionDao(), app.database.categoryBudgetDao())
    }
    val viewModel: FinanceViewModel = viewModel(factory = FinanceViewModel.factory(financeRepository))
    val state by viewModel.uiState.collectAsState()
    FinanceScreen(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onOpenAddSheet = viewModel::openAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onSaveTransaction = viewModel::addTransaction
    )
}

@Composable
fun FinanceScreen(
    state: FinanceUiState,
    onSelectPeriod: (MoneyPeriod) -> Unit,
    onOpenAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onSaveTransaction: (amount: Long, type: TransactionType, category: String, wallet: Wallet, note: String?) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 16.dp),
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

            BalanceCard(balance = state.balance, cash = state.cashBalance, card = state.cardBalance)
            ExpenseBreakdownCard(total = state.expenseTotal, slices = state.expenseSlices)
            BudgetLimitsCard(budgets = state.budgets)
            RecentTransactionsSection(transactions = state.recentTransactions)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 16.dp)
                .size(52.dp)
                .clickable(onClick = onOpenAddSheet)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", color = colors.onAccent, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (state.isAddSheetOpen) {
        AddTransactionSheet(onDismiss = onDismissAddSheet, onSave = onSaveTransaction)
    }
}

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
private fun BalanceCard(balance: Long, cash: Long, card: Long) {
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
            FilledSparkline(strokeColor = colors.accent, fillColor = colors.accent900)
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
private fun FilledSparkline(strokeColor: Color, fillColor: Color) {
    Canvas(modifier = Modifier.size(width = 92.dp, height = 40.dp)) {
        val s = size.width / 92f
        val points = listOf(
            2f to 32f, 13f to 27f, 24f to 29f, 35f to 18f,
            46f to 23f, 57f to 13f, 68f to 16f, 84f to 5f
        )
        val line = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                val offset = Offset(x * s, y * s)
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().first * s, size.height)
            lineTo(points.first().first * s, size.height)
            close()
        }
        drawPath(fill, color = fillColor)
        drawPath(line, color = strokeColor, style = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
private fun BudgetLimitsCard(budgets: List<BudgetProgress>) {
    if (budgets.isEmpty()) return
    val colors = LocalOnIkkiColors.current
    Column {
        Text(
            text = "Budjet limitlari",
            color = colors.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
            budgets.forEach { progress ->
                val limit = progress.budget.monthlyLimit
                val ratio = if (limit <= 0) 0f else progress.spent.toFloat() / limit.toFloat()
                val isWarning = limit > 0 && progress.spent >= (limit * 0.9)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsSection(transactions: List<Transaction>) {
    val colors = LocalOnIkkiColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Oxirgi tranzaksiyalar",
                color = colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
            Text(text = "Barchasi", color = colors.accent, fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        }
        if (transactions.isEmpty()) {
            Text(
                text = "Hali tranzaksiya yo'q",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Column(modifier = Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                transactions.forEach { tx -> TransactionRow(tx) }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction) {
    val colors = LocalOnIkkiColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(colors.surface, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = transaction.category.take(1).uppercase(),
                color = colors.accent300,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(
            text = transaction.category,
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = (if (transaction.type == TransactionType.CHIQIM) "− " else "+ ") + formatSom(transaction.amount),
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}
