package com.onikki.app.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

@Composable
fun BlockedScreen(
    appName: String,
    reason: BlockReason,
    strict: Boolean,
    canChallenge: Boolean,
    onClose: () -> Unit,
    onStartChallenge: () -> Unit
) {
    BackHandler(onBack = onClose)
    val colors = LocalOnIkkiColors.current
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
            when {
                strict -> Text(
                    text = "Qat'iy blok — bu vaqtda ochib bo'lmaydi",
                    color = colors.text.muted(0.5f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                )
                canChallenge -> OnIkkiButton(
                    text = "So'z yodlab ochish",
                    onClick = onStartChallenge,
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
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
    is BlockReason.Schedule -> Triple(
        "Bloklangan vaqt",
        "${hhmm(reason.start)} – ${hhmm(reason.end)} oralig'ida yopiq",
        "$appName siz belgilagan vaqtda ishlamaydi."
    )
    is BlockReason.Zone -> Triple(
        "Bloklangan hudud",
        "Siz hozir: ${reason.zoneName}",
        "$appName bu hududda ishlamaydi."
    )
    is BlockReason.PrayerTime -> Triple(
        "Namoz vaqti",
        "${reason.prayerName} namozi yaqinlashdi",
        "$appName namoz vaqtidan oldingi tinch oynada bloklangan."
    )
}

private fun hhmm(time: java.time.LocalTime) = "%02d:%02d".format(time.hour, time.minute)
