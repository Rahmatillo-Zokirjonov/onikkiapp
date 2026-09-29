package com.onikki.app.domain.sms

import com.onikki.app.data.db.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class BankSmsParserTest {

    /** The real SMS the user shared (virtual Visa card). */
    @Test
    fun realVirtualCardCredit() {
        val sms = "Successful authorization for card Virtual VE *60: 26-09-25 16:29 credit HMB TIETO FO>Andijan +6.00 USD. Avail: 6.61 USD"
        val parsed = BankSmsParser.parse(sms)
        assertNotNull(parsed)
        parsed!!
        assertEquals(TransactionType.KIRIM, parsed.type)
        assertEquals(6.0, parsed.amount, 0.001)
        assertEquals("USD", parsed.currency)
        assertEquals("60", parsed.cardDigits)
        assertEquals("HMB TIETO FO", parsed.merchant)
        assertEquals("Andijan", parsed.city)
        assertEquals(LocalDateTime.of(2025, 9, 26, 16, 29), parsed.time)
    }

    /** The real Uzcard SMS the user shared: money transferred TO the card (P2P) is income. */
    @Test
    fun realUzcardTransferIn() {
        val parsed = BankSmsParser.parse(
            "Kartaga o'tkazma: BEEPUL P2P, UZ,21.09.26 08:40,karta ***0365. Miqdor:80000.00 UZS Qoldiq:89205.15 UZS"
        )
        assertNotNull(parsed)
        parsed!!
        assertEquals(TransactionType.KIRIM, parsed.type)
        assertEquals(80000.0, parsed.amount, 0.001)
        assertEquals("UZS", parsed.currency)
        assertEquals("0365", parsed.cardDigits)
        assertEquals("BEEPUL P2P", parsed.merchant)
        assertNull(parsed.city)
        assertEquals(LocalDateTime.of(2026, 9, 21, 8, 40), parsed.time)
    }

    /** Real pair from the user: a payment, then its cancellation 4 minutes later (same balance after both). */
    @Test
    fun realUzcardPaymentAndItsCancellation() {
        val payment = BankSmsParser.parse(
            "To'lov: HAMKORMOBILE 3 UZCARD KONVERSIYA, UZ,15.09.26 14:14,karta ***0365. Miqdor:19958.89 UZS Qoldiq:79205.15 UZS"
        )!!
        assertEquals(TransactionType.CHIQIM, payment.type)
        assertEquals(19958.89, payment.amount, 0.001)
        assertEquals("HAMKORMOBILE 3 UZCARD KONVERSIYA", payment.merchant)
        assertEquals(false, payment.isCancellation)

        val cancel = BankSmsParser.parse(
            "BEKOR QILINDI: To'lov: HAMKORMOBILE 3 UZCARD KONVERSIYA, UZ,15.09.26 14:18,karta ***0365. Miqdor:19958.89 UZS Qoldiq:79205.15 UZS"
        )!!
        assertEquals(true, cancel.isCancellation)
        assertEquals(payment.merchant, cancel.merchant)
        assertEquals(payment.amount, cancel.amount, 0.001)
        assertEquals(LocalDateTime.of(2026, 9, 15, 14, 18), cancel.time)
    }

    @Test
    fun uzcardTransferOutIsExpense() {
        val parsed = BankSmsParser.parse(
            "Kartadan o'tkazma: CLICK P2P, UZ,22.09.26 12:10,karta ***0365. Miqdor:30000.00 UZS Qoldiq:59205.15 UZS"
        )!!
        assertEquals(TransactionType.CHIQIM, parsed.type)
        assertEquals(30000.0, parsed.amount, 0.001)
    }

    @Test
    fun sameFormatDebitWithMinus() {
        val parsed = BankSmsParser.parse(
            "Successful authorization for card Virtual VE *60: 27-09-25 10:05 debit NETFLIX.COM>Los Gatos -9.99 USD. Avail: 1.62 USD"
        )!!
        assertEquals(TransactionType.CHIQIM, parsed.type)
        assertEquals(9.99, parsed.amount, 0.001)
        assertEquals("NETFLIX.COM", parsed.merchant)
    }

    @Test
    fun russianUzcardPurchase() {
        val parsed = BankSmsParser.parse(
            "Pokupka: KORZINKA SUPERMARKET, Toshkent 29.09.26 14:35 karta ***1234. summa: 125 000,00 UZS, balans: 1 500 000,00 UZS"
        )!!
        assertEquals(TransactionType.CHIQIM, parsed.type)
        assertEquals(125000.0, parsed.amount, 0.001)
        assertEquals("UZS", parsed.currency)
        assertEquals("1234", parsed.cardDigits)
        assertEquals("KORZINKA SUPERMARKET", parsed.merchant)
        assertEquals("Toshkent", parsed.city)
    }

    @Test
    fun uzbekTopUp() {
        val parsed = BankSmsParser.parse("HUMO *4321: Hisobni to'ldirish 500 000.00 so'm. Qoldiq: 700 000.00 so'm")!!
        assertEquals(TransactionType.KIRIM, parsed.type)
        assertEquals(500000.0, parsed.amount, 0.001)
    }

    @Test
    fun balanceIsNotTheAmount() {
        val parsed = BankSmsParser.parse("Oplata 50 000 UZS PAYNET karta *9876 Dostupno: 3 000 000 UZS")!!
        assertEquals(50000.0, parsed.amount, 0.001)
        assertEquals(TransactionType.CHIQIM, parsed.type)
    }

    @Test
    fun oneTimeCodesAreIgnored() {
        assertNull(BankSmsParser.parse("Kod: 482913. Karta *1234 bilan 100 000 UZS to'lovni tasdiqlash uchun. Hech kimga bermang"))
    }

    @Test
    fun unrelatedMessageIsNull() {
        assertNull(BankSmsParser.parse("Salom, ertaga soat 10 da uchrashamiz"))
    }

    @Test
    fun numberFormats() {
        assertEquals(125000.0, BankSmsParser.toNumber("125 000,00")!!, 0.001)
        assertEquals(125000.0, BankSmsParser.toNumber("125,000.00")!!, 0.001)
        assertEquals(1250000.0, BankSmsParser.toNumber("1.250.000")!!, 0.001)
        assertEquals(6.0, BankSmsParser.toNumber("6.00")!!, 0.001)
    }
}
