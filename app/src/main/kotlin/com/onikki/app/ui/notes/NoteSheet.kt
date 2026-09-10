package com.onikki.app.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Note
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, tags: List<String>) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var title by rememberSaveable(note?.id) { mutableStateOf(note?.title ?: "") }
    var content by rememberSaveable(note?.id) { mutableStateOf(note?.content ?: "") }
    var tagsText by rememberSaveable(note?.id) { mutableStateOf(note?.tags?.joinToString(", ") ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (note == null) "Yangi qayd" else "Qaydni tahrirlash",
                color = colors.text,
                fontFamily = OnIkkiFontFamily,
                fontSize = 18.sp
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Sarlavha") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Matn") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Teglar (vergul bilan ajrating)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            Row(modifier = Modifier.padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note != null) {
                    OnIkkiButton(
                        text = "O'chirish",
                        onClick = onDelete,
                        variant = OnIkkiButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
                OnIkkiButton(
                    text = "Saqlash",
                    onClick = {
                        val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        onSave(title, content, tags)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
