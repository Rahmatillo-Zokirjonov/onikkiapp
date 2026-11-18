package com.onikki.app.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.domain.vocab.ChainMemory
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker

/** "Zanjir hikoyasi": an AI story through a part of the chain, words in order and highlighted. */
@Composable
internal fun ChainStoryCard(s: ChainState, viewModel: ChainStoryViewModel, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    val st by viewModel.state.collectAsState()
    val words = s.learnedWords
    val parts = ChainMemory.parts(words.size)
    var showUz by rememberSaveable { mutableStateOf(false) }
    // Opens on the newest part (where today's words are).
    LaunchedEffect(words.size) { if (parts.isNotEmpty()) viewModel.show(parts.lastIndex.coerceAtMost(parts.lastIndex), words) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "📖 ZANJIR HIKOYASI", color = colors.accent, style = OnIkkiType.kicker)
        Text(text = "So'zlar tartib bilan bitta hikoyada — ketma-ketlikni eslash osonlashadi", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        if (parts.isEmpty()) {
            Text(text = "Zanjirda hali so'z yo'q", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            return@Column
        }
        if (parts.size > 1) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                parts.forEachIndexed { i, r ->
                    FilterChip(selected = st.part == i, onClick = { viewModel.show(i, words) }, label = { Text("${r.first + 1}–${r.last + 1}") })
                }
            }
        }
        val story = st.story
        if (story != null) {
            Text(
                text = buildAnnotatedString {
                    ChainMemory.pieces(story.english).forEach { (t, chain) ->
                        if (chain) withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold)) { append(t) } else append(t)
                    }
                },
                color = colors.text,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background).padding(14.dp)
            )
            if (!st.inOrder) Text(text = "⚠ Hikoyada ba'zi so'zlar tartibi buzilgan — yangisini tuzdiring", color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            if (story.uzbek.isNotBlank()) {
                Text(
                    text = if (showUz) story.uzbek else "🇺🇿 Tarjimasini ko'rsatish",
                    color = colors.text.muted(if (showUz) 0.75f else 0.55f),
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showUz = !showUz }.padding(vertical = 6.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnIkkiButton(text = "🔊 O'qib berish", onClick = { speaker.speak(ChainMemory.pieces(story.english).joinToString("") { it.first }) }, modifier = Modifier.weight(1f))
                OnIkkiButton(text = if (st.loading) "Tuzilmoqda…" else "↻ Yangisi", onClick = { viewModel.generate(words) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.weight(1f))
            }
        } else {
            Text(
                text = "AI shu ${ChainMemory.parts(words.size)[st.part.coerceIn(0, parts.lastIndex)].let { it.last - it.first + 1 }} ta so'zdan ular tartibi bo'yicha qisqa hikoya tuzadi. Bir marta tuziladi va saqlanadi (internet kerak).",
                color = colors.text.muted(0.55f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
            OnIkkiButton(text = if (st.loading) "Tuzilmoqda…" else "✨ Hikoya tuzish", onClick = { viewModel.generate(words) }, modifier = Modifier.fillMaxWidth())
        }
        st.error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
    }
}

/** Turns the chain-word notifications on or off. */
@Composable
internal fun ChainNotifyRow(viewModel: ChainStoryViewModel) {
    val colors = LocalOnIkkiColors.current
    val st by viewModel.state.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).clickable { viewModel.setNotify(!st.notify) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "🔔 Kun davomida zanjir so'zi", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = "Soat ${ChainMemory.REMINDER_HOURS.joinToString(", ") { "$it:00" }} da bittadan so'z — eng qiyinlaridan",
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Switch(checked = st.notify, onCheckedChange = viewModel::setNotify)
    }
}
