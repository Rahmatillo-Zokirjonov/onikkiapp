package com.onikki.app.ui.habits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.ALL_DAYS
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.WEEKDAYS
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.domain.habits.HabitDay
import com.onikki.app.domain.habits.HabitDayState
import com.onikki.app.domain.habits.HabitStats
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiColorTokens
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek

@Composable
fun HabitsRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val habitRepository = remember { HabitRepository(app.database.habitDao(), app.database.habitLogDao()) }
    val viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.factory(habitRepository))
    val state by viewModel.uiState.collectAsState()
    var openHabitId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openStats = openHabitId?.let(state::find)

    // The open habit was deleted (from its own edit sheet) → fall back to the list.
    LaunchedEffect(openHabitId, openStats, state.isLoaded) {
        if (openHabitId != null && openStats == null && state.isLoaded) openHabitId = null
    }
    BackHandler(enabled = openHabitId != null) { openHabitId = null }

    if (openStats != null) {
        HabitDetailScreen(
            stats = openStats,
            today = state.today,
            onBack = { openHabitId = null },
            onEdit = { viewModel.openEdit(openStats.habit) },
            onSetTodayCount = { viewModel.setTodayCount(openStats, it) },
            onToggleDay = { day -> viewModel.toggleDay(openStats, day.date, day.state == HabitDayState.DONE) }
        )
    } else {
        HabitsScreen(
            state = state,
            onTapToday = viewModel::tapToday,
            onOpenHabit = { openHabitId = it.habit.id },
            onAddHabit = viewModel::openNewHabit
        )
    }

    state.sheet?.let { target ->
        HabitSheet(
            habit = target.habit,
            onDismiss = viewModel::dismissSheet,
            onSave = { name, icon, dailyTarget, activeDays ->
                viewModel.saveHabit(target.habit, name, icon, dailyTarget, activeDays)
            },
            onDelete = { target.habit?.let(viewModel::deleteHabit) }
        )
    }
}

@Composable
fun HabitsScreen(
    state: HabitsUiState,
    onTapToday: (HabitStats) -> Unit,
    onOpenHabit: (HabitStats) -> Unit,
    onAddHabit: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Odatlar", color = colors.text, style = OnIkkiType.screenTitle)
                    if (state.habits.isNotEmpty()) {
                        val week = state.weekCompletionPercent?.let { " · shu hafta $it%" } ?: ""
                        Text(
                            text = "Bugun ${state.doneTodayCount}/${state.dueTodayCount} bajarildi$week",
                            color = colors.text.muted(0.5f),
                            fontSize = 11.sp,
                            fontFamily = OnIkkiFontFamily,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                OnIkkiButton(
                    text = "+ Odat",
                    onClick = onAddHabit,
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }

        state.topStreak?.let { top -> item(key = "streak") { TopStreakCard(top) } }

        if (state.isLoaded && state.habits.isEmpty()) {
            item(key = "empty") { EmptyHabits(onAddHabit) }
        }

        items(state.habits, key = { it.habit.id }) { stats ->
            HabitRow(stats = stats, onTapToday = { onTapToday(stats) }, onOpen = { onOpenHabit(stats) })
        }
    }
}

@Composable
private fun EmptyHabits(onAddHabit: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Text(text = "Hali odat qo'shilmagan", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Kichikdan boshlang: bitta odat, har kuni bir marta belgilang. Ketma-ket kunlar streak bo'lib yig'iladi.",
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = "Birinchi odatni qo'shish", onClick = onAddHabit, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TopStreakCard(top: HabitStats) {
    val colors = LocalOnIkkiColors.current
    // Weekly completion bars from the same history the detail grid uses.
    val weeks = top.history.chunked(7).map { week ->
        val due = week.count { it.state == HabitDayState.DONE || it.state == HabitDayState.MISSED || it.state == HabitDayState.PARTIAL }
        val done = week.count { it.state == HabitDayState.DONE }
        if (due == 0) null else done.toFloat() / due
    }
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Eng uzun joriy streak", color = colors.accent, style = OnIkkiType.kicker)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = top.currentStreak.toString(),
                color = colors.warmAccent,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.03).em,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = "kun — ${top.habit.name}",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp).padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            weeks.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(weekBarColor(fraction, colors), RoundedCornerShape(3.dp))
                )
            }
        }
        Text(
            text = "oxirgi ${weeks.size} hafta",
            color = colors.text.muted(0.4f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

private fun weekBarColor(fraction: Float?, colors: OnIkkiColorTokens): Color = when {
    fraction == null -> colors.neutral800.copy(alpha = 0.4f)
    fraction >= 0.85f -> colors.accent
    fraction >= 0.6f -> colors.accent600
    fraction >= 0.3f -> colors.accent700
    else -> colors.accent800
}

@Composable
private fun HabitRow(stats: HabitStats, onTapToday: () -> Unit, onOpen: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        padding = PaddingValues(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 13.dp),
        gap = 9.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitIcon(stats.habit.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.habit.name,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (stats.isActiveToday) scheduleLabel(stats.habit) else "bugun dam · ${scheduleLabel(stats.habit)}",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stats.currentStreak.toString(),
                    color = if (stats.currentStreak > 0) colors.warmAccent else colors.text.muted(0.4f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(text = "kun", color = colors.text.muted(0.45f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
            }
            TodayCheck(stats = stats, onClick = onTapToday)
        }
        WeekStrip(stats.last7Days)
    }
}

/** The quick "today" control: a check for one-shot habits, a count ring ("3/8") for counted ones. */
@Composable
fun TodayCheck(stats: HabitStats, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 38.dp) {
    val colors = LocalOnIkkiColors.current
    val done = stats.isDoneToday
    val partial = !done && stats.todayCount > 0
    Box(
        modifier = Modifier
            .size(size)
            .background(if (done) colors.accent else Color.Transparent, CircleShape)
            .border(
                BorderStroke(if (partial) 2.dp else 1.dp, if (done || partial) colors.accent else colors.divider),
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            done -> Text(text = "✓", color = colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            stats.target > 1 -> Text(
                text = "${stats.todayCount}/${stats.target}",
                color = if (partial) colors.accent else colors.text.muted(0.5f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily
            )
            else -> Unit
        }
    }
}

@Composable
private fun WeekStrip(days: List<HabitDay>) {
    val colors = LocalOnIkkiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .dayCell(day.state, colors, RoundedCornerShape(50))
                )
                Text(
                    text = weekdayAbbrUz(day.date.dayOfWeek),
                    color = colors.text.muted(0.35f),
                    fontSize = 9.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
fun HabitIcon(icon: String, boxSize: androidx.compose.ui.unit.Dp = 30.dp) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(boxSize).background(colors.accent900, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, color = colors.accent300, fontSize = (boxSize.value * 0.47f).sp)
    }
}

/** Shared fill/outline per day state, used by the 7-day strip and the detail history grid. */
fun Modifier.dayCell(
    state: HabitDayState,
    colors: OnIkkiColorTokens,
    shape: androidx.compose.ui.graphics.Shape
): Modifier = when (state) {
    HabitDayState.DONE -> background(colors.accent, shape)
    HabitDayState.PARTIAL -> background(colors.accent700, shape)
    HabitDayState.MISSED -> background(colors.neutral700, shape)
    HabitDayState.PENDING -> border(BorderStroke(1.dp, colors.accent600), shape)
    HabitDayState.OFF -> border(BorderStroke(1.dp, colors.divider), shape)
    HabitDayState.NONE -> background(colors.neutral800.copy(alpha = 0.35f), shape)
}

fun scheduleLabel(habit: Habit): String {
    val days = when (habit.activeDays) {
        ALL_DAYS -> "har kuni"
        WEEKDAYS -> "ish kunlari"
        ALL_DAYS xor WEEKDAYS -> "dam olish kunlari"
        else -> DayOfWeek.entries.filter { habit.activeDays and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { weekdayAbbrUz(it) }
    }
    return if (habit.dailyTarget > 1) "$days · ${habit.dailyTarget} marta" else days
}
