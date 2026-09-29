package com.onikki.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.ALL_DAYS
import com.onikki.app.data.db.entity.Habit
import com.onikki.app.data.db.entity.WEEKDAYS
import com.onikki.app.data.repository.HabitRepository
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.weekdayAbbrUz
import java.time.DayOfWeek

private val ICON_CHOICES = listOf("💧", "📖", "🏃", "🧘", "🕌", "💪", "🥗", "😴", "✍️", "🚭", "●", "★")
/** Quick-pick names, each with a fitting default icon. */
private val NAME_SUGGESTIONS = linkedMapOf(
    "Suv ichish" to "💧", "Kitob o'qish" to "📖", "Sport" to "🏃",
    "Namoz" to "🕌", "Erta turish" to "😴", "Ingliz tili" to "✍️"
)

@Composable
fun HabitSheet(
    habit: Habit?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, dailyTarget: Int, activeDays: Int) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val key = habit?.id
    var name by rememberSaveable(key) { mutableStateOf(habit?.name ?: "") }
    // Older habits may carry an icon that isn't in today's list — keep it selectable.
    val icons = remember(key) { listOfNotNull(habit?.icon?.takeIf { it !in ICON_CHOICES }) + ICON_CHOICES }
    var icon by rememberSaveable(key) { mutableStateOf(habit?.icon ?: ICON_CHOICES.first()) }
    var dailyTarget by rememberSaveable(key) { mutableStateOf(habit?.dailyTarget?.coerceAtLeast(1) ?: 1) }
    var activeDays by rememberSaveable(key) { mutableStateOf(habit?.activeDays ?: ALL_DAYS) }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (habit == null) "Yangi odat" else "Odatni tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Nomi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (habit == null) {
            SuggestionChips(
                options = NAME_SUGGESTIONS.keys.toList(),
                selected = name,
                onSelect = { picked ->
                    name = picked
                    NAME_SUGGESTIONS[picked]?.let { icon = it }
                    error = null
                }
            )
        }

        SheetFieldLabel("Belgi")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icons.forEach { candidate ->
                val selected = candidate == icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (selected) colors.accent800 else colors.background, CircleShape)
                        .border(BorderStroke(1.dp, if (selected) colors.accent else colors.divider), CircleShape)
                        .clickable { icon = candidate },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = candidate, color = if (selected) colors.accent100 else colors.text, fontSize = 16.sp)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TargetLabel(
                title = "Kunlik maqsad",
                subtitle = if (dailyTarget == 1) "bir marta belgilash" else "kuniga $dailyTarget marta",
                modifier = Modifier.weight(1f)
            )
            StepperButton(label = "−", enabled = dailyTarget > 1, onClick = { dailyTarget-- })
            Text(
                text = dailyTarget.toString(),
                color = colors.text,
                fontSize = 15.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            StepperButton(
                label = "+",
                enabled = dailyTarget < HabitRepository.MAX_DAILY_COUNT,
                onClick = { dailyTarget++ }
            )
        }

        SheetFieldLabel("Qaysi kunlari")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            DayOfWeek.entries.forEach { day ->
                val bit = 1 shl (day.value - 1)
                val selected = activeDays and bit != 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) colors.accent800 else colors.background, OnIkkiShapes.small)
                        .border(BorderStroke(1.dp, if (selected) colors.accent else colors.divider), OnIkkiShapes.small)
                        .clickable { activeDays = activeDays xor bit; error = null }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = weekdayAbbrUz(day),
                        color = if (selected) colors.accent100 else colors.text.muted(0.6f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
        SuggestionChips(
            options = listOf("Har kuni", "Ish kunlari", "Dam olish kunlari"),
            selected = when (activeDays) {
                ALL_DAYS -> "Har kuni"
                WEEKDAYS -> "Ish kunlari"
                ALL_DAYS xor WEEKDAYS -> "Dam olish kunlari"
                else -> ""
            },
            onSelect = {
                activeDays = when (it) {
                    "Ish kunlari" -> WEEKDAYS
                    "Dam olish kunlari" -> ALL_DAYS xor WEEKDAYS
                    else -> ALL_DAYS
                }
                error = null
            }
        )

        SheetErrorText(error)
        SheetActions(
            onSave = {
                when {
                    name.isBlank() -> error = "Odat nomini yozing"
                    activeDays == 0 -> error = "Kamida bitta kunni tanlang"
                    else -> onSave(name, icon, dailyTarget, activeDays)
                }
            },
            onDelete = if (habit != null) onDelete else null
        )
    }
}

@Composable
private fun TargetLabel(title: String, subtitle: String, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = modifier) {
        Text(text = title, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        Text(text = subtitle, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.small)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) colors.text else colors.text.muted(0.3f),
            fontSize = 16.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}
