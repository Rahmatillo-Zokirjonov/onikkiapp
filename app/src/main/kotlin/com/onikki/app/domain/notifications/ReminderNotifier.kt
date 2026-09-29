package com.onikki.app.domain.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onikki.app.MainActivity
import com.onikki.app.R
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.receiver.ReminderReceiver

enum class ReminderChannel(val id: String, val title: String, val description: String) {
    TASKS("tasks", "Vazifalar", "Kunlik reja vazifalari vaqtidan oldin"),
    PRAYER("prayer", "Namoz vaqtlari", "Har namozdan oldin eslatma"),
    HABITS("habits", "Odatlar", "Kechqurun belgilanmagan odatlar"),
    DAY_REVIEW("day_review", "Kun yakuni", "Kunni yakunlash eslatmasi"),
    FINANCE("finance", "Moliya", "Qarz qaytarish muddati")
}

/** Builds and posts the actual notifications; channels are created once at app start. */
object ReminderNotifier {

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderChannel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.title, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = channel.description
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
        taskIdForDoneAction: Long? = null
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

        @Suppress("MissingPermission") // checked above via PermissionChecker
        NotificationManagerCompat.from(context).notify(key.hashCode(), builder.build())
    }

    fun cancel(context: Context, key: String) {
        NotificationManagerCompat.from(context).cancel(key.hashCode())
    }
}
