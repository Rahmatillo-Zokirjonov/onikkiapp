package com.onikki.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.onikki.app.data.local.BankSmsSettingsStore
import com.onikki.app.domain.sms.BankSmsImporter
import com.onikki.app.domain.sms.SmsImportResult
import com.onikki.app.domain.sms.SmsNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Incoming SMS → bank transaction. Parsing happens on the phone only; the SMS text never leaves it.
 * Does nothing unless the user turned "Bank SMS" on in Moliya.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return
        // A long SMS arrives in parts; join them per sender before parsing.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
            .mapValues { (_, parts) -> parts.joinToString("") { it.messageBody.orEmpty() } to parts.first().timestampMillis }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!BankSmsSettingsStore(context).settings.first().enabled) return@launch
                val importer = BankSmsImporter(context)
                bySender.forEach { (sender, content) ->
                    val (body, time) = content
                    when (val result = importer.import(sender, body, time)) {
                        is SmsImportResult.Imported -> SmsNotifier.showImported(context, result)
                        is SmsImportResult.Cancelled -> SmsNotifier.showCancelled(context, result)
                        else -> Unit
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
