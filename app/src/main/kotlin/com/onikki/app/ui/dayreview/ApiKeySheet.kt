package com.onikki.app.ui.dayreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var key by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(text = "Claude API kaliti", color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 18.sp)
            Text(
                text = "Kun yakunidagi AI tahlil shu kalit orqali to'g'ridan-to'g'ri Anthropic serveriga (internet talab qiladi) yuboriladi — boshqa hech qanday server ishtirok etmaydi.",
                color = colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("sk-ant-...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OnIkkiButton(
                text = "Saqlash",
                onClick = { onSave(key) },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)
            )
        }
    }
}
