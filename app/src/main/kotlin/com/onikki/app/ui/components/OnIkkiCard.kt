package com.onikki.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiShapes

/** Maps to the mockup's `.card` class: surface fill + hairline border, 8dp radius. */
@Composable
fun OnIkkiCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    borderColor: Color = LocalOnIkkiColors.current.cardBorder,
    gap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .background(colors.surface, OnIkkiShapes.medium)
            .border(BorderStroke(1.dp, borderColor), OnIkkiShapes.medium)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

/** Same card, laid out as a row — used for list rows (tasks, prayer time, habits). */
@Composable
fun OnIkkiRowCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 11.dp),
    borderColor: Color = LocalOnIkkiColors.current.cardBorder,
    gap: Dp = 10.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = modifier
            .background(colors.surface, OnIkkiShapes.medium)
            .border(BorderStroke(1.dp, borderColor), OnIkkiShapes.medium)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = verticalAlignment,
        content = content
    )
}
