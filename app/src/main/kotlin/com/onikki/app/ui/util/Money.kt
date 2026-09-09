package com.onikki.app.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val somFormat: DecimalFormat by lazy {
    val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }
    DecimalFormat("#,##0", symbols)
}

/** "1 250 000" style formatting — thousands separated by a space, per the TZ. */
fun formatSom(amount: Long): String = somFormat.format(amount)
