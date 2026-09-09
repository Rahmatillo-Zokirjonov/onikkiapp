package com.onikki.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TODO(design): swap for bundled/downloadable Inter (weight 500 headings) once
// font assets are available — sizes/letter-spacing below already match the mockup.
val OnIkkiFontFamily = FontFamily.Default

object OnIkkiType {
    val screenTitle = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em
    )
    val greetingName = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em
    )
    val sectionHeader = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    )
    val body = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    val caption = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    )
    val kicker = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.1.em
    )
    val buttonLabel = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    val amountLarge = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        letterSpacing = (-0.02).em
    )
}
