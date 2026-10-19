package com.onikki.app.ui.vocabulary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.ChallengeSettings
import com.onikki.app.data.local.ChallengeSettingsStore
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.screentime.VocabChallenge
import com.onikki.app.domain.vocab.DictEntry
import com.onikki.app.domain.vocab.EnglishDictionary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VocabularyUiState(
    val words: List<VocabWord> = emptyList(),
    val settings: ChallengeSettings = ChallengeSettings(),
    val isLoaded: Boolean = false,
    /** The built-in word list is ready (suggestions and auto-translation work offline). */
    val dictionaryReady: Boolean = false
)

/** The AI translation for the word being typed in the sheet. */
sealed interface TranslateState {
    data object Idle : TranslateState
    data object Loading : TranslateState
    data class Done(val english: String, val uzbek: String) : TranslateState
    data class Failed(val message: String) : TranslateState
    data object NeedsKey : TranslateState
}

class VocabularyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val wordDao = db.vocabWordDao()
    private val settingsStore = ChallengeSettingsStore(application)
    private val ai = AiRepository(application, db)
    private var dictionary: EnglishDictionary? = null
    private val dictReady = MutableStateFlow(false)

    private val _translate = MutableStateFlow<TranslateState>(TranslateState.Idle)
    val translate: StateFlow<TranslateState> = _translate

    init {
        viewModelScope.launch {
            dictionary = runCatching { DictionaryStore.get(application) }.getOrNull()
            dictReady.value = dictionary != null
        }
    }

    val uiState: StateFlow<VocabularyUiState> = combine(wordDao.observeAll(), settingsStore.settings, dictReady) { words, settings, ready ->
        VocabularyUiState(words, settings, isLoaded = true, dictionaryReady = ready)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabularyUiState())

    fun suggest(typed: String): List<DictEntry> = dictionary?.suggest(typed, limit = 6).orEmpty()

    fun lookup(english: String): DictEntry? = dictionary?.lookup(english)

    fun isInList(english: String): Boolean {
        val key = VocabChallenge.normalize(english)
        return uiState.value.words.any { VocabChallenge.normalize(it.english) == key }
    }

    fun translateWithAi(english: String) {
        val word = english.trim()
        if (word.isEmpty()) return
        _translate.value = TranslateState.Loading
        viewModelScope.launch {
            _translate.value = when (val r = ai.translateWord(word)) {
                is AiOutcome.Ok -> if (r.value.isBlank()) TranslateState.Failed("Tarjima topilmadi") else TranslateState.Done(word, r.value)
                is AiOutcome.Failed -> TranslateState.Failed(r.message)
                AiOutcome.Offline -> TranslateState.Failed("Internet yo'q")
                AiOutcome.NoKey -> TranslateState.NeedsKey
            }
        }
    }

    fun resetTranslate() { _translate.value = TranslateState.Idle }

    fun saveApiKey(key: String, thenTranslate: String) {
        viewModelScope.launch {
            ai.saveKey(key)
            translateWithAi(thenTranslate)
        }
    }

    fun updateSettings(transform: (ChallengeSettings) -> ChallengeSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    private val learn = com.onikki.app.data.repository.LearnRepository(db)

    fun saveWord(existing: VocabWord?, english: String, uzbek: String) {
        if (english.isBlank() || uzbek.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                wordDao.insert(VocabWord(english = english.trim(), uzbek = uzbek.trim(), deckId = learn.myWordsDeckId()))
            } else {
                wordDao.update(existing.copy(english = english.trim(), uzbek = uzbek.trim()))
            }
        }
    }

    fun deleteWord(word: VocabWord) {
        viewModelScope.launch { wordDao.delete(word) }
    }

    /** Adds pairs, skipping ones whose English word is already in the list. Returns how many were new. */
    fun addPairs(pairs: List<Pair<String, String>>, onDone: (Int) -> Unit = {}) {
        addImported(pairs.map { (en, uz) -> com.onikki.app.domain.vocab.ImportedWord(en, uz, null, null) }, onDone)
    }

    /** Bulk text ("english | uzbek | misol gap") or pairs → "Mening so'zlarim", duplicates skipped. */
    fun addImported(items: List<com.onikki.app.domain.vocab.ImportedWord>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val result = learn.importWords(learn.myWordsDeckId(), items)
            onDone(result.added)
        }
    }

    /** The next [count] most common words from the built-in list that aren't in the user's list yet. */
    fun addPopular(count: Int, onDone: (Int) -> Unit) {
        val dict = dictionary ?: return onDone(0)
        val known = uiState.value.words.map { EnglishDictionary.clean(it.english) }.toSet()
        addPairs(dict.popularMissing(known, count).map { it.english to it.uzbek }, onDone)
    }
}
