package com.onikki.app.ui.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val somFormat: DecimalFormat by lazy {
    val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ' ' }
    DecimalFormat("#,##0", symbols)
}

/** "1 250 000" style formatting — thousands separated by a space, per the TZ. */
fun formatSom(amount: Long): String = somFormat.format(amount)

/** "2,95 mln" style compact formatting for amounts at or above one million so'm. */
fun formatCompactSom(amount: Long): String {
    if (amount < 1_000_000) return formatSom(amount)
    val millions = amount / 1_000_000.0
    return "%.2f mln".format(Locale.US, millions).replace('.', ',')
}

/** Shows raw digit input as "1 250 000" while the field's value stays plain digits. */
object ThousandsSeparatorTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = buildString {
            digits.forEachIndexed { index, char ->
                if (index > 0 && (digits.length - index) % 3 == 0) append(' ')
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + (1 until offset).count { (digits.length - it) % 3 == 0 }

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset).count { it != ' ' }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
