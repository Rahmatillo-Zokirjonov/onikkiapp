package com.onikki.app.ui.finance

import com.onikki.app.ui.components.TimePickerField
import com.onikki.app.data.db.entity.TransactionType
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.Account
import com.onikki.app.data.db.entity.AccountKind
import com.onikki.app.data.db.entity.PlannedExpense
import com.onikki.app.data.db.entity.RepeatKind
import com.onikki.app.data.repository.AccountBalance
import com.onikki.app.ui.components.AmountField
import com.onikki.app.ui.components.ChoiceChips
import com.onikki.app.ui.components.DatePickerField
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.formatSom
import java.time.LocalDate
import java.time.LocalTime

/** One chip per wallet; used by the transaction, planned-expense and pay sheets. */
@Composable
fun AccountPicker(
    accounts: List<AccountBalance>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    allowNone: Boolean = false,
    onSelectNone: () -> Unit = {},
    noneLabel: String = "To'lashda tanlayman"
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (allowNone) {
            FilterChip(selected = selectedId == null, onClick = onSelectNone, label = { Text(noneLabel) })
        }
        accounts.forEach { item ->
            FilterChip(
                selected = item.account.id == selectedId,
                onClick = { onSelect(item.account.id) },
                label = { Text(accountLabel(item.account)) }
            )
        }
    }
}

// ---------------------------------------------------------------- Hamyon (naqd / karta)

@Composable
fun AccountSheet(
    account: Account?,
    errorFromDelete: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: AccountKind, lastDigits: String?, initialBalance: Long) -> Unit,
    onDelete: () -> Unit
) {
    val key = account?.id
    var kind by rememberSaveable(key) { mutableStateOf(account?.kind ?: AccountKind.KARTA) }
    var name by rememberSaveable(key) { mutableStateOf(account?.name ?: "") }
    var digits by rememberSaveable(key) { mutableStateOf(account?.lastDigits ?: "") }
    var initialText by rememberSaveable(key) { mutableStateOf(account?.initialBalance?.takeIf { it != 0L }?.toString() ?: "") }
    var error by remember(errorFromDelete) { mutableStateOf(errorFromDelete) }

    OnIkkiSheet(title = if (account == null) "Yangi hamyon" else "Hamyonni tahrirlash", onDismiss = onDismiss) {
        ChoiceChips(
            options = listOf(AccountKind.KARTA to "Karta", AccountKind.NAQD to "Naqd pul"),
            selected = kind,
            onSelect = { kind = it }
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text(if (kind == AccountKind.KARTA) "Nomi (masalan: Uzcard, Humo, Visa)" else "Nomi (masalan: Hamyon, Seyf)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (account == null) {
            SuggestionChips(
                options = if (kind == AccountKind.KARTA) listOf("Uzcard", "Humo", "Visa", "Mastercard") else listOf("Naqd", "Dollar", "Seyf"),
                selected = name,
                onSelect = { name = it; error = null }
            )
        }
        if (kind == AccountKind.KARTA) {
            OutlinedTextField(
                value = digits,
                onValueChange = { new -> if (new.length <= 4 && new.all { it.isDigit() }) digits = new },
                label = { Text("Oxirgi 4 raqam (ixtiyoriy)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        AmountField(value = initialText, onValueChange = { initialText = it }, label = "Hozirgi qoldiq")
        SheetFieldLabel("Hamyonni qo'shgandagi qoldiq. Keyingi kirim-chiqimlar shunga qo'shiladi.")
        SheetErrorText(error)
        SheetActions(
            onSave = {
                if (name.isBlank()) error = "Hamyon nomini yozing"
                else onSave(name, kind, digits.ifBlank { null }, initialText.toLongOrNull() ?: 0L)
            },
            onDelete = if (account != null) onDelete else null
        )
    }
}

// ---------------------------------------------------------------- Rejali xarajat

private val DAYS_BEFORE_OPTIONS = listOf(0 to "Shu kuni", 1 to "1 kun oldin", 3 to "3 kun oldin", 7 to "1 hafta oldin")

@Composable
fun PlannedExpenseSheet(
    expense: PlannedExpense?,
    accounts: List<AccountBalance>,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        type: TransactionType, title: String, amount: Long, category: String, accountId: Long?, dueDate: LocalDate,
        repeat: RepeatKind, remindEnabled: Boolean, remindDaysBefore: Int, remindTime: LocalTime
    ) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val key = expense?.id
    var type by rememberSaveable(key) { mutableStateOf(expense?.type ?: TransactionType.CHIQIM) }
    var title by rememberSaveable(key) { mutableStateOf(expense?.title ?: "") }
    var amountText by rememberSaveable(key) { mutableStateOf(expense?.amount?.toString() ?: "") }
    var category by rememberSaveable(key) { mutableStateOf(expense?.category ?: "") }
    var accountId by rememberSaveable(key) { mutableStateOf(expense?.accountId) }
    var dueDate by rememberSaveable(key) { mutableStateOf(expense?.dueDate ?: LocalDate.now().plusDays(1)) }
    var repeat by rememberSaveable(key) { mutableStateOf(expense?.repeat ?: RepeatKind.NONE) }
    var remind by rememberSaveable(key) { mutableStateOf(expense?.remindEnabled ?: true) }
    var daysBefore by rememberSaveable(key) { mutableStateOf(expense?.remindDaysBefore ?: 1) }
    var remindTime by rememberSaveable(key) { mutableStateOf(expense?.remindTime ?: LocalTime.of(9, 0)) }
    var error by remember { mutableStateOf<String?>(null) }

    val income = type == TransactionType.KIRIM
    OnIkkiSheet(title = if (expense == null) "Kelgusi pul" else "Rejani tahrirlash", onDismiss = onDismiss) {
        ChoiceChips(
            options = listOf(TransactionType.CHIQIM to "To'lashim kerak", TransactionType.KIRIM to "Olishim kerak"),
            selected = type,
            onSelect = { type = it }
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text(if (income) "Nima / kimdan (masalan: Maosh)" else "Nima uchun (masalan: Uy ijarasi)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (expense == null) {
            SuggestionChips(
                options = if (income) listOf("Maosh", "Avans", "Qarz qaytishi", "Stipendiya", "Ijara daromadi")
                else listOf("Uy ijarasi", "Internet", "Telefon", "Kommunal", "Kredit", "O'qish to'lovi"),
                selected = title,
                onSelect = {
                    title = it
                    if (category.isBlank()) category = when (it) {
                        "Internet", "Telefon" -> "Kommunal"
                        "Avans" -> "Maosh"
                        else -> it
                    }
                    error = null
                }
            )
        }
        AmountField(value = amountText, onValueChange = { amountText = it; error = null }, label = "Summa")
        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Kategoriya") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SuggestionChips(options = if (income) incomeCategories else expenseCategories, selected = category, onSelect = { category = it })
        DatePickerField(label = if (income) "Qachon olinadi" else "To'lov sanasi", date = dueDate, onDateChange = { it?.let { picked -> dueDate = picked } })
        SheetFieldLabel("Takrorlanishi")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatKind.entries.forEach { option ->
                FilterChip(selected = repeat == option, onClick = { repeat = option }, label = { Text(option.label) })
            }
        }
        SheetFieldLabel(if (income) "Qaysi hamyonga" else "Qaysi hamyondan")
        AccountPicker(
            accounts, accountId, onSelect = { accountId = it }, allowNone = true, onSelectNone = { accountId = null },
            noneLabel = if (income) "Olganda tanlayman" else "To'lashda tanlayman"
        )

        Row(modifier = Modifier.fillMaxWidth().clickable { remind = !remind }, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Eslatma", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
            Switch(checked = remind, onCheckedChange = { remind = it })
        }
        if (remind) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAYS_BEFORE_OPTIONS.forEach { (days, label) ->
                    FilterChip(selected = daysBefore == days, onClick = { daysBefore = days }, label = { Text(label) })
                }
            }
            TimePickerField(label = "Eslatma vaqti", time = remindTime, onTimeChange = { remindTime = it })
            Text(
                text = if (daysBefore == 0) "O'sha kuni eslatiladi va Kunlik rejada ko'rinadi."
                else "Oldindan va o'sha kunning o'zida eslatiladi; o'sha kuni Kunlik rejada ko'rinadi.",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val amount = amountText.toLongOrNull()
                when {
                    title.isBlank() -> error = if (income) "Nima yoki kimdan ekanini yozing" else "Nima uchun ekanini yozing"
                    amount == null || amount <= 0 -> error = "Summani kiriting"
                    else -> onSave(
                        type, title, amount, category, accountId, dueDate, repeat, remind, daysBefore,
                        remindTime
                    )
                }
            },
            onDelete = if (expense != null) onDelete else null
        )
    }
}

/** Confirms a payment: the amount/wallet/date can differ from the plan (a bill that came in higher). */
@Composable
fun PlannedPaySheet(
    expense: PlannedExpense,
    accounts: List<AccountBalance>,
    onDismiss: () -> Unit,
    onPay: (account: Account, amount: Long, date: LocalDate) -> Unit,
    onSkip: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    var amountText by rememberSaveable(expense.id) { mutableStateOf(expense.amount.toString()) }
    var accountId by rememberSaveable(expense.id) {
        mutableStateOf(expense.accountId?.takeIf { id -> accounts.any { it.account.id == id } } ?: accounts.firstOrNull()?.account?.id)
    }
    var date by rememberSaveable(expense.id) { mutableStateOf(LocalDate.now()) }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = "${expense.doneLabel}: ${expense.title}", onDismiss = onDismiss) {
        AmountField(value = amountText, onValueChange = { amountText = it; error = null }, label = "Summa")
        SheetFieldLabel(if (expense.isIncome) "Qaysi hamyonga" else "Qaysi hamyondan")
        AccountPicker(accounts, accountId, onSelect = { accountId = it })
        accounts.firstOrNull { it.account.id == accountId }?.let { selected ->
            Text(
                text = "Qoldiq: ${formatSom(selected.balance)} so'm",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { picked -> date = picked } })
        Text(
            text = (if (expense.isIncome) "Kirim" else "Chiqim") + if (expense.repeat == RepeatKind.NONE) " sifatida yoziladi va reja yopiladi."
            else " sifatida yoziladi, keyingi sana avtomatik suriladi.",
            color = colors.text.muted(0.5f),
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily
        )
        SheetErrorText(error)
        OnIkkiButton(
            text = expense.doneLabel,
            onClick = {
                val amount = amountText.toLongOrNull()
                val account = accounts.firstOrNull { it.account.id == accountId }?.account
                when {
                    amount == null || amount <= 0 -> error = "Summani kiriting"
                    account == null -> error = "Hamyonni tanlang"
                    else -> onPay(account, amount, date)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (expense.repeat != RepeatKind.NONE) {
            OnIkkiButton(
                text = "Bu safar o'tkazib yuborish",
                onClick = onSkip,
                variant = OnIkkiButtonVariant.SECONDARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
