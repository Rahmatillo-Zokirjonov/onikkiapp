package com.onikki.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

enum class TagVariant { ACCENT, NEUTRAL, OUTLINE }

/** Maps to the mockup's `.tag` / `.tag-accent` / `.tag-neutral` / `.tag-outline` classes. */
@Composable
fun TagChip(
    text: String,
    variant: TagVariant = TagVariant.ACCENT,
    modifier: Modifier = Modifier
) {
    val colors = LocalOnIkkiColors.current
    val shape = RoundedCornerShape(6.dp)
    val background: Color
    val foreground: Color
    val borderColor: Color?
    when (variant) {
        TagVariant.ACCENT -> {
            background = colors.accent800
            foreground = colors.accent100
            borderColor = null
        }
        TagVariant.NEUTRAL -> {
            background = colors.neutral800
            foreground = colors.text
            borderColor = null
        }
        TagVariant.OUTLINE -> {
            background = Color.Transparent
            foreground = colors.accent
            borderColor = colors.accent
        }
    }
    var chipModifier = modifier.background(background, shape)
    if (borderColor != null) {
        chipModifier = chipModifier.border(BorderStroke(1.dp, borderColor), shape)
    }
    Text(
        text = text,
        color = foreground,
        fontSize = 10.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = chipModifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
