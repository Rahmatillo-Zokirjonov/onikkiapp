package com.onikki.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.onikki.app.ui.theme.LocalOnIkkiColors

@Composable
fun TaskCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val colors = LocalOnIkkiColors.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(19.dp)
            .clickable(onClick = onToggle)
            .let {
                if (checked) {
                    it.background(colors.accent, shape)
                } else {
                    it.border(
                        BorderStroke(1.5.dp, if (highlighted) colors.accent else colors.neutral700),
                        shape
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val s = size.width / 24f
                val path = Path().apply {
                    moveTo(5f * s, 12.5f * s)
                    lineTo(9.5f * s, 17f * s)
                    lineTo(19f * s, 7f * s)
                }
                drawPath(
                    path,
                    color = colors.onAccent,
                    style = Stroke(3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
