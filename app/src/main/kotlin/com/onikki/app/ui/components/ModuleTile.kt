package com.onikki.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiColorTokens
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.muted

/** The app's sections, each with its own colour pair (the "Rangli" tiles on the "Tun" ground). */
enum class AppModule { PLAN, HABITS, MONEY, NOTES }

fun AppModule.tint(colors: OnIkkiColorTokens): Color = when (this) {
    AppModule.PLAN -> colors.planTint
    AppModule.HABITS -> colors.habitTint
    AppModule.MONEY -> colors.moneyTint
    AppModule.NOTES -> colors.noteTint
}

fun AppModule.accent(colors: OnIkkiColorTokens): Color = when (this) {
    AppModule.PLAN -> colors.planAccent
    AppModule.HABITS -> colors.habitAccent
    AppModule.MONEY -> colors.moneyAccent
    AppModule.NOTES -> colors.noteAccent
}

/** Rounded square holding a module's icon, in the module's colour. */
@Composable
fun ModuleIcon(module: AppModule, icon: String, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(size)
            .background(module.accent(colors).copy(alpha = 0.16f), RoundedCornerShape(size * 0.34f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, color = module.accent(colors), fontSize = (size.value * 0.48f).sp)
    }
}

/**
 * A module summary tile: icon, one big number, a short caption. Tinted in the module's colour so the
 * home screen reads as "blue = plan, green = habits, gold = money" at a glance.
 */
@Composable
fun ModuleTile(
    module: AppModule,
    icon: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .background(module.tint(colors), OnIkkiShapes.tile)
            .border(BorderStroke(1.dp, module.accent(colors).copy(alpha = 0.14f)), OnIkkiShapes.tile)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleIcon(module, icon)
            Box(modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        Column {
            Text(
                text = value,
                color = module.accent(colors),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                color = colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A module-tinted card for a whole section (e.g. Moliya's balance, Reja's progress). */
@Composable
fun ModuleCard(
    module: AppModule,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    gap: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(module.tint(colors), OnIkkiShapes.tile)
            .border(BorderStroke(1.dp, module.accent(colors).copy(alpha = 0.14f)), OnIkkiShapes.tile)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

/** The "Tun" hero: a warm gold glow in the top-right corner fading into the card, with a gold hairline. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    gap: androidx.compose.ui.unit.Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.heroGlow, colors.surface, colors.surface),
                    start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                ),
                OnIkkiShapes.tile
            )
            .border(BorderStroke(1.dp, colors.heroBorder), OnIkkiShapes.tile)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}
