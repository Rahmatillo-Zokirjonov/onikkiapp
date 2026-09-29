package com.onikki.app.domain.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onikki.app.MainActivity
import com.onikki.app.R
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.receiver.ReminderReceiver

enum class ReminderChannel(
    val id: String,
    val title: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_HIGH
) {
    TASKS("tasks", "Vazifalar", "Kunlik reja vazifalari vaqtidan oldin"),
    PRAYER("prayer", "Namoz vaqtlari", "Har namozdan oldin eslatma"),
    HABITS("habits", "Odatlar", "Kechqurun belgilanmagan odatlar"),
    DAY_REVIEW("day_review", "Kun yakuni", "Kunni yakunlash eslatmasi"),
    FINANCE("finance", "Moliya", "Qarz qaytarish muddati"),
    // One channel per note priority: the level is exactly the channel's importance, and the user can
    // still tune each level separately in Android's own notification settings.
    NOTES("notes", "Qaydlar — oddiy", "Ovoz bilan, lekin ekranga chiqmaydi", NotificationManager.IMPORTANCE_DEFAULT),
    NOTES_IMPORTANT("notes_important", "Qaydlar — muhim", "Ochiq ilova ustida banner bo'lib chiqadi"),
    NOTES_URGENT("notes_urgent", "Qaydlar — juda muhim", "Budilnikdek: to'liq ekran, to'xtatilguncha jiringlaydi")
}

/** Builds and posts the actual notifications; channels are created once at app start. */
object ReminderNotifier {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderChannel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.title, channel.importance).apply {
                    description = channel.description
                    if (channel == ReminderChannel.NOTES_URGENT) {
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                        )
                        enableVibration(true)
                    }
                }
            )
        }
    }

    fun show(
        context: Context,
        key: String,
        channel: ReminderChannel,
        title: String,
        text: String,
        taskIdForDoneAction: Long? = null,
        plannedIdForPaidAction: Long? = null,
        plannedDoneLabel: String = "To'landi"
    ) {
        // Silently skip when the user hasn't granted/has revoked notifications — nothing else to do offline.
        if (!PermissionChecker.hasNotificationPermission(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (taskIdForDoneAction != null) {
            val done = PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_TASK_DONE)
                    .putExtra(ReminderReceiver.EXTRA_REF_ID, taskIdForDoneAction)
                    .putExtra(ReminderReceiver.EXTRA_KEY, key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Bajarildi", done)
        }

        if (plannedIdForPaidAction != null) {
            val paid = PendingIntent.getBroadcast(
                context,
                "paid:$key".hashCode(),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_PLANNED_PAID)
                    .putExtra(ReminderReceiver.EXTRA_REF_ID, plannedIdForPaidAction)
                    .putExtra(ReminderReceiver.EXTRA_KEY, key),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, plannedDoneLabel, paid)
        }

        @Suppress("MissingPermission") // checked above via PermissionChecker
        NotificationManagerCompat.from(context).notify(key.hashCode(), builder.build())
    }

    fun cancel(context: Context, key: String) {
        NotificationManagerCompat.from(context).cancel(key.hashCode())
    }
}
