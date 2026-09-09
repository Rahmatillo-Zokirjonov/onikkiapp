package com.onikki.app.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    onDismiss: () -> Unit,
    onSave: (amount: Long, type: TransactionType, category: String, wallet: Wallet, note: String?) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var amountText by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TransactionType.CHIQIM) }
    var category by rememberSaveable { mutableStateOf("") }
    var wallet by rememberSaveable { mutableStateOf(Wallet.NAQD) }
    var note by rememberSaveable { mutableStateOf("") }

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
            Text(
                text = "Yangi tranzaksiya",
                color = colors.text,
                fontFamily = OnIkkiFontFamily,
                fontSize = 18.sp
            )
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.CHIQIM,
                    onClick = { type = TransactionType.CHIQIM },
                    label = { Text("Chiqim") }
                )
                FilterChip(
                    selected = type == TransactionType.KIRIM,
                    onClick = { type = TransactionType.KIRIM },
                    label = { Text("Kirim") }
                )
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { new -> if (new.all { it.isDigit() }) amountText = new },
                label = { Text("Summa (so'm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Kategoriya") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = wallet == Wallet.NAQD,
                    onClick = { wallet = Wallet.NAQD },
                    label = { Text("Naqd") }
                )
                FilterChip(
                    selected = wallet == Wallet.KARTA,
                    onClick = { wallet = Wallet.KARTA },
                    label = { Text("Karta") }
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Izoh (ixtiyoriy)") },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
            OnIkkiButton(
                text = "Saqlash",
                onClick = {
                    val amount = amountText.toLongOrNull()
                    if (amount != null && amount > 0 && category.isNotBlank()) {
                        onSave(amount, type, category.trim(), wallet, note.trim().ifBlank { null })
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp)
            )
        }
    }
}
