package com.onikki.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountBalanceTest {
    @Test
    fun typedBalanceIsWhatTheCardShowsAfterSaving() {
        // Card started at 500 000, then −200 000 of spending: it shows 300 000.
        val initial = 500_000L
        val net = -200_000L
        val shownNow = initial + net
        // The user sees the real amount on the card is 450 000 and types that.
        val newInitial = initialBalanceFor(desiredBalance = 450_000, currentBalance = shownNow, currentInitial = initial)
        assertEquals(450_000L, newInitial + net)
        // Saving without changing the number keeps everything as it was.
        assertEquals(initial, initialBalanceFor(shownNow, shownNow, initial))
    }

    @Test
    fun newWalletStartsAtTheTypedAmount() {
        assertEquals(120_000L, initialBalanceFor(desiredBalance = 120_000, currentBalance = 0, currentInitial = 0))
    }
}
