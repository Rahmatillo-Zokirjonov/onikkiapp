package com.onikki.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Task
import com.onikki.app.data.db.entity.TaskCategory
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

/**
 * A single task row: checkbox + title (+ optional category tag) + time. [onClick] opens it for editing;
 * [showTime] is off where the time is already shown beside the card (Kunlik reja's timeline).
 */
@Composable
fun TaskRowCard(
    task: Task,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showTime: Boolean = true
) {
    val colors = LocalOnIkkiColors.current
    val rowModifier = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier.fillMaxWidth()
    OnIkkiRowCard(modifier = rowModifier) {
        TaskCheckbox(checked = task.isCompleted, onToggle = onToggle)
        Text(
            text = task.title,
            color = if (task.isCompleted) colors.text.muted(0.45f) else colors.text,
            fontSize = 14.sp,
            fontFamily = OnIkkiFontFamily,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        if (task.category == TaskCategory.ISH) {
            TagChip(text = "Ish", variant = TagVariant.ACCENT)
        }
        task.time?.takeIf { showTime }?.let {
            Text(
                text = "%02d:%02d".format(it.hour, it.minute),
                color = colors.text.muted(if (task.isCompleted) 0.4f else 0.55f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
    }
}
