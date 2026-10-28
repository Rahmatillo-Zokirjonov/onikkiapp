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
import androidx.compose.ui.draw.alpha
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
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ViewToggle(state.view, viewModel::setView)
                if (state.view != TimetableView.HOMEWORK) SheetSyncRow()
                if (state.view != TimetableView.HOMEWORK) {
                    NavRow(
                        state, now.toLocalDate(),
                        onShift = viewModel::shift,
                        onToday = { viewModel.setDate(now.toLocalDate()) },
                        onLayout = viewModel::setWeekLayout
                    )
                }
            }
            when (state.view) {
                TimetableView.WEEK -> when (state.weekLayout) {
                    WeekLayout.LIST -> WeekAgenda(state, now, onOpen = viewModel::openEdit, onNewOn = viewModel::openNew, onDay = viewModel::openDay)
                    WeekLayout.GRID -> WeekGrid(state, now, onOpen = viewModel::openEdit, onNewOn = viewModel::openNew, onDay = viewModel::openDay)
                }
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

/** Full-width three-way switch: Haftalik | Kunlik | Vazifalar (the pending-homework count rides along). */
@Composable
private fun ViewToggle(view: TimetableView, onChange: (TimetableView) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(50)).padding(3.dp)) {
        TimetableView.entries.forEach { option ->
            val selected = option == view
            Text(
                text = option.label,
                color = if (selected) colors.onAccent else colors.text.muted(0.7f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun NavRow(state: TimetableUiState, today: LocalDate, onShift: (Long) -> Unit, onToday: () -> Unit, onLayout: (WeekLayout) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val step = if (state.view == TimetableView.WEEK) 7L else 1L
    val weekStart = Timetable.weekStart(state.date)
    val (main, sub) = when (state.view) {
        TimetableView.WEEK -> {
            val end = weekStart.plusDays(6)
            val range = if (weekStart.month == end.month) "${weekStart.dayOfMonth}–${end.dayOfMonth} ${monthNameUz(end.monthValue)}"
            else "${weekStart.dayOfMonth} ${monthNameUz(weekStart.monthValue).take(3)} – ${end.dayOfMonth} ${monthNameUz(end.monthValue).take(3)}"
            range to "${Timetable.weekNumber(state.date)}-hafta · ${if (Timetable.isOddWeek(state.date)) "toq" else "juft"}"
        }
        else -> {
            val label = when (state.date) {
                today -> "Bugun"
                today.plusDays(1) -> "Ertaga"
                today.minusDays(1) -> "Kecha"
                else -> "${state.date.dayOfMonth}-${monthNameUz(state.date.monthValue)}"
            }
            label to weekdayNameUz(state.date.dayOfWeek)
        }
    }
    val isCurrent = if (state.view == TimetableView.WEEK) weekStart == Timetable.weekStart(today) else state.date == today
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RoundIcon("‹") { onShift(-step) }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = main, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            Text(text = sub, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        }
        RoundIcon("›") { onShift(step) }
        if (!isCurrent) {
            Text(
                text = "Bugun",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).border(BorderStroke(1.dp, colors.accent700), RoundedCornerShape(50)).clickable(onClick = onToday).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        if (state.view == TimetableView.WEEK) {
            val grid = state.weekLayout == WeekLayout.GRID
            Text(
                text = if (grid) "☰" else "▦",
                color = colors.text.muted(0.8f),
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(colors.surface).clickable { onLayout(if (grid) WeekLayout.LIST else WeekLayout.GRID) }.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RoundIcon(symbol: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = symbol, color = colors.text, fontSize = 20.sp, fontFamily = OnIkkiFontFamily) }
}

/** The colour that marks a lesson everywhere (its own, or gold). */
@Composable
private fun TimetableEntry.accent(): Color = color?.dot() ?: LocalOnIkkiColors.current.accent

// ---------------------------------------------------------------- Haftalik: ro'yxat

@Composable
private fun WeekAgenda(
    state: TimetableUiState,
    now: LocalDateTime,
    onOpen: (TimetableEntry) -> Unit,
    onNewOn: (LocalDate) -> Unit,
    onDay: (LocalDate) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val weekStart = Timetable.weekStart(state.date)
    val days = (0 until 7).map { weekStart.plusDays(it.toLong()) }
    val total = days.sumOf { Timetable.on(state.entries, it).size }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (total == 0) {
            EmptyHint("Bu hafta jadval bo'sh", "+ bilan dars, ish yoki mashg'ulot qo'shing — har hafta, har kuni yoki bir martalik.")
        }
        days.forEach { d ->
            val lessons = Timetable.on(state.entries, d)
            // Empty weekend days take no space; empty weekdays get one quiet line.
            if (lessons.isEmpty() && d.dayOfWeek.value >= 6) return@forEach
            val isToday = d == now.toLocalDate()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onDay(d) }) {
                    Text(
                        text = weekdayNameUz(d.dayOfWeek),
                        color = if (isToday) colors.accent else colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OnIkkiFontFamily
                    )
                    Text(
                        text = "  ${d.dayOfMonth}-${monthNameUz(d.monthValue)}",
                        color = colors.text.muted(0.45f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                    if (isToday) {
                        Text(
                            text = "BUGUN",
                            color = colors.onAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OnIkkiFontFamily,
                            modifier = Modifier.padding(start = 8.dp).background(colors.accent, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = if (lessons.isEmpty()) "+" else "${lessons.size} ta  +",
                        color = colors.text.muted(0.5f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onNewOn(d) }.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                if (lessons.isEmpty()) {
                    Text(text = "Bo'sh kun", color = colors.text.muted(0.35f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                lessons.forEach { e ->
                    LessonCard(
                        e = e,
                        now = now.takeIf { isToday },
                        homework = com.onikki.app.domain.timetable.HomeworkRules.dueOn(state.homework, e.title, d),
                        onClick = { onOpen(e) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "📚 $title", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        Text(text = body, color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
}

/**
 * One lesson, everything visible: times on the left, a coloured bar, the title (two lines), the room as
 * a badge, length / week / reminder, homework due for it, and a progress bar while it's on.
 */
@Composable
private fun LessonCard(e: TimetableEntry, now: LocalDateTime?, homework: List<com.onikki.app.data.db.entity.Homework>, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val t = now?.toLocalTime()
    val running = t != null && !t.isBefore(e.start) && t.isBefore(e.end)
    val done = t != null && !t.isBefore(e.end)
    val total = Timetable.minutes(e.start, e.end).coerceAtLeast(1)
    val accent = e.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(if (running) accent.copy(alpha = 0.14f) else colors.surface)
            .border(BorderStroke(if (running) 1.5.dp else 1.dp, if (running) accent else colors.cardBorder), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .alpha(if (done) 0.6f else 1f)
    ) {
        Column(modifier = Modifier.width(62.dp).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = hm(e.start), color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            Text(text = hm(e.end), color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        Box(modifier = Modifier.padding(vertical = 10.dp).width(4.dp).fillMaxHeight().background(accent, RoundedCornerShape(2.dp)))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = e.title,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Timetable.roomLabel(e.place)?.let { room ->
                    Text(
                        text = room,
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OnIkkiFontFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = listOfNotNull(
                    formatMinutesAsDuration(total),
                    when (e.repeat) {
                        TimetableRepeat.DAILY -> "har kuni"
                        TimetableRepeat.ONCE -> "bir martalik"
                        TimetableRepeat.WEEKLY -> if (e.parity == WeekParity.ALL) null else e.parity.label.lowercase()
                    },
                    e.remindMinutes?.let { if (it == 0) "🔔" else "🔔 $it daq" }
                ).joinToString("  ·  "),
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            e.note?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (homework.isNotEmpty()) {
                Text(
                    text = "📝 ${homework.joinToString { it.title }}",
                    color = colors.warmAccent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (running) {
                LinearProgressTrack(progress = Timetable.minutes(e.start, t!!) / total.toFloat(), trackColor = colors.neutral800, progressColor = accent, height = 4.dp)
                Text(text = "Hozir · ${Timetable.minutes(t, e.end)} daqiqa qoldi", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            }
        }
    }
}

// ---------------------------------------------------------------- Haftalik: to'r

private val HOUR_HEIGHT = 64.dp
private val TIME_COLUMN = 38.dp
private val DAY_COLUMN = 108.dp

/** The time grid, with wide day columns that scroll sideways so every block can show its text. */
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
    val days = allDays.filter { d -> d.dayOfWeek.value <= 5 || Timetable.on(state.entries, d).isNotEmpty() }
    val inWeek = days.flatMap { Timetable.on(state.entries, it) }
    val hours = Timetable.hourRange(inWeek)
    val totalHeight = HOUR_HEIGHT * (hours.last - hours.first)
    val horizontal = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 96.dp)) {
        if (inWeek.isEmpty()) {
            Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                EmptyHint("Bu hafta jadval bo'sh", "+ bilan qo'shing. Ustunni bossangiz, o'sha kunga blok qo'shiladi.")
            }
        }
        Row {
            // Hour labels stay put while the days scroll sideways.
            Column(modifier = Modifier.width(TIME_COLUMN)) {
                Box(modifier = Modifier.height(44.dp))
                Box(modifier = Modifier.height(totalHeight)) {
                    for (h in hours.first until hours.last) {
                        Text(
                            text = "%02d:00".format(h),
                            color = colors.text.muted(0.4f),
                            fontSize = 9.sp,
                            fontFamily = OnIkkiFontFamily,
                            modifier = Modifier.offset(x = 4.dp, y = HOUR_HEIGHT * (h - hours.first) - 5.dp)
                        )
                    }
                }
            }
            Row(modifier = Modifier.horizontalScroll(horizontal).padding(end = 12.dp)) {
                days.forEach { d ->
                    val isToday = d == now.toLocalDate()
                    Column(modifier = Modifier.width(DAY_COLUMN)) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 3.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (isToday) colors.accent else colors.surface).clickable { onDay(d) }.padding(vertical = 4.dp)
                        ) {
                            Text(text = weekdayNameUz(d.dayOfWeek), color = if (isToday) colors.onAccent else colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = 1)
                            Text(text = "${d.dayOfMonth}-${monthNameUz(d.monthValue).take(3)}", color = if (isToday) colors.onAccent.copy(alpha = 0.8f) else colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(totalHeight)
                                .padding(horizontal = 2.dp)
                                .background(if (isToday) colors.accent.copy(alpha = 0.05f) else Color.Transparent)
                                .clickable { onNewOn(d) }
                        ) {
                            for (h in hours.first until hours.last) {
                                Box(modifier = Modifier.offset(y = HOUR_HEIGHT * (h - hours.first)).fillMaxWidth().height(1.dp).background(colors.divider.copy(alpha = 0.35f)))
                            }
                            Timetable.on(state.entries, d).forEach { e ->
                                val top = HOUR_HEIGHT * ((e.start.hour - hours.first) + e.start.minute / 60f)
                                val height = HOUR_HEIGHT * (Timetable.minutes(e.start, e.end).coerceAtLeast(25) / 60f)
                                val accent = e.accent()
                                Row(
                                    modifier = Modifier
                                        .offset(y = top + 1.dp)
                                        .fillMaxWidth()
                                        .height(height - 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(accent.copy(alpha = 0.18f))
                                        .clickable { onOpen(e) }
                                ) {
                                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(accent))
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(text = e.title, color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = if (height > 70.dp) 3 else 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                                        Timetable.roomLabel(e.place)?.let { Text(text = it, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        if (height >= 52.dp) Text(text = "${hm(e.start)}–${hm(e.end)}", color = colors.text.muted(0.6f), fontSize = 9.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
                                    }
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
    }
}

// ---------------------------------------------------------------- Kunlik

private sealed interface DayItem {
    val time: LocalTime
    data class Block(val entry: TimetableEntry) : DayItem { override val time: LocalTime get() = entry.start }
    data class TaskItem(val task: Task) : DayItem { override val time: LocalTime get() = task.time!! }
    data class Prayer(val name: String, override val time: LocalTime) : DayItem
    data class Gap(override val time: LocalTime, val minutes: Long) : DayItem
    data class Now(override val time: LocalTime) : DayItem
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
        state.prayers.map { (n, t) -> DayItem.Prayer(n, t) } + gaps +
        listOfNotNull(DayItem.Now(now.toLocalTime()).takeIf { isToday })).sortedBy { it.time }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekStrip(state.date, now.toLocalDate(), state.entries, onPick = viewModel::setDate)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (blocks.isEmpty()) {
                EmptyHint("Bu kunda jadval yo'q", "+ bilan blok qo'shing: masalan 8:00–12:00 \"Ish (chuqur)\" yoki dars.")
            }
            items.forEach { item ->
                when (item) {
                    is DayItem.Block -> LessonCard(
                        e = item.entry,
                        now = now.takeIf { isToday },
                        homework = com.onikki.app.domain.timetable.HomeworkRules.dueOn(state.homework, item.entry.title, state.date),
                        onClick = { viewModel.openEdit(item.entry) }
                    )
                    is DayItem.TaskItem -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { viewModel.toggleTask(item.task) }.padding(vertical = 4.dp)
                    ) {
                        Text(text = hm(item.time), color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.width(62.dp))
                        Text(text = if (item.task.isCompleted) "☑" else "☐", color = if (item.task.isCompleted) colors.accent else colors.text.muted(0.6f), fontSize = 17.sp)
                        Text(
                            text = item.task.title,
                            color = if (item.task.isCompleted) colors.text.muted(0.45f) else colors.text,
                            fontSize = 14.sp,
                            fontFamily = OnIkkiFontFamily,
                            textDecoration = if (item.task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    is DayItem.Prayer -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(text = hm(item.time), color = colors.text.muted(0.4f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.width(62.dp))
                        Text(text = "🕌 ${item.name}", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                    is DayItem.Gap -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { viewModel.openNew(state.date) }.padding(vertical = 2.dp)
                    ) {
                        Box(modifier = Modifier.width(62.dp), contentAlignment = Alignment.Center) { Text(text = "⋮", color = colors.text.muted(0.3f), fontSize = 14.sp) }
                        Text(text = "Bo'sh vaqt · ${formatMinutesAsDuration(item.minutes)}   + blok", color = colors.text.muted(0.4f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                    is DayItem.Now -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(text = hm(item.time), color = colors.warmAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.width(62.dp))
                        Box(modifier = Modifier.size(8.dp).background(colors.warmAccent, CircleShape))
                        Box(modifier = Modifier.weight(1f).height(2.dp).background(colors.warmAccent.copy(alpha = 0.6f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, entries: List<TimetableEntry>, onPick: (LocalDate) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val start = Timetable.weekStart(selected)
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        (0 until 7).map { start.plusDays(it.toLong()) }.forEach { d ->
            val isSel = d == selected
            val isToday = d == today
            val count = Timetable.on(entries, d).size
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSel) colors.accent else colors.surface)
                    .border(BorderStroke(1.dp, if (isToday && !isSel) colors.accent else Color.Transparent), RoundedCornerShape(14.dp))
                    .clickable { onPick(d) }
                    .padding(vertical = 7.dp)
            ) {
                Text(text = weekdayAbbrUz(d.dayOfWeek), color = if (isSel) colors.onAccent.copy(alpha = 0.8f) else colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "${d.dayOfMonth}", color = if (isSel) colors.onAccent else colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Text(
                    text = if (count > 0) "$count" else " ",
                    color = if (isSel) colors.onAccent.copy(alpha = 0.8f) else colors.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OnIkkiFontFamily
                )
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
