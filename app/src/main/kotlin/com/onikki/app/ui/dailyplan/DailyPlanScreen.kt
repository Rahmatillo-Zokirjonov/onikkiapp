package com.onikki.app.ui.dailyplan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.UZBEKISTAN_CITIES
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.TaskRowCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.monthNameUz
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

private sealed class PlanItem(open val time: LocalTime?) {
    data class TaskItem(val task: Task) : PlanItem(task.time)
    data class PrayerMarker(val name: String, val markerTime: LocalTime) : PlanItem(markerTime)
}

private fun groupLabel(time: LocalTime?): String = when {
    time == null -> "Vaqt belgilanmagan"
    time.hour < 12 -> "Ertalab"
    time.hour < 18 -> "Kunduzi"
    else -> "Kechqurun"
}

private val GROUP_ORDER = listOf("Vaqt belgilanmagan", "Ertalab", "Kunduzi", "Kechqurun")

@Composable
fun DailyPlanRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val locationStore = remember { LocationStore(app) }
    val city by locationStore.city.collectAsState(initial = UZBEKISTAN_CITIES.first())
    val viewModel: DailyPlanViewModel = viewModel(factory = DailyPlanViewModel.factory(app.database.taskDao()))
    val state by viewModel.uiState.collectAsState()
    DailyPlanScreen(
        state = state,
        city = city,
        onSelectDate = viewModel::selectDate,
        onShiftWeek = viewModel::shiftWeek,
        onGoToToday = viewModel::goToToday,
        onToggleTask = viewModel::toggleTask,
        onNewTask = viewModel::openNewTask,
        onOpenTask = viewModel::openTask,
        onMoveOverdue = viewModel::moveOverdueToToday
    )

    state.sheet?.let { target ->
        TaskSheet(
            task = target.task,
            defaultDate = state.selectedDate,
            onDismiss = viewModel::dismissSheet,
            onSave = { title, date, time, category -> viewModel.saveTask(target.task, title, date, time, category) },
            onDelete = { target.task?.let(viewModel::deleteTask) }
        )
    }
}

@Composable
fun DailyPlanScreen(
    state: DailyPlanUiState,
    city: CityLocation,
    onSelectDate: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onGoToToday: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onNewTask: () -> Unit,
    onOpenTask: (Task) -> Unit,
    onMoveOverdue: () -> Unit
) {
    val isToday = state.selectedDate == state.today
    val colors = LocalOnIkkiColors.current

    val prayerTimes = remember(state.selectedDate, city) {
        PrayerTimeCalculator.calculate(state.selectedDate, city.latitude, city.longitude, city.utcOffsetHours)
    }
    val grouped = remember(state.tasks, prayerTimes) {
        val items: List<PlanItem> = state.tasks.map { PlanItem.TaskItem(it) } +
            prayerTimes.asOrderedList().map { (name, time) -> PlanItem.PrayerMarker(name, time) }
        val sorted = items.sortedWith(Comparator { a, b -> compareValues(a.time, b.time) })
        sorted.groupBy { groupLabel(it.time) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
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
            Text(text = "Kunlik reja", color = colors.text, style = OnIkkiType.screenTitle)
            OnIkkiButton(
                text = "+ Vazifa",
                onClick = onNewTask,
                variant = OnIkkiButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
            )
        }

        if (isToday) {
            LiveNextPrayerCard(prayerTimes)
        }

        WeekStrip(
            selectedDate = state.selectedDate,
            today = state.today,
            weekLoad = state.weekLoad,
            onSelectDate = onSelectDate,
            onShiftWeek = onShiftWeek,
            onGoToToday = onGoToToday
        )

        if (isToday && state.overdue.isNotEmpty()) {
            OverdueCard(overdue = state.overdue, onMoveToToday = onMoveOverdue, onOpenTask = onOpenTask)
        }

        if (state.totalCount > 0) OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (isToday) "Bugun bajarildi" else "Bajarildi",
                    color = colors.text,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = "${state.completedCount}/${state.totalCount}",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            LinearProgressTrack(
                progress = if (state.totalCount == 0) 0f else state.completedCount / state.totalCount.toFloat(),
                trackColor = colors.neutral800,
                progressColor = colors.accent
            )
        }

        if (state.tasks.isEmpty()) {
            Text(
                text = if (state.selectedDate.isBefore(state.today)) {
                    "Bu kunda vazifa bo'lmagan"
                } else {
                    "Bu kun uchun vazifa yo'q — \"+ Vazifa\" bilan qo'shing"
                },
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }

        GROUP_ORDER.filter { grouped.containsKey(it) }.forEach { label ->
            Column {
                Text(
                    text = label.uppercase(),
                    color = colors.text.muted(0.45f),
                    style = OnIkkiType.kicker,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    grouped[label]?.forEach { item ->
                        when (item) {
                            is PlanItem.TaskItem -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeLabel(item.task.time)
                                    TaskRowCard(
                                        task = item.task,
                                        onToggle = { onToggleTask(item.task) },
                                        onClick = { onOpenTask(item.task) },
                                        showTime = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            is PlanItem.PrayerMarker -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TimeLabel(item.markerTime)
                                    PrayerMarkerRow(name = item.name, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverdueCard(overdue: List<Task>, onMoveToToday: () -> Unit, onOpenTask: (Task) -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "O'tgan kunlardan", color = colors.accent, style = OnIkkiType.kicker)
                Text(
                    text = "${overdue.size} ta bajarilmagan vazifa",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            OnIkkiButton(
                text = "Bugunga ko'chirish",
                onClick = onMoveToToday,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
            )
        }
        overdue.take(OVERDUE_PREVIEW).forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenTask(task) },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = task.title,
                    color = colors.text.muted(0.75f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatRelativeDateUz(task.date),
                    color = colors.text.muted(0.45f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        if (overdue.size > OVERDUE_PREVIEW) {
            Text(
                text = "va yana ${overdue.size - OVERDUE_PREVIEW} ta",
                color = colors.text.muted(0.45f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

private const val OVERDUE_PREVIEW = 3

@Composable
private fun TimeLabel(time: LocalTime?) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "",
        color = colors.text.muted(0.45f),
        fontSize = 11.sp,
        fontFamily = OnIkkiFontFamily,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.width(46.dp).padding(end = 8.dp)
    )
}

@Composable
private fun PrayerMarkerRow(name: String, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.medium)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(colors.accent, CircleShape)
        )
        Text(
            text = "$name namozi",
            color = colors.text.muted(0.75f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "namoz vaqti",
            color = colors.text.muted(0.4f),
            fontSize = 10.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun LiveNextPrayerCard(prayerTimes: PrayerTimeCalculator.PrayerTimes) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalDateTime.now()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val upcoming = prayerTimes.asOrderedList().firstOrNull { it.second.isAfter(now.toLocalTime()) } ?: return
    val target = java.time.LocalDateTime.of(now.toLocalDate(), upcoming.second)
    val secondsUntil = java.time.Duration.between(now, target).seconds.coerceAtLeast(0)
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = "Keyingi namoz", color = colors.accent, style = OnIkkiType.kicker)
                Text(
                    text = "${upcoming.first} · %02d:%02d".format(upcoming.second.hour, upcoming.second.minute),
                    color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily
                )
            }
            Text(
                text = com.onikki.app.ui.util.formatHmsCountdown(secondsUntil),
                color = colors.text, fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    today: LocalDate,
    weekLoad: Map<LocalDate, DayLoad>,
    onSelectDate: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onGoToToday: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${monthNameUz(selectedDate.monthValue).replaceFirstChar { it.uppercase() }} ${selectedDate.year}",
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        if (selectedDate != today) {
            Text(
                text = "Bugun",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onGoToToday).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        WeekArrow(label = "‹", onClick = { onShiftWeek(-1) })
        WeekArrow(label = "›", onClick = { onShiftWeek(1) })
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { day ->
            val isSelected = day == selectedDate
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDate(day) }
                    .background(
                        if (isSelected) colors.accent800 else androidx.compose.ui.graphics.Color.Transparent,
                        OnIkkiShapes.medium
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekdayAbbrUz(day.dayOfWeek),
                    color = if (isSelected) colors.accent100 else colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    color = when {
                        isSelected -> colors.accent100
                        day == today -> colors.accent
                        else -> colors.text
                    },
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
                LoadDot(load = weekLoad[day], selected = isSelected)
            }
        }
    }
}

@Composable
private fun WeekArrow(label: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(32.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = colors.text.muted(0.7f), fontSize = 20.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** Small dot under a day: accent while tasks are pending, faint once everything is done. */
@Composable
private fun LoadDot(load: DayLoad?, selected: Boolean) {
    val colors = LocalOnIkkiColors.current
    val color = when {
        load == null || load.total == 0 -> androidx.compose.ui.graphics.Color.Transparent
        load.done < load.total -> if (selected) colors.accent100 else colors.accent
        else -> colors.text.muted(0.3f)
    }
    Box(modifier = Modifier.padding(top = 4.dp).size(5.dp).background(color, CircleShape))
}
