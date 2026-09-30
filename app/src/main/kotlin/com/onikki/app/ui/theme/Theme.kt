package com.onikki.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalOnIkkiColors = staticCompositionLocalOf<OnIkkiColorTokens> { DarkOnIkkiColors }

@Composable
fun OnIkkiTheme(
    // The chosen "Tun" look is dark by design, independent of the phone's light/dark setting.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val tokens: OnIkkiColorTokens = if (darkTheme) DarkOnIkkiColors else LightOnIkkiColors

    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            outline = tokens.divider
        )
    } else {
        lightColorScheme(
            primary = tokens.accent,
            onPrimary = tokens.onAccent,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.surface,
            onSurface = tokens.text,
            outline = tokens.divider
        )
    }

    CompositionLocalProvider(LocalOnIkkiColors provides tokens) {
        MaterialTheme(colorScheme = materialColors, typography = OnIkkiMaterialTypography, content = content)
    }
}
