package com.onikki.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.muted

private val ICON_CHOICES = listOf("●", "○", "◆", "◇", "▢", "▣", "♥", "★")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitSheet(
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, dailyTarget: Int) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(ICON_CHOICES.first()) }
    var dailyTarget by rememberSaveable { mutableStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(text = "Yangi odat", color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 18.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nomi") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            Text(
                text = "Ikonka",
                color = colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ICON_CHOICES.forEach { candidate ->
                    val selected = candidate == icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { icon = candidate }
                            .background(if (selected) colors.accent800 else colors.background, CircleShape)
                            .border(
                                BorderStroke(1.dp, if (selected) colors.accent else colors.divider),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = candidate,
                            color = if (selected) colors.accent100 else colors.text,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kunlik maqsad",
                    color = colors.text,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                StepperButton(label = "−", onClick = { if (dailyTarget > 1) dailyTarget-- })
                Text(
                    text = dailyTarget.toString(),
                    color = colors.text,
                    fontSize = 15.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                StepperButton(label = "+", onClick = { dailyTarget++ })
            }
            OnIkkiButton(
                text = "Saqlash",
                onClick = { onSave(name, icon, dailyTarget) },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)
            )
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick)
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.small),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
    }
}
