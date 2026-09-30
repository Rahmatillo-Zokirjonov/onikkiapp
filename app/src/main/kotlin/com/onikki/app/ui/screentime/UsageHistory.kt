package com.onikki.app.ui.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.screentime.AppTotal
import com.onikki.app.domain.screentime.UsagePeriod
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatMinutesAsDuration
import com.onikki.app.ui.util.monthNameUz
import com.onikki.app.ui.util.weekdayAbbrUz
import com.onikki.app.ui.util.weekdayNameUz
import java.time.LocalDate
import java.time.LocalTime

private fun periodLabel(h: UsageHistoryState, today: LocalDate): String {
    val s = h.summary
    return when (h.period) {
        UsagePeriod.DAY -> when (h.anchor) {
            today -> "Bugun"
            today.minusDays(1) -> "Kecha"
            else -> "${h.anchor.dayOfMonth}-${monthNameUz(h.anchor.monthValue)}, ${weekdayNameUz(h.anchor.dayOfWeek).lowercase()}"
        }
        UsagePeriod.WEEK -> if (h.isCurrent) "Bu hafta" else s?.let {
            if (it.from.month == it.to.month) "${it.from.dayOfMonth}–${it.to.dayOfMonth} ${monthNameUz(it.to.monthValue)}"
            else "${it.from.dayOfMonth} ${monthNameUz(it.from.monthValue)} – ${it.to.dayOfMonth} ${monthNameUz(it.to.monthValue)}"
        }.orEmpty()
        UsagePeriod.MONTH -> if (h.isCurrent) "Bu oy" else "${monthNameUz(h.anchor.monthValue).replaceFirstChar { it.uppercase() }} ${h.anchor.year}"
    }
}

/** Kun / Hafta / Oy with ‹ › to walk back through past days, weeks and months. */
@Composable
fun UsageHistorySection(
    history: UsageHistoryState,
    controlled: Set<String>,
    onPeriod: (UsagePeriod) -> Unit,
    onShift: (Int) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenApp: (String) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val summary = history.summary
    var mode by remember { mutableStateOf(ChartModePref.get(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PeriodTabs(history.period, onPeriod)

        // ‹ label ›
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavArrow("‹", enabled = true) { onShift(-1) }
            Text(
                text = periodLabel(history, today),
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            if (!history.isCurrent) {
                Text(
                    text = "Hozirgi",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable { onOpenDay(today); onPeriod(history.period) }.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            NavArrow("›", enabled = !history.isCurrent) { onShift(1) }
        }

        // Total + comparison
        val total = summary?.total ?: 0
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = formatMinutesAsDuration(total.toLong()), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            Text(
                text = "  " + comparison(history),
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }

        OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (history.period) {
                        UsagePeriod.DAY -> "Soatma-soat"
                        UsagePeriod.WEEK -> "Kunlar bo'yicha"
                        UsagePeriod.MONTH -> "Oy kunlari bo'yicha"
                    },
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                ChartModeToggle(mode) { mode = it; ChartModePref.set(context, it) }
            }
            when {
                summary == null -> Unit
                history.period == UsagePeriod.DAY -> {
                    val hours = history.hours
                    if (hours == null || hours.all { it == 0 }) {
                        Text(
                            text = if (total > 0) "Bu kun uchun soatlik ma'lumot saqlanmagan" else "Bu kun uchun ma'lumot yo'q",
                            color = colors.text.muted(0.5f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    } else {
                        val peak = hours.withIndex().maxByOrNull { it.value }!!
                        Text(text = "Eng faol: %02d:00 · %d daq".format(peak.index, peak.value), color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                        HourlyUsageChart(minutes = hours, mode = mode, color = colors.accent, currentHour = if (history.anchor == today) LocalTime.now().hour else null)
                    }
                }
                else -> {
                    val days = summary.dailyTotals.indices.map { summary.from.plusDays(it.toLong()) }
                    val lastIndex = days.indexOfLast { !it.isAfter(today) }
                    DailyUsageChart(
                        values = summary.dailyTotals,
                        labels = days.mapIndexed { i, d ->
                            if (history.period == UsagePeriod.WEEK) weekdayAbbrUz(d.dayOfWeek)
                            else if (i == 0 || (d.dayOfMonth - 1) % 7 == 0) d.dayOfMonth.toString() else null
                        },
                        mode = mode,
                        color = colors.accent,
                        highlight = days.indexOf(today).takeIf { it >= 0 },
                        lastIndex = lastIndex,
                        onSelect = { i -> days.getOrNull(i)?.let(onOpenDay) }
                    )
                    Text(text = "Ustunni bossangiz, o'sha kun ochiladi", color = colors.text.muted(0.35f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                }
            }
        }

        if (summary != null && history.period != UsagePeriod.DAY && summary.trackedDays > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Kunlik o'rtacha", formatMinutesAsDuration(summary.averagePerDay.toLong()), Modifier.weight(1f))
                summary.busiest?.let { (day, minutes) ->
                    StatTile("Eng ko'p: ${day.dayOfMonth}-${monthNameUz(day.monthValue).take(3)}", formatMinutesAsDuration(minutes.toLong()), Modifier.weight(1f).clickable { onOpenDay(day) })
                }
                val span = summary.dailyTotals.indices.count { !summary.from.plusDays(it.toLong()).isAfter(today) }
                StatTile("Kuzatilgan kun", "${summary.trackedDays}/$span", Modifier.weight(1f))
            }
        }

        if (summary != null && summary.apps.isNotEmpty()) {
            Text(
                text = when (history.period) {
                    UsagePeriod.DAY -> "Ilovalar"
                    UsagePeriod.WEEK -> "Hafta davomida ilovalar"
                    UsagePeriod.MONTH -> "Oy davomida ilovalar"
                },
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OnIkkiFontFamily
            )
            summary.apps.take(12).forEach { app ->
                HistoryAppRow(app, summary.total, history.period, isControlled = app.packageName in controlled, onClick = { onOpenApp(app.packageName) })
            }
        } else if (summary != null) {
            Text(
                text = "Bu davr uchun ma'lumot yo'q. Tarix ilova o'rnatilgandan keyin yig'iladi (telefon oxirgi ~10 kunni ham beradi).",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

private fun comparison(h: UsageHistoryState): String {
    val s = h.summary ?: return ""
    val prev = h.previousAverage
    val base = when (h.period) {
        UsagePeriod.DAY -> "kechagidan"
        UsagePeriod.WEEK -> "o'tgan haftadan"
        UsagePeriod.MONTH -> "o'tgan oydan"
    }
    // Days compare totals; weeks/months compare the daily average so a half-finished period is fair.
    val current = if (h.period == UsagePeriod.DAY) s.total else s.averagePerDay
    if (prev == null || prev == 0 || current == 0) return if (h.period == UsagePeriod.DAY) "" else "kuniga ${formatMinutesAsDuration(s.averagePerDay.toLong())}"
    val diffPct = (current - prev) * 100 / prev
    val prefix = if (h.period == UsagePeriod.DAY) "" else "kuniga o'rtacha · "
    return when {
        diffPct > 0 -> "$prefix$base $diffPct% ko'p"
        diffPct < 0 -> "$prefix$base ${-diffPct}% kam"
        else -> "$prefix$base bir xil"
    }
}

@Composable
private fun PeriodTabs(selected: UsagePeriod, onSelect: (UsagePeriod) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(50)).padding(3.dp)) {
        UsagePeriod.entries.forEach { p ->
            val isSelected = p == selected
            Text(
                text = p.label,
                color = if (isSelected) colors.onAccent else colors.text.muted(0.7f),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = OnIkkiFontFamily,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun NavArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = symbol,
        color = if (enabled) colors.text else colors.text.muted(0.2f),
        fontSize = 24.sp,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = modifier.background(colors.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(text = value, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily, maxLines = 1)
        Text(text = label, color = colors.text.muted(0.5f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
    }
}

@Composable
private fun HistoryAppRow(app: AppTotal, total: Int, period: UsagePeriod, isControlled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val percent = if (total <= 0) 0 else app.minutes * 100 / total
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        AppIcon(app.packageName, app.appName)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.appName, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
            Text(
                text = listOfNotNull(
                    formatMinutesAsDuration(app.minutes.toLong()),
                    "$percent%",
                    if (period != UsagePeriod.DAY && app.daysUsed > 0) "${app.daysUsed} kun, kuniga ~${formatMinutesAsDuration((app.minutes / app.daysUsed).toLong())}" else null
                ).joinToString(" · "),
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                maxLines = 1
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).background(colors.neutral800, RoundedCornerShape(2.dp))) {
                if (percent > 0) {
                    Row(modifier = Modifier.weight(percent.toFloat()).height(3.dp).background(colors.accent, RoundedCornerShape(2.dp))) {}
                }
                if (percent < 100) Row(modifier = Modifier.weight((100 - percent).toFloat().coerceAtLeast(0.01f))) {}
            }
        }
        Text(text = if (isControlled) "Nazoratda" else "Cheklash", color = if (isControlled) colors.text.muted(0.45f) else colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}

/**
 * Minutes per day across a week or month: bars or a line (same toggle as the hourly chart).
 * Days after [lastIndex] (the future) are left empty; tapping a day reports its index.
 */
@Composable
fun DailyUsageChart(
    values: List<Int>,
    labels: List<String?>,
    mode: ChartMode,
    color: Color,
    highlight: Int?,
    lastIndex: Int,
    onSelect: (Int) -> Unit,
    height: androidx.compose.ui.unit.Dp = 130.dp
) {
    val colors = LocalOnIkkiColors.current
    val maxValue = maxOf(30, values.maxOrNull() ?: 0)
    val n = values.size.coerceAtLeast(1)
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .pointerInput(values, lastIndex) {
                        detectTapGestures { offset ->
                            val i = (offset.x / (size.width / n.toFloat())).toInt().coerceIn(0, n - 1)
                            if (i <= lastIndex) onSelect(i)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val slot = w / n
                listOf(0f, 0.5f, 1f).forEach { f ->
                    drawLine(
                        color = colors.text.copy(alpha = if (f == 1f) 0.18f else 0.08f),
                        start = Offset(0f, h * (1 - f)),
                        end = Offset(w, h * (1 - f)),
                        strokeWidth = 1f
                    )
                }
                when (mode) {
                    ChartMode.BARS -> values.forEachIndexed { i, v ->
                        if (i > lastIndex) return@forEachIndexed
                        val barW = slot * (if (n > 10) 0.7f else 0.55f)
                        val barH = (v / maxValue.toFloat()) * h
                        if (barH <= 0f) return@forEachIndexed
                        drawRoundRect(
                            color = if (i == highlight) color else color.copy(alpha = 0.55f),
                            topLeft = Offset(i * slot + (slot - barW) / 2, h - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 3, barW / 3)
                        )
                    }
                    ChartMode.LINE -> {
                        val pts = values.take(lastIndex + 1).mapIndexed { i, v -> Offset(i * slot + slot / 2, h - (v / maxValue.toFloat()) * h) }
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
                        }
                        pts.forEachIndexed { i, p -> drawCircle(color, radius = (if (i == highlight) 4.5 else 2.5).dp.toPx(), center = p) }
                    }
                }
            }
            Text(
                text = formatMinutesAsDuration(maxValue.toLong()),
                color = colors.text.muted(0.45f),
                fontSize = 9.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 40.dp)) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = label.orEmpty(),
                    color = if (i == highlight) colors.accent else colors.text.muted(0.45f),
                    fontSize = 10.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
