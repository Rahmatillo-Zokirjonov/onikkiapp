package com.onikki.app.ui.learn

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.VocabDeck
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.VocabImport
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

/** Where new words go: an existing deck, or a deck created on the spot. */
sealed interface DeckTarget {
    data class Existing(val id: Long) : DeckTarget
    data class New(val name: String) : DeckTarget
}

private val LEVELS = listOf<String?>(null, "A1", "A2", "B1", "B2")

/** Pasted text → parsed words, with a live preview and how many lines didn't parse. */
@Composable
fun BulkImportBody(text: String, onText: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val parsed = remember(text) { VocabImport.parse(text) }
    val lines = remember(text) { text.lines().count { it.isNotBlank() && !it.trim().startsWith("#") } }
    Text(
        text = "Har qatorga bitta so'z:\nenglish | o'zbekcha | misol gap\n\nMasalan:\ndecide (decided) | qaror qilmoq | I decided to study.\napple | olma",
        color = colors.text.muted(0.6f),
        fontSize = 12.sp,
        fontFamily = OnIkkiFontFamily
    )
    OutlinedTextField(value = text, onValueChange = onText, label = { Text("So'zlar") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
    Text(
        text = "${parsed.size} ta so'z topildi" + (lines - parsed.size).takeIf { it > 0 }?.let { " · $it ta qator tushunilmadi" }.orEmpty(),
        color = if (lines > parsed.size) colors.warmAccent else colors.text.muted(0.55f),
        fontSize = 12.sp,
        fontFamily = OnIkkiFontFamily
    )
    parsed.take(3).forEach { w ->
        Text(
            text = "• ${w.english}${w.pastForm?.let { " ($it)" }.orEmpty()} — ${w.uzbek}${w.example?.let { "  ·  $it" }.orEmpty()}",
            color = colors.text.muted(0.7f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            maxLines = 1
        )
    }
}

/**
 * "So'z qo'shish" on O'rganish: one by one (with example and past form) or a pasted list, into a chosen deck.
 * Added words are NEW, so they appear as new lessons at the end of that deck's path.
 */
@Composable
fun AddWordsSheet(decks: List<VocabDeck>, onDismiss: () -> Unit, onSubmit: (DeckTarget, List<ImportedWord>, String?, (String) -> Unit) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var bulk by remember { mutableStateOf(false) }
    var deck by remember { mutableStateOf<Long?>(decks.firstOrNull { it.name == com.onikki.app.data.repository.VocabSeeder.MY_WORDS }?.id ?: decks.firstOrNull()?.id) }
    var newDeck by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(decks.isEmpty()) }
    var level by remember { mutableStateOf<String?>(null) }
    var english by remember { mutableStateOf("") }
    var uzbek by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }
    var past by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableIntStateOf(0) }

    fun target(): DeckTarget? = when {
        creating && newDeck.isNotBlank() -> DeckTarget.New(newDeck.trim())
        !creating && deck != null -> DeckTarget.Existing(deck!!)
        else -> null
    }

    OnIkkiSheet(title = "So'z qo'shish", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !bulk, onClick = { bulk = false }, label = { Text("Bittalab") })
            FilterChip(selected = bulk, onClick = { bulk = true }, label = { Text("Ro'yxat (import)") })
        }
        SheetFieldLabel("Qaysi deck'ga")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            decks.forEach { d -> FilterChip(selected = !creating && deck == d.id, onClick = { creating = false; deck = d.id }, label = { Text(d.name) }) }
            FilterChip(selected = creating, onClick = { creating = true }, label = { Text("+ Yangi deck") })
        }
        if (creating) {
            OutlinedTextField(value = newDeck, onValueChange = { newDeck = it.take(40); error = null }, label = { Text("Deck nomi (masalan: Lesson 2)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        SheetFieldLabel("Daraja (ixtiyoriy)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LEVELS.forEach { l -> FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l ?: "—") }) }
        }

        if (bulk) {
            BulkImportBody(text, onText = { text = it; error = null })
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = english, onValueChange = { english = it; error = null }, label = { Text("English") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = past, onValueChange = { past = it }, label = { Text("O'tgan zamon") }, singleLine = true, modifier = Modifier.weight(0.7f))
            }
            OutlinedTextField(value = uzbek, onValueChange = { uzbek = it; error = null }, label = { Text("O'zbekcha (bir nechta bo'lsa — vergul bilan)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("Misol gap (inglizcha, ixtiyoriy)") }, modifier = Modifier.fillMaxWidth())
        }
        message?.let { Text(text = it, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily) }
        SheetErrorText(error)
        OnIkkiButton(
            text = if (bulk) "Import qilish" else "Qo'shish",
            onClick = {
                val t = target()
                val items = if (bulk) VocabImport.parse(text)
                else if (english.isNotBlank() && uzbek.isNotBlank()) listOf(ImportedWord(english.trim(), uzbek.trim(), example.trim().ifBlank { null }, past.trim().ifBlank { null }))
                else emptyList()
                when {
                    t == null -> error = "Deck tanlang yoki nomini yozing"
                    items.isEmpty() -> error = if (bulk) "Bironta ham so'z topilmadi — formatni tekshiring" else "Inglizcha va o'zbekchasini yozing"
                    else -> onSubmit(t, items, level) { result ->
                        message = result
                        if (bulk) text = "" else {
                            added++
                            english = ""; uzbek = ""; example = ""; past = ""
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Yangi so'zlar shu deck'ning oxiriga yangi lesson bo'lib qo'shiladi. Ro'yxatda bor so'z qayta qo'shilmaydi.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

/** Kept tiny so the old So'z yodlash screen can reuse the same parser and preview. */
fun parseImport(text: String): List<ImportedWord> = VocabImport.parse(text)
