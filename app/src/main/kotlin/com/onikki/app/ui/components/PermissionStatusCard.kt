package com.onikki.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

/** Shared by Onboarding and Sozlamalar — one manually-granted permission's status + action. */
@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = modifier.fillMaxWidth(), gap = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            TagChip(
                text = if (isGranted) "Yoqilgan" else "O'chiq",
                variant = if (isGranted) TagVariant.ACCENT else TagVariant.NEUTRAL
            )
        }
        Text(text = description, color = colors.text.muted(0.65f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        if (!isGranted) {
            OnIkkiButton(text = actionLabel, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
