package com.onikki.app.ui.notes

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NoteColor
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.ai.ExtractedTask
import com.onikki.app.domain.notes.Checklist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** The note open in the editor; [note] null means a brand-new note ([checklist] picks its kind). */
data class NoteEditorTarget(val note: Note?, val checklist: Boolean = false)

data class TagCount(val tag: String, val count: Int)

enum class NotesView(val label: String) { ACTIVE("Qaydlar"), ARCHIVE("Arxiv"), TRASH("Savat") }

enum class NoteSort(val label: String) { UPDATED("Tahrirlangan"), CREATED("Yaratilgan"), TITLE("Nomi") }

/** Everything the editor saves on close. */
data class NoteDraft(
    val title: String,
    val content: String,
    val tags: List<String>,
    val remindAt: LocalDateTime?,
    val priority: NotePriority,
    val color: NoteColor?,
    val pinned: Boolean,
    val isChecklist: Boolean
)

/** How the editor was left: plain save, or save + archive toggle / move to Savat in one write. */
enum class CloseAction { SAVE, ARCHIVE, TRASH }

/** AI sheets opened from the editor. */
sealed interface NoteAiState {
    data object Loading : NoteAiState
    data class Summary(val text: String) : NoteAiState
    data class Tasks(val tasks: List<ExtractedTask>) : NoteAiState
    data class Failed(val message: String) : NoteAiState
}

data class NotesUiState(
    val allNotes: List<Note> = emptyList(),
    val visibleNotes: List<Note> = emptyList(),
    val isLoaded: Boolean = false,
    val totalCount: Int = 0,
    val archivedCount: Int = 0,
    val trashCount: Int = 0,
    val tags: List<TagCount> = emptyList(),
    val selectedTag: String? = null,
    val selectedColor: NoteColor? = null,
    val usedColors: List<NoteColor> = emptyList(),
    val query: String = "",
    val view: NotesView = NotesView.ACTIVE,
    val sort: NoteSort = NoteSort.UPDATED,
    val grid: Boolean = false,
    val editor: NoteEditorTarget? = null,
    /** Last note sent to Savat, for the "Qaytarish" bar. */
    val undoNote: Note? = null,
    val message: String? = null,
    val ai: NoteAiState? = null,
    val needsApiKey: Boolean = false
)

/** Tags are stored comma-joined (see Converters), so a comma inside a tag would split it on reload. */
fun sanitizeTag(raw: String): String = raw.replace(",", " ").trim().removePrefix("#").trim()

/** What the list shows as a note's heading: its title, or the first line of the body when untitled. */
fun Note.displayTitle(): String {
    val body = if (isChecklist) Checklist.parse(content).firstOrNull()?.text.orEmpty() else content
    return title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }.ifBlank { "Nomsiz qayd" }
}

private fun Note.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        content.contains(q, ignoreCase = true) ||
        tags.any { it.contains(q, ignoreCase = true) }
}

private data class Filters(val tag: String?, val color: NoteColor?, val query: String, val view: NotesView, val sort: NoteSort)
private data class Transient(val editor: NoteEditorTarget?, val undo: Note?, val message: String?, val ai: NoteAiState?, val needsKey: Boolean, val grid: Boolean)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val noteDao = db.noteDao()
    private val ai = AiRepository(application, db)
    private val prefs = application.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    private val filters = MutableStateFlow(
        Filters(null, null, "", NotesView.ACTIVE, runCatching { NoteSort.valueOf(prefs.getString("sort", null)!!) }.getOrDefault(NoteSort.UPDATED))
    )
    private val transient = MutableStateFlow(Transient(null, null, null, null, false, prefs.getBoolean("grid", false)))

    init {
        // Savat keeps notes for 30 days.
        viewModelScope.launch { noteDao.purgeTrash(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TRASH_DAYS)) }
    }

    val uiState: StateFlow<NotesUiState> = combine(noteDao.observeAll(), filters, transient) { notes, f, t ->
        val live = notes.filter { !it.isDeleted }
        val inView = when (f.view) {
            NotesView.ACTIVE -> live.filter { !it.archived }
            NotesView.ARCHIVE -> live.filter { it.archived }
            NotesView.TRASH -> notes.filter { it.isDeleted }
        }
        val tagCounts = inView.flatMap { it.tags }.groupingBy { it }.eachCount()
            .map { (name, count) -> TagCount(name, count) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.lowercase() })
        // A filter that no longer matches anything (last note deleted/retagged) would show an empty list forever.
        val activeTag = f.tag?.takeIf { tag -> tagCounts.any { it.tag == tag } }
        val usedColors = inView.mapNotNull { it.color }.distinct().sortedBy { it.ordinal }
        val activeColor = f.color?.takeIf { it in usedColors }
        val sorted = when (f.sort) {
            NoteSort.UPDATED -> inView.sortedByDescending { it.updatedAt }
            NoteSort.CREATED -> inView.sortedByDescending { it.createdAt }
            NoteSort.TITLE -> inView.sortedBy { it.displayTitle().lowercase() }
        }.let { list -> if (f.view == NotesView.ACTIVE) list.sortedByDescending { it.pinned } else list }
        NotesUiState(
            allNotes = notes,
            isLoaded = true,
            visibleNotes = sorted.filter { (activeTag == null || activeTag in it.tags) && (activeColor == null || it.color == activeColor) && it.matches(f.query) },
            totalCount = live.count { !it.archived },
            archivedCount = live.count { it.archived },
            trashCount = notes.count { it.isDeleted },
            tags = tagCounts,
            selectedTag = activeTag,
            selectedColor = activeColor,
            usedColors = usedColors,
            query = f.query,
            view = f.view,
            sort = f.sort,
            grid = t.grid,
            editor = t.editor,
            undoNote = t.undo,
            message = t.message,
            ai = t.ai,
            needsApiKey = t.needsKey
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    // ------------------------------------------------------------ list

    fun selectTag(tag: String?) = filters.update { it.copy(tag = tag) }
    fun selectColor(color: NoteColor?) = filters.update { it.copy(color = if (it.color == color) null else color) }
    fun setQuery(text: String) = filters.update { it.copy(query = text) }
    fun setView(view: NotesView) = filters.update { it.copy(view = view, tag = null, color = null) }

    fun setSort(sort: NoteSort) {
        prefs.edit().putString("sort", sort.name).apply()
        filters.update { it.copy(sort = sort) }
    }

    fun toggleGrid() {
        val grid = !transient.value.grid
        prefs.edit().putBoolean("grid", grid).apply()
        transient.update { it.copy(grid = grid) }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch { noteDao.setPinned(note.id, !note.pinned) }
    }

    /** Ticks a checklist item straight from the list card. */
    fun toggleItem(note: Note, index: Int) {
        viewModelScope.launch { noteDao.setContent(note.id, Checklist.toggle(note.content, index), System.currentTimeMillis()) }
    }

    fun archive(note: Note, archived: Boolean) {
        viewModelScope.launch {
            noteDao.setArchived(note.id, archived)
            transient.update { it.copy(editor = null, message = if (archived) "Arxivga olindi" else "Arxivdan chiqarildi") }
        }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch {
            noteDao.setDeletedAt(note.id, System.currentTimeMillis())
            transient.update { it.copy(editor = null, undo = note, message = null) }
        }
    }

    fun undoTrash() {
        val note = transient.value.undo ?: return
        viewModelScope.launch {
            noteDao.setDeletedAt(note.id, null)
            transient.update { it.copy(undo = null) }
        }
    }

    fun restore(note: Note) {
        viewModelScope.launch {
            noteDao.setDeletedAt(note.id, null)
            transient.update { it.copy(message = "Qayd tiklandi") }
        }
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch { noteDao.delete(note) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            noteDao.emptyTrash()
            transient.update { it.copy(message = "Savat tozalandi") }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null, undo = null) }

    // ------------------------------------------------------------ editor

    fun openNew(checklist: Boolean = false) = transient.update { it.copy(editor = NoteEditorTarget(null, checklist), undo = null) }

    fun openNote(note: Note) = transient.update { it.copy(editor = NoteEditorTarget(note), undo = null) }

    fun closeEditor() = transient.update { it.copy(editor = null, ai = null) }

    /**
     * Called when leaving the editor. A new note with nothing in it is simply discarded; clearing an
     * existing note's text keeps the saved version rather than silently wiping it.
     */
    fun saveAndClose(existing: Note?, draft: NoteDraft, action: CloseAction = CloseAction.SAVE) {
        val cleanTitle = draft.title.trim()
        val cleanContent = if (draft.isChecklist) Checklist.serialize(Checklist.parse(draft.content)) else draft.content.trim()
        val cleanTags = draft.tags.map(::sanitizeTag).filter { it.isNotBlank() }.distinct()
        val isEmpty = cleanTitle.isBlank() && cleanContent.isBlank()
        val reminder = draft.remindAt?.withSecond(0)?.withNano(0)
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            when {
                // Deleting an unsaved note just drops it; so does closing an empty one.
                isEmpty || (existing == null && action == CloseAction.TRASH) -> Unit
                existing == null -> noteDao.insert(
                    Note(
                        title = cleanTitle,
                        content = cleanContent,
                        tags = cleanTags,
                        createdAt = now,
                        remindAt = reminder,
                        priority = draft.priority,
                        updatedAt = now,
                        pinned = draft.pinned,
                        color = draft.color,
                        isChecklist = draft.isChecklist,
                        archived = action == CloseAction.ARCHIVE
                    )
                )
                else -> {
                    val textChanged = existing.title != cleanTitle || existing.content != cleanContent || existing.isChecklist != draft.isChecklist
                    val updated = existing.copy(
                        title = cleanTitle,
                        content = cleanContent,
                        tags = cleanTags,
                        remindAt = reminder,
                        priority = draft.priority,
                        color = draft.color,
                        pinned = draft.pinned,
                        isChecklist = draft.isChecklist,
                        updatedAt = if (textChanged || existing.tags != cleanTags) now else existing.updatedAt
                    ).let {
                        when (action) {
                            CloseAction.SAVE -> it
                            CloseAction.ARCHIVE -> it.copy(archived = !existing.archived, pinned = false)
                            CloseAction.TRASH -> it.copy(deletedAt = now)
                        }
                    }
                    if (updated != existing) noteDao.update(updated)
                }
            }
            transient.update {
                it.copy(
                    editor = null,
                    ai = null,
                    undo = if (action == CloseAction.TRASH && existing != null) existing else null,
                    message = when {
                        action != CloseAction.ARCHIVE || existing == null && isEmpty -> null
                        existing?.archived == true -> "Arxivdan chiqarildi"
                        else -> "Arxivga olindi"
                    }
                )
            }
        }
    }

    /** Opens a note requested from a reminder (notification / alert screen). */
    fun openFromReminder(noteId: Long, notes: List<Note>) {
        notes.firstOrNull { it.id == noteId && !it.isDeleted }?.let { note -> transient.update { it.copy(editor = NoteEditorTarget(note)) } }
    }

    // ------------------------------------------------------------ to Kunlik reja

    /** Adds [titles] as tasks on [date]; the editor shows how many went in. */
    fun addTasks(titles: List<String>, date: LocalDate) {
        val clean = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        viewModelScope.launch {
            clean.forEach { db.taskDao().insert(Task(title = it.take(120), date = date, time = null, category = TaskCategory.SHAXSIY)) }
            transient.update { it.copy(message = "${clean.size} ta vazifa Kunlik rejaga qo'shildi", ai = null) }
        }
    }

    fun addExtracted(tasks: List<ExtractedTask>, fallbackDate: LocalDate) {
        if (tasks.isEmpty()) return
        viewModelScope.launch {
            tasks.forEach { db.taskDao().insert(Task(title = it.title, date = it.date ?: fallbackDate, time = it.time, category = TaskCategory.SHAXSIY)) }
            transient.update { it.copy(message = "${tasks.size} ta vazifa Kunlik rejaga qo'shildi", ai = null) }
        }
    }

    // ------------------------------------------------------------ AI

    fun summarize(title: String, content: String) = runAi { ai.summarizeNote(title, content).map(NoteAiState::Summary) }

    fun extractTasks(title: String, content: String) = runAi { ai.extractTasks(title, content).map(NoteAiState::Tasks) }

    private var lastAi: (suspend () -> AiOutcome<NoteAiState>)? = null

    private fun runAi(block: suspend () -> AiOutcome<NoteAiState>) {
        lastAi = block
        transient.update { it.copy(ai = NoteAiState.Loading) }
        viewModelScope.launch {
            val next = when (val r = block()) {
                is AiOutcome.Ok -> r.value
                is AiOutcome.Failed -> NoteAiState.Failed(r.message)
                AiOutcome.Offline -> NoteAiState.Failed("Internet yo'q")
                AiOutcome.NoKey -> null.also { transient.update { it.copy(needsKey = true) } }
            }
            transient.update { it.copy(ai = next) }
        }
    }

    fun dismissAi() = transient.update { it.copy(ai = null) }

    fun dismissApiKey() = transient.update { it.copy(needsKey = false) }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            ai.saveKey(key)
            transient.update { it.copy(needsKey = false) }
            lastAi?.let(::runAi)
        }
    }

    private companion object {
        const val TRASH_DAYS = 30L
    }
}

private inline fun <T, R> AiOutcome<T>.map(f: (T) -> R): AiOutcome<R> = when (this) {
    is AiOutcome.Ok -> AiOutcome.Ok(f(value))
    is AiOutcome.Failed -> this
    AiOutcome.NoKey -> AiOutcome.NoKey
    AiOutcome.Offline -> AiOutcome.Offline
}
