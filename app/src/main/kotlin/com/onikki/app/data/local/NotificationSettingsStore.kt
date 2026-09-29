package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.notificationDataStore by preferencesDataStore(name = "onikki_notifications")

private val KEY_TASKS_ON = booleanPreferencesKey("tasks_on")
private val KEY_TASK_LEAD = intPreferencesKey("task_lead_min")
private val KEY_PRAYER_ON = booleanPreferencesKey("prayer_on")
private val KEY_PRAYER_LEAD = intPreferencesKey("prayer_lead_min")
private val KEY_HABITS_ON = booleanPreferencesKey("habits_on")
private val KEY_HABITS_TIME = intPreferencesKey("habits_time_min")
private val KEY_REVIEW_ON = booleanPreferencesKey("review_on")
private val KEY_REVIEW_TIME = intPreferencesKey("review_time_min")
private val KEY_DEBTS_ON = booleanPreferencesKey("debts_on")

/** Local reminder preferences (TZ 3.3). Times are stored as minutes after midnight. */
data class NotificationSettings(
    val tasksEnabled: Boolean = true,
    val taskLeadMinutes: Int = 10,
    val prayerEnabled: Boolean = true,
    val prayerLeadMinutes: Int = 10,
    val habitsEnabled: Boolean = true,
    val habitsTime: LocalTime = LocalTime.of(21, 0),
    val dayReviewEnabled: Boolean = true,
    val dayReviewTime: LocalTime = LocalTime.of(21, 30),
    val debtsEnabled: Boolean = true
)

/** Choices offered for "how long before" pickers. */
val LEAD_MINUTE_OPTIONS = listOf(0, 5, 10, 15, 30)

private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
private fun Int.toLocalTime(): LocalTime = LocalTime.of((this / 60) % 24, this % 60)

class NotificationSettingsStore(private val context: Context) {

    val settings: Flow<NotificationSettings> = context.notificationDataStore.data.map { readFrom(it) }

    suspend fun update(transform: (NotificationSettings) -> NotificationSettings) {
        context.notificationDataStore.edit { prefs ->
            val current = readFrom(prefs)
            val next = transform(current)
            prefs[KEY_TASKS_ON] = next.tasksEnabled
            prefs[KEY_TASK_LEAD] = next.taskLeadMinutes
            prefs[KEY_PRAYER_ON] = next.prayerEnabled
            prefs[KEY_PRAYER_LEAD] = next.prayerLeadMinutes
            prefs[KEY_HABITS_ON] = next.habitsEnabled
            prefs[KEY_HABITS_TIME] = next.habitsTime.toMinuteOfDay()
            prefs[KEY_REVIEW_ON] = next.dayReviewEnabled
            prefs[KEY_REVIEW_TIME] = next.dayReviewTime.toMinuteOfDay()
            prefs[KEY_DEBTS_ON] = next.debtsEnabled
        }
    }

    private fun readFrom(prefs: Preferences): NotificationSettings {
        val d = NotificationSettings()
        return NotificationSettings(
            tasksEnabled = prefs[KEY_TASKS_ON] ?: d.tasksEnabled,
            taskLeadMinutes = prefs[KEY_TASK_LEAD] ?: d.taskLeadMinutes,
            prayerEnabled = prefs[KEY_PRAYER_ON] ?: d.prayerEnabled,
            prayerLeadMinutes = prefs[KEY_PRAYER_LEAD] ?: d.prayerLeadMinutes,
            habitsEnabled = prefs[KEY_HABITS_ON] ?: d.habitsEnabled,
            habitsTime = prefs[KEY_HABITS_TIME]?.toLocalTime() ?: d.habitsTime,
            dayReviewEnabled = prefs[KEY_REVIEW_ON] ?: d.dayReviewEnabled,
            dayReviewTime = prefs[KEY_REVIEW_TIME]?.toLocalTime() ?: d.dayReviewTime,
            debtsEnabled = prefs[KEY_DEBTS_ON] ?: d.debtsEnabled
        )
    }
}
