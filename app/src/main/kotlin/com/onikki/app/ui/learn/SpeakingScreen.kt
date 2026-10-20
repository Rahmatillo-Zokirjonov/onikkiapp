package com.onikki.app.ui.learn

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.domain.podcast.Podcasts
import com.onikki.app.domain.speaking.SpeakingDeck
import com.onikki.app.ui.components.LinearProgressTrack
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** O'rganish → Speaking: read sentences aloud and see which words were understood. */
@Composable
fun SpeakingRoute(suggested: String, onBack: () -> Unit) {
    val viewModel: SpeakingViewModel = viewModel()
    LaunchedEffect(suggested) { viewModel.setSuggested(suggested) }
    val home by viewModel.home.collectAsState()
    val read by viewModel.read.collectAsState()
    val answer by viewModel.answer.collectAsState()
    val speaker = rememberSpeaker()
    val mic = rememberSpeechInput()
    val r = read
    val a = answer
    if (a != null) {
        BackHandler { mic.cancel(); viewModel.closeAnswer() }
        AnswerScreen(a, speaker, mic, viewModel)
    } else if (r == null) {
        BackHandler(onBack = onBack)
        SpeakingHome(home, suggested, viewModel, mic.available, onBack)
    } else {
        BackHandler { mic.cancel(); viewModel.closeRead() }
        ReadRound(r, speaker, mic, viewModel)
    }
}

@Composable
private fun SpeakingHome(state: SpeakingHomeState, suggested: String, viewModel: SpeakingViewModel, available: Boolean, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubScreenHeader(title = "Speaking", onBack = onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Podcasts.LEVELS.forEach { l -> FilterChip(selected = state.level == l, onClick = { viewModel.setLevel(l) }, label = { Text(if (l == suggested) "$l ★" else l) }) }
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "🗣 OVOZ CHIQARIB O'QISH", color = colors.accent, style = OnIkkiType.kicker)
            Text(text = "${SpeakingDeck.ROUND} ta gap · talaffuz bahosi", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(
                text = "Gapni eshiting, keyin o'zingiz o'qing. Telefon tushungan so'zlar yashil, tushunmaganlari qizil bo'ladi. Avval o'z so'zlaringizning misollari, keyin ${state.level} grammatika misollari.",
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = listOfNotNull(
                    "bugun ${state.readToday} ta gap",
                    state.recentAverage?.let { "oxirgi 20 ta: o'rtacha $it%" }
                ).joinToString(" · "),
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            OnIkkiButton(text = "Boshlash", onClick = viewModel::startRead, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
        }
        // Oral answers.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surface).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "💬 OG'ZAKI JAVOB", color = colors.accent, style = OnIkkiType.kicker)
            Text(text = "Savolga 30–60 soniya gapiring", color = colors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(
                text = "AI kalit bo'lsa — grammatika, so'z boyligi va mavzuga moslik foizda, tuzatilgan variant va o'zbekcha izoh bilan. Kalitsiz — uzunlik va so'z boyligi.",
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
            com.onikki.app.domain.speaking.OralPrompts.forLevel(state.level).forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background).clickable { viewModel.openAnswer(p) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = p.question, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                        Text(text = p.questionUz, color = colors.text.muted(0.5f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                    state.answerBest[p.id]?.let { Text(text = "$it%", color = if (it >= 75) colors.accent else colors.warmAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(start = 8.dp)) }
                }
            }
            OnIkkiButton(text = "🎲 Tasodifiy savol", onClick = viewModel::randomAnswer, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        }
        if (!available) {
            Text(
                text = "⚠ Telefonda ovozni tanish xizmati topilmadi. Google ilovasini o'rnating yoki yangilang.",
                color = colors.warmAccent,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Text(
            text = "Ovozni telefonning tanish xizmati (odatda Google) matnga aylantiradi — internet kerak bo'lishi mumkin.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadRound(s: ReadSession, speaker: Speaker, mic: SpeechInput, viewModel: SpeakingViewModel) {
    val colors = LocalOnIkkiColors.current
    if (s.done) {
        ReadResult(s, speaker, onAgain = viewModel::startRead, onClose = viewModel::closeRead)
        return
    }
    val item = s.current ?: return
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) mic.start { viewModel.heard(it) }
    }
    fun listen() {
        speaker.stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) mic.start { viewModel.heard(it) }
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(s.index, speaker.ready) { if (speaker.ready) speaker.speak(item.english) }
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { mic.cancel(); viewModel.closeRead() }.padding(horizontal = 10.dp, vertical = 4.dp))
            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                LinearProgressTrack(progress = s.index / s.items.size.toFloat(), trackColor = colors.neutral800, progressColor = colors.habitAccent, height = 12.dp)
            }
            Text(text = "⭐ ${s.xp}", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Ovoz chiqarib o'qing" + (item.word?.let { " · so'z: $it" } ?: ""), color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
            // The sentence: plain before a try, coloured by word after.
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val result = s.result
                if (result == null) {
                    Text(text = item.english, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, fontFamily = OnIkkiFontFamily)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.words.forEach { w ->
                            Text(
                                text = w.text,
                                color = if (w.heard) colors.accent else colors.warmAccent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = OnIkkiFontFamily,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = speaker.ready) { speaker.speakSlow(w.text) }
                            )
                        }
                    }
                }
                item.uzbek?.let { Text(text = it, color = colors.text.muted(0.55f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallRound("🔊", enabled = speaker.ready) { speaker.speak(item.english) }
                    SmallRound("🐢", enabled = speaker.ready) { speaker.speakSlow(item.english) }
                }
            }
            s.result?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${r.percent}%",
                        color = if (r.percent >= 80) colors.accent else if (r.percent >= 50) colors.habitAccent else colors.warmAccent,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OnIkkiFontFamily
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = when {
                                r.percent >= 90 -> "Zo'r talaffuz!"
                                r.percent >= 70 -> "Yaxshi! Qizil so'zlarni bosib eshiting"
                                r.percent >= 40 -> "Qizil so'zlarni tinglab, yana urinib ko'ring"
                                else -> "Sekinroq va aniqroq o'qib ko'ring"
                            },
                            color = colors.text,
                            fontSize = 14.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(text = "Eshitildi: \"${r.heardText.ifBlank { "—" }}\"", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    }
                }
            }
            mic.error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            if (mic.listening && mic.partial.isNotBlank()) {
                Text(text = mic.partial, color = colors.text.muted(0.6f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MicButton(mic, onTap = { if (mic.listening) mic.stop() else listen() })
            Text(
                text = when {
                    mic.listening -> "Gapiring… (tugatgach o'zi to'xtaydi)"
                    s.tries == 0 -> "🎤 ni bosing va gapni o'qing"
                    else -> "Yana urinish uchun 🎤 · eng yaxshi natija saqlanadi"
                },
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            OnIkkiButton(
                text = if (s.tries == 0) "O'tkazib yuborish" else if (s.index + 1 >= s.items.size) "Yakunlash" else "Keyingi gap",
                onClick = { mic.cancel(); viewModel.next() },
                variant = if (s.tries == 0) OnIkkiButtonVariant.SECONDARY else com.onikki.app.ui.components.OnIkkiButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MicButton(mic: SpeechInput, onTap: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val pulse by animateFloatAsState(if (mic.listening) 1f + mic.level * 0.35f else 1f, label = "mic")
    Box(contentAlignment = Alignment.Center) {
        if (mic.listening) Box(modifier = Modifier.size(84.dp).scale(pulse).clip(CircleShape).background(colors.warmAccent.copy(alpha = 0.25f)))
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(if (mic.listening) colors.warmAccent else colors.accent).alpha(if (mic.available) 1f else 0.4f).clickable(enabled = mic.available, onClick = onTap),
            contentAlignment = Alignment.Center
        ) { Text(text = if (mic.listening) "■" else "🎤", color = Color(0xFF10131C), fontSize = 28.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SmallRound(icon: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent800).alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text = icon, fontSize = 18.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadResult(s: ReadSession, speaker: Speaker, onAgain: () -> Unit, onClose: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = if (s.average >= 85) "🌟" else if (s.average >= 60) "🗣" else "💪", fontSize = 68.sp, modifier = Modifier.padding(top = 30.dp))
        Text(text = "O'rtacha talaffuz: ${s.average}%", color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center)
        Text(text = "${s.best.size} ta gap o'qildi · +${s.xp} XP", color = colors.text.muted(0.65f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        if (s.missed.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "Mashq qilish kerak bo'lgan so'zlar (bosib eshiting)", color = colors.warmAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.missed.take(20).forEach { w ->
                        Text(
                            text = "🔊 $w",
                            color = colors.text,
                            fontSize = 15.sp,
                            fontFamily = OnIkkiFontFamily,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.background).clickable(enabled = speaker.ready) { speaker.speakSlow(w) }.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        OnIkkiButton(text = "Yana ${SpeakingDeck.ROUND} ta gap", onClick = onAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
        OnIkkiButton(text = "Yopish", onClick = onClose, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp))
    }
}

// ---------------------------------------------------------------- oral answer

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerScreen(s: AnswerSession, speaker: Speaker, mic: SpeechInput, viewModel: SpeakingViewModel) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val p = s.prompt
    fun record() {
        speaker.stop()
        viewModel.recordingStarted()
        mic.start(continuous = true) { list -> viewModel.recorded(list.firstOrNull().orEmpty()) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) record() }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // A recognizer error with nothing heard sends the user back to try again.
    LaunchedEffect(mic.error, mic.listening) {
        if (s.phase == AnswerPhase.RECORDING && !mic.listening && mic.error != null) viewModel.retake()
    }
    // Live seconds while recording.
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.phase) {
        while (s.phase == AnswerPhase.RECORDING) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(250) }
    }
    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "✕", color = colors.text.muted(0.7f), fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { mic.cancel(); viewModel.closeAnswer() }.padding(horizontal = 10.dp, vertical = 4.dp))
            Text(text = "Og'zaki javob · ${p.level}", color = colors.text.muted(0.6f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(start = 6.dp))
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = p.question, color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, fontFamily = OnIkkiFontFamily)
                Text(text = p.questionUz, color = colors.text.muted(0.55f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                SmallRound("🔊", enabled = speaker.ready) { speaker.speak(p.question) }
            }
            when (s.phase) {
                AnswerPhase.PREP -> {
                    Text(text = "FOYDALI IBORALAR", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.hints.forEach { h ->
                            Text(text = h, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable(enabled = speaker.ready) { speaker.speak(h) }.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                    Text(
                        text = "Kamida ~${p.targetWords} so'z (30–60 soniya). Pauza qilsangiz ham tinglash davom etadi — tugatgach ■ ni bosing.",
                        color = colors.text.muted(0.55f),
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                    mic.error?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
                }
                AnswerPhase.RECORDING -> {
                    val secs = ((now - s.startedAt) / 1000).coerceAtLeast(0)
                    Text(text = "%d:%02d".format(secs / 60, secs % 60), color = if (secs >= 30) colors.accent else colors.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(text = mic.partial.ifBlank { "Gapiring…" }, color = colors.text.muted(0.75f), fontSize = 16.sp, lineHeight = 23.sp, fontFamily = OnIkkiFontFamily)
                }
                AnswerPhase.REVIEW, AnswerPhase.CHECKING -> {
                    Text(text = "SIZ AYTDINGIZ", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
                    Text(
                        text = s.transcript.ifBlank { "Hech narsa eshitilmadi." },
                        color = colors.text,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp)
                    )
                    Text(
                        text = "${com.onikki.app.domain.vocab.SentenceCheck.words(s.transcript).size} so'z · ${s.seconds} soniya",
                        color = colors.text.muted(0.55f),
                        fontSize = 13.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                    if (s.phase == AnswerPhase.CHECKING) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
                            Text(text = "Baholanmoqda…", color = colors.text.muted(0.7f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                        }
                    }
                }
                AnswerPhase.RESULT -> s.result?.let { OralResultView(it, s, speaker) }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (s.phase) {
                AnswerPhase.PREP -> {
                    MicButton(mic, onTap = ::startRecording)
                    Text(text = "🎤 ni bosib javob bering", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                AnswerPhase.RECORDING -> {
                    MicButton(mic, onTap = { mic.stop() })
                    Text(text = "Tugatish uchun ■", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                AnswerPhase.REVIEW -> {
                    OnIkkiButton(text = "Baholash", onClick = viewModel::evaluate, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).alpha(if (s.transcript.isNotBlank()) 1f else 0.5f))
                    OnIkkiButton(text = "Qayta gapirish", onClick = viewModel::retake, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
                AnswerPhase.CHECKING -> Unit
                AnswerPhase.RESULT -> {
                    OnIkkiButton(text = "🎲 Boshqa savol", onClick = viewModel::randomAnswer, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp))
                    OnIkkiButton(text = "Shu savolga qayta javob berish", onClick = viewModel::retake, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OralResultView(r: com.onikki.app.domain.speaking.OralResult, s: AnswerSession, speaker: Speaker) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = "${r.overall}%", color = if (r.overall >= 75) colors.accent else if (r.overall >= 50) colors.habitAccent else colors.warmAccent, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
        Text(text = if (r.byAi) "AI bahosi" else "Oddiy tekshiruv (AI'siz)", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        s.aiNote?.let { Text(text = it, color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily, textAlign = TextAlign.Center) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        r.grammar?.let { ScoreBar("Grammatika", it) }
        ScoreBar("So'z boyligi", r.vocabulary)
        r.relevance?.let { ScoreBar("Savolga moslik", it) }
        ScoreBar("Uzunlik", r.length)
    }
    if (r.feedback.isNotBlank()) {
        Text(text = r.feedback, color = colors.text.muted(0.85f), fontSize = 14.sp, lineHeight = 21.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp))
    }
    if (r.mistakes.isNotEmpty()) {
        Text(text = "XATOLAR", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        r.mistakes.forEach { m ->
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = "✗ ${m.wrong}", color = colors.warmAccent, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                Text(text = "✓ ${m.right}", color = colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                if (m.why.isNotBlank()) Text(text = m.why, color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            }
        }
    }
    r.corrected?.let { c ->
        Text(text = "TO'G'RILANGAN VARIANT", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = c, color = colors.text, fontSize = 15.sp, lineHeight = 22.sp, fontFamily = OnIkkiFontFamily)
            SmallRound("🔊", enabled = speaker.ready) { speaker.speak(c) }
        }
    }
    Text(text = "SIZNING JAVOBINGIZ", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
    Text(text = s.transcript, color = colors.text.muted(0.7f), fontSize = 14.sp, lineHeight = 21.sp, fontFamily = OnIkkiFontFamily)
}

@Composable
private fun ScoreBar(label: String, value: Int) {
    val colors = LocalOnIkkiColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(text = label, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
            Text(text = "$value%", color = colors.text.muted(0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        }
        LinearProgressTrack(progress = value / 100f, trackColor = colors.neutral800, progressColor = if (value >= 75) colors.accent else if (value >= 50) colors.habitAccent else colors.warmAccent, height = 7.dp)
    }
}
