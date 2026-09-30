package com.onikki.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Note
import com.onikki.app.domain.notes.Journal
import com.onikki.app.domain.notes.NoteFormat
import com.onikki.app.ui.components.HeroCard
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.monthNameUz
import com.onikki.app.ui.util.weekdayAbbrUz
import com.onikki.app.ui.util.weekdayNameUz
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

fun journalTitle(date: LocalDate): String =
    "${date.dayOfMonth}-${monthNameUz(date.monthValue)}, ${weekdayNameUz(date.dayOfWeek).lowercase()}"

/** Kundalik: one entry per day, a month calendar with dots, and the writing streak. */
@Composable
fun JournalScreen(state: NotesUiState, viewModel: NotesViewModel, onOpen: (Note) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val byDate = state.journalEntries.associateBy { it.journalDate!! }
    val streak = Journal.streak(byDate.keys, today)
    val todays = byDate[today]
    BackHandler(onBack = viewModel::closeJournal)

    fun openDay(date: LocalDate) {
        val entry = byDate[date]
        if (entry != null) onOpen(entry) else viewModel.openJournalDay(date)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader(title = "Kundalik", onBack = viewModel::closeJournal) }
        item {
            HeroCard(gap = 10.dp) {
                Text(text = if (streak > 0) "🔥 $streak kun ketma-ket" else "KUNDALIK", color = colors.accent, style = OnIkkiType.kicker)
                Text(
                    text = if (todays != null) "Bugungi kundalik yozilgan" else "Bugun nima bo'ldi?",
                    color = colors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
                Text(
                    text = if (todays != null) NoteFormat.plain(todays.content).lineSequence().firstOrNull { it.isNotBlank() && !it.endsWith("?") }.orEmpty().ifBlank { "Davom ettirish uchun oching" }
                    else "Har kuni 3–5 daqiqa: nima yaxshi bo'ldi, nimani o'rgandingiz, ertaga eng muhim ish.",
                    color = colors.text.muted(0.65f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OnIkkiButton(
                    text = if (todays != null) "Ochish" else "Bugungini yozish",
                    onClick = { openDay(today) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "‹", color = colors.text, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { monthText = month.minusMonths(1).toString() }.padding(horizontal = 12.dp))
                    Text(
                        text = "${monthNameUz(month.monthValue).replaceFirstChar { it.uppercase() }} ${month.year}",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OnIkkiFontFamily,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    val canNext = month.isBefore(YearMonth.from(today))
                    Text(
                        text = "›",
                        color = if (canNext) colors.text else colors.text.muted(0.2f),
                        fontSize = 22.sp,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = canNext) { monthText = month.plusMonths(1).toString() }.padding(horizontal = 12.dp)
                    )
                }
                Row {
                    DayOfWeek.entries.forEach { d ->
                        Text(text = weekdayAbbrUz(d), color = colors.text.muted(0.45f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                val first = month.atDay(1)
                val offset = first.dayOfWeek.value - 1
                val cells = offset + month.lengthOfMonth()
                (0 until (cells + 6) / 7).forEach { week ->
                    Row {
                        (0 until 7).forEach { col ->
                            val dayIndex = week * 7 + col - offset
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (dayIndex in 0 until month.lengthOfMonth()) {
                                    val date = month.atDay(dayIndex + 1)
                                    val has = date in byDate
                                    val future = date.isAfter(today)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(if (has) colors.accent800 else colors.background.copy(alpha = 0f))
                                            .border(BorderStroke(1.dp, if (date == today) colors.accent else colors.background.copy(alpha = 0f)), CircleShape)
                                            .clickable(enabled = !future) { openDay(date) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${date.dayOfMonth}",
                                            color = when {
                                                future -> colors.text.muted(0.2f)
                                                has -> colors.accent100
                                                else -> colors.text.muted(0.7f)
                                            },
                                            fontSize = 13.sp,
                                            fontFamily = OnIkkiFontFamily
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "${state.journalEntries.count { YearMonth.from(it.journalDate) == month }} kun yozilgan · kunni bossangiz ochiladi",
                    color = colors.text.muted(0.45f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
        if (state.journalEntries.isNotEmpty()) {
            item { Text(text = "OXIRGI YOZUVLAR", color = colors.text.muted(0.45f), style = OnIkkiType.kicker) }
            items(state.journalEntries.take(20), key = { it.id }) { entry ->
                val preview = NoteFormat.plain(entry.content).lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.endsWith("?") }
                    .take(2).joinToString(" · ")
                OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) }, gap = 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = journalTitle(entry.journalDate!!), color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                        if (entry.locked) Text(text = "🔒", fontSize = 12.sp)
                        if ((state.attachments[entry.id]?.size ?: 0) > 0) Text(text = " 📎", fontSize = 12.sp)
                    }
                    if (!entry.locked && preview.isNotBlank()) {
                        Text(text = preview, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
