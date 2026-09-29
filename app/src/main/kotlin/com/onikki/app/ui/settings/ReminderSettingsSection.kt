package com.onikki.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.local.LEAD_MINUTE_OPTIONS
import com.onikki.app.data.local.NotificationSettings
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.PermissionStatusCard
import com.onikki.app.ui.components.ProvideUzbekLocale
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import java.time.LocalTime

/** Sozlamalar > Eslatmalar: which local reminders fire and when (TZ 3.3). */
@Composable
fun ReminderSettingsSection(
    settings: NotificationSettings,
    canScheduleExact: Boolean,
    onUpdate: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    var editingTime by remember { mutableStateOf<TimeTarget?>(null) }

    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 14.dp) {
        ReminderToggle(
            title = "Vazifalar",
            subtitle = "Vazifa vaqtidan oldin",
            checked = settings.tasksEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(tasksEnabled = on) } }
        ) {
            LeadChips(selected = settings.taskLeadMinutes) { m -> onUpdate { it.copy(taskLeadMinutes = m) } }
        }
        ReminderToggle(
            title = "Namoz vaqtlari",
            subtitle = "Har namozdan oldin, tanlangan shahar bo'yicha",
            checked = settings.prayerEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(prayerEnabled = on) } }
        ) {
            LeadChips(selected = settings.prayerLeadMinutes) { m -> onUpdate { it.copy(prayerLeadMinutes = m) } }
        }
        ReminderToggle(
            title = "Odatlar",
            subtitle = "Faqat bugun belgilanmagan odat qolsa",
            checked = settings.habitsEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(habitsEnabled = on) } }
        ) {
            TimeValue(time = settings.habitsTime) { editingTime = TimeTarget.HABITS }
        }
        ReminderToggle(
            title = "Kun yakuni",
            subtitle = "Faqat kun hali yakunlanmagan bo'lsa",
            checked = settings.dayReviewEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(dayReviewEnabled = on) } }
        ) {
            TimeValue(time = settings.dayReviewTime) { editingTime = TimeTarget.DAY_REVIEW }
        }
        ReminderToggle(
            title = "Qarz muddati",
            subtitle = "Qaytarish kuni ertalab 09:00 da",
            checked = settings.debtsEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(debtsEnabled = on) } }
        )
    }

    if (!canScheduleExact) {
        PermissionStatusCard(
            title = "Aniq vaqtda eslatish",
            description = "Ruxsat bo'lmasa ham eslatmalar keladi, lekin bir necha daqiqa kechikishi mumkin.",
            isGranted = false,
            actionLabel = "Ruxsat berish",
            onAction = onOpenExactAlarmSettings
        )
    }

    editingTime?.let { target ->
        val initial = if (target == TimeTarget.HABITS) settings.habitsTime else settings.dayReviewTime
        ReminderTimeDialog(
            initial = initial,
            onDismiss = { editingTime = null },
            onConfirm = { time ->
                onUpdate { if (target == TimeTarget.HABITS) it.copy(habitsTime = time) else it.copy(dayReviewTime = time) }
                editingTime = null
            }
        )
    }
}

private enum class TimeTarget { HABITS, DAY_REVIEW }

@Composable
private fun ReminderToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: (@Composable () -> Unit)? = null
) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                Text(
                    text = subtitle,
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (checked && detail != null) detail()
    }
}

@Composable
private fun LeadChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LEAD_MINUTE_OPTIONS.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(if (minutes == 0) "O'z vaqtida" else "$minutes daq oldin") }
            )
        }
    }
}

@Composable
private fun TimeValue(time: LocalTime, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = "Soat %02d:%02d · o'zgartirish".format(time.hour, time.minute),
        color = colors.accent,
        fontSize = 13.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eslatma vaqti") },
        text = { ProvideUzbekLocale { TimeInput(state = state) } },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor") } }
    )
}
