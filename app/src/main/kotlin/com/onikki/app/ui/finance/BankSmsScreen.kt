package com.onikki.app.ui.finance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.data.db.entity.SmsImport
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatRelativeDateUz
import com.onikki.app.ui.util.formatSom
import java.util.Locale

@Composable
fun BankSmsScreen(expenseCategories: List<String>, incomeCategories: List<String>, onBack: () -> Unit) {
    val viewModel: BankSmsViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current

    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var canReceive by remember { mutableStateOf(granted(Manifest.permission.RECEIVE_SMS)) }
    var canRead by remember { mutableStateOf(granted(Manifest.permission.READ_SMS)) }
    LifecycleResumeEffect(Unit) {
        canReceive = granted(Manifest.permission.RECEIVE_SMS)
        canRead = granted(Manifest.permission.READ_SMS)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        canReceive = result[Manifest.permission.RECEIVE_SMS] == true || granted(Manifest.permission.RECEIVE_SMS)
        canRead = result[Manifest.permission.READ_SMS] == true || granted(Manifest.permission.READ_SMS)
        if (canReceive) viewModel.setEnabled(true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader(title = "Bank SMS", onBack = onBack) }

        item {
            OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Karta SMS'larini avtomatik yozish", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                        Text(
                            text = when {
                                !canReceive -> "SMS ruxsati kerak"
                                state.settings.enabled -> "Yoqilgan — har bir to'lov va tushum Moliya'ga yoziladi"
                                else -> "O'chiq"
                            },
                            color = colors.text.muted(0.55f),
                            fontSize = 12.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                    }
                    Switch(
                        checked = state.settings.enabled && canReceive,
                        onCheckedChange = { on ->
                            if (on && !canReceive) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                            } else {
                                viewModel.setEnabled(on)
                            }
                        }
                    )
                }
                Text(
                    text = "SMS matni faqat telefoningizda o'qiladi, hech qayerga yuborilmaydi. " +
                        "Karta raqamining oxirgi raqamlarini hamyonda yozib qo'ysangiz, to'lov o'sha hamyonga tushadi.",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
                if (canRead) {
                    OnIkkiButton(
                        text = if (state.isImporting) "O'qilmoqda…" else "Oxirgi 30 kunlik SMS'larni qo'shish",
                        onClick = { if (!state.isImporting) viewModel.importInbox() },
                        variant = OnIkkiButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.message?.let { Text(text = it, color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
            }
        }

        item { RatesCard(usd = state.settings.usdRate, eur = state.settings.eurRate, onSave = viewModel::setRates) }

        if (state.unnoted.isNotEmpty()) {
            item {
                Text(
                    text = "Izoh yozilmagan · ${state.unnoted.size}",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily
                )
            }
            items(state.unnoted, key = { it.id }) { tx ->
                UnnotedCard(
                    tx = tx,
                    notes = state.frequentNotes,
                    categories = if (tx.type == TransactionType.KIRIM) incomeCategories else expenseCategories,
                    onSave = { note, category -> viewModel.saveReview(tx, note, category) }
                )
            }
        }

        if (state.unrecognized.isNotEmpty()) {
            item {
                Text(text = "Tushunilmagan SMS'lar", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
            }
            items(state.unrecognized, key = { it.hash }) { sms -> UnrecognizedCard(sms, onDismiss = { viewModel.dismissUnrecognized(sms) }) }
        }

        item { PasteTester(viewModel) }
    }
}

@Composable
private fun RatesCard(usd: Long, eur: Long, onSave: (Long, Long) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var usdText by rememberSaveable(usd) { mutableStateOf(usd.toString()) }
    var eurText by rememberSaveable(eur) { mutableStateOf(eur.toString()) }
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Text(text = "Valyuta kursi", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Dollar yoki yevro kartadagi summalar shu kurs bo'yicha so'mga o'girib yoziladi.",
            color = colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = usdText,
                onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 7) usdText = v },
                label = { Text("1 USD, so'm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = eurText,
                onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 7) eurText = v },
                label = { Text("1 EUR, so'm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        if (usdText != usd.toString() || eurText != eur.toString()) {
            OnIkkiButton(text = "Kursni saqlash", onClick = { onSave(usdText.toLongOrNull() ?: 0, eurText.toLongOrNull() ?: 0) })
        }
    }
}

/** One SMS transaction waiting for a note: tap a frequent note or type one, optionally fix the category. */
@Composable
private fun UnnotedCard(tx: Transaction, notes: List<String>, categories: List<String>, onSave: (String, String) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var note by rememberSaveable(tx.id) { mutableStateOf("") }
    var category by rememberSaveable(tx.id) { mutableStateOf(tx.category) }
    val income = tx.type == TransactionType.KIRIM
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tx.merchant ?: if (income) "Tushum" else "To'lov", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                Text(text = formatRelativeDateUz(tx.date), color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            }
            Text(
                text = (if (income) "+ " else "− ") + formatSom(tx.amount),
                color = if (income) colors.accent else colors.text,
                fontSize = 15.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { option ->
                FilterChip(selected = note == option, onClick = { note = if (note == option) "" else option }, label = { Text(option) })
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Izoh (masalan: suv oldim)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { option ->
                FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
            }
        }
        OnIkkiButton(
            text = "Saqlash",
            onClick = { onSave(note.ifBlank { category }, category) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun UnrecognizedCard(sms: SmsImport, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 6.dp) {
        Text(text = sms.sender, color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
        Text(text = sms.body, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
        Text(
            text = "Kerak bo'lsa, + tugmasi bilan qo'lda qo'shing.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        Text(
            text = "Ro'yxatdan olib tashlash",
            color = colors.accent,
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 4.dp)
        )
    }
}

/** Paste any bank SMS to see how it's read — and add it (useful for old messages too). */
@Composable
private fun PasteTester(viewModel: BankSmsViewModel) {
    val colors = LocalOnIkkiColors.current
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = if (text.isBlank()) null else viewModel.preview(text)
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Text(text = "SMS'ni sinab ko'rish", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Bank SMS matnini shu yerga qo'ying") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
        )
        if (text.isNotBlank()) {
            if (parsed == null) {
                Text(text = "Tushunilmadi", color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            } else {
                val lines = listOfNotNull(
                    (if (parsed.isCancellation) "Bekor qilish · " else "") + (if (parsed.type == TransactionType.KIRIM) "Kirim" else "Chiqim"),
                    "Summa: " + String.format(Locale.US, "%,.2f", parsed.amount).replace(',', ' ') + " ${parsed.currency}",
                    parsed.cardDigits?.let { "Karta: *$it" },
                    parsed.merchant?.let { "Qayerda: $it" + (parsed.city?.let { c -> ", $c" } ?: "") },
                    parsed.time?.let { "Vaqt: %02d.%02d.%d %02d:%02d".format(it.dayOfMonth, it.monthValue, it.year, it.hour, it.minute) }
                )
                lines.forEach { Text(text = it, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
                OnIkkiButton(text = "Moliya'ga qo'shish", onClick = { viewModel.importPasted(text); text = "" }, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            text = "Tushunmagan SMS bo'lsa, matnini dasturchiga yuboring — format qo'shiladi.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
    }
}
