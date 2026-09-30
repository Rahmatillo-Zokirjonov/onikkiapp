package com.onikki.app.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NoteColor
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.domain.notes.Checklist
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatReminderUz
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun NotesRoute() {
    val viewModel: NotesViewModel = viewModel()
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
            target = editor,
            state = state,
            viewModel = viewModel
        )
    } else {
        NotesScreen(state = state, viewModel = viewModel)
    }
}

@Composable
fun NotesScreen(state: NotesUiState, viewModel: NotesViewModel) {
    val colors = LocalOnIkkiColors.current
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderRow(state, viewModel)
            ViewSwitcher(state, onSelect = viewModel::setView)
            if (state.view == NotesView.TRASH && state.trashCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Savatdagi qaydlar 30 kundan keyin butunlay o'chadi",
                        color = colors.text.muted(0.5f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Tozalash",
                        color = colors.warmAccent,
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.clickable(onClick = viewModel::emptyTrash).padding(6.dp)
                    )
                }
            }
            if (state.allNotes.isNotEmpty()) SearchField(query = state.query, onQueryChange = viewModel::setQuery)
            if (state.tags.isNotEmpty() || state.usedColors.isNotEmpty()) {
                FilterRow(state, onSelectTag = viewModel::selectTag, onSelectColor = viewModel::selectColor)
            }
            if (state.visibleNotes.isEmpty()) {
                Text(
                    text = when {
                        state.query.isNotBlank() -> "\"${state.query.trim()}\" bo'yicha hech narsa topilmadi"
                        state.selectedTag != null || state.selectedColor != null -> "Bu filtr bo'yicha qayd yo'q"
                        state.view == NotesView.ARCHIVE -> "Arxiv bo'sh. Kerak bo'lmagan, lekin o'chirmoqchi bo'lmagan qaydni arxivga oling."
                        state.view == NotesView.TRASH -> "Savat bo'sh"
                        else -> "Hali qayd yo'q. Fikr, ro'yxat yoki eslatmani + bilan yozib qo'ying."
                    },
                    color = colors.text.muted(0.5f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(if (state.grid) 2 else 1),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 140.dp),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { header() }
            items(state.visibleNotes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    compact = state.grid,
                    selectedTag = state.selectedTag,
                    trash = state.view == NotesView.TRASH,
                    onClick = { if (state.view != NotesView.TRASH) viewModel.openNote(note) },
                    onTogglePin = { viewModel.togglePin(note) },
                    onToggleItem = { viewModel.toggleItem(note, it) },
                    onTagClick = viewModel::selectTag,
                    onRestore = { viewModel.restore(note) },
                    onDeleteForever = { viewModel.deleteForever(note) }
                )
            }
        }

        if (state.view == NotesView.ACTIVE) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.surface, CircleShape)
                        .border(BorderStroke(1.dp, colors.accent700), CircleShape)
                        .clickable { viewModel.openNew(checklist = true) },
                    contentAlignment = Alignment.Center
                ) { Text(text = "☑", color = colors.accent, fontSize = 18.sp) }
                AddFab(onClick = { viewModel.openNew() })
            }
        }

        BottomBar(state, viewModel, Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 90.dp, bottom = 22.dp))
    }
}

@Composable
private fun HeaderRow(state: NotesUiState, viewModel: NotesViewModel) {
    val colors = LocalOnIkkiColors.current
    var sortOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Qaydlar", color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.weight(1f))
        Text(
            text = if (state.grid) "☰" else "▦",
            color = colors.text.muted(0.7f),
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = viewModel::toggleGrid).padding(8.dp)
        )
        Box {
            Text(
                text = "⇅ ${state.sort.label}",
                color = colors.text.muted(0.7f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable { sortOpen = true }.padding(8.dp)
            )
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                NoteSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text((if (sort == state.sort) "✓ " else "   ") + sort.label) },
                        onClick = { viewModel.setSort(sort); sortOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewSwitcher(state: NotesUiState, onSelect: (NotesView) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NotesView.entries.forEach { view ->
            val count = when (view) {
                NotesView.ACTIVE -> state.totalCount
                NotesView.ARCHIVE -> state.archivedCount
                NotesView.TRASH -> state.trashCount
            }
            if (view != NotesView.ACTIVE && count == 0 && state.view != view) return@forEach
            FilterPill(label = if (count > 0) "${view.label} $count" else view.label, isSelected = state.view == view, onClick = { onSelect(view) })
        }
    }
    if (state.view == NotesView.ACTIVE && state.totalCount == 0 && state.archivedCount == 0 && state.trashCount == 0) {
        Text(
            text = "Maslahat: ☑ tugmasi — xarid yoki ishlar ro'yxati, 🎤 — ovoz bilan yozish.",
            color = colors.text.muted(0.4f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

/** "Qaytarish" after a delete, or a short status message. */
@Composable
private fun BottomBar(state: NotesUiState, viewModel: NotesViewModel, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    val undo = state.undoNote
    val message = state.message
    if (undo == null && message == null) return
    LaunchedEffect(undo, message) {
        delay(if (undo != null) 6_000 else 2_500)
        viewModel.clearMessage()
    }
    Row(
        modifier = modifier
            .background(colors.neutral800, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (undo != null) "Savatga o'tkazildi" else message.orEmpty(),
            color = colors.text,
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (undo != null) {
            Text(
                text = "Qaytarish",
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = viewModel::undoTrash).padding(start = 14.dp)
            )
        }
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
private fun FilterRow(state: NotesUiState, onSelectTag: (String?) -> Unit, onSelectColor: (NoteColor?) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.usedColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.dot(), CircleShape)
                    .border(BorderStroke(2.dp, if (state.selectedColor == color) colors.text else colors.background), CircleShape)
                    .clickable { onSelectColor(color) }
            )
        }
        if (state.tags.isNotEmpty()) {
            FilterPill(label = "Barchasi", isSelected = state.selectedTag == null, onClick = { onSelectTag(null) })
            state.tags.forEach { (tag, count) ->
                FilterPill(
                    label = "#$tag $count",
                    isSelected = tag == state.selectedTag,
                    onClick = { onSelectTag(if (tag == state.selectedTag) null else tag) }
                )
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    compact: Boolean,
    selectedTag: String?,
    trash: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleItem: (Int) -> Unit,
    onTagClick: (String) -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val heading = note.displayTitle()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(note.color?.tint() ?: colors.surface, shape)
            .border(BorderStroke(1.dp, if (note.pinned) colors.accent700 else note.color?.dot()?.copy(alpha = 0.35f) ?: colors.cardBorder), shape)
            .combinedClickable(onClick = onClick, onLongClick = if (trash) null else onTogglePin)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (note.pinned) Text(text = "📌", fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp, top = 2.dp))
            Text(
                text = heading,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!compact) {
                Text(
                    text = formatRelativeDateUz(note.updatedAt),
                    color = colors.text.muted(0.4f),
                    fontSize = 10.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                )
            }
        }
        note.remindAt?.takeIf { it.isAfter(LocalDateTime.now()) && !trash }?.let { at -> ReminderBadge(at, note.priority) }
        if (note.isChecklist) {
            ChecklistPreview(note, limit = if (compact) 4 else 5, enabled = !trash, onToggle = onToggleItem)
        } else {
            // When the note has no title, the heading already is the body's first line — don't repeat it below.
            val preview = if (note.title.isBlank()) note.content.trim().substringAfter('\n', "").trim() else note.content.trim()
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    color = colors.text.muted(0.65f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = if (compact) 6 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (note.tags.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                note.tags.forEach { tag ->
                    Text(
                        text = "#$tag",
                        color = if (tag == selectedTag) colors.accent100 else colors.accent,
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier
                            .background(if (tag == selectedTag) colors.accent800 else colors.accent800.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .clickable { onTagClick(tag) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (trash) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "↩ Tiklash", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable(onClick = onRestore).padding(vertical = 4.dp))
                Text(text = "Butunlay o'chirish", color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clickable(onClick = onDeleteForever).padding(vertical = 4.dp))
            }
        }
    }
}

/** First items with tappable boxes; finished ones struck through, plus "3/7" progress. */
@Composable
private fun ChecklistPreview(note: Note, limit: Int, enabled: Boolean, onToggle: (Int) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val items = Checklist.parse(note.content)
    if (items.isEmpty()) return
    // Open items first, like the editor, but keep each item's real index for toggling.
    val ordered = items.withIndex().sortedBy { it.value.done }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ordered.take(limit).forEach { (index, item) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle(index) }.padding(vertical = 2.dp)
            ) {
                Text(text = if (item.done) "☑" else "☐", color = if (item.done) colors.accent else colors.text.muted(0.6f), fontSize = 15.sp)
                Text(
                    text = item.text,
                    color = if (item.done) colors.text.muted(0.4f) else colors.text.muted(0.8f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        val done = items.count { it.done }
        Text(
            text = listOfNotNull(
                "$done/${items.size}",
                (items.size - limit).takeIf { it > 0 }?.let { "+$it ta yana" }
            ).joinToString(" · "),
            color = colors.text.muted(0.4f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
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
