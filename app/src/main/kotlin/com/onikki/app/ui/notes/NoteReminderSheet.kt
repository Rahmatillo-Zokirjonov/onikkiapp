package com.onikki.app.ui.notes

import com.onikki.app.ui.components.TimePickerField
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.muted
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val PRIORITY_HINTS = mapOf(
    NotePriority.ODDIY to "Bildirishnomalar ro'yxatiga ovoz bilan tushadi, ekranga chiqmaydi.",
    NotePriority.MUHIM to "Qaysi ilovada bo'lsangiz ham, tepada banner bo'lib chiqadi.",
    NotePriority.JUDA_MUHIM to "Budilnikdek: butun ekranni egallaydi va \"Ko'rdim\" bosilmaguncha jiringlaydi."
)

/** Sets, changes or removes a note's reminder. [remindAt] null = the note has none yet. */
@Composable
fun NoteReminderSheet(
    remindAt: LocalDateTime?,
    priority: NotePriority,
    onDismiss: () -> Unit,
    onSave: (LocalDateTime, NotePriority) -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val initial = remember { remindAt?.takeIf { it.isAfter(LocalDateTime.now()) } ?: defaultReminderTime() }
    var date by rememberSaveable { mutableStateOf(initial.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime()) }
    var level by rememberSaveable { mutableStateOf(priority) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyPreset(at: LocalDateTime) {
        date = at.toLocalDate()
        time = at.toLocalTime()
        error = null
    }

    val presets = remember { reminderPresets(LocalDateTime.now()) }

    OnIkkiSheet(title = if (remindAt == null) "Eslatma qo'shish" else "Eslatma", onDismiss = onDismiss) {
        SuggestionChips(
            options = presets.map { it.first },
            selected = "",
            onSelect = { label -> presets.firstOrNull { it.first == label }?.let { applyPreset(it.second) } }
        )
        DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { picked -> date = picked; error = null } })
        TimePickerField(label = "Vaqt", time = time, onTimeChange = { time = it; error = null })

        SheetFieldLabel("Darajasi")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NotePriority.entries.forEach { option ->
                val selected = option == level
                Text(
                    text = option.label,
                    color = if (selected) colors.accent100 else colors.text.muted(0.7f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) priorityFill(option) else colors.background, OnIkkiShapes.small)
                        .border(BorderStroke(1.dp, if (selected) colors.accent else colors.divider), OnIkkiShapes.small)
                        .clickable { level = option }
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Text(
            text = PRIORITY_HINTS.getValue(level),
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        if (level == NotePriority.JUDA_MUHIM) UrgentPermissions()

        SheetErrorText(error)
        SheetActions(
            onSave = {
                val at = date.atTime(time)
                if (!at.isAfter(LocalDateTime.now())) error = "Bu vaqt o'tib ketgan — keyingi vaqtni tanlang" else onSave(at, level)
            },
            onDelete = if (remindAt != null) onRemove else null
        )
    }
}

@Composable
private fun priorityFill(priority: NotePriority) = LocalOnIkkiColors.current.let {
    if (priority == NotePriority.JUDA_MUHIM) it.accent700 else it.accent800
}

/**
 * The two extra grants a "Juda muhim" alert needs to truly take over the screen. Both are optional:
 * without them it still rings as a persistent pop-up banner. Rechecked each time the user comes back.
 */
@Composable
private fun UrgentPermissions() {
    val context = LocalContext.current
    var overlay by remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    var fullScreen by remember { mutableStateOf(PermissionChecker.canUseFullScreenIntent(context)) }
    LifecycleResumeEffect(Unit) {
        overlay = PermissionChecker.canDrawOverlays(context)
        fullScreen = PermissionChecker.canUseFullScreenIntent(context)
        onPauseOrDispose { }
    }
    val packageUri = Uri.parse("package:${context.packageName}")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PermissionLine(
            granted = overlay,
            text = "Boshqa ilovalar ustida ochish",
            onGrant = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PermissionLine(
                granted = fullScreen,
                text = "Qulflangan ekranda to'liq ochish",
                onGrant = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)) }
            )
        }
    }
}

@Composable
private fun PermissionLine(granted: Boolean, text: String, onGrant: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.small)
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = (if (granted) "✓ " else "! ") + text,
            color = if (granted) colors.text.muted(0.6f) else colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f)
        )
        if (!granted) Text(text = "Ruxsat berish", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
    }
}

/** Next full hour — tomorrow 09:00 if that would land after 22:00. */
private fun defaultReminderTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
    val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
    return if (nextHour.hour >= 22 || nextHour.toLocalDate() != now.toLocalDate()) {
        now.toLocalDate().plusDays(1).atTime(9, 0)
    } else {
        nextHour
    }
}

private fun reminderPresets(now: LocalDateTime): List<Pair<String, LocalDateTime>> {
    val base = now.withSecond(0).withNano(0)
    val tomorrow: LocalDate = now.toLocalDate().plusDays(1)
    return buildList {
        add("30 daqiqadan keyin" to base.plusMinutes(30))
        add("1 soatdan keyin" to base.plusHours(1))
        val evening = now.toLocalDate().atTime(LocalTime.of(20, 0))
        if (evening.isAfter(now.plusMinutes(30))) add("Bugun 20:00" to evening)
        add("Ertaga 09:00" to tomorrow.atTime(9, 0))
    }
}
