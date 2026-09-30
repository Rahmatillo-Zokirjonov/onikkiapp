package com.onikki.app.ui.screentime

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ChartMode(val label: String) { BARS("Ustun"), LINE("Chiziq") }

/** The viewer's chart preference, remembered on the phone. */
object ChartModePref {
    private const val PREFS = "usage_chart"
    fun get(context: Context): ChartMode =
        runCatching { ChartMode.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null) ?: "") }
            .getOrDefault(ChartMode.BARS)
    fun set(context: Context, mode: ChartMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }
}

/** Small two-way switch: Ustun | Chiziq. */
@Composable
fun ChartModeToggle(mode: ChartMode, onChange: (ChartMode) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier
            .background(colors.background, RoundedCornerShape(50))
            .padding(3.dp)
    ) {
        ChartMode.entries.forEach { option ->
            val selected = option == mode
            Text(
                text = option.label,
                color = if (selected) colors.onAccent else colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable { onChange(option) }
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Minutes of screen time in each hour of the day (0–23). Bars or a filled line, one shared scale:
 * the y-axis tops out at the busiest hour (at least 10 min so a quiet day doesn't look full).
 * The current hour is highlighted; future hours are left empty.
 */
@Composable
fun HourlyUsageChart(
    minutes: List<Int>,
    mode: ChartMode,
    color: Color,
    currentHour: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    val colors = LocalOnIkkiColors.current
    val maxValue = maxOf(10, minutes.maxOrNull() ?: 0)
    val topLabel = "$maxValue daq"
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Canvas(modifier = Modifier.weight(1f).height(height)) {
                val w = size.width
                val h = size.height
                val slot = w / 24f
                // Faint guide lines at full, half and zero.
                listOf(0f, 0.5f, 1f).forEach { f ->
                    drawLine(
                        color = colors.text.copy(alpha = if (f == 1f) 0.18f else 0.08f),
                        start = Offset(0f, h * (1 - f)),
                        end = Offset(w, h * (1 - f)),
                        strokeWidth = 1f,
                        pathEffect = if (f == 0.5f) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null
                    )
                }
                val limit = currentHour ?: 23
                when (mode) {
                    ChartMode.BARS -> minutes.forEachIndexed { hour, value ->
                        if (hour > limit) return@forEachIndexed
                        val barH = (value / maxValue.toFloat()) * h
                        if (barH <= 0f) return@forEachIndexed
                        val barW = slot * 0.62f
                        drawRoundRect(
                            color = if (hour == currentHour) color else color.copy(alpha = 0.55f),
                            topLeft = Offset(hour * slot + (slot - barW) / 2, h - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 3, barW / 3)
                        )
                    }
                    ChartMode.LINE -> {
                        val pts = minutes.take(limit + 1).mapIndexed { hour, value ->
                            Offset(hour * slot + slot / 2, h - (value / maxValue.toFloat()) * h)
                        }
                        if (pts.size >= 2) {
                            val line = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 1 until pts.size) {
                                    val p0 = pts[i - 1]
                                    val p1 = pts[i]
                                    val mx = (p0.x + p1.x) / 2
                                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                                }
                            }
                            val fill = Path().apply {
                                addPath(line)
                                lineTo(pts.last().x, h)
                                lineTo(pts.first().x, h)
                                close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
                            drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawCircle(color, radius = 4.dp.toPx(), center = pts.last())
                        }
                    }
                }
            }
            Text(
                text = topLabel,
                color = colors.text.muted(0.45f),
                fontSize = 9.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 34.dp)) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
                Text(
                    text = label,
                    color = colors.text.muted(0.45f),
                    fontSize = 10.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(if (i == 4) 0.001f else 1f)
                )
            }
        }
    }
}

private val iconCache = LruCache<String, ImageBitmap>(64)

/** The app's real launcher icon (falls back to its initials while loading or if missing). */
@Composable
fun AppIcon(packageName: String, label: String, size: Dp = 34.dp) {
    val context = LocalContext.current
    val colors = LocalOnIkkiColors.current
    val icon by produceState(initialValue = iconCache.get(packageName), packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val px = 96
                    context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(packageName, it) }
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = label, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        Box(
            modifier = Modifier.size(size).background(colors.neutral800, RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label.take(2).uppercase(), color = colors.neutral300, fontSize = (size.value * 0.36f).sp, fontFamily = OnIkkiFontFamily)
        }
    }
}
