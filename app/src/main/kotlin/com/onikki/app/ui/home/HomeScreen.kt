package com.onikki.app.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.repository.HabitProgress
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.ui.components.CircularProgressRing
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.TaskRowCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatFullDateUz
import com.onikki.app.ui.util.formatMinutesAsDuration
import com.onikki.app.ui.util.formatSom
import java.time.LocalDate

@Composable
fun HomeRoute(onNavigateToDayReview: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val db = app.database
    val habitRepository = remember { HabitRepository(db.habitDao(), db.habitLogDao()) }
    val locationStore = remember { LocationStore(app) }
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(db.taskDao(), habitRepository, db.transactionDao(), locationStore)
    )
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onToggleTask = viewModel::toggleTask,
        onToggleHabit = viewModel::toggleHabitToday,
        onOpenDayReview = onNavigateToDayReview
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleTask: (Task) -> Unit,
    onToggleHabit: (HabitProgress) -> Unit,
    onOpenDayReview: () -> Unit = {}
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GreetingHeader()
        NextPrayerCard(
            prayerName = state.nextPrayerName,
            prayerTime = state.nextPrayerTime,
            minutesUntil = state.minutesUntilNextPrayer
        )
        TodayPlanSection(
            tasks = state.tasks,
            completedCount = state.completedCount,
            totalCount = state.totalCount,
            onToggleTask = onToggleTask
        )
        HabitsSection(habits = state.habits, onToggleHabit = onToggleHabit)
        BalanceCard(balance = state.balance, income = state.income, expense = state.expense)
        DayReviewEntryRow(onClick = onOpenDayReview)
    }
}

@Composable
private fun DayReviewEntryRow(onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiRowCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Text(
            text = "Kun yakuni",
            color = colors.text,
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        Text(text = "→", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun GreetingHeader() {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(
                text = "Assalomu alaykum",
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = formatFullDateUz(LocalDate.now()),
                color = colors.text.muted(0.45f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.accent800, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "O", color = colors.accent100, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        }
    }
}

@Composable
private fun NextPrayerCard(prayerName: String, prayerTime: java.time.LocalTime, minutesUntil: Long) {
    val colors = LocalOnIkkiColors.current
    OnIkkiRowCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Keyingi namoz",
                color = colors.accent,
                style = OnIkkiType.kicker
            )
            Text(
                text = "$prayerName · %02d:%02d".format(prayerTime.hour, prayerTime.minute),
                color = colors.text,
                fontSize = 17.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatMinutesAsDuration(minutesUntil.coerceAtLeast(0)),
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = "qoldi",
                color = colors.text.muted(0.45f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
private fun TodayPlanSection(
    tasks: List<Task>,
    completedCount: Int,
    totalCount: Int,
    onToggleTask: (Task) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = "Bugungi reja", color = colors.text, style = OnIkkiType.sectionHeader)
            if (totalCount > 0) {
                Text(
                    text = "$completedCount/$totalCount bajarildi",
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tasks.isEmpty()) {
                Text(
                    text = "Bugun uchun vazifa yo'q",
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            } else {
                tasks.forEach { task -> TaskRowCard(task = task, onToggle = { onToggleTask(task) }) }
            }
        }
    }
}

@Composable
private fun HabitsSection(habits: List<HabitProgress>, onToggleHabit: (HabitProgress) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column {
        Text(
            text = "Odatlar",
            color = colors.text,
            style = OnIkkiType.sectionHeader,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (habits.isEmpty()) {
            Text(
                text = "Hali odat qo'shilmagan",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits, key = { it.habit.id }) { progress ->
                    HabitRingCard(progress = progress, onClick = { onToggleHabit(progress) })
                }
            }
        }
    }
}

@Composable
private fun HabitRingCard(progress: HabitProgress, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(
        modifier = Modifier
            .size(width = 96.dp, height = 108.dp)
            .clickable(onClick = onClick),
        padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
        gap = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressRing(
                progress = progress.completionRate,
                trackColor = colors.neutral800,
                progressColor = colors.accent,
                size = 52.dp
            ) {
                Text(
                    text = "${(progress.completionRate * 100).toInt()}%",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        Text(
            text = progress.habit.name,
            color = colors.text,
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "${progress.habit.streakCount} kun",
            color = colors.warmAccent,
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun BalanceCard(balance: Long, income: Long, expense: Long) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Balans",
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
            Sparkline(color = colors.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "Kirim ${formatSom(income)}",
                color = colors.text.muted(0.6f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = "Chiqim ${formatSom(expense)}",
                color = colors.text.muted(0.6f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
private fun Sparkline(color: androidx.compose.ui.graphics.Color) {
    Canvas(modifier = Modifier.size(width = 96.dp, height = 38.dp)) {
        val s = size.width / 96f
        val points = listOf(
            2f to 30f, 14f to 24f, 26f to 27f, 38f to 16f,
            50f to 21f, 62f to 12f, 74f to 15f, 86f to 6f
        )
        val path = Path().apply {
            points.forEachIndexed { index, (x, y) ->
                val offset = Offset(x * s, y * s)
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
