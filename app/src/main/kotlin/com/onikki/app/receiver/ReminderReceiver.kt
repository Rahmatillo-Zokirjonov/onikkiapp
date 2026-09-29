package com.onikki.app.receiver

import com.onikki.app.data.repository.payPlanned
import com.onikki.app.data.db.entity.DEFAULT_CASH_ACCOUNT_ID
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.domain.notifications.NoteReminderNotifier
import com.onikki.app.domain.notifications.ReminderChannel
import com.onikki.app.domain.notifications.snoozeNoteReminder
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
                    ACTION_PLANNED_PAID -> handlePlannedPaid(context, intent)
                    ACTION_SMS_NOTE -> handleSmsNote(context, intent)
                    ACTION_NOTE_SNOOZE -> snoozeNote(context, intent.getLongExtra(EXTRA_REF_ID, 0))
                    ACTION_NOTE_DISMISS -> NoteReminderNotifier.cancel(context, intent.getLongExtra(EXTRA_REF_ID, 0))
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

            ReminderType.NOTE -> {
                val note = db.noteDao().findById(refId) ?: return
                // The key carries the time it was planned for; an edited/cleared reminder must not fire.
                if (note.remindAt == null || key != "note:${note.id}:${note.remindAt}") return
                NoteReminderNotifier.show(context, note)
            }

            ReminderType.PLANNED_EXPENSE -> {
                val expense = db.plannedExpenseDao().findById(refId) ?: return
                if (expense.paidDate != null || !expense.remindEnabled) return
                // Key = planned:<id>:<dueDate>:<early|due>; a paid/moved expense no longer matches.
                if (!key.startsWith("planned:${expense.id}:${expense.dueDate}:")) return
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, expense.dueDate)
                val whenText = when {
                    days <= 0L -> "bugun"
                    days == 1L -> "ertaga"
                    else -> "$days kundan keyin"
                }
                val wallet = expense.accountId?.let { db.accountDao().findById(it)?.name }?.let { " · $it" } ?: ""
                ReminderNotifier.show(
                    context, key, ReminderChannel.FINANCE,
                    title = if (expense.isIncome) "${expense.title} — $whenText olinishi kerak" else "${expense.title} — to'lov $whenText",
                    text = "${formatSom(expense.amount)} so'm$wallet",
                    plannedIdForPaidAction = expense.id,
                    plannedDoneLabel = expense.doneLabel
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

    /** Inline reply on a bank-SMS notification: the typed text becomes the transaction's note. */
    private suspend fun handleSmsNote(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_REF_ID, 0)
        val note = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(com.onikki.app.domain.sms.SmsNotifier.KEY_NOTE)?.toString()?.trim()
        if (id == 0L || note.isNullOrBlank()) return
        (context.applicationContext as OnIkkiApplication).database.smsImportDao().setNote(id, note)
        com.onikki.app.domain.sms.SmsNotifier.showNoteSaved(context, id, note)
    }

    /** "To'landi" straight from the notification: pays the planned amount from its wallet (or cash). */
    private suspend fun handlePlannedPaid(context: Context, intent: Intent) {
        val db = (context.applicationContext as OnIkkiApplication).database
        val expense = db.plannedExpenseDao().findById(intent.getLongExtra(EXTRA_REF_ID, 0)) ?: return
        if (expense.paidDate == null) {
            val account = expense.accountId?.let { db.accountDao().findById(it) }
                ?: db.accountDao().findById(DEFAULT_CASH_ACCOUNT_ID)
                ?: return
            payPlanned(db.transactionDao(), db.plannedExpenseDao(), expense, account, expense.amount, LocalDate.now())
        }
        intent.getStringExtra(EXTRA_KEY)?.let { ReminderNotifier.cancel(context, it) }
    }

    private suspend fun handleTaskDone(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_REF_ID, 0)
        val db = (context.applicationContext as OnIkkiApplication).database
        db.taskDao().setCompleted(taskId, true)
        intent.getStringExtra(EXTRA_KEY)?.let { ReminderNotifier.cancel(context, it) }
    }

    private suspend fun snoozeNote(context: Context, noteId: Long) {
        NoteReminderNotifier.cancel(context, noteId)
        snoozeNoteReminder(context, noteId)
    }

    private fun hhmm(time: LocalTime) = "%02d:%02d".format(time.hour, time.minute)

    /** Rounds up: an alarm set "10 min before" fires a few seconds late, and should still read "10", not "9". */
    private fun minutesUntil(remaining: Duration): Long = ((remaining.seconds + 59) / 60).coerceAtLeast(0)

    companion object {
        const val ACTION_REMIND = "com.onikki.app.action.REMIND"
        const val ACTION_TASK_DONE = "com.onikki.app.action.TASK_DONE"
        const val ACTION_SMS_NOTE = "com.onikki.app.action.SMS_NOTE"
        const val ACTION_PLANNED_PAID = "com.onikki.app.action.PLANNED_PAID"
        const val ACTION_NOTE_SNOOZE = "com.onikki.app.action.NOTE_SNOOZE"
        const val ACTION_NOTE_DISMISS = "com.onikki.app.action.NOTE_DISMISS"
        const val EXTRA_KEY = "key"
        const val EXTRA_TYPE = "type"
        const val EXTRA_REF_ID = "ref_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_EVENT_TIME = "event_time"
    }
}
