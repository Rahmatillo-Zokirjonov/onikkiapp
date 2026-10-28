package com.onikki.app.ui.reminders

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.domain.notifications.ReminderLevel
import com.onikki.app.domain.notifications.ReminderRepeat
import com.onikki.app.domain.notifications.ReminderScheduler
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.TimePickerField
import com.onikki.app.ui.notes.UrgentPermissions
import com.onikki.app.ui.notes.defaultReminderTime
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** A note with a reminder, as the Eslatmalar list shows it. */
val Note.reminderLevel: ReminderLevel
    get() = when {
        remindGate -> ReminderLevel.BEFORE_APP
        priority == NotePriority.JUDA_MUHIM -> ReminderLevel.FULL_SCREEN
        else -> ReminderLevel.TOP
    }

/** Level → how the note is stored: loudness (priority) and the "before the next app" flag. */
fun ReminderLevel.priority(): NotePriority = when (this) {
    ReminderLevel.TOP -> NotePriority.MUHIM
    ReminderLevel.BEFORE_APP -> NotePriority.ODDIY
    ReminderLevel.FULL_SCREEN -> NotePriority.JUDA_MUHIM
}

/** Marks notes made on this screen, so removing the reminder removes the note too. */
const val REMINDER_TAG = "eslatma"

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as OnIkkiApplication).database.noteDao()

    /** Upcoming first (soonest on top); past one-off reminders after them. */
    val reminders: StateFlow<List<Note>> = dao.observeWithReminder().map { list ->
        val now = LocalDateTime.now()
        list.sortedWith(compareBy({ !it.remindAt!!.isAfter(now) }, { it.remindAt }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(existing: Note?, text: String, at: LocalDateTime, repeat: ReminderRepeat, level: ReminderLevel) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (existing == null) {
                dao.insert(
                    Note(
                        title = text.trim(), content = "", tags = listOf(REMINDER_TAG), createdAt = now, updatedAt = now,
                        remindAt = at, priority = level.priority(), remindRepeat = repeat, remindGate = level == ReminderLevel.BEFORE_APP
                    )
                )
            } else {
                dao.update(existing.copy(title = text.trim(), remindAt = at, priority = level.priority(), remindRepeat = repeat, remindGate = level == ReminderLevel.BEFORE_APP, updatedAt = now))
            }
            ReminderScheduler(getApplication()).resync()
        }
    }

    /** A reminder made here goes with its note; a note's own reminder is just switched off. */
    fun remove(note: Note) {
        viewModelScope.launch {
            if (REMINDER_TAG in note.tags && note.content.isBlank()) dao.delete(note) else dao.setRemindAt(note.id, null)
            ReminderScheduler(getApplication()).resync()
        }
    }
}

private val MONTHS = listOf("yan", "fev", "mar", "apr", "may", "iyun", "iyul", "avg", "sen", "okt", "noy", "dek")

private fun whenText(at: LocalDateTime): String {
    val today = LocalDate.now()
    val day = when (at.toLocalDate()) {
        today -> "Bugun"
        today.plusDays(1) -> "Ertaga"
        else -> "${at.dayOfMonth}-${MONTHS[at.monthValue - 1]}"
    }
    return "$day, %02d:%02d".format(at.hour, at.minute)
}

/** "⏰ Eslatmalar": everything that will ring, with three levels and repeats. */
@Composable
fun RemindersRoute(onBack: () -> Unit) {
    val vm: RemindersViewModel = viewModel()
    val list by vm.reminders.collectAsState()
    val colors = LocalOnIkkiColors.current
    var editing by remember { mutableStateOf<Note?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val now = LocalDateTime.now()
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader(title = "Eslatmalar", onBack = onBack) }
        item {
            Text(
                text = "Istalgan narsaga eslatma: vaqti kelganda tepada, keyingi ochiladigan ilovadan oldin yoki butun ekranni yopib chiqadi.",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        item { OnIkkiButton(text = "＋ Yangi eslatma", onClick = { adding = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) }
        if (list.isEmpty()) {
            item { Text(text = "Hali eslatma yo'q.", color = colors.text.muted(0.5f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
        }
        items(list, key = { it.id }) { n ->
            val at = n.remindAt ?: return@items
            val past = !at.isAfter(now)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(16.dp))
                    .clickable { editing = n }
                    .alpha(if (past) 0.5f else 1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (n.reminderLevel) {
                        ReminderLevel.TOP -> "🔔"
                        ReminderLevel.BEFORE_APP -> "📲"
                        ReminderLevel.FULL_SCREEN -> "🚨"
                    },
                    fontSize = 22.sp
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = n.title.ifBlank { "Eslatma" }, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOfNotNull(
                            if (past) "o'tdi · ${whenText(at)}" else whenText(at),
                            n.remindRepeat?.takeIf { it != ReminderRepeat.NONE }?.label,
                            n.reminderLevel.label
                        ).joinToString(" · "),
                        color = colors.text.muted(0.6f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
    }
    if (adding || editing != null) {
        ReminderSheet(editing, onDismiss = { adding = false; editing = null }, onSave = { text, at, repeat, level ->
            vm.save(editing, text, at, repeat, level); adding = false; editing = null
        }, onRemove = { editing?.let(vm::remove); editing = null })
    }
}

@Composable
private fun ReminderSheet(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, LocalDateTime, ReminderRepeat, ReminderLevel) -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val initial = remember { note?.remindAt?.takeIf { it.isAfter(LocalDateTime.now()) } ?: defaultReminderTime() }
    var text by rememberSaveable { mutableStateOf(note?.title.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime()) }
    var repeat by rememberSaveable { mutableStateOf(note?.remindRepeat ?: ReminderRepeat.NONE) }
    var level by rememberSaveable { mutableStateOf(note?.reminderLevel ?: ReminderLevel.TOP) }
    var error by remember { mutableStateOf<String?>(null) }
    OnIkkiSheet(title = if (note == null) "Yangi eslatma" else "Eslatma", onDismiss = onDismiss) {
        OutlinedTextField(value = text, onValueChange = { text = it.take(200); error = null }, label = { Text("Nimani eslatay?") }, modifier = Modifier.fillMaxWidth())
        DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { d -> date = d; error = null } })
        TimePickerField(label = "Vaqt", time = time, onTimeChange = { time = it; error = null })
        SheetFieldLabel("Takrorlash")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            ReminderRepeat.entries.forEach { r -> FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label, fontSize = 11.sp) }) }
        }
        SheetFieldLabel("Qanday chiqsin")
        ReminderLevel.entries.forEach { l ->
            val selected = level == l
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colors.accent.copy(alpha = 0.14f) else colors.background)
                    .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.divider), RoundedCornerShape(14.dp))
                    .clickable { level = l }
                    .padding(12.dp)
            ) {
                Text(text = l.label, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                Text(text = l.hint, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        when (level) {
            ReminderLevel.FULL_SCREEN -> UrgentPermissions()
            ReminderLevel.BEFORE_APP -> if (!PermissionChecker.isAccessibilityServiceEnabled(context)) {
                Text(
                    text = "⚠ Buning uchun Ilovalar nazorati xizmati (Maxsus imkoniyatlar) yoqilgan bo'lishi kerak — aks holda oddiy bildirishnoma bo'lib keladi.",
                    color = colors.warmAccent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            ReminderLevel.TOP -> Unit
        }
        SheetErrorText(error)
        OnIkkiButton(
            text = "Saqlash",
            onClick = {
                val at = date.atTime(time).withSecond(0).withNano(0)
                when {
                    text.isBlank() -> error = "Eslatma matnini yozing"
                    !at.isAfter(LocalDateTime.now()) -> error = "Bu vaqt o'tib ketgan — keyingi vaqtni tanlang"
                    else -> onSave(text, at, repeat, level)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        )
        if (note != null) OnIkkiButton(text = "O'chirish", onClick = onRemove, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
    }
}
