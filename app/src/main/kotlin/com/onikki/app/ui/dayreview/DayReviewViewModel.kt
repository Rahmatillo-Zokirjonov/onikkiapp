package com.onikki.app.ui.dayreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.data.repository.AiAnswerStatus
import com.onikki.app.data.repository.AiInsightStatus
import com.onikki.app.data.repository.DayReviewRepository
import com.onikki.app.data.repository.DayStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayReviewUiState(
    val isLoading: Boolean = true,
    val stats: DayStats? = null,
    val hasApiKey: Boolean = false,
    val hasInternet: Boolean = true,
    val aiStatus: AiInsightStatus = AiInsightStatus.NotAttempted,
    val isLoadingAi: Boolean = false,
    val isApiKeySheetOpen: Boolean = false,
    val followUpAnswer: AiAnswerStatus? = null,
    val isAskingFollowUp: Boolean = false
)

class DayReviewViewModel(
    private val repository: DayReviewRepository,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val _uiState = MutableStateFlow(DayReviewUiState())
    val uiState: StateFlow<DayReviewUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val stats = repository.computeStats(today)
            val cachedInsight = repository.ensureDaySaved(today, stats)
            val hasKey = repository.hasApiKey()
            val hasInternet = repository.hasInternet()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    stats = stats,
                    hasApiKey = hasKey,
                    hasInternet = hasInternet,
                    aiStatus = if (cachedInsight != null) {
                        AiInsightStatus.Success(cachedInsight, rawText = "")
                    } else {
                        AiInsightStatus.NotAttempted
                    }
                )
            }

            if (cachedInsight == null && hasKey && hasInternet) {
                fetchAi(stats)
            }
        }
    }

    fun retryAi() {
        val stats = _uiState.value.stats ?: return
        fetchAi(stats)
    }

    private fun fetchAi(stats: DayStats) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAi = true) }
            val status = repository.fetchAiInsight(stats)
            if (status is AiInsightStatus.Success) {
                repository.saveAiSummary(today, stats, status.rawText)
            }
            _uiState.update { it.copy(isLoadingAi = false, aiStatus = status) }
        }
    }

    fun askFollowUp(question: String) {
        val stats = _uiState.value.stats ?: return
        if (question.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAskingFollowUp = true, followUpAnswer = null) }
            val answer = repository.askFollowUp(stats, question.trim())
            _uiState.update { it.copy(isAskingFollowUp = false, followUpAnswer = answer) }
        }
    }

    fun openApiKeySheet() {
        _uiState.update { it.copy(isApiKeySheetOpen = true) }
    }

    fun dismissApiKeySheet() {
        _uiState.update { it.copy(isApiKeySheetOpen = false) }
    }

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            apiKeyStore.setApiKey(key.trim())
            _uiState.update { it.copy(isApiKeySheetOpen = false) }
            refresh()
        }
    }

    companion object {
        fun factory(repository: DayReviewRepository, apiKeyStore: ApiKeyStore) = viewModelFactory {
            initializer { DayReviewViewModel(repository, apiKeyStore) }
        }
    }
}
