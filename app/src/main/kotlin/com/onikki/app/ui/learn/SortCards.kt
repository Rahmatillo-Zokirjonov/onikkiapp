package com.onikki.app.ui.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.VocabWord
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.Relearn
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.vocabulary.SpeakButton
import com.onikki.app.ui.vocabulary.rememberSpeaker
import com.onikki.app.ui.theme.muted

/**
 * "Bilaman / Bilmayman": new words one by one. Known ones skip the lesson cards (checked again in 3 days),
 * unknown ones go to "Bilmaganlarim" and can be learned right after.
 */
@Composable
fun SortScreen(s: SortSession, onReveal: () -> Unit, onAnswer: (Boolean) -> Unit, onRelearn: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 10.dp, vertical = 4.dp))
            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                LinearProgressTrack(progress = s.index / s.words.size.coerceAtLeast(1).toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent, height = 12.dp)
            }
            Text(text = "${minOf(s.index + 1, s.words.size)}/${s.words.size}", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        }
        val word = s.current
        if (word == null) {
            SortResult(s, onRelearn, onClose)
            return@Column
        }
        LaunchedEffect(word.id, speaker.ready) { if (speaker.ready) speaker.speak(word.english) }
        Text(text = "Bu so'zni bilasizmi?", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surface)
                .border(BorderStroke(1.dp, colors.heroBorder), RoundedCornerShape(26.dp))
                .clickable(enabled = !s.revealed, onClick = onReveal)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Text(text = word.english, color = colors.text, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
            SpeakButton(speaker, word.english, big = true)
            if (s.revealed) {
                Text(text = word.uzbek, color = colors.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
                word.example?.let { Text(text = it, color = colors.text.muted(0.7f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center) }
            } else {
                Text(text = "Ma'nosini ko'rish uchun bosing", color = colors.text.muted(0.45f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SortButton("Bilmayman", colors.warmAccent, Modifier.weight(1f)) { onAnswer(false) }
            SortButton("Bilaman", colors.accent, Modifier.weight(1f)) { onAnswer(true) }
        }
        Text(
            text = "Bilgan so'zingiz darsda kartadan o'tkazilmaydi, 3 kundan keyin tekshiriladi. Bilmaganingiz \"Bilmaganlarim\"ga tushadi.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@Composable
private fun SortResult(s: SortSession, onRelearn: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "🗂", fontSize = 64.sp)
        Text(text = "Saralash tugadi", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(
            text = "${s.known} ta so'zni bilasiz · ${s.unknown.size} tasi Bilmaganlarimga qo'shildi",
            color = colors.text.muted(0.7f),
            fontSize = 15.sp,
            fontFamily = OnIkkiFontFamily,
            textAlign = TextAlign.Center
        )
        if (s.unknown.isNotEmpty()) {
            SortButton("Bilmaganlarni hozir o'rganish (${minOf(s.unknown.size, Relearn.MAX_WORDS)})", colors.accent, Modifier.fillMaxWidth(), onClick = onRelearn)
        }
        SortButton("Yopish", colors.surface, Modifier.fillMaxWidth(), onClick = onClose)
    }
}

@Composable
private fun SortButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val dark = color != colors.surface
    Box(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .border(BorderStroke(1.dp, if (dark) Color.Transparent else colors.cardBorder), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = if (dark) Color(0xFF10131C) else colors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
    }
}

/** The whole "Bilmaganlarim" list: listen, or take a word off it. */
@Composable
fun UnknownListSheet(words: List<VocabWord>, onRemove: (VocabWord) -> Unit, onStudy: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val speaker = rememberSpeaker()
    OnIkkiSheet(title = "Bilmaganlarim · ${words.size}", onDismiss = onDismiss) {
        Text(
            text = "Xato qilgan yoki \"Bilmayman\" degan so'zlaringiz. Har biri ${Relearn.CLEAR_AFTER} ta alohida mashqda to'g'ri topilsa, ro'yxatdan o'zi chiqadi.",
            color = colors.text.muted(0.55f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        if (words.isNotEmpty()) SortButton("Takrorlash", colors.accent, Modifier.fillMaxWidth()) { onDismiss(); onStudy() }
        words.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = w.english, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                        if (Relearn.isLeech(w)) Text(text = "  qiyin", color = colors.warmAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                    }
                    Text(text = Exercises.primary(w.uzbek), color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(text = "✓".repeat(w.relearnHits) + "○".repeat((Relearn.CLEAR_AFTER - w.relearnHits).coerceAtLeast(0)), color = colors.accent, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text(text = "🔊", fontSize = 18.sp, modifier = Modifier.clip(CircleShape).clickable(enabled = speaker.ready) { speaker.speak(w.english) }.padding(6.dp))
                Text(text = "✕", color = colors.text.muted(0.5f), fontSize = 16.sp, modifier = Modifier.clip(CircleShape).clickable { onRemove(w) }.padding(6.dp))
            }
        }
    }
}
