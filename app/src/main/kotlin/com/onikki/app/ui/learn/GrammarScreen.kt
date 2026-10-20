package com.onikki.app.ui.learn

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.grammar.Grammar
import com.onikki.app.domain.grammar.GrammarTopic
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.vocabulary.Speaker
import com.onikki.app.ui.vocabulary.rememberSpeaker

/** O'rganish → Grammatika: topics by level; a topic opens its explanation. */
@Composable
fun GrammarRoute(suggested: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LearnPrefs(context) }
    var level by rememberSaveable { mutableStateOf(prefs.grammarLevel() ?: suggested) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val speaker = rememberSpeaker()
    val open = openId?.let(Grammar::byId)
    if (open != null) {
        BackHandler { openId = null }
        TopicScreen(open, speaker, onBack = { openId = null })
    } else {
        BackHandler(onBack = onBack)
        TopicList(level, suggested, onLevel = { level = it; prefs.setGrammarLevel(it) }, onOpen = { openId = it.id }, onBack = onBack)
    }
}

@Composable
private fun TopicList(level: String, suggested: String, onLevel: (String) -> Unit, onOpen: (GrammarTopic) -> Unit, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val topics = Grammar.forLevel(level)
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubScreenHeader(title = "Grammatika", onBack = onBack)
        Text(
            text = "Har bir mavzuda qisqa o'zbekcha tushuntirish, qoida va misollar. ★ — so'zlaringizga qarab tavsiya qilingan daraja.",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Grammar.LEVELS.forEach { l ->
                FilterChip(selected = level == l, onClick = { onLevel(l) }, label = { Text(if (l == suggested) "$l ★" else l) })
            }
        }
        topics.forEachIndexed { i, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
                    .clickable { onOpen(t) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accent800), contentAlignment = Alignment.Center) {
                    Text(text = "${i + 1}", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = t.title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
                    Text(text = t.titleUz, color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                }
                Text(text = "›", color = colors.text.muted(0.4f), fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun TopicScreen(t: GrammarTopic, speaker: Speaker, onBack: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SubScreenHeader(title = t.level, onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = t.title, color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = OnIkkiFontFamily)
            Text(text = t.titleUz, color = colors.text.muted(0.6f), fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
        }
        // The pattern at a glance.
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.accent.copy(alpha = 0.12f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "QOIDA", color = colors.accent, style = OnIkkiType.kicker)
            Text(text = t.formula, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            t.explanation.lines().filter { it.isNotBlank() }.forEach { line ->
                val bullet = line.trimStart().startsWith("•")
                Row {
                    if (bullet) Text(text = "•  ", color = colors.accent, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                    Text(
                        text = line.trim().removePrefix("•").trim(),
                        color = colors.text.muted(if (bullet) 0.9f else 0.8f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
        Text(text = "MISOLLAR", color = colors.text.muted(0.55f), style = OnIkkiType.kicker)
        t.examples.forEach { e ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).clickable(enabled = speaker.ready) { speaker.speak(e.english) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = e.english, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                    Text(text = e.uzbek, color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
                }
                Text(text = "🔊", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp).alpha(if (speaker.ready) 1f else 0.3f))
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Mashqlar — keyingi bosqichda", color = colors.text.muted(0.45f), fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        }
    }
}
