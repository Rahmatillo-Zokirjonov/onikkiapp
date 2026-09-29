package com.onikki.app

import android.app.Application
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.NotificationSettingsStore
import com.onikki.app.domain.notifications.ReminderNotifier
import com.onikki.app.domain.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate

class OnIkkiApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier.createChannels(this)
        keepRemindersInSync()
    }

    /**
     * Any change that affects reminders — a task added/edited/done, a debt, a note reminder, the reminder settings or the
     * city (prayer times) — re-plans the alarms, so no screen has to remember to do it.
     */
    @OptIn(FlowPreview::class)
    private fun keepRemindersInSync() {
        val scheduler = ReminderScheduler(this)
        val today = LocalDate.now()
        appScope.launch {
            combine(
                // Room re-emits on any write to these tables, so the date range only needs to exist, not stay current.
                database.taskDao().observeBetween(today, today.plusDays(2)),
                database.debtDao().observeAll(),
                database.noteDao().observeWithReminder(),
                NotificationSettingsStore(this@OnIkkiApplication).settings,
                LocationStore(this@OnIkkiApplication).city
            ) { _, _, _, _, _ -> }
                .debounce(500)
                .collect { scheduler.resync() }
        }
    }
}
