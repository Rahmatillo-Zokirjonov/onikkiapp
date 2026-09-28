package com.onikki.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

/** Standard add/edit bottom sheet: title, scrollable form body, fully expanded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnIkkiSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalOnIkkiColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 18.sp)
            content()
        }
    }
}

@Composable
fun SheetFieldLabel(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(text = text, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
}

@Composable
fun SheetErrorText(message: String?) {
    if (message == null) return
    val colors = LocalOnIkkiColors.current
    Text(text = message, color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
}

/** Save + (for existing items) a delete that needs a second tap to confirm. */
@Composable
fun SheetActions(onSave: () -> Unit, onDelete: (() -> Unit)?) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Row(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onDelete != null) {
            OnIkkiButton(
                text = if (confirmingDelete) "Rostdan o'chirish?" else "O'chirish",
                onClick = { if (confirmingDelete) onDelete() else confirmingDelete = true },
                variant = OnIkkiButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
        OnIkkiButton(text = "Saqlash", onClick = onSave, modifier = Modifier.weight(1f))
    }
}

/** Single-choice chip row (Chiqim/Kirim, Ish/Shaxsiy, ...). */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}
