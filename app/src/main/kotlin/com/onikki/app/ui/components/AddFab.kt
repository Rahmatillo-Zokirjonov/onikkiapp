package com.onikki.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.theme.LocalOnIkkiColors

/** The mockup's round floating "+" — used by every list screen with an add action. */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = modifier
            .size(52.dp)
            .background(colors.accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+", color = colors.onAccent, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}
