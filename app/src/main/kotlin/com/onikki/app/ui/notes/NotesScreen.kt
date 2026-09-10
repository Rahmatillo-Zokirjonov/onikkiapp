package com.onikki.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Note
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.TagChip
import com.onikki.app.ui.components.TagVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz

@Composable
fun NotesRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(app.database.noteDao()))
    val state by viewModel.uiState.collectAsState()
    NotesScreen(
        state = state,
        onSelectTag = viewModel::selectTag,
        onOpenAddSheet = viewModel::openAddSheet,
        onOpenEditSheet = viewModel::openEditSheet,
        onDismissSheet = viewModel::dismissSheet,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteEditingNote
    )
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    onSelectTag: (String?) -> Unit,
    onOpenAddSheet: () -> Unit,
    onOpenEditSheet: (Note) -> Unit,
    onDismissSheet: () -> Unit,
    onSaveNote: (title: String, content: String, tags: List<String>) -> Unit,
    onDeleteNote: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Qaydlar", color = colors.text, style = OnIkkiType.screenTitle)

            if (state.availableTags.isNotEmpty()) {
                TagFilterRow(
                    tags = state.availableTags,
                    selectedTag = state.selectedTag,
                    onSelectTag = onSelectTag
                )
            }

            if (state.visibleNotes.isEmpty()) {
                Text(
                    text = if (state.selectedTag != null) "Bu teg bilan qayd yo'q" else "Hali qayd yo'q",
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.visibleNotes.forEach { note ->
                        NoteCard(note = note, onClick = { onOpenEditSheet(note) })
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 16.dp)
                .size(52.dp)
                .clickable(onClick = onOpenAddSheet)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "+", color = colors.onAccent, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (state.isSheetOpen) {
        NoteSheet(
            note = state.editingNote,
            onDismiss = onDismissSheet,
            onSave = onSaveNote,
            onDelete = onDeleteNote
        )
    }
}

@Composable
private fun TagFilterRow(tags: List<String>, selectedTag: String?, onSelectTag: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterPill(label = "Barchasi", isSelected = selectedTag == null, onClick = { onSelectTag(null) })
        tags.forEach { tag ->
            FilterPill(label = tag, isSelected = tag == selectedTag, onClick = { onSelectTag(tag) })
        }
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (isSelected) colors.accent800 else colors.surface, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) colors.accent100 else colors.text.muted(0.7f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), gap = 6.dp) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = note.title,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatRelativeDateUz(note.createdAt),
                color = colors.text.muted(0.4f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        if (note.content.isNotBlank()) {
            Text(
                text = note.content,
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (note.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                note.tags.forEach { tag -> TagChip(text = tag, variant = TagVariant.OUTLINE) }
            }
        }
    }
}
