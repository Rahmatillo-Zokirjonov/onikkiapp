package com.onikki.app.ui.timetable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.data.db.entity.NoteColor
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import com.onikki.app.data.db.entity.WeekParity
import com.onikki.app.domain.timetable.Timetable
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.components.TimePickerField
import com.onikki.app.ui.notes.dot
import com.onikki.app.ui.notes.tint
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatMinutesAsDuration
import com.onikki.app.ui.util.monthNameUz
import com.onikki.app.ui.util.weekdayAbbrUz
import com.onikki.app.ui.util.weekdayNameUz
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun hm(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)

@Composable
fun TimetableRoute() {
    val viewModel: TimetableViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val colors = LocalOnIkkiColors.current
    val requested by TimetableDeepLink.requested.collectAsState()
    LaunchedEffect(requested) {
        when (requested) {
            TimetableDeepLink.Target.HOMEWORK -> viewModel.setView(TimetableView.HOMEWORK)
            TimetableDeepLink.Target.TIMETABLE, null -> Unit
        }
        if (requested != null) TimetableDeepLink.consume()
    }
    // A clock for the "now" line and badges.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Jadval", color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.weight(1f))
                    ViewToggle(state.view, viewModel::setView)
                }
                if (state.view != TimetableView.HOMEWORK) {
                    NavRow(state, now.toLocalDate(), onShift = viewModel::shift, onToday = { viewModel.setDate(now.toLocalDate()) })
                }
            }
            when (state.view) {
                TimetableView.WEEK -> WeekGrid(state, now, onOpen = viewModel::openEdit, onNewOn = viewModel::openNew, onDay = viewModel::openDay)
                TimetableView.DAY -> DayTimeline(state, now, viewModel)
                TimetableView.HOMEWORK -> HomeworkList(state, now, viewModel)
            }
        }
        AddFab(
            onClick = { if (state.view == TimetableView.HOMEWORK) viewModel.openNewHomework() else viewModel.openNew() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)
        )
    }
    state.homeworkSheet?.let { target ->
        HomeworkSheet(
            target = target,
            entries = state.entries,
            now = now,
            onDismiss = viewModel::dismissHomework,
            onSave = { draft -> viewModel.saveHomework(target.homework, draft) },
            onStatus = { hw, status -> viewModel.setHomeworkStatus(hw, status) },
            onDelete = { target.homework?.let(viewModel::deleteHomework) }
        )
    }

    state.sheet?.let { target ->
        EntrySheet(
            target = target,
            all = state.entries,
            onDismiss = viewModel::dismissSheet,
            onSave = { drafts -> viewModel.save(target.entry, drafts) },
            onDelete = { target.entry?.let(viewModel::delete) }
        )
    }
}

@Composable
private fun ViewToggle(view: TimetableView, onChange: (TimetableView) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.background(colors.surface, RoundedCornerShape(50)).padding(3.dp)) {
        TimetableView.entries.forEach { option ->
            val selected = option == view
            Text(
                text = option.label,
                color = if (selected) colors.onAccent else colors.text.muted(0.7f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun NavRow(state: TimetableUiState, today: LocalDate, onShift: (Long) -> Unit, onToday: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val step = if (state.view == TimetableView.WEEK) 7L else 1L
    val weekStart = Timetable.weekStart(state.date)
    val label = when (state.view) {
        TimetableView.WEEK -> {
            val end = weekStart.plusDays(6)
            val range = if (weekStart.month == end.month) "${weekStart.dayOfMonth}–${end.dayOfMonth} ${monthNameUz(end.monthValue)}"
            else "${weekStart.dayOfMonth} ${monthNameUz(weekStart.monthValue)} – ${end.dayOfMonth} ${monthNameUz(end.monthValue)}"
            "$range · ${Timetable.weekNumber(state.date)}-hafta (${if (Timetable.isOddWeek(state.date)) "toq" else "juft"})"
        }
        TimetableView.HOMEWORK -> ""
        TimetableView.DAY -> when (state.date) {
            today -> "Bugun, ${weekdayNameUz(state.date.dayOfWeek).lowercase()}"
            today.plusDays(1) -> "Ertaga, ${weekdayNameUz(state.date.dayOfWeek).lowercase()}"
            today.minusDays(1) -> "Kecha, ${weekdayNameUz(state.date.dayOfWeek).lowercase()}"
            else -> "${state.date.dayOfMonth}-${monthNameUz(state.date.monthValue)}, ${weekdayNameUz(state.date.dayOfWeek).lowercase()}"
        }
    }
    val isCurrent = if (state.view == TimetableView.WEEK) weekStart == Timetable.weekStart(today) else state.date == today
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "‹", color = colors.text, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { onShift(-step) }.padding(horizontal = 10.dp))
        Text(text = label, color = colors.text.muted(0.85f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        if (!isCurrent) {
            Text(text = "Bugun", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable(onClick = onToday).padding(horizontal = 6.dp, vertical = 4.dp))
        }
        Text(text = "›", color = colors.text, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { onShift(step) }.padding(horizontal = 10.dp))
    }
}

// ---------------------------------------------------------------- Haftalik

private val HOUR_HEIGHT = 52.dp
private val TIME_COLUMN = 34.dp

@Composable
private fun WeekGrid(
    state: TimetableUiState,
    now: LocalDateTime,
    onOpen: (TimetableEntry) -> Unit,
    onNewOn: (LocalDate) -> Unit,
    onDay: (LocalDate) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val weekStart = Timetable.weekStart(state.date)
    val allDays = (0 until 7).map { weekStart.plusDays(it.toLong()) }
    // Weekends only when something is on them.
    val days = allDays.filter { d -> d.dayOfWeek.value <= 5 || Timetable.on(state.entries, d).isNotEmpty() }
    val inWeek = days.flatMap { Timetable.on(state.entries, it) }
    val hours = Timetable.hourRange(inWeek)
    val totalHeight = HOUR_HEIGHT * (hours.last - hours.first)

    Column(modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 6.dp)) {
        Row {
            Box(modifier = Modifier.width(TIME_COLUMN))
            days.forEach { d ->
                val today = d == now.toLocalDate()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onDay(d) }.padding(vertical = 4.dp)
                ) {
                    Text(text = weekdayAbbrUz(d.dayOfWeek), color = if (today) colors.accent else colors.text.muted(0.6f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                    Text(
                        text = "${d.dayOfMonth}",
                        color = if (today) colors.onAccent else colors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.background(if (today) colors.accent else Color.Transparent, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (inWeek.isEmpty()) {
            Text(
                text = "Bu haftada jadval bo'sh. + bilan dars, ish yoki mashg'ulot qo'shing — har hafta, har kuni yoki bir martalik.",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(12.dp)
            )
        }
        Row(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 90.dp)) {
            Box(modifier = Modifier.width(TIME_COLUMN).height(totalHeight)) {
                for (h in hours.first until hours.last) {
                    Text(
                        text = "%02d".format(h),
                        color = colors.text.muted(0.4f),
                        fontSize = 10.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.offset(y = HOUR_HEIGHT * (h - hours.first) - 6.dp)
                    )
                }
            }
            days.forEach { d ->
                val isToday = d == now.toLocalDate()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(totalHeight)
                        .padding(horizontal = 1.5.dp)
                        .background(if (isToday) colors.accent800.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onNewOn(d) }
                ) {
                    for (h in hours.first until hours.last) {
                        Box(modifier = Modifier.offset(y = HOUR_HEIGHT * (h - hours.first)).fillMaxWidth().height(1.dp).background(colors.divider.copy(alpha = 0.4f)))
                    }
                    Timetable.on(state.entries, d).forEach { e ->
                        val top = HOUR_HEIGHT * ((e.start.hour - hours.first) + e.start.minute / 60f)
                        val height = HOUR_HEIGHT * (Timetable.minutes(e.start, e.end).coerceAtLeast(20) / 60f)
                        val tint = e.color?.tint() ?: colors.accent800
                        val edge = e.color?.dot() ?: colors.accent
                        Column(
                            modifier = Modifier
                                .offset(y = top)
                                .fillMaxWidth()
                                .height(height - 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(tint)
                                .border(BorderStroke(1.dp, edge.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                                .clickable { onOpen(e) }
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        ) {
                            Text(text = e.title, color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
                            Timetable.roomLabel(e.place)?.let { room ->
                                Text(text = room, color = edge, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Clip)
                            }
                            if (height >= 44.dp) Text(text = hm(e.start), color = colors.text.muted(0.6f), fontSize = 9.sp, fontFamily = OnIkkiFontFamily)
                        }
                    }
                    if (isToday && now.hour in hours.first until hours.last) {
                        val y = HOUR_HEIGHT * ((now.hour - hours.first) + now.minute / 60f)
                        Box(modifier = Modifier.offset(y = y).fillMaxWidth().height(2.dp).background(colors.warmAccent))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Kunlik

private sealed interface DayItem {
    val time: LocalTime
    data class Block(val entry: TimetableEntry) : DayItem { override val time: LocalTime get() = entry.start }
    data class TaskItem(val task: Task) : DayItem { override val time: LocalTime get() = task.time!! }
    data class Prayer(val name: String, override val time: LocalTime) : DayItem
    data class Gap(override val time: LocalTime, val minutes: Long) : DayItem
}

@Composable
private fun DayTimeline(state: TimetableUiState, now: LocalDateTime, viewModel: TimetableViewModel) {
    val colors = LocalOnIkkiColors.current
    val blocks = Timetable.on(state.entries, state.date)
    val isToday = state.date == now.toLocalDate()
    // Free time between blocks (30+ min) is shown, so the day can be planned into it.
    val gaps = blocks.zipWithNext().mapNotNull { (a, b) ->
        val m = Timetable.minutes(a.end, b.start)
        if (m >= 30) DayItem.Gap(a.end, m) else null
    }
    val items: List<DayItem> = (blocks.map { DayItem.Block(it) } + state.tasks.map { DayItem.TaskItem(it) } +
        state.prayers.map { (n, t) -> DayItem.Prayer(n, t) } + gaps).sortedBy { it.time }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekStrip(state.date, now.toLocalDate(), state.entries, onPick = viewModel::setDate)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (blocks.isEmpty()) {
                Text(
                    text = "Bu kunda jadval yo'q. + bilan blok qo'shing: masalan 8:00–12:00 \"Ish (chuqur)\".",
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items.forEach { item ->
                when (item) {
                    is DayItem.Block -> {
                        BlockCard(item.entry, now.takeIf { isToday }, onClick = { viewModel.openEdit(item.entry) })
                        val due = com.onikki.app.domain.timetable.HomeworkRules.dueOn(state.homework, item.entry.title, state.date)
                        Text(
                            text = if (due.isEmpty()) "        + vazifa" else "        📝 ${due.size} ta vazifa: ${due.joinToString { it.title }}",
                            color = if (due.isEmpty()) colors.text.muted(0.35f) else colors.warmAccent,
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                if (due.isEmpty()) viewModel.openNewHomework(item.entry.title) else viewModel.setView(TimetableView.HOMEWORK)
                            }
                        )
                    }
                    is DayItem.TaskItem -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleTask(item.task) }.padding(vertical = 2.dp)
                    ) {
                        Text(text = hm(item.time), color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(52.dp))
                        Text(text = if (item.task.isCompleted) "☑" else "☐", color = if (item.task.isCompleted) colors.accent else colors.text.muted(0.6f), fontSize = 16.sp)
                        Text(text = item.task.title, color = if (item.task.isCompleted) colors.text.muted(0.45f) else colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(start = 8.dp))
                    }
                    is DayItem.Prayer -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = hm(item.time), color = colors.text.muted(0.4f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.width(52.dp))
                        Text(text = "🕌 ${item.name}", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                    is DayItem.Gap -> Text(
                        text = "        ⋯ bo'sh vaqt ${formatMinutesAsDuration(item.minutes)}",
                        color = colors.text.muted(0.35f),
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.clickable { viewModel.openNew(state.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, entries: List<TimetableEntry>, onPick: (LocalDate) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val start = Timetable.weekStart(selected)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        (0 until 7).map { start.plusDays(it.toLong()) }.forEach { d ->
            val isSel = d == selected
            val count = Timetable.on(entries, d).size
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) colors.accent800 else Color.Transparent)
                    .clickable { onPick(d) }
                    .padding(vertical = 6.dp)
            ) {
                Text(text = weekdayAbbrUz(d.dayOfWeek), color = if (d == today) colors.accent else colors.text.muted(0.6f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "${d.dayOfMonth}", color = if (isSel) colors.accent100 else colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                Text(text = if (count > 0) "•".repeat(count.coerceAtMost(3)) else " ", color = colors.accent, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun BlockCard(e: TimetableEntry, now: LocalDateTime?, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val t = now?.toLocalTime()
    val running = t != null && !t.isBefore(e.start) && t.isBefore(e.end)
    val done = t != null && !t.isBefore(e.end)
    val total = Timetable.minutes(e.start, e.end).coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.width(52.dp)) {
            Text(text = hm(e.start), color = if (running) colors.accent else colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            Text(text = hm(e.end), color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(e.color?.tint() ?: colors.surface)
                .border(BorderStroke(if (running) 1.5.dp else 1.dp, if (running) colors.accent else (e.color?.dot()?.copy(alpha = 0.4f) ?: colors.cardBorder)), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = e.title,
                    color = if (done) colors.text.muted(0.5f) else colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Timetable.roomLabel(e.place)?.let { room ->
                    Text(
                        text = "📍 $room",
                        color = colors.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.background(colors.background.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (running) Text(text = "HOZIR", color = colors.accent, style = OnIkkiType.kicker, modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                text = listOfNotNull(
                    formatMinutesAsDuration(total),
                    when (e.repeat) {
                        TimetableRepeat.DAILY -> "har kuni"
                        TimetableRepeat.ONCE -> "bir martalik"
                        TimetableRepeat.WEEKLY -> if (e.parity == WeekParity.ALL) null else e.parity.label.lowercase()
                    },
                    e.remindMinutes?.let { if (it == 0) "🔔" else "🔔 $it daq oldin" }
                ).joinToString(" · "),
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            e.note?.let { Text(text = it, color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (running) {
                LinearProgressTrack(progress = Timetable.minutes(e.start, t!!) / total.toFloat(), trackColor = colors.neutral800, progressColor = colors.accent, height = 4.dp)
                Text(text = "${Timetable.minutes(t, e.end)} daqiqa qoldi", color = colors.accent, fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}

// ---------------------------------------------------------------- sheet

private val REMIND_OPTIONS: List<Pair<Int?, String>> = listOf(null to "Yo'q", 0 to "Boshlanishida", 5 to "5 daq", 10 to "10 daq", 15 to "15 daq", 30 to "30 daq")

@Composable
private fun EntrySheet(target: EntrySheetTarget, all: List<TimetableEntry>, onDismiss: () -> Unit, onSave: (List<TimetableEntry>) -> Unit, onDelete: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val e = target.entry
    val key = e?.id
    var title by rememberSaveable(key) { mutableStateOf(e?.title ?: "") }
    var repeat by rememberSaveable(key) { mutableStateOf(e?.repeat ?: TimetableRepeat.WEEKLY) }
    var days by rememberSaveable(key) { mutableStateOf(setOf(e?.dayOfWeek ?: target.date.dayOfWeek.value)) }
    var date by rememberSaveable(key) { mutableStateOf(e?.date ?: target.date) }
    var start by rememberSaveable(key) { mutableStateOf(e?.start ?: LocalTime.of(9, 0)) }
    var end by rememberSaveable(key) { mutableStateOf(e?.end ?: LocalTime.of(10, 0)) }
    var place by rememberSaveable(key) { mutableStateOf(e?.place ?: "") }
    var note by rememberSaveable(key) { mutableStateOf(e?.note ?: "") }
    var color by rememberSaveable(key) { mutableStateOf(e?.color) }
    var parity by rememberSaveable(key) { mutableStateOf(e?.parity ?: WeekParity.ALL) }
    var remind by rememberSaveable(key) { mutableStateOf(if (e == null) 10 else e.remindMinutes) }
    var error by remember { mutableStateOf<String?>(null) }

    fun drafts(): List<TimetableEntry> {
        val base = TimetableEntry(
            title = title.trim(),
            repeat = repeat,
            date = if (repeat == TimetableRepeat.ONCE) date else null,
            start = start,
            end = end,
            place = place.trim().ifBlank { null },
            note = note.trim().ifBlank { null },
            color = color,
            parity = if (repeat == TimetableRepeat.WEEKLY) parity else WeekParity.ALL,
            remindMinutes = remind
        )
        return if (repeat == TimetableRepeat.WEEKLY) days.sorted().map { base.copy(dayOfWeek = it) } else listOf(base)
    }
    val clashes = if (title.isBlank() || !end.isAfter(start)) emptyList()
    else drafts().flatMap { d -> Timetable.clashes(all, d.copy(id = e?.id ?: -1), target.date) }.distinctBy { it.id }

    OnIkkiSheet(title = if (e == null) "Yangi blok" else "Blokni tahrirlash", onDismiss = onDismiss) {
        fun pickTitle(t: String) {
            title = t
            error = null
            // The same lesson again: bring its room and colour along.
            if (e == null) Timetable.lastUsed(all, t)?.let { prev ->
                if (place.isBlank()) place = prev.place.orEmpty()
                if (color == null) color = prev.color
            }
        }
        OutlinedTextField(value = title, onValueChange = { title = it; error = null }, label = { Text("Nomi (dars, ish, sport…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val known = all.map { it.title }.distinct().filter { it != title }.take(6)
        SuggestionChips(options = known.ifEmpty { listOf("Ingliz tili", "Matematika", "Ish", "Sport", "Kitob o'qish") }, selected = title, onSelect = ::pickTitle)

        SheetFieldLabel("Takrorlanish")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimetableRepeat.entries.forEach { r -> FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label) }) }
        }
        when (repeat) {
            TimetableRepeat.WEEKLY -> {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val selected = d.value in days
                        FilterChip(
                            selected = selected,
                            onClick = {
                                // Editing moves the one block; adding can pick several days at once.
                                days = when {
                                    e != null -> setOf(d.value)
                                    selected && days.size > 1 -> days - d.value
                                    selected -> days
                                    else -> days + d.value
                                }
                            },
                            label = { Text(weekdayAbbrUz(d)) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekParity.entries.forEach { p -> FilterChip(selected = parity == p, onClick = { parity = p }, label = { Text(p.label) }) }
                }
            }
            TimetableRepeat.ONCE -> DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { d -> date = d } })
            TimetableRepeat.DAILY -> Text(text = "Har kuni shu vaqtda", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePickerField(label = "Boshlanishi", time = start, onTimeChange = {
                // Keep the length when the start moves.
                val length = Timetable.minutes(start, end).coerceAtLeast(15)
                start = it
                end = it.plusMinutes(length).let { e2 -> if (e2.isBefore(it)) LocalTime.of(23, 59) else e2 }
            }, modifier = Modifier.weight(1f))
            TimePickerField(label = "Tugashi", time = end, onTimeChange = { end = it; error = null }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            value = place,
            onValueChange = { place = it.take(30) },
            label = { Text("Xona (masalan 204 yoki A-12)") },
            singleLine = true,
            supportingText = Timetable.roomLabel(place)?.takeIf { place.trim().all { c -> c.isDigit() } }?.let { { Text("Ko'rinishi: $it") } },
            modifier = Modifier.fillMaxWidth()
        )
        val rooms = all.mapNotNull { it.place?.trim() }.filter { it.isNotEmpty() && it != place.trim() }.distinct().take(6)
        if (rooms.isNotEmpty()) SuggestionChips(options = rooms, selected = place, onSelect = { place = it })
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Izoh (o'qituvchi, havola…)") }, modifier = Modifier.fillMaxWidth())

        SheetFieldLabel("Rang")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier.size(28.dp).background(colors.accent800, CircleShape)
                    .border(BorderStroke(2.dp, if (color == null) colors.text else Color.Transparent), CircleShape).clickable { color = null }
            )
            NoteColor.entries.forEach { c ->
                Box(
                    modifier = Modifier.size(28.dp).background(c.dot(), CircleShape)
                        .border(BorderStroke(2.dp, if (color == c) colors.text else Color.Transparent), CircleShape).clickable { color = c }
                )
            }
        }
        SheetFieldLabel("Eslatma")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            REMIND_OPTIONS.forEach { (value, label) -> FilterChip(selected = remind == value, onClick = { remind = value }, label = { Text(label) }) }
        }
        if (clashes.isNotEmpty()) {
            Text(
                text = "⚠ Vaqti ustma-ust: ${clashes.joinToString { "${it.title} (${hm(it.start)}–${hm(it.end)})" }}",
                color = colors.warmAccent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                when {
                    title.isBlank() -> error = "Nomini yozing"
                    !end.isAfter(start) -> error = "Tugash vaqti boshlanishidan keyin bo'lsin"
                    repeat == TimetableRepeat.WEEKLY && days.isEmpty() -> error = "Kamida bitta kunni tanlang"
                    else -> onSave(drafts())
                }
            },
            onDelete = if (e != null) onDelete else null
        )
    }
}
