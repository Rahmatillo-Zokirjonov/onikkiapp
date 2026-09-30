package com.onikki.app.domain.notes

import java.time.LocalDate

object Journal {
    /** Days in a row with an entry, ending today — or yesterday while today's isn't written yet. */
    fun streak(dates: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in dates) today else today.minusDays(1)
        var count = 0
        while (day in dates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
