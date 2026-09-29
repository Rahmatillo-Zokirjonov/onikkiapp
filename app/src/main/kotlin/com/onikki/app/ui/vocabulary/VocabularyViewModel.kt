package com.onikki.app.ui.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.dao.VocabWordDao
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.data.local.ChallengeSettings
import com.onikki.app.data.local.ChallengeSettingsStore
import com.onikki.app.domain.screentime.VocabChallenge
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VocabularyUiState(
    val words: List<VocabWord> = emptyList(),
    val settings: ChallengeSettings = ChallengeSettings(),
    val isLoaded: Boolean = false
)

class VocabularyViewModel(
    private val wordDao: VocabWordDao,
    private val settingsStore: ChallengeSettingsStore
) : ViewModel() {

    val uiState: StateFlow<VocabularyUiState> = combine(wordDao.observeAll(), settingsStore.settings) { words, settings ->
        VocabularyUiState(words, settings, isLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabularyUiState())

    fun updateSettings(transform: (ChallengeSettings) -> ChallengeSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun saveWord(existing: VocabWord?, english: String, uzbek: String) {
        if (english.isBlank() || uzbek.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                wordDao.insert(VocabWord(english = english.trim(), uzbek = uzbek.trim()))
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
        viewModelScope.launch {
            val known = uiState.value.words.map { VocabChallenge.normalize(it.english) }.toMutableSet()
            val fresh = pairs.filter { (english, _) -> known.add(VocabChallenge.normalize(english)) }
            if (fresh.isNotEmpty()) wordDao.insertAll(fresh.map { (en, uz) -> VocabWord(english = en, uzbek = uz) })
            onDone(fresh.size)
        }
    }

    companion object {
        fun factory(wordDao: VocabWordDao, settingsStore: ChallengeSettingsStore) = viewModelFactory {
            initializer { VocabularyViewModel(wordDao, settingsStore) }
        }
    }
}
