package com.onikki.app.domain.sms

import android.content.Context
import android.provider.Telephony
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.Account
import com.onikki.app.data.db.entity.AccountKind
import com.onikki.app.data.db.entity.DEFAULT_CARD_ACCOUNT_ID
import com.onikki.app.data.db.entity.SmsImport
import com.onikki.app.data.db.entity.SmsImportStatus
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.local.BankSmsSettings
import com.onikki.app.data.local.BankSmsSettingsStore
import com.onikki.app.data.repository.toWallet
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

sealed class SmsImportResult {
    data class Imported(val transaction: Transaction, val account: Account, val parsed: ParsedBankSms) : SmsImportResult()
    data class Cancelled(val removed: Transaction?) : SmsImportResult()
    data object Duplicate : SmsImportResult()
    data object Unrecognized : SmsImportResult()
    data object NotBank : SmsImportResult()
}

/**
 * Turns one bank SMS into a transaction. Shared by the live SMS receiver, the "import the last 30 days"
 * button and the paste-to-test box, so all three behave identically (and dedupe against each other).
 */
class BankSmsImporter(private val context: Context) {

    private val db = (context.applicationContext as OnIkkiApplication).database

    /** Card-ish + money-ish: worth logging as "aniqlanmagan" even when the parser gave up. */
    private val looksLikeBank = Regex("""(\*{2,}\d{2,4}|\*\d{2,4}\b|karta|card|карта).*(UZS|USD|so'm|sum|сум)|(UZS|USD).*(\*\d{2,4})""", RegexOption.IGNORE_CASE)

    suspend fun import(sender: String, body: String, receivedAt: Long): SmsImportResult {
        val hash = hashOf(sender, body, receivedAt)
        val dao = db.smsImportDao()
        if (dao.exists(hash)) return SmsImportResult.Duplicate

        val parsed = BankSmsParser.parse(body)
        if (parsed == null) {
            if (!looksLikeBank.containsMatchIn(body)) return SmsImportResult.NotBank
            dao.insert(SmsImport(hash, sender, body, receivedAt, SmsImportStatus.UNRECOGNIZED))
            return SmsImportResult.Unrecognized
        }

        val settings = BankSmsSettingsStore(context).settings.first()
        val account = matchAccount(parsed.cardDigits)
        val amount = toSom(parsed, settings)
        val date = parsed.time?.toLocalDate()
            ?: Instant.ofEpochMilli(receivedAt).atZone(ZoneId.systemDefault()).toLocalDate()

        if (parsed.isCancellation) {
            val removed = findOriginal(parsed, account, amount, date)
            removed?.let { db.transactionDao().delete(it) }
            dao.insert(SmsImport(hash, sender, body, receivedAt, SmsImportStatus.DISMISSED, removed?.id))
            return SmsImportResult.Cancelled(removed)
        }

        val merchant = parsed.merchant?.trim()
        val category = merchant?.let { dao.categoryFor(it.uppercase()) }
            ?: if (parsed.type == TransactionType.KIRIM) "Kirim" else "Boshqa"
        val transaction = Transaction(
            amount = amount,
            type = parsed.type,
            category = category,
            wallet = account.kind.toWallet(),
            date = date,
            note = null,
            accountId = account.id,
            merchant = merchant,
            fromSms = true
        )
        val id = db.transactionDao().insert(transaction)
        dao.insert(SmsImport(hash, sender, body, receivedAt, SmsImportStatus.IMPORTED, id))
        return SmsImportResult.Imported(transaction.copy(id = id), account, parsed)
    }

    /**
     * Past SMS already on the phone (needs READ_SMS). Dedupe makes it safe to run repeatedly; returns
     * how many transactions were created.
     */
    suspend fun importInbox(days: Long): Int {
        val since = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
        val rows = mutableListOf<Triple<String, String, Long>>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) rows += Triple(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2))
        }
        return rows.count { (sender, body, date) -> import(sender, body, date) is SmsImportResult.Imported }
    }

    /**
     * The wallet whose saved digits match the SMS ("*60" vs "1260", "***0365" vs "0365"). Unknown card →
     * the first card wallet, so money still lands somewhere sensible and the user can move it.
     */
    private suspend fun matchAccount(digits: String?): Account {
        val accounts = db.accountDao().observeAll().first()
        if (digits != null) {
            accounts.firstOrNull { a ->
                val saved = a.lastDigits ?: return@firstOrNull false
                saved.endsWith(digits) || digits.endsWith(saved)
            }?.let { return it }
        }
        return accounts.firstOrNull { it.kind == AccountKind.KARTA }
            ?: accounts.firstOrNull { it.id == DEFAULT_CARD_ACCOUNT_ID }
            ?: accounts.first()
    }

    /** A cancellation removes the matching SMS-created operation from the last few days. */
    private suspend fun findOriginal(parsed: ParsedBankSms, account: Account, amount: Long, date: LocalDate): Transaction? {
        val candidates = db.transactionDao().observeSince(date.minusDays(3)).first()
        return candidates
            .filter { it.fromSms && it.accountId == account.id && abs(it.amount - amount) <= 1 && it.type == parsed.type }
            .filter { parsed.merchant == null || it.merchant.equals(parsed.merchant, ignoreCase = true) }
            .minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }
    }

    private fun toSom(parsed: ParsedBankSms, settings: BankSmsSettings): Long = when (parsed.currency) {
        "USD" -> (parsed.amount * settings.usdRate).roundToLong()
        "EUR" -> (parsed.amount * settings.eurRate).roundToLong()
        else -> parsed.amount.roundToLong()
    }

    /** Same SMS delivered twice (or read again from the inbox) → same hash; minute precision absorbs clock drift. */
    private fun hashOf(sender: String, body: String, receivedAt: Long): String {
        val minute = receivedAt / 60_000
        val bytes = MessageDigest.getInstance("SHA-1").digest("$sender|$body|$minute".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
