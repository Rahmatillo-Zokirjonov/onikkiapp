package com.onikki.app.ui.notes

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.AttachmentKind
import com.onikki.app.data.db.entity.Goal
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NoteAttachment
import com.onikki.app.data.db.entity.NoteColor
import com.onikki.app.data.db.entity.NoteFolder
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.data.local.NoteMedia
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.ai.ExtractedTask
import com.onikki.app.domain.notes.Checklist
import com.onikki.app.domain.notes.NoteFormat
import com.onikki.app.domain.notes.NoteTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The note open in the editor; [note] null means a brand-new note, started from [template],
 * as the Kundalik entry for [journalDate], or inside [folderId].
 */
data class NoteEditorTarget(
    val note: Note?,
    val checklist: Boolean = false,
    val template: NoteTemplate? = null,
    val journalDate: LocalDate? = null,
    val folderId: Long? = null
)

data class TagCount(val tag: String, val count: Int)

data class FolderCount(val folder: NoteFolder, val count: Int)

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
    val isChecklist: Boolean,
    val folderId: Long? = null,
    val locked: Boolean = false,
    val journalDate: LocalDate? = null,
    val goalId: Long? = null,
    val taskId: Long? = null
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
    val folders: List<FolderCount> = emptyList(),
    val selectedFolder: Long? = null,
    val query: String = "",
    val view: NotesView = NotesView.ACTIVE,
    val sort: NoteSort = NoteSort.UPDATED,
    val grid: Boolean = false,
    val editor: NoteEditorTarget? = null,
    /** Kundalik (journal) screen open. */
    val journalOpen: Boolean = false,
    val journalEntries: List<Note> = emptyList(),
    val attachments: Map<Long, List<NoteAttachment>> = emptyMap(),
    /** All goals (labels + the link picker shows the unfinished ones). */
    val goals: List<Goal> = emptyList(),
    /** Open tasks around today, for the link picker. */
    val tasks: List<Task> = emptyList(),
    val taskTitles: Map<Long, String> = emptyMap(),
    /** Last note sent to Savat, for the "Qaytarish" bar. */
    val undoNote: Note? = null,
    val message: String? = null,
    val ai: NoteAiState? = null,
    val needsApiKey: Boolean = false
) {
    fun folderName(id: Long?): String? = folders.firstOrNull { it.folder.id == id }?.folder?.let { "${it.icon} ${it.name}" }
    fun goalLabel(id: Long?): String? = goals.firstOrNull { it.id == id }?.let { g ->
        val parent = g.parentId?.let { pid -> goals.firstOrNull { it.id == pid } }
        if (parent != null) "${parent.icon} ${parent.title} → ${g.title}" else "${g.icon} ${g.title}"
    }
}

/** Tags are stored comma-joined (see Converters), so a comma inside a tag would split it on reload. */
fun sanitizeTag(raw: String): String = raw.replace(",", " ").trim().removePrefix("#").trim()

/** What the list shows as a note's heading: its title, or the first line of the body when untitled. */
fun Note.displayTitle(): String {
    val body = if (isChecklist) Checklist.parse(content).firstOrNull()?.text.orEmpty() else NoteFormat.plain(content)
    return title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }.ifBlank { "Nomsiz qayd" }
}

private fun Note.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    // A locked note gives away only its title.
    return title.contains(q, ignoreCase = true) ||
        (!locked && (content.contains(q, ignoreCase = true) || tags.any { it.contains(q, ignoreCase = true) }))
}

private data class Filters(
    val tag: String?,
    val color: NoteColor?,
    val folder: Long?,
    val query: String,
    val view: NotesView,
    val sort: NoteSort
)

private data class Transient(
    val editor: NoteEditorTarget?,
    val journal: Boolean,
    val undo: Note?,
    val message: String?,
    val ai: NoteAiState?,
    val needsKey: Boolean,
    val grid: Boolean
)

private data class Links(val goals: List<Goal>, val tasks: List<Task>, val taskTitles: Map<Long, String>)
private data class Library(val notes: List<Note>, val folders: List<NoteFolder>, val attachments: Map<Long, List<NoteAttachment>>, val links: Links)

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val noteDao = db.noteDao()
    private val folderDao = db.noteFolderDao()
    private val attachmentDao = db.noteAttachmentDao()
    private val ai = AiRepository(application, db)
    private val prefs = application.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val today = LocalDate.now()

    private val filters = MutableStateFlow(
        Filters(null, null, null, "", NotesView.ACTIVE, runCatching { NoteSort.valueOf(prefs.getString("sort", null)!!) }.getOrDefault(NoteSort.UPDATED))
    )
    private val transient = MutableStateFlow(Transient(null, false, null, null, null, false, prefs.getBoolean("grid", false)))

    init {
        // Savat keeps notes for 30 days; their photos and recordings go with them.
        viewModelScope.launch {
            val before = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(TRASH_DAYS)
            deleteFiles(attachmentDao.listForPurge(before))
            noteDao.purgeTrash(before)
            attachmentDao.deleteOrphans()
        }
    }

    private val links = combine(
        db.goalDao().observeAll(),
        db.taskDao().observeBetween(today.minusDays(7), today.plusDays(14)),
        noteDao.observeLinkedTaskTitles()
    ) { goals, tasks, titles ->
        Links(goals, tasks.filter { !it.isCompleted }.sortedWith(compareBy({ it.date }, { it.time })), titles.associate { it.id to it.title })
    }

    private val library = combine(noteDao.observeAll(), folderDao.observeAll(), attachmentDao.observeAll(), links) { notes, folders, attachments, l ->
        Library(notes, folders, attachments.groupBy { it.noteId }, l)
    }

    val uiState: StateFlow<NotesUiState> = combine(library, filters, transient) { lib, f, t ->
        val notes = lib.notes
        val live = notes.filter { !it.isDeleted }
        val searching = f.query.isNotBlank()
        val inView = when (f.view) {
            // Kundalik entries live in their own screen, but a search finds them too.
            NotesView.ACTIVE -> live.filter { !it.archived && (it.journalDate == null || searching) }
            NotesView.ARCHIVE -> live.filter { it.archived }
            NotesView.TRASH -> notes.filter { it.isDeleted }
        }
        val folderCounts = lib.folders.map { folder -> FolderCount(folder, live.count { !it.archived && it.folderId == folder.id }) }
        val activeFolder = f.folder?.takeIf { id -> lib.folders.any { it.id == id } }
        val inFolder = if (activeFolder == null || f.view == NotesView.TRASH) inView else inView.filter { it.folderId == activeFolder }
        val tagCounts = inFolder.flatMap { it.tags }.groupingBy { it }.eachCount()
            .map { (name, count) -> TagCount(name, count) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag.lowercase() })
        // A filter that no longer matches anything (last note deleted/retagged) would show an empty list forever.
        val activeTag = f.tag?.takeIf { tag -> tagCounts.any { it.tag == tag } }
        val usedColors = inFolder.mapNotNull { it.color }.distinct().sortedBy { it.ordinal }
        val activeColor = f.color?.takeIf { it in usedColors }
        val sorted = when (f.sort) {
            NoteSort.UPDATED -> inFolder.sortedByDescending { it.updatedAt }
            NoteSort.CREATED -> inFolder.sortedByDescending { it.createdAt }
            NoteSort.TITLE -> inFolder.sortedBy { it.displayTitle().lowercase() }
        }.let { list -> if (f.view == NotesView.ACTIVE) list.sortedByDescending { it.pinned } else list }
        NotesUiState(
            allNotes = notes,
            isLoaded = true,
            visibleNotes = sorted.filter { (activeTag == null || activeTag in it.tags) && (activeColor == null || it.color == activeColor) && it.matches(f.query) },
            totalCount = live.count { !it.archived && it.journalDate == null },
            archivedCount = live.count { it.archived },
            trashCount = notes.count { it.isDeleted },
            tags = tagCounts,
            selectedTag = activeTag,
            selectedColor = activeColor,
            usedColors = usedColors,
            folders = folderCounts,
            selectedFolder = activeFolder,
            query = f.query,
            view = f.view,
            sort = f.sort,
            grid = t.grid,
            editor = t.editor,
            journalOpen = t.journal,
            journalEntries = live.filter { it.journalDate != null }.sortedByDescending { it.journalDate },
            attachments = lib.attachments,
            goals = lib.links.goals,
            tasks = lib.links.tasks,
            taskTitles = lib.links.taskTitles,
            undoNote = t.undo,
            message = t.message,
            ai = t.ai,
            needsApiKey = t.needsKey
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    // ------------------------------------------------------------ list

    fun selectTag(tag: String?) = filters.update { it.copy(tag = tag) }
    fun selectColor(color: NoteColor?) = filters.update { it.copy(color = if (it.color == color) null else color) }
    fun selectFolder(folderId: Long?) = filters.update { it.copy(folder = if (it.folder == folderId) null else folderId, tag = null) }
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
        viewModelScope.launch {
            deleteFiles(attachmentDao.listFor(note.id))
            attachmentDao.deleteFor(note.id)
            noteDao.delete(note)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            deleteFiles(attachmentDao.listInTrash())
            noteDao.emptyTrash()
            attachmentDao.deleteOrphans()
            transient.update { it.copy(message = "Savat tozalandi") }
        }
    }

    fun clearMessage() = transient.update { it.copy(message = null, undo = null) }

    // ------------------------------------------------------------ folders

    fun addFolder(name: String, icon: String, moveNoteId: Long? = null, onCreated: (Long) -> Unit = {}) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = folderDao.insert(NoteFolder(name = clean.take(30), icon = icon))
            moveNoteId?.let { noteId -> noteDao.findById(noteId)?.let { noteDao.update(it.copy(folderId = id)) } }
            onCreated(id)
        }
    }

    fun updateFolder(folder: NoteFolder) {
        if (folder.name.isBlank()) return
        viewModelScope.launch { folderDao.update(folder.copy(name = folder.name.trim().take(30))) }
    }

    /** The folder goes; its notes stay, just without a folder. */
    fun deleteFolder(folder: NoteFolder) {
        viewModelScope.launch {
            folderDao.clearFolder(folder.id)
            folderDao.delete(folder)
            transient.update { it.copy(message = "Papka o'chirildi, qaydlar saqlandi") }
        }
    }

    // ------------------------------------------------------------ journal

    fun openJournal() = transient.update { it.copy(journal = true) }

    fun closeJournal() = transient.update { it.copy(journal = false) }

    /** Opens the Kundalik entry for [date], or starts one. */
    fun openJournalDay(date: LocalDate) {
        if (date.isAfter(today)) return
        val existing = uiState.value.allNotes.firstOrNull { it.journalDate == date && !it.isDeleted }
        transient.update { it.copy(editor = NoteEditorTarget(existing, journalDate = date), undo = null) }
    }

    // ------------------------------------------------------------ editor

    fun openNew(checklist: Boolean = false, template: NoteTemplate? = null) = transient.update {
        it.copy(
            editor = NoteEditorTarget(null, checklist = template?.checklist ?: checklist, template = template, folderId = filters.value.folder),
            undo = null
        )
    }

    fun openNote(note: Note) = transient.update { it.copy(editor = NoteEditorTarget(note), undo = null) }

    /**
     * A new note needs an id before a photo or recording can be attached to it: saves the draft now
     * and hands back the id; the editor keeps editing that row.
     */
    fun saveDraftNow(draft: NoteDraft, onSaved: (Long) -> Unit) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val id = noteDao.insert(newNote(draft, clean(draft), now, archived = false))
            onSaved(id)
        }
    }

    private data class Clean(val title: String, val content: String, val tags: List<String>, val reminder: LocalDateTime?) {
        val isEmpty: Boolean get() = title.isBlank() && content.isBlank()
    }

    private fun clean(draft: NoteDraft) = Clean(
        title = draft.title.trim(),
        content = if (draft.isChecklist) Checklist.serialize(Checklist.parse(draft.content)) else draft.content.trim(),
        tags = draft.tags.map(::sanitizeTag).filter { it.isNotBlank() }.distinct(),
        reminder = draft.remindAt?.withSecond(0)?.withNano(0)
    )

    private fun newNote(draft: NoteDraft, c: Clean, now: Long, archived: Boolean) = Note(
        title = c.title,
        content = c.content,
        tags = c.tags,
        createdAt = now,
        remindAt = c.reminder,
        priority = draft.priority,
        updatedAt = now,
        pinned = draft.pinned,
        color = draft.color,
        isChecklist = draft.isChecklist,
        archived = archived,
        folderId = draft.folderId,
        locked = draft.locked,
        journalDate = draft.journalDate,
        goalId = draft.goalId,
        taskId = draft.taskId
    )

    /**
     * Called when leaving the editor. A new note with nothing in it is simply discarded; clearing an
     * existing note's text keeps the saved version rather than silently wiping it — unless it was only
     * saved early for an attachment that's gone again.
     */
    fun saveAndClose(existingId: Long?, draft: NoteDraft, action: CloseAction = CloseAction.SAVE) {
        val c = clean(draft)
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val existing = existingId?.let { noteDao.findById(it) }
            val hasMedia = existing != null && attachmentDao.listFor(existing.id).isNotEmpty()
            when {
                existing == null && (c.isEmpty || action == CloseAction.TRASH) -> Unit
                existing == null -> noteDao.insert(newNote(draft, c, now, archived = action == CloseAction.ARCHIVE))
                c.isEmpty && !hasMedia && existing.title.isBlank() && existing.content.isBlank() -> noteDao.delete(existing)
                else -> {
                    val keepOld = c.isEmpty && !hasMedia
                    val textChanged = !keepOld && (existing.title != c.title || existing.content != c.content || existing.isChecklist != draft.isChecklist)
                    val updated = existing.copy(
                        title = if (keepOld) existing.title else c.title,
                        content = if (keepOld) existing.content else c.content,
                        tags = c.tags,
                        remindAt = c.reminder,
                        priority = draft.priority,
                        color = draft.color,
                        pinned = draft.pinned,
                        isChecklist = if (keepOld) existing.isChecklist else draft.isChecklist,
                        folderId = draft.folderId,
                        locked = draft.locked,
                        journalDate = draft.journalDate,
                        goalId = draft.goalId,
                        taskId = draft.taskId,
                        updatedAt = if (textChanged || existing.tags != c.tags) now else existing.updatedAt
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
                        action != CloseAction.ARCHIVE || (existing == null && c.isEmpty) -> null
                        existing?.archived == true -> "Arxivdan chiqarildi"
                        else -> "Arxivga olindi"
                    }
                )
            }
        }
    }

    /** Opens a note requested from a reminder (notification / alert screen). */
    fun openFromReminder(note: Note) = transient.update { it.copy(editor = NoteEditorTarget(note), journal = false) }

    // ------------------------------------------------------------ attachments

    fun attachImage(noteId: Long, uri: Uri) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { NoteMedia.importImage(getApplication(), uri) }
            if (name == null) transient.update { it.copy(message = "Rasmni o'qib bo'lmadi") }
            else attachmentDao.insert(NoteAttachment(noteId = noteId, kind = AttachmentKind.IMAGE, fileName = name))
        }
    }

    /** A photo the camera wrote into a temporary file; imported (scaled) and the temp file removed. */
    fun attachCameraPhoto(noteId: Long, temp: File) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                NoteMedia.importImage(getApplication(), Uri.fromFile(temp)).also { temp.delete() }
            }
            if (name != null) attachmentDao.insert(NoteAttachment(noteId = noteId, kind = AttachmentKind.IMAGE, fileName = name))
        }
    }

    fun attachAudio(noteId: Long, fileName: String, durationMs: Long) {
        viewModelScope.launch {
            attachmentDao.insert(NoteAttachment(noteId = noteId, kind = AttachmentKind.AUDIO, fileName = fileName, durationMs = durationMs))
        }
    }

    fun deleteAttachment(attachment: NoteAttachment) {
        viewModelScope.launch {
            attachmentDao.delete(attachment)
            NoteMedia.delete(getApplication(), attachment.fileName)
        }
    }

    private suspend fun deleteFiles(list: List<NoteAttachment>) = withContext(Dispatchers.IO) {
        list.forEach { NoteMedia.delete(getApplication(), it.fileName) }
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

    fun summarize(title: String, content: String) = runAi { ai.summarizeNote(title, NoteFormat.plain(content)).map(NoteAiState::Summary) }

    fun extractTasks(title: String, content: String) = runAi { ai.extractTasks(title, NoteFormat.plain(content)).map(NoteAiState::Tasks) }

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
