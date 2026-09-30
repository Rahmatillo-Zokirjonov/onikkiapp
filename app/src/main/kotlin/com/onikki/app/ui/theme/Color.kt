package com.onikki.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens. Since 2026-09-30 the app uses the look the user picked from three directions:
 * "Tun" (A) — ink-navy ground, gold accent, serif display titles — with the "Rangli" (B) module tiles,
 * re-tinted for the dark ground (Reja blue, Odatlar green, Moliya gold, Qaydlar rose).
 * Gold is the app accent; [warmAccent] (coral-orange) is kept for warnings: over-limit budgets and apps,
 * overdue items.
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
    val neutral600: Color
    val neutral700: Color
    val neutral800: Color

    /** Warnings only: over-limit budgets and apps, late payments, overdue work. */
    val warmAccent: Color

    /** Card outline for the same warning cases. */
    val warmBorder: Color

    /** Content color to place on top of a solid [accent] fill. */
    val onAccent: Color

    // Module colours (the "B" tiles): each section has its own tint and icon/number colour.
    val planTint: Color
    val planAccent: Color
    val habitTint: Color
    val habitAccent: Color
    val moneyTint: Color
    val moneyAccent: Color
    val noteTint: Color
    val noteAccent: Color

    /** The hero (next prayer) card: a warm corner glow fading into [surface]. */
    val heroGlow: Color
    val heroBorder: Color
    val navBackground: Color
}

object DarkOnIkkiColors : OnIkkiColorTokens {
    override val background = Color(0xFF10131C)
    override val surface = Color(0xFF171B27)
    override val text = Color(0xFFE9E6DC)
    override val divider = Color(0xFFE9E6DC).copy(alpha = 0.10f)
    override val cardBorder = Color(0xFF242A3A)

    override val accent = Color(0xFFD9B36A)
    override val accent100 = Color(0xFFFBF3E2)
    override val accent200 = Color(0xFFF3E2BD)
    override val accent300 = Color(0xFFE8CB8F)
    override val accent400 = Color(0xFFDDBA77)
    override val accent500 = Color(0xFFD9B36A)
    override val accent600 = Color(0xFFB8924F)
    override val accent700 = Color(0xFF8A6C3A)
    override val accent800 = Color(0xFF3A3020)
    override val accent900 = Color(0xFF241F16)

    override val neutral300 = Color(0xFFC9CDDA)
    override val neutral400 = Color(0xFFA9AEBF)
    override val neutral500 = Color(0xFF8F94A6)
    override val neutral600 = Color(0xFF6F7588)
    override val neutral700 = Color(0xFF4A5268)
    override val neutral800 = Color(0xFF262C3C)

    override val warmAccent = Color(0xFFF08A5D)
    override val warmBorder = Color(0xFF7A3E27)
    override val onAccent = Color(0xFF10131C)

    override val planTint = Color(0xFF16223A)
    override val planAccent = Color(0xFF8FB4FF)
    override val habitTint = Color(0xFF142A22)
    override val habitAccent = Color(0xFF74D3A0)
    override val moneyTint = Color(0xFF2A2314)
    override val moneyAccent = Color(0xFFE2BC72)
    override val noteTint = Color(0xFF2A1A2A)
    override val noteAccent = Color(0xFFE39AD2)

    override val heroGlow = Color(0xFF3A2F18)
    override val heroBorder = Color(0xFF3B3222)
    override val navBackground = Color(0xFF0C0F16)
}

/** Kept for completeness (the app is dark-only since the "Tun" choice). */
object LightOnIkkiColors : OnIkkiColorTokens {
    override val background = Color(0xFFF5F6FB)
    override val surface = Color(0xFFFFFFFF)
    override val text = Color(0xFF292B31)
    override val divider = Color(0xFF292B31).copy(alpha = 0.13f)
    override val cardBorder = Color(0xFF292B31).copy(alpha = 0.07f)

    override val accent = Color(0xFF8A6420)
    override val accent100 = Color(0xFF3A2A0C)
    override val accent200 = Color(0xFFF3E2BD)
    override val accent300 = Color(0xFF6E4F17)
    override val accent400 = Color(0xFFB8924F)
    override val accent500 = Color(0xFF8A6420)
    override val accent600 = Color(0xFFB8924F)
    override val accent700 = Color(0xFFE8CB8F)
    override val accent800 = Color(0xFFF6EBD2)
    override val accent900 = Color(0xFFFBF5E8)

    override val neutral300 = Color(0xFFCFD3E5)
    override val neutral400 = Color(0xFFB2B6CA)
    override val neutral500 = Color(0xFF9397AB)
    override val neutral600 = Color(0xFFB2B6CA)
    override val neutral700 = Color(0xFFB2B6CA)
    override val neutral800 = Color(0xFFE4E7F5)

    override val warmAccent = Color(0xFFB4501F)
    override val warmBorder = Color(0xFFE7B69F)
    override val onAccent = Color(0xFFFFFFFF)

    override val planTint = Color(0xFFE6EFFF)
    override val planAccent = Color(0xFF2F5FC4)
    override val habitTint = Color(0xFFE5F6E8)
    override val habitAccent = Color(0xFF1F7A45)
    override val moneyTint = Color(0xFFFFF1DC)
    override val moneyAccent = Color(0xFF9A5B0E)
    override val noteTint = Color(0xFFF8E6F3)
    override val noteAccent = Color(0xFF8E3A7A)

    override val heroGlow = Color(0xFFF1E4C6)
    override val heroBorder = Color(0xFFE6D6B0)
    override val navBackground = Color(0xFFFFFFFF)
}

/** Mirrors the mockup's `color-mix(in srgb, var(--color-text) N%, transparent)` pattern. */
fun Color.muted(opacity: Float): Color = this.copy(alpha = opacity)
