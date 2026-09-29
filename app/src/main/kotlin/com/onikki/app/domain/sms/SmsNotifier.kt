package com.onikki.app.domain.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.onikki.app.MainActivity
import com.onikki.app.R
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.domain.notifications.ReminderChannel
import com.onikki.app.domain.permissions.PermissionChecker
import com.onikki.app.receiver.ReminderReceiver
import com.onikki.app.ui.finance.accountLabel
import com.onikki.app.ui.util.formatSom
import java.util.Locale

/** "💳 −125 000 so'm · Humo" with an inline "Izoh yozish" reply, so the note is written without opening the app. */
object SmsNotifier {
    const val KEY_NOTE = "sms_note"

    fun notificationId(transactionId: Long): Int = "sms-tx:$transactionId".hashCode()

    fun showImported(context: Context, result: SmsImportResult.Imported) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val tx = result.transaction
        val sign = if (tx.type == TransactionType.KIRIM) "+" else "−"
        val foreign = result.parsed.currency.takeIf { it != "UZS" }?.let {
            " (" + String.format(Locale.US, "%.2f", result.parsed.amount) + " $it)"
        }.orEmpty()
        val title = "💳 $sign${formatSom(tx.amount)} so'm$foreign · ${accountLabel(result.account)}"
        val text = listOfNotNull(tx.merchant, tx.category.takeIf { it != "Boshqa" && it != "Kirim" }).joinToString(" · ")
            .ifBlank { if (tx.type == TransactionType.KIRIM) "Kartaga tushum" else "Karta orqali to'lov" }

        val reply = RemoteInput.Builder(KEY_NOTE).setLabel("Izoh (masalan: suv oldim)").build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            notificationId(tx.id),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_SMS_NOTE)
                .putExtra(ReminderReceiver.EXTRA_REF_ID, tx.id),
            // RemoteInput must be able to fill in the reply text → mutable on Android 12+.
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        val action = NotificationCompat.Action.Builder(0, "Izoh yozish", replyIntent)
            .addRemoteInput(reply)
            .setAllowGeneratedReplies(false)
            .build()

        val notification = NotificationCompat.Builder(context, ReminderChannel.FINANCE.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .addAction(action)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId(tx.id), notification)
    }

    fun showCancelled(context: Context, result: SmsImportResult.Cancelled) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val removed = result.removed ?: return
        NotificationManagerCompat.from(context).cancel(notificationId(removed.id))
        val notification = NotificationCompat.Builder(context, ReminderChannel.FINANCE.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("To'lov bekor qilindi")
            .setContentText("${removed.merchant ?: "To'lov"} · ${formatSom(removed.amount)} so'm — hisobdan olib tashlandi")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify("sms-cancel:${removed.id}".hashCode(), notification)
    }

    /** After an inline reply Android keeps a spinner until the notification is updated — replace it with a confirmation. */
    fun showNoteSaved(context: Context, transactionId: Long, note: String) {
        if (!PermissionChecker.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, ReminderChannel.FINANCE.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Izoh saqlandi")
            .setContentText(note)
            .setTimeoutAfter(4_000)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(notificationId(transactionId), notification)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE
    )
}
