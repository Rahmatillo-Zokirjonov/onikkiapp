package com.onikki.app.ui.dailyplan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSheet(
    onDismiss: () -> Unit,
    onSave: (title: String, time: LocalTime, category: TaskCategory) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(TaskCategory.SHAXSIY) }
    val now = LocalTime.now()
    val timeState = rememberTimePickerState(initialHour = now.hour, initialMinute = now.minute, is24Hour = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Yangi vazifa",
                color = colors.text,
                fontFamily = OnIkkiFontFamily,
                fontSize = 18.sp
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nomi") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            Row(modifier = Modifier.padding(top = 14.dp)) {
                FilterChip(
                    selected = category == TaskCategory.SHAXSIY,
                    onClick = { category = TaskCategory.SHAXSIY },
                    label = { Text("Shaxsiy") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = category == TaskCategory.ISH,
                    onClick = { category = TaskCategory.ISH },
                    label = { Text("Ish") }
                )
            }
            TimeInput(state = timeState, modifier = Modifier.padding(top = 14.dp))
            OnIkkiButton(
                text = "Saqlash",
                onClick = {
                    onSave(title, LocalTime.of(timeState.hour, timeState.minute), category)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)
            )
        }
    }
}
