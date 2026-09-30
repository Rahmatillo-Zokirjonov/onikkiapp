package com.onikki.app.domain.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onikki.app.MainActivity
import com.onikki.app.R
import com.onikki.app.data.db.entity.Note
import com.onikki.app.data.db.entity.NotePriority
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.receiver.ReminderReceiver
import com.onikki.app.ui.notes.NoteAlertActivity
import com.onikki.app.ui.notes.displayTitle

const val NOTE_SNOOZE_MINUTES = 10L

/**
 * Posts a note's reminder at the loudness its priority asks for:
 * ODDIY → shade only, MUHIM → heads-up banner over the current app,
 * JUDA_MUHIM → alarm-style full-screen alert that keeps ringing until "Ko'rdim" or snooze.
 */
object NoteReminderNotifier {

    fun notificationId(noteId: Long): Int = "note-alert:$noteId".hashCode()

    fun show(context: Context, note: Note) {
        val urgent = note.priority == NotePriority.JUDA_MUHIM

        // An urgent alert must reach the user even with notifications muted for the app, so the
        // over-other-apps screen is opened directly when that permission exists.
        if (urgent && PermissionChecker.canDrawOverlays(context)) {
            runCatching { context.startActivity(NoteAlertActivity.intent(context, note.id)) }
        }
        if (!PermissionChecker.hasNotificationPermission(context)) return

        val channel = when (note.priority) {
            NotePriority.ODDIY -> ReminderChannel.NOTES
            NotePriority.MUHIM -> ReminderChannel.NOTES_IMPORTANT
            NotePriority.JUDA_MUHIM -> ReminderChannel.NOTES_URGENT
        }
        // A locked note never shows its text outside the app (lock screen, notification shade).
        val body = if (note.locked) "🔒 Maxfiy qayd — ochish uchun bosing" else com.onikki.app.domain.notes.NoteFormat.plain(note.content).trim().ifBlank { "Qayd eslatmasi" }
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (note.locked) note.title.ifBlank { "Maxfiy qayd" } else note.displayTitle())
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(600)))
            .setContentIntent(openNote(context, note.id))
            .setAutoCancel(true)
            .setPriority(
                if (note.priority == NotePriority.ODDIY) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)

        if (note.priority != NotePriority.ODDIY) {
            builder.addAction(0, "$NOTE_SNOOZE_MINUTES daqiqadan keyin", noteAction(context, ReminderReceiver.ACTION_NOTE_SNOOZE, note.id))
        }
        if (urgent) {
            builder.addAction(0, "Ko'rdim", noteAction(context, ReminderReceiver.ACTION_NOTE_DISMISS, note.id))
                .setOngoing(true)
                .setAutoCancel(false)
                // Shown full-screen when the phone is locked/idle; as a persistent banner while in use.
                .setFullScreenIntent(
                    PendingIntent.getActivity(
                        context,
                        notificationId(note.id),
                        NoteAlertActivity.intent(context, note.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ),
                    true
                )
        }

        val notification = builder.build()
        // Keep ringing (like an alarm) until the user reacts.
        if (urgent) notification.flags = notification.flags or Notification.FLAG_INSISTENT

        @Suppress("MissingPermission") // checked above
        NotificationManagerCompat.from(context).notify(notificationId(note.id), notification)
    }

    fun cancel(context: Context, noteId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(noteId))
    }

    fun openNote(context: Context, noteId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(noteId),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun noteAction(context: Context, action: String, noteId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        "$action:$noteId".hashCode(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .putExtra(ReminderReceiver.EXTRA_REF_ID, noteId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Moves the note's reminder to [NOTE_SNOOZE_MINUTES] from now; the app-wide sync re-plans the alarm. */
suspend fun snoozeNoteReminder(context: Context, noteId: Long) {
    val db = (context.applicationContext as com.onikki.app.OnIkkiApplication).database
    val at = java.time.LocalDateTime.now().plusMinutes(NOTE_SNOOZE_MINUTES).withSecond(0).withNano(0)
    db.noteDao().setRemindAt(noteId, at)
    // The receiver/activity may run while the app process was dead (no sync collector yet) — plan directly too.
    ReminderScheduler(context).resync()
}
