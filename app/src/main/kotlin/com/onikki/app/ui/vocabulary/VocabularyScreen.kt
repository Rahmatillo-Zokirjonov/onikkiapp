package com.onikki.app.ui.vocabulary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.ChallengeDirection
import com.onikki.app.data.local.ChallengeMode
import com.onikki.app.data.local.ChallengeSettings
import com.onikki.app.data.local.ChallengeSettingsStore
import com.onikki.app.domain.screentime.VocabChallenge
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

@Composable
fun VocabularyRoute(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as OnIkkiApplication
    val viewModel: VocabularyViewModel = viewModel(
        factory = VocabularyViewModel.factory(app.database.vocabWordDao(), ChallengeSettingsStore(app))
    )
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    VocabularyScreen(state = state, viewModel = viewModel, onBack = onBack)
}

/** Challenge settings on top, then the two-column English | O'zbekcha word table. */
@Composable
private fun VocabularyScreen(state: VocabularyUiState, viewModel: VocabularyViewModel, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var editing by remember { mutableStateOf<VocabWord?>(null) }
    var adding by remember { mutableStateOf(false) }
    var bulk by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visible = state.words.filter {
        query.isBlank() || it.english.contains(query.trim(), true) || it.uzbek.contains(query.trim(), true)
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SubScreenHeader(title = "So'z yodlash", onBack = onBack) }
            item { ChallengeSettingsCard(state.settings, viewModel::updateSettings) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        text = "So'zlar · ${state.words.size} ta",
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Ko'p qo'shish",
                        color = colors.accent,
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.clickable { bulk = true }.padding(4.dp)
                    )
                }
            }
            if (state.isLoaded && state.words.isEmpty()) {
                item {
                    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
                        Text(
                            text = "Hali so'z yo'q. O'zingiz qo'shing yoki 30 ta boshlang'ich so'z bilan boshlang.",
                            color = colors.text.muted(0.65f),
                            fontSize = 13.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        OnIkkiButton(
                            text = "Namuna so'zlarni qo'shish",
                            onClick = { viewModel.addPairs(VocabChallenge.STARTER_WORDS) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (state.words.size > 8) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Qidirish") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (state.words.isNotEmpty()) {
                item { WordTableHeader() }
            }
            items(visible, key = { it.id }) { word -> WordRow(word, onClick = { editing = word }) }
        }
        AddFab(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }

    if (adding || editing != null) {
        WordSheet(
            word = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { en, uz ->
                viewModel.saveWord(editing, en, uz)
                // Adding stays open for the next word; editing closes.
                if (editing != null) editing = null
            },
            onDelete = { editing?.let(viewModel::deleteWord); editing = null }
        )
    }
    if (bulk) {
        BulkSheet(onDismiss = { bulk = false }, onAdd = { pairs -> viewModel.addPairs(pairs) { bulk = false } })
    }
}

@Composable
private fun ChallengeSettingsCard(settings: ChallengeSettings, update: ((ChallengeSettings) -> ChallengeSettings) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var phrase by remember(settings.phrase) { mutableStateOf(settings.phrase) }
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { update { it.copy(enabled = !it.enabled) } }) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ilova ochish sharti", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = if (settings.enabled) "Yoqilgan" else "O'chiq — hech bir ilova so'z so'ramaydi",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = { on -> update { it.copy(enabled = on) } })
        }
        if (!settings.enabled) return@OnIkkiCard

        SheetFieldLabel("Usul")
        ChipRow(ChallengeMode.entries.map { it to it.label }, settings.mode) { mode -> update { it.copy(mode = mode) } }

        if (settings.mode == ChallengeMode.WORDS) {
            SheetFieldLabel("Har safar nechta so'z")
            ChipRow(ChallengeSettings.WORD_COUNT_OPTIONS.map { it to "$it ta" }, settings.wordCount) { n -> update { it.copy(wordCount = n) } }
            SheetFieldLabel("Yo'nalish")
            ChipRow(ChallengeDirection.entries.map { it to it.label }, settings.direction) { d -> update { it.copy(direction = d) } }
        } else {
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it },
                label = { Text("Yoziladigan matn") },
                modifier = Modifier.fillMaxWidth()
            )
            if (phrase != settings.phrase && phrase.isNotBlank()) {
                OnIkkiButton(text = "Matnni saqlash", onClick = { update { it.copy(phrase = phrase.trim()) } })
            }
        }

        SheetFieldLabel("To'g'ri bajargach ilova qancha ochiq tursin")
        ChipRow(ChallengeSettings.GRACE_OPTIONS.map { it to if (it == 0) "Chiqquncha" else "$it daq" }, settings.graceMinutes) { g -> update { it.copy(graceMinutes = g) } }
        Text(
            text = if (settings.graceMinutes == 0) "Boshqa ilovaga yoki bosh ekranga o'tsangiz, qaytib kirganda yana so'raladi."
            else "Shu vaqt ichida chiqib qayta kirsangiz, qayta so'ralmaydi.",
            color = colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { update { it.copy(unlockBlockedApps = !it.unlockBlockedApps) } }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Bloklangan ilovani ham so'z bilan ochish", color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = "Qat'iy rejimdagi ilovalarga ta'sir qilmaydi",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Switch(checked = settings.unlockBlockedApps, onCheckedChange = { on -> update { it.copy(unlockBlockedApps = on) } })
        }
        Text(
            text = "Qaysi ilova har ochilganda so'rashini Ilovalar nazorati > ilova > \"Ochishda so'z yodlash\"da tanlaysiz.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun WordTableHeader() {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(text = "ENGLISH", color = colors.accent, style = OnIkkiType.kicker, modifier = Modifier.weight(1f))
        Text(text = "O'ZBEKCHA", color = colors.accent, style = OnIkkiType.kicker, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WordRow(word: VocabWord, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        gap = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = word.english,
                color = colors.text,
                fontSize = 14.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = word.uzbek,
                color = colors.text.muted(0.8f),
                fontSize = 14.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (word.correctCount + word.wrongCount > 0) {
            Text(
                text = "✓ ${word.correctCount}  ✗ ${word.wrongCount}",
                color = colors.text.muted(0.4f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

@Composable
private fun WordSheet(word: VocabWord?, onDismiss: () -> Unit, onSave: (String, String) -> Unit, onDelete: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var english by remember(word?.id) { mutableStateOf(word?.english ?: "") }
    var uzbek by remember(word?.id) { mutableStateOf(word?.uzbek ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }

    OnIkkiSheet(title = if (word == null) "Yangi so'z" else "So'zni tahrirlash", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = english,
                onValueChange = { english = it; error = null },
                label = { Text("English") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uzbek,
                onValueChange = { uzbek = it; error = null },
                label = { Text("O'zbekcha") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "Bir nechta to'g'ri javob bo'lsa, vergul bilan yozing: \"katta, ulkan\"",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        if (word == null && savedCount > 0) {
            Text(text = "$savedCount ta so'z qo'shildi — keyingisini yozing", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                if (english.isBlank() || uzbek.isBlank()) {
                    error = "Ikkala ustunni ham to'ldiring"
                } else {
                    onSave(english, uzbek)
                    if (word == null) {
                        savedCount++
                        english = ""
                        uzbek = ""
                    }
                }
            },
            onDelete = if (word != null) onDelete else null
        )
    }
}

@Composable
private fun BulkSheet(onDismiss: () -> Unit, onAdd: (List<Pair<String, String>>) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var text by remember { mutableStateOf("") }
    val parsed = VocabChallenge.parseBulk(text)
    OnIkkiSheet(title = "Ko'p so'z qo'shish", onDismiss = onDismiss) {
        Text(
            text = "Har qatorga bitta juftlik: inglizcha - o'zbekcha\nMasalan:\napple - olma\nto run - yugurmoq",
            color = colors.text.muted(0.6f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("So'zlar") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
        )
        Text(text = "${parsed.size} ta juftlik topildi", color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        OnIkkiButton(
            text = "Qo'shish",
            onClick = { if (parsed.isNotEmpty()) onAdd(parsed) },
            modifier = Modifier.fillMaxWidth()
        )
        OnIkkiButton(text = "Bekor", onClick = onDismiss, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
    }
}
