package com.onikki.app.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.repository.BlockReason
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.delay

private const val WAIT_SECONDS = 15

@Composable
fun BlockedScreen(
    appName: String,
    reason: BlockReason,
    onClose: () -> Unit,
    onEmergencyUnlock: () -> Unit
) {
    BackHandler(onBack = onClose)
    val colors = LocalOnIkkiColors.current
    var waiting by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(WAIT_SECONDS) }

    LaunchedEffect(waiting) {
        if (waiting) {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
            onEmergencyUnlock()
        }
    }

    val (kicker, title, message) = blockReasonCopy(appName, reason)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(26.dp),
        contentAlignment = Alignment.Center
    ) {
        OnIkkiCard(
            modifier = Modifier.fillMaxWidth(),
            padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 26.dp),
            gap = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingWithBadge(appName = appName)
            }
            Text(
                text = kicker.uppercase(),
                color = colors.warmAccent,
                style = OnIkkiType.kicker,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Text(
                text = title,
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                text = message,
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(vertical = 18.dp)
                    .background(colors.divider)
            )
            OnIkkiButton(
                text = "Yopish",
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 13.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable(enabled = !waiting) { waiting = true },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (waiting) "$secondsLeft soniya qoldi…" else "15 soniya kutib ochish",
                    color = colors.text.muted(0.7f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Text(
                text = "favqulodda holat uchun",
                color = colors.text.muted(0.4f),
                fontSize = 10.sp,
                fontFamily = OnIkkiFontFamily,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RingWithBadge(appName: String) {
    val colors = LocalOnIkkiColors.current
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(92.dp)) {
            val stroke = 3.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = colors.neutral800,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                color = colors.warmAccent,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.neutral800, OnIkkiShapes.large),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(2).uppercase(),
                color = colors.neutral300,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}

private fun blockReasonCopy(appName: String, reason: BlockReason): Triple<String, String, String> = when (reason) {
    BlockReason.LimitReached -> Triple(
        "Vaqt tugadi",
        "Bugungi limit tugadi",
        "$appName uchun bugungi limit ishlatildi."
    )
    BlockReason.WorkHours -> Triple(
        "Ish vaqti",
        "Hozir ish/o'quv vaqti",
        "$appName ish yoki o'quv soatlarida bloklangan."
    )
    is BlockReason.PrayerTime -> Triple(
        "Namoz vaqti",
        "${reason.prayerName} namozi yaqinlashdi",
        "$appName namoz vaqtidan oldingi tinch oynada bloklangan."
    )
}
