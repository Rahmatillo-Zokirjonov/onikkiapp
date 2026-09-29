package com.onikki.app.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatReminderUz
import java.time.LocalDateTime

@Composable
fun NotesRoute() {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(app.database.noteDao()))
    val state by viewModel.uiState.collectAsState()

    // A reminder (notification / alert screen) asked to open a specific note.
    val requestedNote by NoteDeepLink.requested.collectAsState()
    LaunchedEffect(requestedNote, state.isLoaded) {
        val id = requestedNote ?: return@LaunchedEffect
        if (!state.isLoaded) return@LaunchedEffect
        viewModel.openFromReminder(id, state.allNotes)
        NoteDeepLink.consume()
    }

    val editor = state.editor
    if (editor != null) {
        NoteEditorScreen(
            note = editor.note,
            existingTags = state.tags.map { it.tag },
            onClose = { title, content, tags, remindAt, priority ->
                viewModel.saveAndClose(editor.note, title, content, tags, remindAt, priority)
            },
            onDelete = viewModel::delete
        )
    } else {
        NotesScreen(
            state = state,
            onQueryChange = viewModel::setQuery,
            onSelectTag = viewModel::selectTag,
            onNewNote = viewModel::openNew,
            onOpenNote = viewModel::openNote
        )
    }
}

@Composable
fun NotesScreen(
    state: NotesUiState,
    onQueryChange: (String) -> Unit,
    onSelectTag: (String?) -> Unit,
    onNewNote: () -> Unit,
    onOpenNote: (Note) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "Qaydlar", color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.weight(1f))
                    if (state.totalCount > 0) {
                        Text(
                            text = "${state.totalCount} ta",
                            color = colors.text.muted(0.45f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
            if (state.totalCount > 0) {
                item { SearchField(query = state.query, onQueryChange = onQueryChange) }
            }
            if (state.tags.isNotEmpty()) {
                item { TagFilterRow(tags = state.tags, selectedTag = state.selectedTag, onSelectTag = onSelectTag) }
            }
            if (state.visibleNotes.isEmpty()) {
                item {
                    Text(
                        text = when {
                            state.totalCount == 0 -> "Hali qayd yo'q. Fikr, ro'yxat yoki eslatmani + bilan yozib qo'ying."
                            state.query.isNotBlank() -> "\"${state.query.trim()}\" bo'yicha hech narsa topilmadi"
                            else -> "Bu teg bilan qayd yo'q"
                        },
                        color = colors.text.muted(0.5f),
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            items(state.visibleNotes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    selectedTag = state.selectedTag,
                    onClick = { onOpenNote(note) },
                    onTagClick = onSelectTag
                )
            }
        }

        AddFab(onClick = onNewNote, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val style = TextStyle(color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, OnIkkiShapes.medium)
            .border(BorderStroke(1.dp, colors.cardBorder), OnIkkiShapes.medium)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text(text = "Qidirish", style = style.copy(color = colors.text.muted(0.4f)))
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Text(
                text = "×",
                color = colors.text.muted(0.5f),
                fontSize = 18.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable { onQueryChange("") }.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun TagFilterRow(tags: List<TagCount>, selectedTag: String?, onSelectTag: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterPill(label = "Barchasi", isSelected = selectedTag == null, onClick = { onSelectTag(null) })
        tags.forEach { (tag, count) ->
            FilterPill(
                label = "#$tag $count",
                isSelected = tag == selectedTag,
                onClick = { onSelectTag(if (tag == selectedTag) null else tag) }
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .background(if (isSelected) colors.accent800 else colors.surface, RoundedCornerShape(50))
            .clickable(onClick = onClick)
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
private fun NoteCard(note: Note, selectedTag: String?, onClick: () -> Unit, onTagClick: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val heading = note.displayTitle()
    // When the note has no title, the heading already is the body's first line — don't repeat it below.
    val preview = if (note.title.isBlank()) note.content.trim().substringAfter('\n', "").trim() else note.content.trim()
    OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), gap = 6.dp) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = heading,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatRelativeDateUz(note.createdAt),
                color = colors.text.muted(0.4f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(start = 8.dp, top = 3.dp)
            )
        }
        note.remindAt?.takeIf { it.isAfter(LocalDateTime.now()) }?.let { at -> ReminderBadge(at, note.priority) }
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (note.tags.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                note.tags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        color = if (tag == selectedTag) colors.accent100 else colors.accent,
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier
                            .background(
                                if (tag == selectedTag) colors.accent800 else colors.accent800.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderBadge(at: LocalDateTime, priority: NotePriority, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    val urgent = priority == NotePriority.JUDA_MUHIM
    val level = if (priority == NotePriority.ODDIY) "" else " · ${priority.label.lowercase()}"
    Text(
        text = "🔔 ${formatReminderUz(at)}$level",
        color = if (urgent) colors.warmAccent else colors.accent,
        fontSize = 11.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = modifier
            .border(BorderStroke(1.dp, if (urgent) colors.warmBorder else colors.accent700), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
