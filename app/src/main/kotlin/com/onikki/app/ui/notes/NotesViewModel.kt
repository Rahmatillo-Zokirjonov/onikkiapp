package com.onikki.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.dao.NoteDao
import com.onikki.app.data.db.entity.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private sealed class SheetState {
    data object Closed : SheetState()
    data object Adding : SheetState()
    data class Editing(val note: Note) : SheetState()
}

data class NotesUiState(
    val visibleNotes: List<Note> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isSheetOpen: Boolean = false,
    val editingNote: Note? = null
)

class NotesViewModel(private val noteDao: NoteDao) : ViewModel() {

    private val selectedTag = MutableStateFlow<String?>(null)
    private val sheetState = MutableStateFlow<SheetState>(SheetState.Closed)

    val uiState: StateFlow<NotesUiState> = combine(
        noteDao.observeAll(),
        selectedTag,
        sheetState
    ) { notes, tag, sheet ->
        NotesUiState(
            visibleNotes = if (tag == null) notes else notes.filter { tag in it.tags },
            availableTags = notes.flatMap { it.tags }.distinct().sorted(),
            selectedTag = tag,
            isSheetOpen = sheet != SheetState.Closed,
            editingNote = (sheet as? SheetState.Editing)?.note
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }

    fun openAddSheet() {
        sheetState.value = SheetState.Adding
    }

    fun openEditSheet(note: Note) {
        sheetState.value = SheetState.Editing(note)
    }

    fun dismissSheet() {
        sheetState.value = SheetState.Closed
    }

    fun saveNote(title: String, content: String, tags: List<String>) {
        if (title.isBlank()) return
        val editing = (sheetState.value as? SheetState.Editing)?.note
        viewModelScope.launch {
            if (editing != null) {
                noteDao.update(editing.copy(title = title.trim(), content = content.trim(), tags = tags))
            } else {
                noteDao.insert(
                    Note(title = title.trim(), content = content.trim(), tags = tags, createdAt = System.currentTimeMillis())
                )
            }
            sheetState.value = SheetState.Closed
        }
    }

    fun deleteEditingNote() {
        val editing = (sheetState.value as? SheetState.Editing)?.note ?: return
        viewModelScope.launch {
            noteDao.delete(editing)
            sheetState.value = SheetState.Closed
        }
    }

    companion object {
        fun factory(noteDao: NoteDao) = viewModelFactory {
            initializer { NotesViewModel(noteDao) }
        }
    }
}
