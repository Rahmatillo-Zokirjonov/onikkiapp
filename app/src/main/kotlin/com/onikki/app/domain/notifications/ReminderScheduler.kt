package com.onikki.app.domain.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.NotificationSettingsStore
import com.onikki.app.domain.prayer.PrayerTimeCalculator
import com.onikki.app.receiver.ReminderReceiver
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId

private const val PREFS = "onikki_alarms"
private const val KEY_SCHEDULED = "scheduled_keys"
private const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L

/** Turns a [ReminderPlanner] plan into real AlarmManager alarms, cancelling ones that dropped out of the plan. */
class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Exact alarms need a user grant on Android 12+; without it reminders still fire, just possibly late. */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Reads the current data/settings and re-plans every alarm. Safe to call as often as needed. */
    suspend fun resync(now: LocalDateTime = LocalDateTime.now()) {
        val app = context.applicationContext as OnIkkiApplication
        val db = app.database
        val settings = NotificationSettingsStore(context).settings.first()
        val city = LocationStore(context).city.first()
        val today = now.toLocalDate()
        val tasks = db.taskDao().observeBetween(today, today.plusDays(2)).first()
        val debts = db.debtDao().observeAll().first()
        val plan = ReminderPlanner.plan(now, settings, tasks, debts) { day ->
            PrayerTimeCalculator.calculate(day, city.latitude, city.longitude, city.utcOffsetHours)
        }
        apply(plan)
    }

    private fun apply(plan: List<PlannedReminder>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val current = plan.map { it.key }.toSet()
        (previous - current).forEach { key -> alarmManager.cancel(pendingIntent(key, null)) }
        plan.forEach(::schedule)
        prefs.edit().putStringSet(KEY_SCHEDULED, current).apply()
    }

    private fun schedule(reminder: PlannedReminder) {
        val triggerAt = reminder.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendingIntent(reminder.key, reminder)
        when {
            reminder.type == ReminderType.REFRESH -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            canScheduleExact() -> alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            // Fallback when exact alarms were revoked: a bounded 10-minute window (the platform minimum)
            // instead of setAndAllowWhileIdle, which the system may defer by up to an hour.
            else -> alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, FALLBACK_WINDOW_MS, pi)
        }
    }

    /** The data URI makes each key a distinct PendingIntent, so hash collisions can't overwrite each other. */
    private fun pendingIntent(key: String, reminder: PlannedReminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .setData(Uri.parse("onikki://reminder/${Uri.encode(key)}"))
        if (reminder != null) {
            intent.putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
                .putExtra(ReminderReceiver.EXTRA_TYPE, reminder.type.name)
                .putExtra(ReminderReceiver.EXTRA_REF_ID, reminder.refId)
                .putExtra(ReminderReceiver.EXTRA_LABEL, reminder.label)
                .putExtra(ReminderReceiver.EXTRA_EVENT_TIME, reminder.eventTime?.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
