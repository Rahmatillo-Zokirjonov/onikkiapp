package com.onikki.app.ui.assistant

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.dayreview.ApiKeySheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

@Composable
fun AssistantRoute(onBack: () -> Unit) {
    val viewModel: AssistantViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val colors = LocalOnIkkiColors.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    BackHandler(onBack = onBack)

    val itemCount = state.messages.size + (if (state.isThinking) 1 else 0)
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount) }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).imePadding()) {
        Box(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp)) {
            SubScreenHeader(title = "AI yordamchi", onBack = onBack) {
                if (state.messages.isNotEmpty()) {
                    OnIkkiButton(text = "Tozalash", onClick = viewModel::clear, variant = OnIkkiButtonVariant.SECONDARY, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp))
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "✨ Salom!", color = colors.accent, style = OnIkkiType.kicker)
                        Text(
                            text = "Men vazifalaringiz, odatlaringiz, maqsadlaringiz, moliyangiz va ekran vaqtingizni ko'rib turaman. " +
                                "Savol bering — shu ma'lumotlarga qarab javob beraman.",
                            color = colors.text.muted(0.75f),
                            fontSize = 14.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        Text(
                            text = "Har bir savolda shu ma'lumotlarning qisqa xulosasi sizning kalitingiz orqali Anthropic'ga yuboriladi.",
                            color = colors.text.muted(0.45f),
                            fontSize = 11.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                }
                items(ASSISTANT_SUGGESTIONS) { suggestion ->
                    Text(
                        text = suggestion,
                        color = colors.text,
                        fontSize = 14.sp,
                        fontFamily = OnIkkiFontFamily,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { viewModel.send(suggestion) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
            items(state.messages) { message -> Bubble(message, onRetry = viewModel::retry) }
            if (state.isThinking) {
                item { Text(text = "O'ylayapti…", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            }
            state.error?.let { error ->
                item { Text(text = error, color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Savolingiz…") },
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            OnIkkiButton(
                text = "↑",
                onClick = { viewModel.send(input); input = "" },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }

    if (state.needsKey) ApiKeySheet(onDismiss = viewModel::dismissKey, onSave = viewModel::saveKey)
}

@Composable
private fun Bubble(message: ChatMessage, onRetry: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start) {
            SelectionContainer {
                Text(
                    text = message.text,
                    color = if (message.fromUser) colors.onAccent else colors.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .background(
                            if (message.fromUser) colors.accent else colors.surface,
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (message.fromUser) 16.dp else 4.dp, bottomEnd = if (message.fromUser) 4.dp else 16.dp)
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                )
            }
            if (message.failed) {
                Text(
                    text = "Yuborilmadi · qayta urinish",
                    color = colors.warmAccent,
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable(onClick = onRetry).padding(top = 3.dp)
                )
            }
        }
    }
}
