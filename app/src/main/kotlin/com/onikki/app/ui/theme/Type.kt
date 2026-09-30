package com.onikki.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import com.onikki.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Body/UI face: Onest (bundled, OFL) — calm, readable Latin + Cyrillic. */
val OnIkkiFontFamily = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold)
)

/** Display face for greetings and screen titles: Cormorant Garamond (bundled, OFL). Used sparingly. */
val OnIkkiDisplayFamily = FontFamily(
    Font(R.font.cormorant_semibold, FontWeight.SemiBold),
    Font(R.font.cormorant_bold, FontWeight.Bold)
)

/** Material components (text fields, chips, dialogs) use the same body face. */
val OnIkkiMaterialTypography: Typography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = OnIkkiFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = OnIkkiFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = OnIkkiFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = OnIkkiFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = OnIkkiFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = OnIkkiFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = OnIkkiFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = OnIkkiFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = OnIkkiFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = OnIkkiDisplayFamily)
    )
}

object OnIkkiType {
    val screenTitle = TextStyle(
        fontFamily = OnIkkiDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 33.sp
    )
    val greetingName = TextStyle(
        fontFamily = OnIkkiDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 29.sp,
        lineHeight = 31.sp
    )
    val sectionHeader = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.12.em
    )
    val buttonLabel = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
    val amountLarge = TextStyle(
        fontFamily = OnIkkiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em
    )
}
