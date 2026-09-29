package com.onikki.app.ui.notes

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatReminderUz
import java.time.LocalDateTime

/**
 * Full-screen note editor. Leaving it (back arrow or system back) saves — there's no separate
 * "Saqlash" step to forget, matching how people expect a notes app to behave.
 */
@Composable
fun NoteEditorScreen(
    note: Note?,
    existingTags: List<String>,
    onClose: (title: String, content: String, tags: List<String>, remindAt: LocalDateTime?, priority: NotePriority) -> Unit,
    onDelete: (Note) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val key = note?.id
    var title by rememberSaveable(key) { mutableStateOf(note?.title ?: "") }
    var content by rememberSaveable(key) { mutableStateOf(note?.content ?: "") }
    var tags by rememberSaveable(key) { mutableStateOf(note?.tags ?: emptyList()) }
    var tagInput by rememberSaveable(key) { mutableStateOf("") }
    var remindAt by rememberSaveable(key) { mutableStateOf(note?.remindAt) }
    var priority by rememberSaveable(key) { mutableStateOf(note?.priority ?: NotePriority.ODDIY) }
    var reminderSheetOpen by rememberSaveable(key) { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    fun commitTagInput() {
        val tag = sanitizeTag(tagInput)
        if (tag.isNotBlank() && tag !in tags) tags = tags + tag
        tagInput = ""
    }

    var closed by remember { mutableStateOf(false) }

    fun close() {
        if (closed) return
        closed = true
        commitTagInput()
        onClose(title, content, tags, remindAt, priority)
    }

    BackHandler(onBack = ::close)

    // The bottom tabs stay reachable while editing; switching tab disposes this screen, so save then too.
    // Skipped during rotation/config changes, where the editor is recreated with its saved state instead.
    val latestClose by rememberUpdatedState(::close)
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            if (activity?.isChangingConfigurations != true) latestClose()
        }
    }

    val bodyStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily, lineHeight = 22.sp)
    val titleStyle = TextStyle(
        color = colors.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = OnIkkiFontFamily
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                color = colors.text.muted(0.7f),
                fontSize = 20.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = ::close).padding(8.dp)
            )
            Text(
                text = note?.let { "Yaratildi: ${formatRelativeDateUz(it.createdAt)}" } ?: "Yangi qayd",
                color = colors.text.muted(0.45f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f)
            )
            if (title.isNotBlank() || content.isNotBlank()) {
                EditorAction(label = "Ulashish") {
                    val text = listOf(title.trim(), content.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
            if (note != null) {
                EditorAction(label = if (confirmingDelete) "Rostdan?" else "O'chirish") {
                    if (confirmingDelete) onDelete(note) else confirmingDelete = true
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
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
                suggestions = existingTags.filter { it !in tags && (tagInput.isBlank() || it.contains(tagInput.trim(), true)) },
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

            BasicTextField(
                value = content,
                onValueChange = { content = it },
                textStyle = bodyStyle,
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(bottom = 24.dp),
                decorationBox = { inner ->
                    Box {
                        if (content.isEmpty()) Text(text = "Yozishni boshlang…", style = bodyStyle.copy(color = colors.text.muted(0.35f)))
                        inner()
                    }
                }
            )
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
private fun EditorAction(label: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = label,
        color = colors.accent,
        fontSize = 13.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp)
    )
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
                    Text(
                        text = " ×",
                        color = colors.accent100.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontFamily = OnIkkiFontFamily
                    )
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
                        if (input.isEmpty()) {
                            Text(
                                text = "+ teg qo'shish",
                                color = colors.text.muted(0.4f),
                                fontSize = 13.sp,
                                fontFamily = OnIkkiFontFamily
                            )
                        }
                        inner()
                    }
                }
            )
        }
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
