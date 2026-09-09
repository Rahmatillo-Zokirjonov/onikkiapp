package com.onikki.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens pulled from the imported Claude Design mockup
 * ("On ikki - Maketlar.dc.html", Nocturne design system, screens 1a/1b/1g).
 * The warm accent (#e0a45f dark / #8a5518 light) is reserved for streak
 * counts, budget warnings and the day-rating score per the TZ — never
 * used as a general accent.
 */
interface OnIkkiColorTokens {
    val background: Color
    val surface: Color
    val text: Color
    val divider: Color
    val cardBorder: Color

    val accent: Color
    val accent100: Color
    val accent200: Color
    val accent300: Color
    val accent400: Color
    val accent500: Color
    val accent600: Color
    val accent700: Color
    val accent800: Color
    val accent900: Color

    val neutral300: Color
    val neutral400: Color
    val neutral500: Color
    val neutral700: Color
    val neutral800: Color

    /** Reserved for streak counts, budget warnings, day-rating score only. */
    val warmAccent: Color

    /** Content color to place on top of a solid [accent] fill. */
    val onAccent: Color
}

object DarkOnIkkiColors : OnIkkiColorTokens {
    override val background = Color(0xFF161826)
    override val surface = Color(0xFF232532)
    override val text = Color(0xFFE9E9ED)
    override val divider = Color(0xFFE9E9ED).copy(alpha = 0.16f)
    override val cardBorder = Color(0xFF3F424D)

    override val accent = Color(0xFF9184D9)
    override val accent100 = Color(0xFFF5F4FF)
    override val accent200 = Color(0xFFE7E5FE)
    override val accent300 = Color(0xFFD2CEFD)
    override val accent400 = Color(0xFFB5ABFC)
    override val accent500 = Color(0xFF968AE0)
    override val accent600 = Color(0xFF796CBF)
    override val accent700 = Color(0xFF5D5294)
    override val accent800 = Color(0xFF423A6A)
    override val accent900 = Color(0xFF2B2741)

    override val neutral300 = Color(0xFFCFD3E5)
    override val neutral400 = Color(0xFFB2B6CA)
    override val neutral500 = Color(0xFF9397AB)
    override val neutral700 = Color(0xFF595D6C)
    override val neutral800 = Color(0xFF3F424D)

    override val warmAccent = Color(0xFFE0A45F)
    override val onAccent = background
}

object LightOnIkkiColors : OnIkkiColorTokens {
    override val background = Color(0xFFF5F6FB)
    override val surface = Color(0xFFFFFFFF)
    override val text = Color(0xFF292B31)
    override val divider = Color(0xFF292B31).copy(alpha = 0.13f)
    override val cardBorder = Color(0xFF292B31).copy(alpha = 0.07f)

    override val accent = Color(0xFF6A5DB2)
    override val accent100 = Color(0xFF423A6A)
    override val accent200 = Color(0xFFE7E5FE)
    override val accent300 = Color(0xFF5D5294)
    override val accent400 = Color(0xFF8D80D6)
    override val accent500 = Color(0xFF6A5DB2)
    override val accent600 = Color(0xFF796CBF)
    override val accent700 = Color(0xFFC6C0FB)
    override val accent800 = Color(0xFFE7E5FE)
    override val accent900 = Color(0xFFEEECFE)

    override val neutral300 = Color(0xFFCFD3E5)
    override val neutral400 = Color(0xFFB2B6CA)
    override val neutral500 = Color(0xFF9397AB)
    override val neutral700 = Color(0xFFB2B6CA)
    override val neutral800 = Color(0xFFE4E7F5)

    override val warmAccent = Color(0xFF8A5518)
    override val onAccent = Color(0xFFFFFFFF)
}

/** Mirrors the mockup's `color-mix(in srgb, var(--color-text) N%, transparent)` pattern. */
fun Color.muted(opacity: Float): Color = this.copy(alpha = opacity)
