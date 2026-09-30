package com.onikki.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

/** Matches OnikkiTabBar.dc.html — 5 tabs, active tab tinted with the accent. */
enum class OnIkkiTab(val label: String) {
    HOME("Bosh sahifa"),
    PLAN("Reja"),
    MONEY("Moliya"),
    NOTES("Qaydlar"),
    PROFILE("Profil")
}

@Composable
fun OnIkkiBottomNavBar(
    active: OnIkkiTab,
    onSelect: (OnIkkiTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.navBackground)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 22.dp)
        ) {
            OnIkkiTab.entries.forEach { tab ->
                val tint = if (tab == active) colors.accent else colors.text.muted(0.42f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TabIcon(tab, tint)
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 10.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: OnIkkiTab, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(width = 1.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            OnIkkiTab.HOME -> {
                val roof = Path().apply {
                    moveTo(3.5f * s, 10.6f * s)
                    lineTo(12f * s, 4f * s)
                    lineTo(20.5f * s, 10.6f * s)
                    lineTo(20.5f * s, 20f * s)
                    cubicTo(20.5f * s, 20.55f * s, 20.05f * s, 21f * s, 19.5f * s, 21f * s)
                    lineTo(4.5f * s, 21f * s)
                    cubicTo(3.95f * s, 21f * s, 3.5f * s, 20.55f * s, 3.5f * s, 20f * s)
                    close()
                }
                drawPath(roof, color = tint, style = stroke)
                val door = Path().apply {
                    moveTo(9.5f * s, 21f * s)
                    lineTo(9.5f * s, 15f * s)
                    lineTo(14.5f * s, 15f * s)
                    lineTo(14.5f * s, 21f * s)
                }
                drawPath(door, color = tint, style = stroke)
            }
            OnIkkiTab.PLAN -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3.5f * s, 4f * s),
                    size = Size(17f * s, 16.5f * s),
                    cornerRadius = CornerRadius(3f * s, 3f * s),
                    style = stroke
                )
                val check = Path().apply {
                    moveTo(7.5f * s, 10.5f * s)
                    lineTo(9.5f * s, 12.5f * s)
                    lineTo(13f * s, 8.9f * s)
                }
                drawPath(check, color = tint, style = stroke)
                drawLine(tint, Offset(15.5f * s, 15.5f * s), Offset(17.5f * s, 15.5f * s), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(tint, Offset(7.5f * s, 15.5f * s), Offset(11.5f * s, 15.5f * s), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            OnIkkiTab.MONEY -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * s, 6f * s),
                    size = Size(18f * s, 13f * s),
                    cornerRadius = CornerRadius(3f * s, 3f * s),
                    style = stroke
                )
                drawLine(tint, Offset(3f * s, 10f * s), Offset(21f * s, 10f * s), strokeWidth = stroke.width)
                drawCircle(tint, radius = 1.4f * s, center = Offset(16.5f * s, 14.5f * s))
            }
            OnIkkiTab.NOTES -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3.5f * s, 4.5f * s),
                    size = Size(17f * s, 15f * s),
                    cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
                    style = stroke
                )
                drawLine(tint, Offset(3.5f * s, 9.5f * s), Offset(20.5f * s, 9.5f * s), strokeWidth = stroke.width)
                drawLine(tint, Offset(9.5f * s, 9.5f * s), Offset(9.5f * s, 19.5f * s), strokeWidth = stroke.width)
                drawLine(tint, Offset(15f * s, 9.5f * s), Offset(15f * s, 19.5f * s), strokeWidth = stroke.width)
            }
            OnIkkiTab.PROFILE -> {
                drawCircle(tint, radius = 3.6f * s, center = Offset(12f * s, 9f * s), style = stroke)
                val shoulders = Path().apply {
                    moveTo(5f * s, 20f * s)
                    cubicTo(6.4f * s, 16.6f * s, 9f * s, 15f * s, 12f * s, 15f * s)
                    cubicTo(15f * s, 15f * s, 17.6f * s, 16.6f * s, 19f * s, 20f * s)
                }
                drawPath(shoulders, color = tint, style = stroke)
            }
        }
    }
}
