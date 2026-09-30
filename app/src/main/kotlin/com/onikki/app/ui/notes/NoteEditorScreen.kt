package com.onikki.app.ui.notes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.AttachmentKind
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NoteAttachment
import com.onikki.app.data.local.NoteMedia
import com.onikki.app.domain.notes.NoteFormat
import com.onikki.app.domain.notes.NoteTemplates
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.onikki.app.data.db.entity.NoteColor
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.domain.ai.ExtractedTask
import com.onikki.app.domain.notes.Checklist
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.dayreview.ApiKeySheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatReminderUz
import java.time.LocalDate
import java.time.LocalDateTime

private const val OPEN = "☐ "
private const val DONE = "☑ "

/** Checklist being edited: raw lines "☐ text"/"☑ text"; blank rows are kept while editing, dropped on save. */
private fun rows(raw: String): List<Pair<Boolean, String>> =
    if (raw.isEmpty()) emptyList() else raw.split("\n").map { line -> line.startsWith("☑") to line.drop(2) }

private fun join(rows: List<Pair<Boolean, String>>): String = rows.joinToString("\n") { (done, text) -> (if (done) DONE else OPEN) + text }

/**
 * Full-screen note editor. Leaving it (back arrow or system back) saves — there's no separate
 * "Saqlash" step to forget, matching how people expect a notes app to behave.
 */
@Composable
fun NoteEditorScreen(target: NoteEditorTarget, state: NotesUiState, viewModel: NotesViewModel) {
    val note = target.note
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val key = note?.id
    val template = target.template
    val journalDate = note?.journalDate ?: target.journalDate
    var title by rememberSaveable(key) {
        mutableStateOf(note?.title ?: template?.title ?: target.journalDate?.let(::journalTitle) ?: "")
    }
    var isChecklist by rememberSaveable(key) { mutableStateOf(note?.isChecklist ?: target.checklist) }
    val initialText = when {
        note != null -> if (note.isChecklist) "" else note.content
        template != null && !template.checklist -> template.content
        target.journalDate != null -> NoteTemplates.JOURNAL
        else -> ""
    }
    var contentValue by rememberSaveable(key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText)) }
    val content = contentValue.text
    fun setContent(text: String) { contentValue = TextFieldValue(text, TextRange(text.length)) }
    var checkRaw by rememberSaveable(key) {
        mutableStateOf(
            when {
                note?.isChecklist == true -> note.content
                template?.checklist == true -> template.content
                target.checklist -> OPEN
                else -> ""
            }
        )
    }
    var tags by rememberSaveable(key) { mutableStateOf(note?.tags ?: template?.tags ?: emptyList()) }
    var noteId by rememberSaveable(key) { mutableStateOf(note?.id) }
    var folderId by rememberSaveable(key) { mutableStateOf(note?.folderId ?: target.folderId) }
    var locked by rememberSaveable(key) { mutableStateOf(note?.locked ?: false) }
    var goalId by rememberSaveable(key) { mutableStateOf(note?.goalId) }
    var taskId by rememberSaveable(key) { mutableStateOf(note?.taskId) }
    var cameraFile by rememberSaveable(key) { mutableStateOf<String?>(null) }
    var recorderOpen by rememberSaveable(key) { mutableStateOf(false) }
    var photoMenuOpen by remember { mutableStateOf(false) }
    var folderPickerOpen by rememberSaveable(key) { mutableStateOf(false) }
    var linkSheetOpen by rememberSaveable(key) { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<NoteAttachment?>(null) }
    val attachments = noteId?.let { state.attachments[it] }.orEmpty()
    var tagInput by rememberSaveable(key) { mutableStateOf("") }
    var remindAt by rememberSaveable(key) { mutableStateOf(note?.remindAt) }
    var priority by rememberSaveable(key) { mutableStateOf(note?.priority ?: NotePriority.ODDIY) }
    var color by rememberSaveable(key) { mutableStateOf(note?.color) }
    var pinned by rememberSaveable(key) { mutableStateOf(note?.pinned ?: false) }
    var reminderSheetOpen by rememberSaveable(key) { mutableStateOf(false) }
    var planSheetOpen by rememberSaveable(key) { mutableStateOf(false) }
    var colorsOpen by rememberSaveable(key) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var aiMenuOpen by remember { mutableStateOf(false) }
    var focusRow by remember { mutableStateOf<Int?>(null) }

    fun commitTagInput() {
        val tag = sanitizeTag(tagInput)
        if (tag.isNotBlank() && tag !in tags) tags = tags + tag
        tagInput = ""
    }

    fun bodyText(): String = if (isChecklist) Checklist.toPlain(join(rows(checkRaw))) else content

    var closed by remember { mutableStateOf(false) }

    fun draft() = NoteDraft(
        title, if (isChecklist) checkRaw else content, tags, remindAt, priority, color, pinned, isChecklist,
        folderId = folderId, locked = locked, journalDate = journalDate, goalId = goalId, taskId = taskId
    )

    fun close(action: CloseAction = CloseAction.SAVE) {
        if (closed) return
        closed = true
        commitTagInput()
        viewModel.saveAndClose(noteId, draft(), action)
    }

    /** Attachments need a saved note: a new one is saved first, then [then] gets its id. */
    fun withNoteId(then: (Long) -> Unit) {
        val id = noteId
        if (id != null) then(id) else viewModel.saveDraftNow(draft()) { newId -> noteId = newId; then(newId) }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) withNoteId { id -> viewModel.attachImage(id, uri) }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = cameraFile
        cameraFile = null
        if (ok && path != null) withNoteId { id -> viewModel.attachCameraPhoto(id, java.io.File(path)) }
        else path?.let { java.io.File(it).delete() }
    }

    BackHandler { close() }

    // The bottom tabs stay reachable while editing; switching tab disposes this screen, so save then too.
    // Skipped during rotation/config changes, where the editor is recreated with its saved state instead.
    val latestClose by rememberUpdatedState({ close() })
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            if (activity?.isChangingConfigurations != true) latestClose()
        }
    }

    // Voice typing: appends to the text, or adds an item to a checklist.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrEmpty()) {
            if (isChecklist) {
                val current = rows(checkRaw).filter { it.second.isNotBlank() }
                checkRaw = join(current + (false to spoken))
            } else {
                setContent(if (content.isBlank()) spoken else content.trimEnd() + (if (content.endsWith("\n")) "" else " ") + spoken)
            }
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Gapiring…")
        try {
            voice.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Telefonda ovozli yozish xizmati yo'q (Google ilovasini o'rnating)", Toast.LENGTH_LONG).show()
        }
    }

    val bodyStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, lineHeight = 22.sp)
    val titleStyle = TextStyle(color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
    val background = color?.tint() ?: colors.background

    Column(modifier = Modifier.fillMaxSize().background(background).imePadding()) {
        // ------------------------------------------------ top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconText("←") { close() }
            Text(
                text = (if (locked) "🔒 " else "") + when {
                    journalDate != null -> "Kundalik"
                    note != null -> "Tahrirlandi: ${formatRelativeDateUz(note.updatedAt)}"
                    isChecklist -> "Yangi ro'yxat"
                    else -> "Yangi qayd"
                },
                color = colors.text.muted(0.45f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            IconText(if (pinned) "📌" else "📍", alpha = if (pinned) 1f else 0.5f) { pinned = !pinned }
            Box {
                IconText("⋮") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val hasText = title.isNotBlank() || bodyText().isNotBlank()
                    if (hasText) {
                        DropdownMenuItem(text = { Text("Ulashish") }, onClick = {
                            menuOpen = false
                            val text = listOf(title.trim(), bodyText().trim()).filter { it.isNotBlank() }.joinToString("\n\n")
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                        })
                        DropdownMenuItem(text = { Text("Nusxa olish") }, onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(listOf(title.trim(), bodyText().trim()).filter { it.isNotBlank() }.joinToString("\n\n")))
                            Toast.makeText(context, "Nusxa olindi", Toast.LENGTH_SHORT).show()
                        })
                    }
                    if (journalDate == null) {
                        DropdownMenuItem(text = { Text("📁 Papka: ${state.folderName(folderId) ?: "yo'q"}") }, onClick = { menuOpen = false; folderPickerOpen = true })
                    }
                    DropdownMenuItem(text = { Text("🎯 Maqsad yoki vazifaga bog'lash") }, onClick = { menuOpen = false; linkSheetOpen = true })
                    DropdownMenuItem(
                        text = { Text(if (locked) "🔓 Qulfni olish" else "🔒 Qulflash (maxfiy)") },
                        onClick = {
                            menuOpen = false
                            if (locked) locked = false
                            else if (isDeviceSecure(context)) {
                                locked = true
                                Toast.makeText(context, "Endi bu qayd barmoq izi yoki PIN bilan ochiladi", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Avval telefon sozlamalarida ekran qulfi (PIN yoki barmoq izi) o'rnating", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    if (note != null || hasText) {
                        DropdownMenuItem(
                            text = { Text(if (note?.archived == true) "Arxivdan chiqarish" else "Arxivga olish") },
                            onClick = { menuOpen = false; close(CloseAction.ARCHIVE) }
                        )
                    }
                    DropdownMenuItem(text = { Text("O'chirish (Savatga)") }, onClick = { menuOpen = false; close(CloseAction.TRASH) })
                }
            }
        }
        if (colorsOpen) ColorPickerRow(selected = color, onSelect = { color = it })

        // ------------------------------------------------ body
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = titleStyle,
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) Text(text = "Sarlavha", style = titleStyle.copy(color = colors.text.muted(0.35f)))
                        inner()
                    }
                }
            )

            TagEditor(
                tags = tags,
                input = tagInput,
                suggestions = state.tags.map { it.tag }.filter { it !in tags && (tagInput.isBlank() || it.contains(tagInput.trim(), true)) },
                onInputChange = { value ->
                    // Typing a comma finishes the tag, like most tag inputs (commas can't live inside a tag anyway).
                    if (value.endsWith(",")) {
                        tagInput = value.dropLast(1)
                        commitTagInput()
                    } else {
                        tagInput = value
                    }
                },
                onCommit = ::commitTagInput,
                onAddSuggestion = { tags = tags + it; tagInput = "" },
                onRemove = { removed -> tags = tags - removed }
            )

            ReminderRow(remindAt = remindAt, priority = priority, onClick = { reminderSheetOpen = true })
            val linkChips = listOfNotNull(
                state.folderName(folderId)?.let { Triple(it, { folderPickerOpen = true }, { folderId = null }) },
                state.goalLabel(goalId)?.let { Triple("🎯 $it", { linkSheetOpen = true }, { goalId = null }) },
                taskId?.let { id -> (state.taskTitles[id] ?: state.tasks.firstOrNull { it.id == id }?.title)?.let { Triple("✅ $it", { linkSheetOpen = true }, { taskId = null }) } }
            )
            if (linkChips.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    linkChips.forEach { (label, onOpen, onRemove) -> LinkChip(label, onOpen, onRemove) }
                }
            }
            val images = attachments.filter { it.kind == AttachmentKind.IMAGE }
            if (images.isNotEmpty()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ImageThumbRow(images, size = 96.dp, onOpen = { viewing = it })
                }
            }
            attachments.filter { it.kind == AttachmentKind.AUDIO }.forEach { audio ->
                AudioAttachmentRow(audio, onDelete = { viewModel.deleteAttachment(audio) })
            }

            if (isChecklist) {
                ChecklistEditor(
                    raw = checkRaw,
                    onChange = { checkRaw = it },
                    focusRow = focusRow,
                    onFocusRow = { focusRow = it },
                    textStyle = bodyStyle
                )
            } else {
                BasicTextField(
                    value = contentValue,
                    onValueChange = { contentValue = it },
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(colors.accent),
                    visualTransformation = remember(colors) { MarkdownTransformation(colors) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    decorationBox = { inner ->
                        Box {
                            if (content.isEmpty()) Text(text = "Yozishni boshlang…", style = bodyStyle.copy(color = colors.text.muted(0.35f)))
                            inner()
                        }
                    }
                )
            }
            val words = NoteFormat.plain(bodyText()).split(Regex("""\s+""")).count { it.isNotBlank() && it != "•" }
            Text(
                text = listOfNotNull(
                    "$words so'z",
                    note?.let { "yaratildi ${formatRelativeDateUz(it.createdAt)}" }
                ).joinToString(" · "),
                color = colors.text.muted(0.35f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }

        // ------------------------------------------------ formatting
        if (!isChecklist) {
            FormatBar(
                onWrap = { marker ->
                    val e = NoteFormat.toggleWrap(content, contentValue.selection.start, contentValue.selection.end, marker)
                    contentValue = TextFieldValue(e.text, TextRange(e.selStart, e.selEnd))
                },
                onLine = { prefix ->
                    val e = NoteFormat.toggleLinePrefix(content, contentValue.selection.start, contentValue.selection.end, prefix)
                    contentValue = TextFieldValue(e.text, TextRange(e.selStart, e.selEnd))
                }
            )
        }

        // ------------------------------------------------ bottom toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.92f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton("🎤", "Gapirish", onClick = ::startVoice)
            ToolButton("🎙", "Yozuv") { recorderOpen = true }
            Box {
                ToolButton("📷", "Rasm") { photoMenuOpen = true }
                DropdownMenu(expanded = photoMenuOpen, onDismissRequest = { photoMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Kamera") }, onClick = {
                        photoMenuOpen = false
                        val (file, uri) = NoteMedia.cameraTarget(context)
                        cameraFile = file.path
                        runCatching { takePhoto.launch(uri) }.onFailure {
                            file.delete()
                            Toast.makeText(context, "Kamera ochilmadi", Toast.LENGTH_SHORT).show()
                        }
                    })
                    DropdownMenuItem(text = { Text("Galereya") }, onClick = {
                        photoMenuOpen = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                }
            }
            ToolButton(if (isChecklist) "¶" else "☑", if (isChecklist) "Matn" else "Ro'yxat") {
                if (isChecklist) {
                    setContent(Checklist.toPlain(join(rows(checkRaw))))
                    isChecklist = false
                } else {
                    checkRaw = Checklist.fromPlain(content).ifEmpty { OPEN }
                    isChecklist = true
                }
            }
            ToolButton("🎨", "Rang") { colorsOpen = !colorsOpen }
            ToolButton("📅", "Rejaga") { planSheetOpen = true }
            Box {
                ToolButton("✨", "AI") { aiMenuOpen = true }
                DropdownMenu(expanded = aiMenuOpen, onDismissRequest = { aiMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Qisqacha xulosa") }, onClick = {
                        aiMenuOpen = false
                        viewModel.summarize(title, bodyText())
                    })
                    DropdownMenuItem(text = { Text("Vazifalarni ajratib olish") }, onClick = {
                        aiMenuOpen = false
                        viewModel.extractTasks(title, bodyText())
                    })
                }
            }
        }
    }

    if (reminderSheetOpen) {
        NoteReminderSheet(
            remindAt = remindAt,
            priority = priority,
            onDismiss = { reminderSheetOpen = false },
            onSave = { at, level -> remindAt = at; priority = level; reminderSheetOpen = false },
            onRemove = { remindAt = null; priority = NotePriority.ODDIY; reminderSheetOpen = false }
        )
    }
    if (planSheetOpen) {
        val openItems = if (isChecklist) rows(checkRaw).filter { !it.first && it.second.isNotBlank() }.map { it.second.trim() } else emptyList()
        val single = title.trim().ifBlank { bodyText().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
        PlanSheet(
            items = if (isChecklist) openItems else listOfNotNull(single.takeIf { it.isNotEmpty() }),
            editableSingle = !isChecklist,
            onDismiss = { planSheetOpen = false },
            onAdd = { titles, date -> viewModel.addTasks(titles, date); planSheetOpen = false }
        )
    }
    state.ai?.let { ai ->
        NoteAiSheet(
            state = ai,
            canInsert = !isChecklist,
            onDismiss = viewModel::dismissAi,
            onInsertSummary = { summary ->
                setContent(summary.trim() + "\n\n" + content)
                viewModel.dismissAi()
            },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                Toast.makeText(context, "Nusxa olindi", Toast.LENGTH_SHORT).show()
            },
            onAddTasks = { tasks, date -> viewModel.addExtracted(tasks, date) }
        )
    }
    if (recorderOpen) {
        AudioRecorderSheet(
            onDone = { name, duration ->
                recorderOpen = false
                withNoteId { id -> viewModel.attachAudio(id, name, duration) }
            },
            onDismiss = { recorderOpen = false }
        )
    }
    viewing?.let { image ->
        ImageViewer(image.fileName, onDelete = { viewModel.deleteAttachment(image); viewing = null }, onDismiss = { viewing = null })
    }
    if (folderPickerOpen) {
        FolderPickerSheet(
            state = state,
            selected = folderId,
            onPick = { folderId = it; folderPickerOpen = false },
            onCreate = { name, icon -> viewModel.addFolder(name, icon) { id -> folderId = id }; folderPickerOpen = false },
            onDismiss = { folderPickerOpen = false }
        )
    }
    if (linkSheetOpen) {
        LinkSheet(
            state = state,
            goalId = goalId,
            taskId = taskId,
            onGoal = { goalId = it; linkSheetOpen = false },
            onTask = { taskId = it; linkSheetOpen = false },
            onDismiss = { linkSheetOpen = false }
        )
    }
    if (state.needsApiKey) ApiKeySheet(onDismiss = viewModel::dismissApiKey, onSave = viewModel::saveApiKey)
    state.message?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
}

// ---------------------------------------------------------------- checklist editor

@Composable
private fun ChecklistEditor(
    raw: String,
    onChange: (String) -> Unit,
    focusRow: Int?,
    onFocusRow: (Int?) -> Unit,
    textStyle: TextStyle
) {
    val colors = LocalOnIkkiColors.current
    val list = rows(raw)
    val requesters = remember(list.size) { List(list.size) { FocusRequester() } }
    LaunchedEffect(focusRow, list.size) {
        focusRow?.let { i -> requesters.getOrNull(i)?.let { runCatching { it.requestFocus() } } }
    }
    fun update(index: Int, row: Pair<Boolean, String>) = onChange(join(list.toMutableList().also { it[index] = row }))
    fun insertAfter(index: Int) {
        onChange(join(list.toMutableList().also { it.add(index + 1, false to "") }))
        onFocusRow(index + 1)
    }
    fun remove(index: Int) {
        onChange(join(list.toMutableList().also { it.removeAt(index) }))
        onFocusRow((index - 1).takeIf { it >= 0 })
    }

    val open = list.withIndex().filter { !it.value.first }
    val done = list.withIndex().filter { it.value.first }
    var showDone by rememberSaveable { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        open.forEach { (index, row) ->
            ChecklistRow(row, requesters.getOrNull(index), textStyle,
                onToggle = { update(index, true to row.second) },
                onText = { update(index, false to it) },
                onNext = { insertAfter(index) },
                onRemove = { remove(index) })
        }
        Text(
            text = "+ Band qo'shish",
            color = colors.accent,
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier
                .clickable {
                    val at = open.lastOrNull()?.index ?: -1
                    insertAfter(at)
                }
                .padding(vertical = 8.dp)
        )
        if (done.isNotEmpty()) {
            Text(
                text = "${if (showDone) "▾" else "▸"} Bajarilganlar (${done.size})",
                color = colors.text.muted(0.5f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable { showDone = !showDone }.padding(vertical = 6.dp)
            )
            if (showDone) {
                done.forEach { (index, row) ->
                    ChecklistRow(row, requesters.getOrNull(index), textStyle,
                        onToggle = { update(index, false to row.second) },
                        onText = { update(index, true to it) },
                        onNext = { insertAfter(index) },
                        onRemove = { remove(index) })
                }
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    row: Pair<Boolean, String>,
    requester: FocusRequester?,
    textStyle: TextStyle,
    onToggle: () -> Unit,
    onText: (String) -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val (done, text) = row
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (done) "☑" else "☐",
            color = if (done) colors.accent else colors.text.muted(0.6f),
            fontSize = 20.sp,
            modifier = Modifier.clickable(onClick = onToggle).padding(end = 10.dp, top = 4.dp, bottom = 4.dp)
        )
        BasicTextField(
            value = text,
            onValueChange = { value ->
                // A pasted multi-line block or Enter adds rows instead of breaking the format.
                if (value.contains('\n')) {
                    onText(value.substringBefore('\n'))
                    onNext()
                } else {
                    onText(value)
                }
            },
            textStyle = textStyle.copy(
                color = if (done) colors.text.muted(0.45f) else colors.text,
                textDecoration = if (done) TextDecoration.LineThrough else null
            ),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.weight(1f).let { if (requester != null) it.focusRequester(requester) else it },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(text = "Band", style = textStyle.copy(color = colors.text.muted(0.3f)))
                    inner()
                }
            }
        )
        Text(
            text = "×",
            color = colors.text.muted(0.35f),
            fontSize = 18.sp,
            modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 6.dp)
        )
    }
}

// ---------------------------------------------------------------- sheets

@Composable
private fun PlanSheet(items: List<String>, editableSingle: Boolean, onDismiss: () -> Unit, onAdd: (List<String>, LocalDate) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    var date by remember { mutableStateOf(today) }
    var single by remember { mutableStateOf(items.firstOrNull().orEmpty()) }
    var chosen by remember { mutableStateOf(items.indices.toSet()) }
    OnIkkiSheet(title = "Kunlik rejaga qo'shish", onDismiss = onDismiss) {
        if (editableSingle) {
            OutlinedTextField(value = single, onValueChange = { single = it }, label = { Text("Vazifa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else if (items.isEmpty()) {
            Text(text = "Ro'yxatda bajarilmagan band yo'q", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        } else {
            Text(text = "Bajarilmagan bandlar vazifa bo'ladi:", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            items.forEachIndexed { i, item ->
                Text(
                    text = "${if (i in chosen) "☑" else "☐"}  $item",
                    color = if (i in chosen) colors.text else colors.text.muted(0.45f),
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.fillMaxWidth().clickable { chosen = if (i in chosen) chosen - i else chosen + i }.padding(vertical = 4.dp)
                )
            }
        }
        DayChips(date = date, onDate = { date = it })
        val titles = if (editableSingle) listOf(single) else items.filterIndexed { i, _ -> i in chosen }
        OnIkkiButton(
            text = if (titles.size > 1) "${titles.size} ta vazifa qo'shish" else "Qo'shish",
            onClick = { onAdd(titles, date) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DayChips(date: LocalDate, onDate: (LocalDate) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Bugun" to today, "Ertaga" to today.plusDays(1), "Indinga" to today.plusDays(2)).forEach { (label, day) ->
            val selected = date == day
            Text(
                text = label,
                color = if (selected) colors.accent100 else colors.text.muted(0.7f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier
                    .background(if (selected) colors.accent800 else colors.background, RoundedCornerShape(50))
                    .clickable { onDate(day) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    DatePickerField(label = "Sana", date = date, onDateChange = { it?.let(onDate) })
}

@Composable
private fun NoteAiSheet(
    state: NoteAiState,
    canInsert: Boolean,
    onDismiss: () -> Unit,
    onInsertSummary: (String) -> Unit,
    onCopy: (String) -> Unit,
    onAddTasks: (List<ExtractedTask>, LocalDate) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    OnIkkiSheet(title = "✨ AI", onDismiss = onDismiss) {
        when (state) {
            NoteAiState.Loading -> Text(text = "O'ylayapti…", color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            is NoteAiState.Failed -> Text(text = state.message, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            is NoteAiState.Summary -> {
                Text(text = "Qisqacha xulosa", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                SelectionContainer {
                    Text(text = state.text, color = colors.text, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = OnIkkiFontFamily)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnIkkiButton(text = "Nusxa olish", onClick = { onCopy(state.text) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
                    if (canInsert) OnIkkiButton(text = "Qayd boshiga qo'shish", onClick = { onInsertSummary(state.text) }, modifier = Modifier.weight(1f))
                }
            }
            is NoteAiState.Tasks -> {
                if (state.tasks.isEmpty()) {
                    Text(text = "Bu qaydda bajariladigan ish topilmadi.", color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                } else {
                    var chosen by remember(state) { mutableStateOf(state.tasks.indices.toSet()) }
                    var fallback by remember(state) { mutableStateOf(LocalDate.now()) }
                    Text(text = "Topilgan vazifalar", color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                    state.tasks.forEachIndexed { i, t ->
                        val selected = i in chosen
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { chosen = if (selected) chosen - i else chosen + i }.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${if (selected) "☑" else "☐"}  ${t.title}",
                                color = if (selected) colors.text else colors.text.muted(0.45f),
                                fontSize = 14.sp,
                                fontFamily = OnIkkiFontFamily
                            )
                            if (t.date != null || t.time != null) {
                                Text(
                                    text = listOfNotNull(t.date?.let(::formatRelativeDateUz), t.time?.toString()).joinToString(", "),
                                    color = colors.text.muted(0.5f),
                                    fontSize = 11.sp,
                                    fontFamily = OnIkkiFontFamily,
                                    modifier = Modifier.padding(start = 26.dp)
                                )
                            }
                        }
                    }
                    if (state.tasks.any { it.date == null }) {
                        Text(text = "Sanasiz vazifalar kuni:", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                        DayChips(date = fallback, onDate = { fallback = it })
                    }
                    OnIkkiButton(
                        text = "Kunlik rejaga qo'shish (${chosen.size})",
                        onClick = { onAddTasks(state.tasks.filterIndexed { i, _ -> i in chosen }, fallback) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- small pieces

@Composable
private fun ColorPickerRow(selected: NoteColor?, onSelect: (NoteColor?) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(colors.surface, CircleShape)
                .border(BorderStroke(2.dp, if (selected == null) colors.text else colors.divider), CircleShape)
                .clickable { onSelect(null) },
            contentAlignment = Alignment.Center
        ) { Text(text = "∅", color = colors.text.muted(0.6f), fontSize = 13.sp) }
        NoteColor.entries.forEach { c ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(c.dot(), CircleShape)
                    .border(BorderStroke(2.dp, if (selected == c) colors.text else colors.background.copy(alpha = 0f)), CircleShape)
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
private fun IconText(icon: String, alpha: Float = 0.75f, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = icon,
        color = colors.text.muted(alpha),
        fontSize = 19.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun ToolButton(icon: String, label: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 6.dp)
    ) {
        Text(text = icon, fontSize = 17.sp, color = colors.text)
        Text(text = label, fontSize = 10.sp, color = colors.text.muted(0.6f), fontFamily = OnIkkiFontFamily)
    }
}

/** Off by default: a quiet link until the user sets a reminder; then its time and level. */
@Composable
private fun ReminderRow(remindAt: LocalDateTime?, priority: NotePriority, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val upcoming = remindAt?.takeIf { it.isAfter(LocalDateTime.now()) }
    if (upcoming != null) {
        ReminderBadge(upcoming, priority, Modifier.clickable(onClick = onClick))
    } else {
        Text(
            text = if (remindAt != null) "🔔 Eslatma o'tdi (${formatReminderUz(remindAt)}) — yangisini qo'yish" else "🔔 Eslatma qo'shish",
            color = colors.text.muted(0.45f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun TagEditor(
    tags: List<String>,
    input: String,
    suggestions: List<String>,
    onInputChange: (String) -> Unit,
    onCommit: () -> Unit,
    onAddSuggestion: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier
                        .background(colors.accent800, RoundedCornerShape(50))
                        .clickable { onRemove(tag) }
                        .padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "#$tag", color = colors.accent100, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    Text(text = " ×", color = colors.accent100.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
            }
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit() }),
                modifier = Modifier.width(120.dp).padding(vertical = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) Text(text = "+ teg qo'shish", color = colors.text.muted(0.4f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                        inner()
                    }
                }
            )
        }
        if (suggestions.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.take(8).forEach { suggestion ->
                    Text(
                        text = "#$suggestion",
                        color = colors.text.muted(0.6f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier
                            .background(colors.surface, RoundedCornerShape(50))
                            .clickable { onAddSuggestion(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---------------------------------------------------------------- formatting bar, links, folders

@Composable
private fun FormatBar(onWrap: (String) -> Unit, onLine: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.surface.copy(alpha = 0.6f)).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormatKey("B", FontWeight.Bold) { onWrap("**") }
        FormatKey("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) { onWrap("*") }
        FormatKey("S", decoration = TextDecoration.LineThrough) { onWrap("~~") }
        FormatKey("H1", FontWeight.SemiBold) { onLine("# ") }
        FormatKey("H2") { onLine("## ") }
        FormatKey("•") { onLine("- ") }
        FormatKey("❝") { onLine("> ") }
    }
}

@Composable
private fun FormatKey(
    label: String,
    weight: FontWeight = FontWeight.Normal,
    fontStyle: androidx.compose.ui.text.font.FontStyle? = null,
    decoration: TextDecoration? = null,
    onClick: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = label,
        color = colors.text.muted(0.85f),
        fontSize = 15.sp,
        fontWeight = weight,
        fontStyle = fontStyle,
        textDecoration = decoration,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun LinkChip(label: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(colors.surface, RoundedCornerShape(50)).clickable(onClick = onOpen).padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(text = label, color = colors.text.muted(0.8f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
        Text(text = " ×", color = colors.text.muted(0.4f), fontSize = 13.sp, modifier = Modifier.clickable(onClick = onRemove).padding(horizontal = 4.dp))
    }
}

@Composable
private fun FolderPickerSheet(state: NotesUiState, selected: Long?, onPick: (Long?) -> Unit, onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderSheet(folder = null, onDismiss = { creating = false }, onSave = { name, icon -> onCreate(name, icon) }, onDelete = null)
        return
    }
    OnIkkiSheet(title = "Papka", onDismiss = onDismiss) {
        PickRow("Papkasiz", selected == null) { onPick(null) }
        state.folders.forEach { (folder, count) -> PickRow("${folder.icon} ${folder.name}  ($count)", selected == folder.id) { onPick(folder.id) } }
        Text(
            text = "+ Yangi papka",
            color = colors.accent,
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clickable { creating = true }.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun LinkSheet(state: NotesUiState, goalId: Long?, taskId: Long?, onGoal: (Long?) -> Unit, onTask: (Long?) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val openGoals = state.goals.filter { it.doneAt == null }
    // Big goals first, each followed by its stages.
    val ordered = openGoals.filter { it.parentId == null }.flatMap { big -> listOf(big) + openGoals.filter { it.parentId == big.id }.sortedBy { it.orderIndex } }
    OnIkkiSheet(title = "Bog'lash", onDismiss = onDismiss) {
        Text(text = "MAQSAD", color = colors.text.muted(0.45f), style = com.onikki.app.ui.theme.OnIkkiType.kicker)
        if (ordered.isEmpty()) Text(text = "Faol maqsad yo'q", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        if (goalId != null) PickRow("Bog'lamaslik", false) { onGoal(null) }
        ordered.forEach { g ->
            PickRow((if (g.parentId != null) "     ↳ " else "${g.icon} ") + g.title, goalId == g.id) { onGoal(g.id) }
        }
        Text(text = "VAZIFA", color = colors.text.muted(0.45f), style = com.onikki.app.ui.theme.OnIkkiType.kicker, modifier = Modifier.padding(top = 8.dp))
        if (state.tasks.isEmpty()) Text(text = "Yaqin kunlarda ochiq vazifa yo'q", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        if (taskId != null) PickRow("Bog'lamaslik", false) { onTask(null) }
        state.tasks.take(25).forEach { t -> PickRow("${formatRelativeDateUz(t.date)} · ${t.title}", taskId == t.id) { onTask(t.id) } }
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = (if (selected) "✓ " else "") + label,
        color = if (selected) colors.accent else colors.text,
        fontSize = 14.sp,
        fontFamily = OnIkkiFontFamily,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)
    )
}
