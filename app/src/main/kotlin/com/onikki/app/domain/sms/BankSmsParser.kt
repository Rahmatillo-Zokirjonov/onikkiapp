package com.onikki.app.domain.sms

import com.onikki.app.data.db.entity.TransactionType
import java.time.LocalDateTime
import java.util.Locale

/** What a bank SMS said, in the card's own currency. */
data class ParsedBankSms(
    val type: TransactionType,
    /** In minor-free units of [currency] (e.g. 6.00 USD → 6.0). */
    val amount: Double,
    val currency: String,
    /** Last card digits as printed ("*60" → "60", "***1234" → "1234"). */
    val cardDigits: String?,
    val merchant: String?,
    val city: String?,
    val time: LocalDateTime?,
    /** "BEKOR QILINDI: To'lov: …" — the bank reversed an earlier operation with the same details. */
    val isCancellation: Boolean = false
)

/**
 * Heuristic parser for Uzbek bank card SMS (Uzcard/Humo/Visa virtual cards; English, Russian and
 * Uzbek wordings). Built from the patterns these messages share — a masked card, a signed or
 * keyword-typed amount with a currency, an optional "balance" figure — rather than one exact bank
 * template, so an unfamiliar bank still has a fair chance. Anything it can't read confidently returns
 * null and is kept as "aniqlanmagan" for the user to see.
 *
 * Real sample it must handle:
 * "Successful authorization for card Virtual VE *60: 26-09-25 16:29 credit HMB TIETO FO>Andijan +6.00 USD. Avail: 6.61 USD"
 */
object BankSmsParser {

    private const val CURRENCY = """UZS|USD|EUR|RUB|SUM|SO'M|SOM|СУМ|so'm|sum|сум"""

    /** Amount with optional sign next to a currency, either order ("+6.00 USD", "UZS 125 000,00"). */
    private val AMOUNT_AFTER = Regex("""([+\-−]?)\s?(\d{1,3}(?:[  .,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s?($CURRENCY)\b""", RegexOption.IGNORE_CASE)
    private val AMOUNT_BEFORE = Regex("""($CURRENCY)\s?([+\-−]?)\s?(\d{1,3}(?:[  .,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)

    private val CARD = Regex("""(?:\*{1,}|•{1,}|x{2,}|karta\s*[:№]?\s*\**|card\s*\**)\s?(\d{2,4})\b""", RegexOption.IGNORE_CASE)

    private val BALANCE_WORDS = listOf("avail", "balance", "balans", "ostatok", "остаток", "dostupno", "доступно", "qoldiq", "mavjud")
    private val ONE_TIME_CODE = Regex("""\b(kod|code|parol|пароль|код)\b\s*[:\-]?\s*\d{4,6}""", RegexOption.IGNORE_CASE)

    private val INCOME_WORDS = listOf(
        "credit", "popolnenie", "пополнение", "zachislenie", "зачисление", "postuplenie", "поступление",
        "to'ldirish", "toldirish", "tushum", "kirim", "o'tkazma qabul", "kartaga o'tkazma", "kartaga o‘tkazma",
        "perevod na kartu", "received", "refund", "vozvrat", "возврат"
    )
    private val EXPENSE_WORDS = listOf(
        "debit", "pokupka", "покупка", "oplata", "оплата", "spisanie", "списание", "xarid", "to'lov", "tolov",
        "snyatie", "снятие", "yechib", "yechish", "chiqim", "purchase", "payment", "withdrawal", "perevod s karty",
        "kartadan o'tkazma", "kartadan o‘tkazma"
    )
    // "P2P" alone says nothing about direction ("Kartaga o'tkazma: BEEPUL P2P" is money IN), so it's no keyword.

    private val CANCELLATION = Regex("""^\s*(bekor qilindi|otmena|отмена|reversal|cancel+ed|vozvrat operatsii)\b""", RegexOption.IGNORE_CASE)

    private val DATE_PATTERNS = listOf(
        Regex("""(\d{2})[.\-/](\d{2})[.\-/](\d{2,4})\s+(\d{1,2}):(\d{2})""")
    )

    fun parse(body: String): ParsedBankSms? {
        val text = body.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (ONE_TIME_CODE.containsMatchIn(text)) return null

        val amount = pickAmount(text) ?: return null
        val lower = text.lowercase(Locale.ROOT)
        val type = when {
            amount.sign == '+' -> TransactionType.KIRIM
            amount.sign == '-' -> TransactionType.CHIQIM
            else -> keywordType(lower) ?: return null
        }
        val card = CARD.find(text)?.groupValues?.get(1)
        val (merchant, city) = merchantAndCity(text, lower, amount.start)
        return ParsedBankSms(
            type = type,
            amount = amount.value,
            currency = normalizeCurrency(amount.currency),
            cardDigits = card,
            merchant = merchant,
            city = city,
            time = parseTime(text),
            isCancellation = CANCELLATION.containsMatchIn(text)
        )
    }

    private data class FoundAmount(val value: Double, val currency: String, val sign: Char?, val start: Int)

    /** The first amount that isn't a balance figure ("Avail: 6.61 USD" is not the transaction). */
    private fun pickAmount(text: String): FoundAmount? {
        val candidates = buildList {
            AMOUNT_AFTER.findAll(text).forEach { m ->
                add(FoundAmount(toNumber(m.groupValues[2]) ?: return@forEach, m.groupValues[3], signOf(m.groupValues[1]), m.range.first))
            }
            AMOUNT_BEFORE.findAll(text).forEach { m ->
                add(FoundAmount(toNumber(m.groupValues[3]) ?: return@forEach, m.groupValues[1], signOf(m.groupValues[2]), m.range.first))
            }
        }.sortedBy { it.start }
        return candidates.firstOrNull { candidate ->
            val before = text.substring(maxOf(0, candidate.start - 14), candidate.start).lowercase(Locale.ROOT)
            candidate.value > 0 && BALANCE_WORDS.none { before.contains(it) }
        }
    }

    private fun signOf(raw: String): Char? = when (raw) {
        "+" -> '+'
        "-", "−" -> '-'
        else -> null
    }

    /**
     * "125 000,00" / "125,000.00" / "6.00" / "1.250.000" → number. The last separator followed by
     * exactly 1–2 digits is the decimal point; every other separator groups thousands.
     */
    fun toNumber(raw: String): Double? {
        val cleaned = raw.replace(' ', ' ').trim()
        val decimal = Regex("""[.,](\d{1,2})$""").find(cleaned)
        val integerPart = (if (decimal != null) cleaned.substring(0, decimal.range.first) else cleaned).replace(Regex("""[ .,]"""), "")
        val fraction = decimal?.groupValues?.get(1)?.padEnd(2, '0') ?: "00"
        return "$integerPart.$fraction".toDoubleOrNull()
    }

    private fun keywordType(lower: String): TransactionType? {
        val income = INCOME_WORDS.filter { lower.contains(it) }.minOfOrNull { lower.indexOf(it) }
        val expense = EXPENSE_WORDS.filter { lower.contains(it) }.minOfOrNull { lower.indexOf(it) }
        return when {
            income == null && expense == null -> null
            expense == null -> TransactionType.KIRIM
            income == null -> TransactionType.CHIQIM
            // Both present ("perevod s karty ... zachislenie"): the earlier word describes the event.
            income < expense -> TransactionType.KIRIM
            else -> TransactionType.CHIQIM
        }
    }

    /**
     * The shop is the text between the operation word (or the date) and the amount:
     * "credit HMB TIETO FO>Andijan +6.00 USD" → "HMB TIETO FO", city "Andijan".
     */
    private fun merchantAndCity(text: String, lower: String, amountStart: Int): Pair<String?, String?> {
        val words = (INCOME_WORDS + EXPENSE_WORDS)
        val anchorEnd = words.mapNotNull { w ->
            val i = lower.indexOf(w)
            if (i in 0 until amountStart) i + w.length else null
        }.maxOrNull()
            ?: DATE_PATTERNS.firstNotNullOfOrNull { it.find(text)?.range?.last?.plus(1) }?.takeIf { it < amountStart }
            ?: return null to null
        // Cut at the first thing that isn't the shop: the date, the card, or the amount label.
        var raw = text.substring(anchorEnd, amountStart)
        val stop = listOfNotNull(
            DATE_PATTERNS.firstNotNullOfOrNull { it.find(raw)?.range?.first },
            Regex("""\b(karta|card|miqdor|summa|сумма|na summu)\b""", RegexOption.IGNORE_CASE).find(raw)?.range?.first
        ).minOrNull()
        if (stop != null) raw = raw.substring(0, stop)
        raw = raw.trim(' ', ':', ',', ';', '.', '-')
        if (raw.isBlank()) return null to null
        val merchant: String?
        val city: String?
        if ('>' in raw) {
            val parts = raw.split('>', limit = 2)
            merchant = parts[0].trim()
            city = parts[1].trim().split(',').first().trim()
        } else {
            val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            merchant = parts.firstOrNull()
            // A trailing two-letter code ("UZ") is the country, not a city.
            city = parts.getOrNull(1)?.takeUnless { it.length <= 2 }
        }
        return merchant?.takeIf { it.length >= 2 }?.take(40) to city?.takeIf { it.isNotBlank() }
    }

    private fun parseTime(text: String): LocalDateTime? {
        val m = DATE_PATTERNS.firstNotNullOfOrNull { it.find(text) } ?: return null
        val (d, mo, y, h, mi) = m.destructured
        val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
        return runCatching { LocalDateTime.of(year, mo.toInt(), d.toInt(), h.toInt(), mi.toInt()) }.getOrNull()
    }

    private fun normalizeCurrency(raw: String): String = when (raw.uppercase(Locale.ROOT)) {
        "USD" -> "USD"
        "EUR" -> "EUR"
        "RUB" -> "RUB"
        else -> "UZS"
    }
}
