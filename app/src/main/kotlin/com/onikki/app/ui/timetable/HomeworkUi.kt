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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.HomeworkBlockScope
import com.onikki.app.data.db.entity.HomeworkStatus
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.domain.timetable.HomeworkRules
import com.onikki.app.domain.timetable.HomeworkState
import com.onikki.app.domain.timetable.Timetable
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.TimePickerField
import com.onikki.app.ui.notes.dot
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.monthNameUz
import com.onikki.app.ui.util.weekdayNameUz
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private fun whenText(at: LocalDateTime, now: LocalDateTime): String {
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> "Bugun"
        now.toLocalDate().plusDays(1) -> "Ertaga"
        now.toLocalDate().minusDays(1) -> "Kecha"
        else -> "${at.dayOfMonth}-${monthNameUz(at.monthValue)}, ${weekdayNameUz(at.dayOfWeek).lowercase()}"
    }
    return "$day %02d:%02d".format(at.hour, at.minute)
}

// ---------------------------------------------------------------- list

/**
 * One line to add homework: the subject is picked for you (the lesson going on or just over), you
 * type what was given; it's due at that subject's next lesson and reminded the evening before.
 */
@Composable
private fun QuickHomework(state: TimetableUiState, now: LocalDateTime, viewModel: TimetableViewModel) {
    val colors = LocalOnIkkiColors.current
    val subjects = remember(state.entries) { HomeworkRules.subjects(state.entries) }
    if (subjects.isEmpty()) return
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf<String?>(null) }
    val chosen = subject ?: com.onikki.app.domain.timetable.HomeworkQuick.suggestSubject(state.entries, now) ?: subjects.first()
    val next = remember(chosen, state.entries, now.toLocalDate()) { HomeworkRules.nextLesson(state.entries, chosen, now) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(chosen) + subjects.filter { it != chosen }).forEach { s ->
                FilterChip(selected = s == chosen, onClick = { subject = s }, label = { Text(s, maxLines = 1) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; saved = null },
                placeholder = { Text("Vazifa: masalan 12-mashq") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OnIkkiButton(
                text = "＋",
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.quickHomework(chosen, text)
                        saved = "✓ $chosen: ${text.trim()}"
                        text = ""
                    }
                }
            )
        }
        Text(
            text = saved ?: next?.let { "Muddat: keyingi dars — ${whenText(it, now)}" } ?: "Muddat: ertaga 09:00",
            color = if (saved != null) colors.accent else colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
fun HomeworkList(state: TimetableUiState, now: LocalDateTime, viewModel: TimetableViewModel) {
    val colors = LocalOnIkkiColors.current
    var subjectFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = HomeworkRules.pending(state.homework)
    val subjects = pending.map { it.subject }.distinct()
    val visible = pending.filter { subjectFilter == null || it.subject == subjectFilter }
    val history = state.homework.filter { it.status != HomeworkStatus.PENDING }.sortedByDescending { it.finishedAt ?: 0L }.take(20)
    val blocking = HomeworkRules.blocking(state.homework, now)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickHomework(state, now, viewModel)
        if (blocking.isNotEmpty()) {
            Text(
                text = "⚠ ${blocking.size} ta vazifa xavfli zonada — bajarilmaguncha ilovalar bloklangan",
                color = colors.warmAccent,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, colors.warmBorder), RoundedCornerShape(12.dp)).padding(12.dp)
            )
        }
        if (subjects.size > 1) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = subjectFilter == null, onClick = { subjectFilter = null }, label = { Text("Barchasi ${pending.size}") })
                subjects.forEach { s ->
                    FilterChip(selected = subjectFilter == s, onClick = { subjectFilter = if (subjectFilter == s) null else s }, label = { Text("$s ${pending.count { it.subject == s }}") })
                }
            }
        }
        if (pending.isEmpty()) {
            Text(
                text = if (state.homework.isEmpty()) "Hali vazifa yo'q. + bilan fan bo'yicha vazifa qo'shing: muddat, eslatma va bajarilmasa ilovalarni bloklash."
                else "🎉 Hamma vazifa bajarilgan",
                color = colors.text.muted(0.55f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        visible.forEach { hw ->
            HomeworkCard(
                hw = hw,
                now = now,
                subjectColor = Timetable.lastUsed(state.entries, hw.subject)?.color?.dot(),
                onClick = { viewModel.openHomework(hw) },
                onDone = { viewModel.setHomeworkStatus(hw, HomeworkStatus.DONE) },
                onMissed = { viewModel.setHomeworkStatus(hw, HomeworkStatus.MISSED) }
            )
        }
        if (history.isNotEmpty()) {
            Text(text = "TARIX", color = colors.text.muted(0.45f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 10.dp))
            val done = state.homework.count { it.status == HomeworkStatus.DONE }
            val missed = state.homework.count { it.status == HomeworkStatus.MISSED }
            Text(text = "✓ $done bajarildi · ✗ $missed bajarilmadi", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            history.forEach { hw ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openHomework(hw) }.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = if (hw.status == HomeworkStatus.DONE) "✓" else "✗",
                        color = if (hw.status == HomeworkStatus.DONE) colors.habitAccent else colors.warmAccent,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text(
                        text = "${hw.subject}: ${hw.title}",
                        color = colors.text.muted(0.6f),
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeworkCard(
    hw: Homework,
    now: LocalDateTime,
    subjectColor: androidx.compose.ui.graphics.Color?,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onMissed: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val state = HomeworkRules.state(hw, now)
    val danger = state == HomeworkState.DANGER || state == HomeworkState.OVERDUE
    val left = HomeworkRules.formatLeft(HomeworkRules.timeLeft(hw, now))
    val dangerStart = HomeworkRules.dangerStart(hw)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(if (danger) colors.warmAccent.copy(alpha = 0.08f) else colors.surface)
            .border(BorderStroke(if (danger) 1.5.dp else 1.dp, if (danger) colors.warmAccent else colors.cardBorder), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
    Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(subjectColor ?: colors.accent))
    Column(
        modifier = Modifier.weight(1f).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = hw.subject.uppercase(), color = subjectColor ?: colors.accent, style = OnIkkiType.kicker, modifier = Modifier.weight(1f))
            Text(
                text = when (state) {
                    HomeworkState.CALM -> "$left qoldi"
                    HomeworkState.DANGER -> "⚠ $left qoldi"
                    HomeworkState.OVERDUE -> "⏰ $left"
                    else -> ""
                },
                color = if (danger) colors.warmAccent else colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(text = hw.title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
        hw.note?.let { Text(text = it, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(
            text = listOfNotNull(
                "Muddat: ${whenText(hw.dueAt, now)}",
                hw.remindAt?.takeIf { it.isAfter(now) }?.let { "🔔 ${whenText(it, now)}" },
                when {
                    dangerStart == null -> null
                    danger -> "🔒 ilovalar bloklangan"
                    else -> "🔒 ${whenText(dangerStart, now)} dan bloklanadi"
                }
            ).joinToString(" · "),
            color = if (danger) colors.warmAccent else colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OnIkkiButton(text = "✓ Bajardim", onClick = onDone, modifier = Modifier.weight(1f))
            if (state == HomeworkState.OVERDUE || danger) {
                OnIkkiButton(text = "✗ Bajarmadim", onClick = onMissed, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
            }
        }
    }
    }
}

// ---------------------------------------------------------------- sheet

private enum class DueChoice(val label: String) { NEXT_LESSON("Keyingi dars"), TOMORROW("Ertaga"), CUSTOM("Boshqa vaqt") }
private enum class RemindChoice(val label: String) { NONE("Yo'q"), EVENING_BEFORE("Oldingi kun 20:00"), THREE_HOURS("3 soat oldin"), CUSTOM("Boshqa vaqt") }

private val BLOCK_HOURS = listOf(2, 4, 8, 12, 24)

@Composable
fun HomeworkSheet(
    target: HomeworkSheetTarget,
    entries: List<TimetableEntry>,
    now: LocalDateTime,
    onDismiss: () -> Unit,
    onSave: (Homework) -> Unit,
    onStatus: (Homework, HomeworkStatus) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val hw = target.homework
    val key = hw?.id
    val subjects = HomeworkRules.subjects(entries)
    var subject by rememberSaveable(key) { mutableStateOf(hw?.subject ?: target.subject ?: subjects.firstOrNull().orEmpty()) }
    var title by rememberSaveable(key) { mutableStateOf(hw?.title ?: "") }
    var note by rememberSaveable(key) { mutableStateOf(hw?.note ?: "") }
    val nextLesson = remember(subject, entries) { HomeworkRules.nextLesson(entries, subject, now) }
    var dueChoice by rememberSaveable(key) { mutableStateOf(if (hw != null) DueChoice.CUSTOM else DueChoice.NEXT_LESSON) }
    var dueDate by rememberSaveable(key) { mutableStateOf(hw?.dueAt?.toLocalDate() ?: now.toLocalDate().plusDays(1)) }
    var dueTime by rememberSaveable(key) { mutableStateOf(hw?.dueAt?.toLocalTime() ?: LocalTime.of(9, 0)) }
    var remindChoice by rememberSaveable(key) { mutableStateOf(if (hw?.remindAt != null) RemindChoice.CUSTOM else if (hw != null) RemindChoice.NONE else RemindChoice.EVENING_BEFORE) }
    var remindDate by rememberSaveable(key) { mutableStateOf(hw?.remindAt?.toLocalDate() ?: now.toLocalDate()) }
    var remindTime by rememberSaveable(key) { mutableStateOf(hw?.remindAt?.toLocalTime() ?: LocalTime.of(20, 0)) }
    var block by rememberSaveable(key) { mutableStateOf(hw?.blockHoursBefore != null || hw == null) }
    var blockHours by rememberSaveable(key) { mutableStateOf(hw?.blockHoursBefore ?: 8) }
    var scope by rememberSaveable(key) { mutableStateOf(hw?.blockScope ?: HomeworkBlockScope.CONTROLLED) }
    var error by remember { mutableStateOf<String?>(null) }

    val dueAt: LocalDateTime = when (dueChoice) {
        DueChoice.NEXT_LESSON -> nextLesson ?: now.toLocalDate().plusDays(1).atTime(9, 0)
        DueChoice.TOMORROW -> now.toLocalDate().plusDays(1).atTime(dueTime)
        DueChoice.CUSTOM -> dueDate.atTime(dueTime)
    }
    val remindAt: LocalDateTime? = when (remindChoice) {
        RemindChoice.NONE -> null
        RemindChoice.EVENING_BEFORE -> dueAt.toLocalDate().minusDays(1).atTime(20, 0)
        RemindChoice.THREE_HOURS -> dueAt.minusHours(3)
        RemindChoice.CUSTOM -> remindDate.atTime(remindTime)
    }

    OnIkkiSheet(title = if (hw == null) "Yangi vazifa" else "Vazifa", onDismiss = onDismiss) {
        SheetFieldLabel("Fan")
        if (subjects.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                subjects.forEach { s -> FilterChip(selected = subject == s, onClick = { subject = s; error = null }, label = { Text(s) }) }
            }
        }
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it; error = null },
            label = { Text(if (subjects.isEmpty()) "Fan nomi (jadvalga dars qo'shsangiz, shu yerda chiqadi)" else "yoki boshqa fan") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = title, onValueChange = { title = it; error = null }, label = { Text("Nima qilish kerak (masalan: 12-mashq)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Izoh (ixtiyoriy)") }, modifier = Modifier.fillMaxWidth())

        SheetFieldLabel("Muddat")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            DueChoice.entries.forEach { c ->
                if (c == DueChoice.NEXT_LESSON && nextLesson == null) return@forEach
                FilterChip(selected = dueChoice == c, onClick = { dueChoice = c }, label = { Text(c.label) })
            }
        }
        if (dueChoice == DueChoice.CUSTOM) {
            DatePickerField(label = "Sana", date = dueDate, onDateChange = { it?.let { d -> dueDate = d } })
        }
        if (dueChoice != DueChoice.NEXT_LESSON) {
            TimePickerField(label = "Soat", time = dueTime, onTimeChange = { dueTime = it })
        }
        Text(text = "📅 ${whenText(dueAt, now)} · ${HomeworkRules.formatLeft(java.time.Duration.between(now, dueAt))}${if (dueAt.isAfter(now)) " qoldi" else ""}", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)

        SheetFieldLabel("Eslatma qachon")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            RemindChoice.entries.forEach { c -> FilterChip(selected = remindChoice == c, onClick = { remindChoice = c }, label = { Text(c.label) }) }
        }
        if (remindChoice == RemindChoice.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) { DatePickerField(label = "Sana", date = remindDate, onDateChange = { it?.let { d -> remindDate = d } }) }
                TimePickerField(label = "Soat", time = remindTime, onTimeChange = { remindTime = it }, modifier = Modifier.weight(1f))
            }
        }
        remindAt?.let {
            Text(
                text = if (it.isAfter(now)) "🔔 ${whenText(it, now)}" else "Bu vaqt o'tib ketgan — eslatma kelmaydi",
                color = if (it.isAfter(now)) colors.text.muted(0.6f) else colors.warmAccent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { block = !block }) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Bajarilmasa ilovalarni bloklash", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "Faqat muddatga yaqinlashganda (xavfli zona) — \"Bajardim\" bosilguncha", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }
            Switch(checked = block, onCheckedChange = { block = it })
        }
        if (block) {
            SheetFieldLabel("Muddatga necha soat qolganda")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                BLOCK_HOURS.forEach { h -> FilterChip(selected = blockHours == h, onClick = { blockHours = h }, label = { Text("$h soat") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HomeworkBlockScope.entries.forEach { s -> FilterChip(selected = scope == s, onClick = { scope = s }, label = { Text(s.label) }) }
            }
            val starts = dueAt.minusHours(blockHours.toLong())
            Text(
                text = (if (starts.isAfter(now)) "🔒 ${whenText(starts, now)} dan bloklanadi" else "🔒 Saqlashingiz bilan bloklanadi (muddatga $blockHours soatdan kam qoldi)") +
                    if (scope == HomeworkBlockScope.ALL) " — qo'ng'iroq, SMS va sozlamalar ochiq qoladi" else " — Ilovalar nazorati'dagi ilovalar",
                color = colors.warmAccent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }

        if (hw != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hw.status == HomeworkStatus.PENDING) {
                    OnIkkiButton(text = "✓ Bajardim", onClick = { onStatus(hw, HomeworkStatus.DONE) }, modifier = Modifier.weight(1f))
                    OnIkkiButton(text = "✗ Bajarmadim", onClick = { onStatus(hw, HomeworkStatus.MISSED) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                } else {
                    OnIkkiButton(text = "Qayta ochish", onClick = { onStatus(hw, HomeworkStatus.PENDING) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                when {
                    subject.isBlank() -> error = "Fanni tanlang yoki yozing"
                    title.isBlank() -> error = "Vazifani yozing"
                    hw == null && !dueAt.isAfter(now) -> error = "Muddat o'tib ketgan — kelajakdagi vaqtni tanlang"
                    else -> onSave(
                        (hw ?: Homework(subject = subject, title = title, dueAt = dueAt)).copy(
                            subject = subject.trim(),
                            title = title.trim(),
                            note = note.trim().ifBlank { null },
                            dueAt = dueAt,
                            remindAt = remindAt?.takeIf { it.isAfter(now) },
                            blockHoursBefore = if (block) blockHours else null,
                            blockScope = scope
                        )
                    )
                }
            },
            onDelete = if (hw != null) onDelete else null
        )
    }
}
