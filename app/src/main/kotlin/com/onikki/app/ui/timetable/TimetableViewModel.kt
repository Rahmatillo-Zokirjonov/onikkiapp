@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.onikki.app.ui.timetable

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.local.LocationStore
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class TimetableView(val label: String) { WEEK("Haftalik"), DAY("Kunlik") }

/** New block ([entry] null, starting on [date]) or an existing one. */
data class EntrySheetTarget(val entry: TimetableEntry?, val date: LocalDate)

data class TimetableUiState(
    val entries: List<TimetableEntry> = emptyList(),
    val view: TimetableView = TimetableView.WEEK,
    /** The day shown in Kunlik; its week is shown in Haftalik. */
    val date: LocalDate = LocalDate.now(),
    /** Timed tasks on [date] (Kunlik reja), shown in the day timeline. */
    val tasks: List<Task> = emptyList(),
    val prayers: List<Pair<String, LocalTime>> = emptyList(),
    val sheet: EntrySheetTarget? = null,
    val isLoaded: Boolean = false
)

private data class Ui(val view: TimetableView, val date: LocalDate, val sheet: EntrySheetTarget?)

class TimetableViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as OnIkkiApplication).database
    private val dao = db.timetableDao()
    private val prefs = application.getSharedPreferences("timetable", Context.MODE_PRIVATE)
    private val ui = MutableStateFlow(
        Ui(runCatching { TimetableView.valueOf(prefs.getString("view", null)!!) }.getOrDefault(TimetableView.WEEK), LocalDate.now(), null)
    )

    val uiState: StateFlow<TimetableUiState> = ui.flatMapLatest { u ->
        combine(dao.observeAll(), db.taskDao().observeByDate(u.date), LocationStore(application).city) { entries, tasks, city ->
            TimetableUiState(
                entries = entries,
                view = u.view,
                date = u.date,
                tasks = tasks.filter { it.time != null }.sortedBy { it.time },
                prayers = PrayerTimeCalculator.calculate(u.date, city.latitude, city.longitude, city.utcOffsetHours).asOrderedList(),
                sheet = u.sheet,
                isLoaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    fun setView(view: TimetableView) {
        prefs.edit().putString("view", view.name).apply()
        ui.update { it.copy(view = view) }
    }

    fun setDate(date: LocalDate) = ui.update { it.copy(date = date) }

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

    fun toggleTask(task: Task) {
        viewModelScope.launch { db.taskDao().setCompleted(task.id, !task.isCompleted) }
    }
}
