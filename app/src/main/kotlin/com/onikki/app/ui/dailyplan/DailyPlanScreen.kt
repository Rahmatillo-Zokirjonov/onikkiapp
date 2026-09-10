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
import com.onikki.app.data.db.entity.TaskCategory
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
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek
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
        onToggleTask = viewModel::toggleTask,
        onOpenAddSheet = viewModel::openAddSheet,
        onDismissAddSheet = viewModel::dismissAddSheet,
        onSaveTask = viewModel::addTask
    )
}

@Composable
fun DailyPlanScreen(
    state: DailyPlanUiState,
    city: CityLocation,
    onSelectDate: (java.time.LocalDate) -> Unit,
    onToggleTask: (Task) -> Unit,
    onOpenAddSheet: () -> Unit,
    onDismissAddSheet: () -> Unit,
    onSaveTask: (title: String, time: LocalTime, category: TaskCategory) -> Unit
) {
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
                onClick = onOpenAddSheet,
                variant = OnIkkiButtonVariant.PRIMARY,
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
            )
        }

        if (state.selectedDate == java.time.LocalDate.now()) {
            LiveNextPrayerCard(prayerTimes)
        }

        WeekStrip(selectedDate = state.selectedDate, onSelectDate = onSelectDate)

        OnIkkiCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (state.selectedDate == java.time.LocalDate.now()) "Bugun bajarildi" else "Bajarildi",
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

    if (state.isAddSheetOpen) {
        AddTaskSheet(onDismiss = onDismissAddSheet, onSave = onSaveTask)
    }
}

@Composable
private fun TimeLabel(time: LocalTime?) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "",
        color = colors.text.muted(0.45f),
        fontSize = 11.sp,
        fontFamily = OnIkkiFontFamily,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        modifier = Modifier.width(38.dp).padding(end = 10.dp)
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
private fun WeekStrip(selectedDate: java.time.LocalDate, onSelectDate: (java.time.LocalDate) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { monday.plusDays(it.toLong()) }
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
                    color = if (isSelected) colors.accent100 else colors.text,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
