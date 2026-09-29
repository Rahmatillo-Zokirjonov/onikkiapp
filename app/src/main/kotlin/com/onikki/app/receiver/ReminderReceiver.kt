package com.onikki.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.domain.notifications.ReminderChannel
import com.onikki.app.domain.notifications.ReminderNotifier
import com.onikki.app.domain.notifications.ReminderScheduler
import com.onikki.app.domain.notifications.ReminderType
import com.onikki.app.ui.util.formatSom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Fires every planned reminder. Text is built from the database at fire time (not when scheduled), so an
 * edited/completed/deleted task or an already-reviewed day never produces a stale notification.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> handleReminder(context, intent)
                    ACTION_TASK_DONE -> handleTaskDone(context, intent)
                }
                ReminderScheduler(context).resync()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleReminder(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { ReminderType.valueOf(it) }.getOrNull() } ?: return
        val refId = intent.getLongExtra(EXTRA_REF_ID, 0)
        val db = (context.applicationContext as OnIkkiApplication).database
        val today = LocalDate.now()

        when (type) {
            ReminderType.REFRESH -> Unit

            ReminderType.TASK -> {
                val task = db.taskDao().findById(refId) ?: return
                val time = task.time ?: return
                if (task.isCompleted) return
                val minutesLeft = minutesUntil(Duration.between(LocalDateTime.now(), task.date.atTime(time)))
                val whenText = if (minutesLeft <= 1) "Hozir" else "$minutesLeft daqiqadan keyin"
                val category = if (task.category == TaskCategory.ISH) " · Ish" else ""
                ReminderNotifier.show(
                    context, key, ReminderChannel.TASKS,
                    title = task.title,
                    text = "$whenText, ${hhmm(time)}$category",
                    taskIdForDoneAction = task.id
                )
            }

            ReminderType.PRAYER -> {
                val name = intent.getStringExtra(EXTRA_LABEL).orEmpty()
                val time = intent.getStringExtra(EXTRA_EVENT_TIME)?.let(LocalTime::parse) ?: return
                val minutesLeft = minutesUntil(Duration.between(LocalTime.now(), time))
                ReminderNotifier.show(
                    context, key, ReminderChannel.PRAYER,
                    title = if (minutesLeft <= 1) "$name namozi vaqti" else "$name namoziga $minutesLeft daqiqa qoldi",
                    text = "$name · ${hhmm(time)}"
                )
            }

            ReminderType.HABITS -> {
                val habits = db.habitDao().observeAll().first()
                val doneIds = db.habitLogDao().observeByDate(today).first().filter { it.isDone }.map { it.habitId }.toSet()
                val pendingHabits = habits.filter { it.isActiveOn(today) && it.id !in doneIds }
                if (pendingHabits.isEmpty()) return
                ReminderNotifier.show(
                    context, key, ReminderChannel.HABITS,
                    title = "Bugun ${pendingHabits.size} ta odat belgilanmagan",
                    text = pendingHabits.joinToString(", ") { it.name }
                )
            }

            ReminderType.DAY_REVIEW -> {
                if (db.dailyReviewDao().findByDate(today) != null) return
                val tasks = db.taskDao().observeByDate(today).first()
                val progress = if (tasks.isEmpty()) "" else " — ${tasks.count { it.isCompleted }}/${tasks.size} vazifa bajarildi"
                ReminderNotifier.show(
                    context, key, ReminderChannel.DAY_REVIEW,
                    title = "Kun yakuni",
                    text = "Bugungi kunni yakunlang$progress"
                )
            }

            ReminderType.DEBT -> {
                val debt = db.debtDao().findById(refId) ?: return
                if (debt.status != DebtStatus.OCHIQ) return
                val direction = if (debt.direction == DebtDirection.MENGA_QARZDOR) "sizga qaytarishi kerak" else "siz qaytarishingiz kerak"
                ReminderNotifier.show(
                    context, key, ReminderChannel.FINANCE,
                    title = "Qarz muddati bugun",
                    text = "${debt.personName} — ${formatSom(debt.amount)} so'm ($direction)"
                )
            }
        }
    }

    private suspend fun handleTaskDone(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_REF_ID, 0)
        val db = (context.applicationContext as OnIkkiApplication).database
        db.taskDao().setCompleted(taskId, true)
        intent.getStringExtra(EXTRA_KEY)?.let { ReminderNotifier.cancel(context, it) }
    }

    private fun hhmm(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

    /** Rounds up: an alarm set "10 min before" fires a few seconds late, and should still read "10", not "9". */
    private fun minutesUntil(remaining: Duration): Long = ((remaining.seconds + 59) / 60).coerceAtLeast(0)

    companion object {
        const val ACTION_REMIND = "com.onikki.app.action.REMIND"
        const val ACTION_TASK_DONE = "com.onikki.app.action.TASK_DONE"
        const val EXTRA_KEY = "key"
        const val EXTRA_TYPE = "type"
        const val EXTRA_REF_ID = "ref_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_EVENT_TIME = "event_time"
    }
}
