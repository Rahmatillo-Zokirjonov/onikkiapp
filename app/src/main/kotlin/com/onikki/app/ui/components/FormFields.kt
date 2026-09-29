package com.onikki.app.ui.components

import java.time.LocalTime
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimeInput
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.ThousandsSeparatorTransformation
import com.onikki.app.ui.util.formatFullDateUz
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** Title row for screens reached from inside a tab (not a tab root): back arrow + title + optional action. */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "←",
            color = colors.text.muted(0.6f),
            fontSize = 18.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clickable(onClick = onBack).padding(4.dp)
        )
        Text(text = title, color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Digits-only so'm amount input, displayed as "1 250 000". [value] stays plain digits. */
@Composable
fun AmountField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.length <= 13 && new.all { it.isDigit() }) onValueChange(new) },
        label = { Text(label) },
        suffix = { Text("so'm") },
        singleLine = true,
        visualTransformation = ThousandsSeparatorTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

/** Horizontally scrolling quick-pick chips; tapping one fills the related field. */
@Composable
fun SuggestionChips(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (options.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.equals(selected.trim(), ignoreCase = true),
                onClick = { onSelect(option) },
                label = { Text(option) }
            )
        }
    }
}

/** Tappable date row opening a Material date picker. With [allowClear], the date is optional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false
) {
    val colors = LocalOnIkkiColors.current
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.medium)
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = date?.let { formatFullDateUz(it) } ?: "Belgilanmagan",
                color = if (date == null) colors.text.muted(0.45f) else colors.text,
                fontSize = 15.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (allowClear && date != null) {
            Text(
                text = "Tozalash",
                color = colors.accent,
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable { onDateChange(null) }.padding(4.dp)
            )
        }
    }

    if (showPicker) {
        // Force Uzbek month names and a Monday week start regardless of the phone's system language.
        ProvideUzbekLocale {
            UzbekDatePickerDialog(
                date = date,
                onDismiss = { showPicker = false },
                onConfirm = { picked ->
                    onDateChange(picked)
                    showPicker = false
                }
            )
        }
    }
}

/**
 * Tappable time row opening a 24h time dialog. Used instead of an inline TimeInput inside sheets:
 * TimeInput grabs focus on appear, pops the keyboard and scrolls the sheet away from its top fields.
 */
@Composable
fun TimePickerField(label: String, time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    var showPicker by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.medium)
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text = label, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "%02d:%02d".format(time.hour, time.minute),
            color = colors.text,
            fontSize = 15.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    if (showPicker) {
        TimeInputDialog(
            initial = time,
            onDismiss = { showPicker = false },
            onConfirm = { picked -> onTimeChange(picked); showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Tayyor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor") } },
        // A dialog re-provides the system configuration, so the Uzbek locale is applied inside it.
        text = { ProvideUzbekLocale { TimeInput(state = state) } }
    )
}

private val UZBEK_LOCALE = Locale("uz", "UZ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UzbekDatePickerDialog(date: LocalDate?, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // Material's DatePicker speaks UTC-midnight millis; convert at the boundary so the day never shifts.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                else onDismiss()
            }) { Text("Tanlash") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor") } }
    ) {
        // A Dialog is its own window and re-provides the system configuration, so the Uzbek
        // locale has to be applied again inside it, not just around the dialog call.
        ProvideUzbekLocale {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                title = {
                    Text(
                        text = "Sanani tanlang",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}

/**
 * Renders [content] as if the phone were set to Uzbek: Material pickers read their locale from
 * [LocalConfiguration] and their built-in labels ("Hour", "Select date"...) from [LocalContext]'s
 * resources, so both are swapped. Needed because the app is Uzbek-only regardless of system language.
 */
@Composable
fun ProvideUzbekLocale(content: @Composable () -> Unit) {
    val baseConfig = LocalConfiguration.current
    val baseContext = LocalContext.current
    val uzConfig = remember(baseConfig) { Configuration(baseConfig).apply { setLocale(UZBEK_LOCALE) } }
    val uzContext = remember(baseContext, uzConfig) { UzbekContext(baseContext, uzConfig) }
    CompositionLocalProvider(LocalConfiguration provides uzConfig, LocalContext provides uzContext, content = content)
}

/** Keeps the original context (activity, theme) but answers resource lookups in Uzbek. */
private class UzbekContext(base: Context, config: Configuration) : ContextWrapper(base) {
    private val uzResources: Resources = base.createConfigurationContext(config).resources
    override fun getResources(): Resources = uzResources
}
