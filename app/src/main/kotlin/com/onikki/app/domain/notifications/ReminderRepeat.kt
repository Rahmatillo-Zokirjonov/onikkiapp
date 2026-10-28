package com.onikki.app.domain.notifications

import java.time.DayOfWeek
import java.time.LocalDateTime

/** How a reminder repeats after it fires. */
enum class ReminderRepeat(val label: String) {
    NONE("Bir marta"),
    DAILY("Har kuni"),
    WEEKDAYS("Ish kunlari (Du–Ju)"),
    WEEKLY("Har hafta");

    /** The next time after [at] (and after [now], so a missed one isn't fired in a burst). Null = no repeat. */
    fun next(at: LocalDateTime, now: LocalDateTime = at): LocalDateTime? {
        if (this == NONE) return null
        var t = at
        do {
            t = when (this) {
                DAILY -> t.plusDays(1)
                WEEKLY -> t.plusWeeks(1)
                WEEKDAYS -> {
                    var d = t.plusDays(1)
                    while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                    d
                }
                NONE -> return null
            }
        } while (!t.isAfter(now))
        return t
    }
}

/** How a reminder reaches the user — the three levels on the Eslatmalar screen. */
enum class ReminderLevel(val label: String, val hint: String) {
    TOP("Tepada", "Namoz vaqtlari kabi — ekran tepasida, ovoz bilan"),
    BEFORE_APP("Ilovaga kirishdan oldin", "Vaqti kelgach, keyingi ochilgan ilovadan oldin ekranga chiqadi"),
    FULL_SCREEN("To'liq ekran", "Budilnikdek — ekranni yopadi, qulflangan ekranda ham");
}
