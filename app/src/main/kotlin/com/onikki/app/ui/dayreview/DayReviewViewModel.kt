package com.onikki.app.ui.dayreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onikki.app.data.db.entity.DailyReview
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.data.repository.AiAnswerStatus
import com.onikki.app.data.repository.AiInsightStatus
import com.onikki.app.data.repository.DayData
import com.onikki.app.data.repository.DayReviewRepository
import com.onikki.app.domain.habits.HabitStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** AI / key / connectivity side of the screen — everything not derived from the database. */
data class AiUiState(
    val hasApiKey: Boolean = false,
    val hasInternet: Boolean = true,
    val aiStatus: AiInsightStatus = AiInsightStatus.NotAttempted,
    val isLoadingAi: Boolean = false,
    val isApiKeySheetOpen: Boolean = false,
    val followUpAnswer: AiAnswerStatus? = null,
    val isAskingFollowUp: Boolean = false,
    /** True after "Tahrirlash" on an already finished day. */
    val isEditing: Boolean = false,
    /** A past day opened from the week strip. */
    val viewingPast: DailyReview? = null
)

data class DayReviewUiState(
    val today: LocalDate = LocalDate.now(),
    val day: DayData? = null,
    val ai: AiUiState = AiUiState()
) {
    val isFinished: Boolean get() = day?.review != null
    val showForm: Boolean get() = !isFinished || ai.isEditing
}

class DayReviewViewModel(
    private val repository: DayReviewRepository,
    private val apiKeyStore: ApiKeyStore
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val ai = MutableStateFlow(AiUiState())

    val uiState: StateFlow<DayReviewUiState> = combine(repository.observeDay(today), ai) { day, aiState ->
        DayReviewUiState(today = today, day = day, ai = aiState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayReviewUiState())

    init {
        viewModelScope.launch {
            val hasKey = repository.hasApiKey()
            ai.update { it.copy(hasApiKey = hasKey, hasInternet = repository.hasInternet()) }
            // A day finished earlier keeps its saved AI summary; show it without calling the API again.
            val review = repository.observeDay(today).first().review
            review?.aiSummary?.let { raw ->
                ai.update { it.copy(aiStatus = AiInsightStatus.Success(repository.parseInsight(raw), raw)) }
            }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { repository.setTaskDone(task, !task.isCompleted) }
    }

    fun tapHabit(stats: HabitStats) {
        viewModelScope.launch { repository.tapHabit(stats, today) }
    }

    fun moveUnfinishedToTomorrow() {
        val tasks = uiState.value.day?.tasks ?: return
        viewModelScope.launch { repository.moveUnfinishedToTomorrow(tasks, today) }
    }

    fun finishDay(mood: Int?, reflection: String, tomorrow: List<String>) {
        val day = uiState.value.day ?: return
        viewModelScope.launch {
            repository.finishDay(today, day.stats, mood, reflection, tomorrow)
            ai.update { it.copy(isEditing = false) }
            val saved = repository.observeDay(today).first()
            if (ai.value.hasApiKey && repository.hasInternet()) fetchAi(saved)
        }
    }

    fun startEditing() = ai.update { it.copy(isEditing = true) }

    fun cancelEditing() = ai.update { it.copy(isEditing = false) }

    fun retryAi() {
        val day = uiState.value.day ?: return
        viewModelScope.launch { fetchAi(day) }
    }

    private suspend fun fetchAi(day: DayData) {
        ai.update { it.copy(isLoadingAi = true, hasInternet = true) }
        val unfinished = day.tasks.filter { !it.isCompleted }.map { it.title }
        val status = repository.fetchAiInsight(day.stats, day.review, unfinished)
        if (status is AiInsightStatus.Success) repository.saveAiSummary(today, status.rawText)
        ai.update { it.copy(isLoadingAi = false, aiStatus = status, hasInternet = repository.hasInternet()) }
    }

    fun askFollowUp(question: String) {
        val day = uiState.value.day ?: return
        if (question.isBlank()) return
        viewModelScope.launch {
            ai.update { it.copy(isAskingFollowUp = true, followUpAnswer = null) }
            val answer = repository.askFollowUp(day.stats, day.review, question.trim())
            ai.update { it.copy(isAskingFollowUp = false, followUpAnswer = answer) }
        }
    }

    fun openPastDay(review: DailyReview) = ai.update { it.copy(viewingPast = review) }

    fun closePastDay() = ai.update { it.copy(viewingPast = null) }

    fun parseInsight(raw: String) = repository.parseInsight(raw)

    fun openApiKeySheet() = ai.update { it.copy(isApiKeySheetOpen = true) }

    fun dismissApiKeySheet() = ai.update { it.copy(isApiKeySheetOpen = false) }

    fun saveApiKey(key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            apiKeyStore.setApiKey(key.trim())
            ai.update { it.copy(isApiKeySheetOpen = false, hasApiKey = true) }
            val day = uiState.value.day
            if (day?.review != null && repository.hasInternet()) fetchAi(day)
        }
    }

    companion object {
        fun factory(repository: DayReviewRepository, apiKeyStore: ApiKeyStore) = viewModelFactory {
            initializer { DayReviewViewModel(repository, apiKeyStore) }
        }
    }
}
