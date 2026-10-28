@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.timetable

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.HomeworkStatus
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.local.LocationStore
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class TimetableView(val label: String) { WEEK("Haftalik"), DAY("Kunlik"), HOMEWORK("Vazifalar") }

/** Haftalik as a day-by-day list (everything readable) or as a time grid. */
enum class WeekLayout { LIST, GRID }

/** New block ([entry] null, starting on [date]) or an existing one. */
data class EntrySheetTarget(val entry: TimetableEntry?, val date: LocalDate)

/** New homework ([homework] null, optionally for [subject]) or an existing one. */
data class HomeworkSheetTarget(val homework: Homework?, val subject: String? = null)

data class TimetableUiState(
    val entries: List<TimetableEntry> = emptyList(),
    val view: TimetableView = TimetableView.WEEK,
    /** The day shown in Kunlik; its week is shown in Haftalik. */
    val date: LocalDate = LocalDate.now(),
    /** Timed tasks on [date] (Kunlik reja), shown in the day timeline. */
    val tasks: List<Task> = emptyList(),
    val prayers: List<Pair<String, LocalTime>> = emptyList(),
    val sheet: EntrySheetTarget? = null,
    val homework: List<Homework> = emptyList(),
    val homeworkSheet: HomeworkSheetTarget? = null,
    val weekLayout: WeekLayout = WeekLayout.LIST,
    val isLoaded: Boolean = false
)

private data class Ui(
    val view: TimetableView,
    val date: LocalDate,
    val sheet: EntrySheetTarget?,
    val homeworkSheet: HomeworkSheetTarget? = null,
    val weekLayout: WeekLayout = WeekLayout.LIST
)

class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.timetableDao()
    private val prefs = application.getSharedPreferences("timetable", Context.MODE_PRIVATE)
    private val ui = MutableStateFlow(
        Ui(
            runCatching { TimetableView.valueOf(prefs.getString("view", null)!!) }.getOrDefault(TimetableView.WEEK),
            LocalDate.now(),
            null,
            weekLayout = runCatching { WeekLayout.valueOf(prefs.getString("week_layout", null)!!) }.getOrDefault(WeekLayout.LIST)
        )
    )

    val uiState: StateFlow<TimetableUiState> = ui.flatMapLatest { u ->
        combine(dao.observeAll(), db.taskDao().observeByDate(u.date), LocationStore(application).city, db.homeworkDao().observeAll()) { entries, tasks, city, homework ->
            TimetableUiState(
                entries = entries,
                view = u.view,
                date = u.date,
                tasks = tasks.filter { it.time != null }.sortedBy { it.time },
                prayers = PrayerTimeCalculator.calculate(u.date, city.latitude, city.longitude, city.utcOffsetHours).asOrderedList(),
                sheet = u.sheet,
                homework = homework,
                homeworkSheet = u.homeworkSheet,
                weekLayout = u.weekLayout,
                isLoaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    fun setView(view: TimetableView) {
        prefs.edit().putString("view", view.name).apply()
        ui.update { it.copy(view = view) }
    }

    fun setDate(date: LocalDate) = ui.update { it.copy(date = date) }

    fun setWeekLayout(layout: WeekLayout) {
        prefs.edit().putString("week_layout", layout.name).apply()
        ui.update { it.copy(weekLayout = layout) }
    }

    fun shift(days: Long) = ui.update { it.copy(date = it.date.plusDays(days)) }

    fun openDay(date: LocalDate) {
        prefs.edit().putString("view", TimetableView.DAY.name).apply()
        ui.update { it.copy(view = TimetableView.DAY, date = date) }
    }

    fun openNew(date: LocalDate = ui.value.date) = ui.update { it.copy(sheet = EntrySheetTarget(null, date)) }

    fun openEdit(entry: TimetableEntry) = ui.update { it.copy(sheet = EntrySheetTarget(entry, it.date)) }

    fun dismissSheet() = ui.update { it.copy(sheet = null) }

    /** A new weekly block picked for several days becomes one row per day. */
    fun save(existing: TimetableEntry?, drafts: List<TimetableEntry>) {
        if (drafts.isEmpty()) return
        viewModelScope.launch {
            if (existing == null) dao.insertAll(drafts) else dao.update(drafts.first().copy(id = existing.id))
            ui.update { it.copy(sheet = null) }
        }
    }

    fun delete(entry: TimetableEntry) {
        viewModelScope.launch {
            dao.delete(entry)
            ui.update { it.copy(sheet = null) }
        }
    }

    // ------------------------------------------------------------ homework

    fun openNewHomework(subject: String? = null) = ui.update { it.copy(homeworkSheet = HomeworkSheetTarget(null, subject)) }

    fun openHomework(hw: Homework) = ui.update { it.copy(homeworkSheet = HomeworkSheetTarget(hw)) }

    fun dismissHomework() = ui.update { it.copy(homeworkSheet = null) }

    fun saveHomework(existing: Homework?, draft: Homework) {
        if (draft.title.isBlank() || draft.subject.isBlank()) return
        viewModelScope.launch {
            val db = db.homeworkDao()
            if (existing == null) db.insert(draft.copy(id = 0)) else db.update(draft.copy(id = existing.id, createdAt = existing.createdAt))
            ui.update { it.copy(homeworkSheet = null) }
        }
    }

    /** The one-line add: due at the subject's next lesson, reminded the evening before. */
    fun quickHomework(subject: String, text: String) {
        if (subject.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            val entries = db.timetableDao().observeAll().first()
            db.homeworkDao().insert(com.onikki.app.domain.timetable.HomeworkQuick.make(subject, text, entries, java.time.LocalDateTime.now()))
        }
    }

    /** Done or missed both lift the block; pending puts it back. */
    fun setHomeworkStatus(hw: Homework, status: HomeworkStatus) {
        viewModelScope.launch {
            db.homeworkDao().setStatus(hw.id, status, if (status == HomeworkStatus.PENDING) null else System.currentTimeMillis())
            ui.update { it.copy(homeworkSheet = null) }
        }
    }

    fun deleteHomework(hw: Homework) {
        viewModelScope.launch {
            db.homeworkDao().delete(hw)
            ui.update { it.copy(homeworkSheet = null) }
        }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { db.taskDao().setCompleted(task.id, !task.isCompleted) }
    }
}
