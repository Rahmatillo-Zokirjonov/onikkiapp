package com.onikki.app.ui.dailyplan

import com.onikki.app.ui.components.SheetFieldLabel
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import com.onikki.app.data.db.entity.Goal
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.ui.components.ChoiceChips
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.ProvideUzbekLocale
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(
    task: Task?,
    defaultDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, time: LocalTime?, category: TaskCategory, goalId: Long?) -> Unit,
    onDelete: () -> Unit,
    goals: List<Goal> = emptyList(),
    presetGoalId: Long? = null
) {
    val colors = LocalOnIkkiColors.current
    val key = task?.id
    var title by rememberSaveable(key) { mutableStateOf(task?.title ?: "") }
    var category by rememberSaveable(key) { mutableStateOf(task?.category ?: TaskCategory.SHAXSIY) }
    var date by rememberSaveable(key) { mutableStateOf(task?.date ?: defaultDate) }
    var goalId by rememberSaveable(key) { mutableStateOf(task?.goalId ?: presetGoalId) }
    // New tasks get a time by default (TZ 3.1 lists it as a field); an existing untimed task stays untimed.
    var hasTime by rememberSaveable(key) { mutableStateOf(task == null || task.time != null) }
    val initialTime = remember(key) { task?.time ?: defaultNewTaskTime() }
    val timeState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (task == null) "Yangi vazifa" else "Vazifani tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text("Nima qilish kerak") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceChips(
            options = listOf(TaskCategory.SHAXSIY to "Shaxsiy", TaskCategory.ISH to "Ish"),
            selected = category,
            onSelect = { category = it }
        )
        DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { picked -> date = picked } })
        if (goals.isNotEmpty()) {
            SheetFieldLabel("Qaysi maqsad uchun")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = goalId == null, onClick = { goalId = null }, label = { Text("Maqsadsiz") })
                goals.forEach { goal ->
                    FilterChip(
                        selected = goalId == goal.id,
                        onClick = { goalId = goal.id },
                        label = { Text("${goal.icon} ${goal.title}") }
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable { hasTime = !hasTime },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Vaqt belgilash",
                color = colors.text,
                fontSize = 14.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = hasTime, onCheckedChange = { hasTime = it })
        }
        if (hasTime) {
            ProvideUzbekLocale { TimeInput(state = timeState) }
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                if (title.isBlank()) {
                    error = "Vazifa nomini yozing"
                } else {
                    onSave(title, date, if (hasTime) LocalTime.of(timeState.hour, timeState.minute) else null, category, goalId)
                }
            },
            onDelete = if (task != null) onDelete else null
        )
    }
}

/** The next full hour — or, after 23:00, the current minute, so the default never wraps into a past 00:00. */
private fun defaultNewTaskTime(): LocalTime {
    val now = LocalTime.now().withSecond(0).withNano(0)
    return if (now.hour >= 23) now else now.plusHours(1).withMinute(0)
}
