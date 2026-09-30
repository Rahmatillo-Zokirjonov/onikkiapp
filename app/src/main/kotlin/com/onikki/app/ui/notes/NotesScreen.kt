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
import com.onikki.app.data.db.entity.AttachmentKind
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NoteAttachment
import com.onikki.app.data.db.entity.NoteFolder
import com.onikki.app.domain.notes.NoteFormat
import com.onikki.app.domain.notes.NoteTemplate
import com.onikki.app.domain.notes.NoteTemplates
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiSheet
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
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
    val unlock = rememberNoteUnlocker()
    val open: (Note) -> Unit = { note ->
        if (note.locked) unlock("\"${note.title.ifBlank { "Maxfiy qayd" }}\" ni ochish") { viewModel.openNote(note) }
        else viewModel.openNote(note)
    }

    // A reminder (notification / alert screen) asked to open a specific note.
    val requestedNote by NoteDeepLink.requested.collectAsState()
    LaunchedEffect(requestedNote, state.isLoaded) {
        val id = requestedNote ?: return@LaunchedEffect
        if (!state.isLoaded) return@LaunchedEffect
        NoteDeepLink.consume()
        val note = state.allNotes.firstOrNull { it.id == id && !it.isDeleted } ?: return@LaunchedEffect
        if (note.locked) unlock("Eslatma: maxfiy qayd") { viewModel.openFromReminder(note) } else viewModel.openFromReminder(note)
    }

    val editor = state.editor
    when {
        editor != null -> NoteEditorScreen(target = editor, state = state, viewModel = viewModel)
        state.journalOpen -> JournalScreen(state = state, viewModel = viewModel, onOpen = open)
        else -> NotesScreen(state = state, viewModel = viewModel, onOpenNote = open)
    }
}

@Composable
fun NotesScreen(state: NotesUiState, viewModel: NotesViewModel, onOpenNote: (Note) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var templatesOpen by remember { mutableStateOf(false) }
    var folderSheet by remember { mutableStateOf<FolderSheetTarget?>(null) }
    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderRow(state, viewModel)
            ViewSwitcher(state, onSelect = viewModel::setView)
            if (state.view != NotesView.TRASH) {
                FolderRow(
                    state = state,
                    onSelect = viewModel::selectFolder,
                    onEdit = { folderSheet = FolderSheetTarget(it) },
                    onAdd = { folderSheet = FolderSheetTarget(null) }
                )
            }
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
                    attachments = state.attachments[note.id].orEmpty(),
                    folderLabel = if (state.selectedFolder == null) state.folderName(note.folderId) else null,
                    goalLabel = state.goalLabel(note.goalId),
                    onClick = { if (state.view != NotesView.TRASH) onOpenNote(note) },
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
                        .clickable { templatesOpen = true },
                    contentAlignment = Alignment.Center
                ) { Text(text = "▤", color = colors.accent, fontSize = 18.sp) }
                AddFab(onClick = { viewModel.openNew() })
            }
        }

        BottomBar(state, viewModel, Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 90.dp, bottom = 22.dp))
    }

    if (templatesOpen) {
        TemplateSheet(
            onDismiss = { templatesOpen = false },
            onPick = { template, checklist ->
                templatesOpen = false
                viewModel.openNew(checklist = checklist, template = template)
            }
        )
    }
    folderSheet?.let { target ->
        FolderSheet(
            folder = target.folder,
            onDismiss = { folderSheet = null },
            onSave = { name, icon ->
                if (target.folder == null) viewModel.addFolder(name, icon) else viewModel.updateFolder(target.folder.copy(name = name, icon = icon))
                folderSheet = null
            },
            onDelete = target.folder?.let { f -> { viewModel.deleteFolder(f); folderSheet = null } }
        )
    }
}

@Composable
private fun HeaderRow(state: NotesUiState, viewModel: NotesViewModel) {
    val colors = LocalOnIkkiColors.current
    var sortOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Qaydlar", color = colors.text, style = OnIkkiType.screenTitle, modifier = Modifier.weight(1f))
        Text(
            text = "📔 Kundalik",
            color = colors.accent,
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.accent800.copy(alpha = 0.4f))
                .clickable(onClick = viewModel::openJournal)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
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
    attachments: List<NoteAttachment>,
    folderLabel: String?,
    goalLabel: String?,
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
        val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
        val audio = attachments.count { it.kind == AttachmentKind.AUDIO }
        if (note.locked) {
            Text(text = "🔒 Maxfiy — ochish uchun barmoq izi yoki PIN", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        } else if (images.isNotEmpty()) {
            Box {
                NoteImage(images.first().fileName, Modifier.fillMaxWidth().height(if (compact) 110.dp else 140.dp).clip(RoundedCornerShape(10.dp)))
                if (images.size > 1) {
                    Text(
                        text = "+${images.size - 1}",
                        color = colors.text,
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(colors.background.copy(alpha = 0.7f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (note.locked) {
            // nothing more: a locked note shows only its title
        } else if (note.isChecklist) {
            ChecklistPreview(note, limit = if (compact) 4 else 5, enabled = !trash, onToggle = onToggleItem)
        } else {
            // When the note has no title, the heading already is the body's first line — don't repeat it below.
            val plain = NoteFormat.plain(note.content).trim()
            val preview = if (note.title.isBlank()) plain.substringAfter('\n', "").trim() else plain
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
        val meta = listOfNotNull(
            if (audio > 0 && !note.locked) "🎙 $audio" else null,
            folderLabel,
            goalLabel?.let { "🎯 $it" }
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString("  ·  "),
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

// ---------------------------------------------------------------- folders & templates

private data class FolderSheetTarget(val folder: NoteFolder?)

private val FOLDER_ICONS = listOf("📁", "💼", "🏠", "📚", "💡", "❤️", "💰", "✈️", "🕌", "🎓")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(state: NotesUiState, onSelect: (Long?) -> Unit, onEdit: (NoteFolder) -> Unit, onAdd: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.folders.isNotEmpty()) {
            FilterPill(label = "Barcha papkalar", isSelected = state.selectedFolder == null, onClick = { onSelect(null) })
        }
        state.folders.forEach { (folder, count) ->
            val selected = state.selectedFolder == folder.id
            Box(
                modifier = Modifier
                    .background(if (selected) colors.accent800 else colors.surface, RoundedCornerShape(50))
                    .combinedClickable(onClick = { onSelect(folder.id) }, onLongClick = { onEdit(folder) })
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${folder.icon} ${folder.name} $count",
                    color = if (selected) colors.accent100 else colors.text.muted(0.7f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        Text(
            text = "+ Papka",
            color = colors.accent,
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAdd).padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun FolderSheet(folder: NoteFolder?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: (() -> Unit)?) {
    val colors = LocalOnIkkiColors.current
    var name by remember { mutableStateOf(folder?.name ?: "") }
    var icon by remember { mutableStateOf(folder?.icon ?: FOLDER_ICONS.first()) }
    OnIkkiSheet(title = if (folder == null) "Yangi papka" else "Papka", onDismiss = onDismiss) {
        OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("Nomi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FOLDER_ICONS.forEach { candidate ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (candidate == icon) colors.accent800 else colors.background, CircleShape)
                        .border(BorderStroke(1.dp, if (candidate == icon) colors.accent else colors.divider), CircleShape)
                        .clickable { icon = candidate },
                    contentAlignment = Alignment.Center
                ) { Text(text = candidate, fontSize = 17.sp) }
            }
        }
        OnIkkiButton(text = "Saqlash", onClick = { if (name.isNotBlank()) onSave(name, icon) }, modifier = Modifier.fillMaxWidth())
        if (onDelete != null) {
            Text(
                text = "Papkani o'chirish (qaydlar qoladi)",
                color = colors.warmAccent,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onDelete).padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun TemplateSheet(onDismiss: () -> Unit, onPick: (NoteTemplate?, Boolean) -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiSheet(title = "Yangi qayd", onDismiss = onDismiss) {
        TemplateRow("📝", "Bo'sh qayd", "Oddiy matn") { onPick(null, false) }
        TemplateRow("☑", "Ro'yxat", "Belgilanadigan bandlar") { onPick(null, true) }
        Text(text = "SHABLONLAR", color = colors.text.muted(0.45f), style = OnIkkiType.kicker, modifier = Modifier.padding(top = 6.dp))
        NoteTemplates.all.forEach { t ->
            TemplateRow(t.icon, t.name, if (t.checklist) "Ro'yxat" else NoteFormat.plain(t.content).lineSequence().filter { it.isNotBlank() }.take(2).joinToString(" · ")) {
                onPick(t, t.checklist)
            }
        }
    }
}

@Composable
private fun TemplateRow(icon: String, name: String, hint: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(text = icon, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            Text(text = hint, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
