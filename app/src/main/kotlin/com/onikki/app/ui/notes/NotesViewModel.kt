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

/** The note open in the editor; [note] null means a brand-new note. */
data class NoteEditorTarget(val note: Note?)

data class TagCount(val tag: String, val count: Int)

data class NotesUiState(
    val visibleNotes: List<Note> = emptyList(),
    val totalCount: Int = 0,
    val tags: List<TagCount> = emptyList(),
    val selectedTag: String? = null,
    val query: String = "",
    val editor: NoteEditorTarget? = null
)

/** Tags are stored comma-joined (see Converters), so a comma inside a tag would split it on reload. */
fun sanitizeTag(raw: String): String = raw.replace(",", " ").trim().removePrefix("#").trim()

/** What the list shows as a note's heading: its title, or the first line of the body when untitled. */
fun Note.displayTitle(): String =
    title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }.ifBlank { "Nomsiz qayd" }

private fun Note.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        content.contains(q, ignoreCase = true) ||
        tags.any { it.contains(q, ignoreCase = true) }
}

class NotesViewModel(private val noteDao: NoteDao) : ViewModel() {

    private val selectedTag = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val editor = MutableStateFlow<NoteEditorTarget?>(null)

    val uiState: StateFlow<NotesUiState> = combine(
        noteDao.observeAll(),
        selectedTag,
        query,
        editor
    ) { notes, tag, q, openEditor ->
        val tagCounts = notes.flatMap { it.tags }.groupingBy { it }.eachCount()
            .map { (name, count) -> TagCount(name, count) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.lowercase() })
        // A filter tag that no longer exists (its last note was deleted/retagged) would show an empty list forever.
        val activeTag = tag?.takeIf { t -> tagCounts.any { it.tag == t } }
        NotesUiState(
            visibleNotes = notes.filter { (activeTag == null || activeTag in it.tags) && it.matches(q) },
            totalCount = notes.size,
            tags = tagCounts,
            selectedTag = activeTag,
            query = q,
            editor = openEditor
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun openNew() {
        editor.value = NoteEditorTarget(null)
    }

    fun openNote(note: Note) {
        editor.value = NoteEditorTarget(note)
    }

    fun closeEditor() {
        editor.value = null
    }

    /**
     * Called when leaving the editor. A new note with nothing in it is simply discarded; clearing an
     * existing note's text keeps the saved version rather than silently wiping it.
     */
    fun saveAndClose(existing: Note?, title: String, content: String, tags: List<String>) {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        val cleanTags = tags.map(::sanitizeTag).filter { it.isNotBlank() }.distinct()
        val isEmpty = cleanTitle.isBlank() && cleanContent.isBlank()
        viewModelScope.launch {
            when {
                isEmpty -> Unit
                existing == null -> noteDao.insert(
                    Note(title = cleanTitle, content = cleanContent, tags = cleanTags, createdAt = System.currentTimeMillis())
                )
                existing.title != cleanTitle || existing.content != cleanContent || existing.tags != cleanTags ->
                    noteDao.update(existing.copy(title = cleanTitle, content = cleanContent, tags = cleanTags))
            }
            editor.value = null
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch {
            noteDao.delete(note)
            editor.value = null
        }
    }

    companion object {
        fun factory(noteDao: NoteDao) = viewModelFactory {
            initializer { NotesViewModel(noteDao) }
        }
    }
}
