package com.onikki.app.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.weekdayAbbrUz
import com.onikki.app.ui.vocabulary.rememberSpeaker

@Composable
fun LearnStatsScreen(
    state: LearnStatsState,
    onBack: () -> Unit,
    modes: Pair<List<com.onikki.app.domain.vocab.ModeStat>, Pair<Int?, Int?>> = emptyList<com.onikki.app.domain.vocab.ModeStat>() to (null to null)
) {
    val colors = LocalOnIkkiColors.current
    val s = state.stats
    val speaker = rememberSpeaker()
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubScreenHeader(title = "Statistika", onBack = onBack)

        // Words by status
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberTile("Jami", s.total, colors.text, Modifier.weight(1f))
            NumberTile("O'rganilgan", s.learned, colors.planAccent, Modifier.weight(1f))
            NumberTile("O'zlashtirilgan", s.mastered, colors.accent, Modifier.weight(1f))
        }
        if (s.total > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(colors.neutral800)) {
                    listOf(s.mastered to colors.accent, s.review to colors.habitAccent, s.learning to colors.planAccent, s.new to colors.neutral700)
                        .filter { it.first > 0 }
                        .forEach { (n, c) -> Box(modifier = Modifier.weight(n.toFloat()).fillMaxHeight().background(c)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Legend("O'zlashtirilgan ${s.mastered}", colors.accent)
                    Legend("Takrorlash ${s.review}", colors.habitAccent)
                    Legend("O'rganilmoqda ${s.learning}", colors.planAccent)
                    Legend("Yangi ${s.new}", colors.neutral700)
                }
            }
        }

        // Streak
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔥", fontSize = 34.sp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = "${s.streak} kun ketma-ket", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                Text(text = "Eng uzun: ${s.bestStreak} kun · jami ${state.totalXp} XP", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }

        // Weekly XP chart
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Haftalik XP", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                Text(text = "${s.weekXp} XP" + (s.weekAccuracy?.let { " · aniqlik $it%" } ?: ""), color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
            val max = (s.week.maxOfOrNull { it.xp } ?: 0).coerceAtLeast(10)
            Row(modifier = Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                s.week.forEachIndexed { i, d ->
                    val isToday = i == s.week.lastIndex
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (d.xp > 0) Text(text = "${d.xp}", color = if (isToday) colors.accent else colors.text.muted(0.6f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((d.xp / max.toFloat()).coerceIn(0.03f, 0.85f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (isToday) colors.accent else if (d.xp > 0) colors.accent700 else colors.neutral800)
                        )
                        Text(text = weekdayAbbrUz(d.day.dayOfWeek), color = if (isToday) colors.accent else colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // Today and what's coming
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberTile("Bugun javob", s.today?.answers ?: 0, colors.text, Modifier.weight(1f))
            NumberTile("Bugun muddati", s.dueToday, colors.warmAccent, Modifier.weight(1f))
            NumberTile("Ertaga", s.dueTomorrow, colors.text, Modifier.weight(1f))
        }
        Text(text = "Keyingi 7 kunda yana ${s.dueThisWeek} ta takrorlash kutilmoqda", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)

        // Every mode in one place: accuracy this week, and against last week.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val (thisWeek, lastWeek) = modes.second
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Rejimlar bo'yicha aniqlik (7 kun)", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                thisWeek?.let { now ->
                    val delta = lastWeek?.let { now - it }
                    Text(
                        text = "$now%" + (delta?.let { if (it >= 0) "  ▲$it" else "  ▼${-it}" } ?: ""),
                        color = if ((delta ?: 0) >= 0) colors.accent else colors.warmAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
            if (modes.first.isEmpty()) {
                Text(text = "Bu hafta hali mashq yo'q.", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
            modes.first.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row {
                        Text(text = m.label, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                        Text(
                            text = "${m.accuracy}% · ${m.answers} ta" + (m.lastWeek?.let { lw -> val d = m.accuracy - lw; if (d >= 0) " ▲$d" else " ▼${-d}" } ?: ""),
                            color = colors.text.muted(0.7f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                    com.onikki.app.ui.components.LinearProgressTrack(
                        progress = m.accuracy / 100f,
                        trackColor = colors.neutral800,
                        progressColor = if (m.accuracy >= 80) colors.accent else if (m.accuracy >= 60) colors.habitAccent else colors.warmAccent,
                        height = 6.dp
                    )
                }
            }
            Text(text = "▲/▼ — o'tgan haftaga nisbatan", color = colors.text.muted(0.4f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        }

        // Hardest words
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "Eng qiyin so'zlar", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            if (state.hardest.isEmpty()) {
                Text(text = "Hali xato yo'q — davom eting!", color = colors.text.muted(0.55f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
            state.hardest.forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = h.word.english + if (com.onikki.app.domain.vocab.Relearn.isLeech(h.word)) "  · qiyin" else "",
                            color = colors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(text = Exercises.primary(h.word.uzbek), color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(text = "✗ ${h.wrong}/${h.total}", color = colors.warmAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(horizontal = 10.dp))
                    Text(
                        text = "🔊",
                        fontSize = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = speaker.ready) { speaker.speak(h.word.english) }.padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberTile(label: String, value: Int, tint: Color, modifier: Modifier) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$value", color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = label, color = colors.text.muted(0.55f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    val colors = LocalOnIkkiColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "● ", color = color, fontSize = 10.sp)
        Text(text = text, color = colors.text.muted(0.6f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily, maxLines = 1)
    }
}
