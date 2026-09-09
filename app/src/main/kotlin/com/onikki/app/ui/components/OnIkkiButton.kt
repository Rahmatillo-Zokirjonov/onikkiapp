package com.onikki.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType

enum class OnIkkiButtonVariant { PRIMARY, SECONDARY }

/** Maps to the mockup's `.btn-primary` / `.btn-secondary` — outline buttons, no fill. */
@Composable
fun OnIkkiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OnIkkiButtonVariant = OnIkkiButtonVariant.PRIMARY,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
) {
    val colors = LocalOnIkkiColors.current
    val foreground: Color
    val border: Color
    when (variant) {
        OnIkkiButtonVariant.PRIMARY -> {
            foreground = colors.accent
            border = colors.accent
        }
        OnIkkiButtonVariant.SECONDARY -> {
            foreground = colors.text
            border = colors.divider
        }
    }
    Box(
        modifier = modifier
            .background(Color.Transparent, OnIkkiShapes.medium)
            .border(BorderStroke(1.dp, border), OnIkkiShapes.medium)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = OnIkkiType.buttonLabel, color = foreground)
    }
}
