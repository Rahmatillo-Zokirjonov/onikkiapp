package com.onikki.app.domain.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.onikki.app.MainActivity
import com.onikki.app.R
import com.onikki.app.data.db.entity.Homework
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.receiver.ReminderReceiver

/**
 * Homework without opening the app: after a lesson "vazifa berildimi?" takes the homework as an inline
 * reply, and homework reminders carry "✓ Bajardim".
 */
object HomeworkNotifier {
    const val KEY_TEXT = "homework_text"

    private fun askId(entryId: Long) = "hw-ask:$entryId".hashCode()
    fun homeworkId(id: Long) = "hw:$id".hashCode()

    /** At a lesson's end: write the homework right here (due at the next lesson of the subject). */
    fun askAfterLesson(context: Context, entry: TimetableEntry, nextLessonText: String?) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val reply = RemoteInput.Builder(KEY_TEXT).setLabel("Vazifa (masalan: 12-mashq)").build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            askId(entry.id),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_HOMEWORK_ADD)
                .putExtra(ReminderReceiver.EXTRA_REF_ID, entry.id),
            // RemoteInput fills in the reply text → mutable on Android 12+.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val write = NotificationCompat.Action.Builder(0, "Vazifa yozish", replyIntent).addRemoteInput(reply).setAllowGeneratedReplies(false).build()
        val notification = NotificationCompat.Builder(context, ReminderChannel.HOMEWORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📝 ${entry.title}: vazifa berildimi?")
            .setContentText(nextLessonText?.let { "Yozing — $it gacha deb saqlanadi" } ?: "Yozing — keyingi darsgacha deb saqlanadi")
            .setContentIntent(openHomework(context))
            .setAutoCancel(true)
            .setTimeoutAfter(6 * 60 * 60 * 1000L)
            .addAction(write)
            .addAction(0, "Berilmadi", dismissIntent(context, askId(entry.id)))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(askId(entry.id), notification)
    }

    /** After the inline reply: confirm, so the reply spinner goes away. */
    fun saved(context: Context, entryId: Long, homework: Homework, dueText: String) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val n = NotificationCompat.Builder(context, ReminderChannel.HOMEWORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✓ Vazifa saqlandi")
            .setContentText("${homework.subject}: ${homework.title} · $dueText gacha")
            .setContentIntent(openHomework(context))
            .setTimeoutAfter(5_000)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(askId(entryId), n)
    }

    /** A homework reminder with "✓ Bajardim" (marks it done, lifts any block). */
    fun reminder(context: Context, key: String, homework: Homework, title: String, text: String) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val done = PendingIntent.getBroadcast(
            context,
            "hw-done:${homework.id}".hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_HOMEWORK_DONE).putExtra(ReminderReceiver.EXTRA_REF_ID, homework.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, ReminderChannel.HOMEWORK.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openHomework(context))
            .setAutoCancel(true)
            .addAction(0, "✓ Bajardim", done)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(homeworkId(homework.id), n)
    }

    fun cancel(context: Context, homeworkId: Long) = NotificationManagerCompat.from(context).cancel(homeworkId(homeworkId))

    private fun dismissIntent(context: Context, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        "hw-dismiss:$notificationId".hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DISMISS).putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun openHomework(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        "open-homework".hashCode(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_HOMEWORK, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
