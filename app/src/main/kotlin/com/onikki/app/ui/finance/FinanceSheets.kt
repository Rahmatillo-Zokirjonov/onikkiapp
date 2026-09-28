package com.onikki.app.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.db.entity.CategoryBudget
import com.onikki.app.data.db.entity.Debt
import com.onikki.app.data.db.entity.DebtDirection
import com.onikki.app.data.db.entity.DebtStatus
import com.onikki.app.data.db.entity.SavingsGoal
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.db.entity.TransactionType
import com.onikki.app.data.db.entity.Wallet
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

@Composable
fun TransactionSheet(
    transaction: Transaction?,
    expenseCategories: List<String>,
    incomeCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (amount: Long, type: TransactionType, category: String, wallet: Wallet, date: LocalDate, note: String?) -> Unit,
    onDelete: () -> Unit
) {
    val key = transaction?.id
    var amountText by rememberSaveable(key) { mutableStateOf(transaction?.amount?.toString() ?: "") }
    var type by rememberSaveable(key) { mutableStateOf(transaction?.type ?: TransactionType.CHIQIM) }
    var category by rememberSaveable(key) { mutableStateOf(transaction?.category ?: "") }
    var wallet by rememberSaveable(key) { mutableStateOf(transaction?.wallet ?: Wallet.NAQD) }
    var date by rememberSaveable(key) { mutableStateOf(transaction?.date ?: LocalDate.now()) }
    var note by rememberSaveable(key) { mutableStateOf(transaction?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(
        title = if (transaction == null) "Yangi tranzaksiya" else "Tranzaksiyani tahrirlash",
        onDismiss = onDismiss
    ) {
        ChoiceChips(
            options = listOf(TransactionType.CHIQIM to "Chiqim", TransactionType.KIRIM to "Kirim"),
            selected = type,
            onSelect = { type = it }
        )
        AmountField(value = amountText, onValueChange = { amountText = it; error = null }, label = "Summa")
        OutlinedTextField(
            value = category,
            onValueChange = { category = it; error = null },
            label = { Text("Kategoriya") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SuggestionChips(
            options = if (type == TransactionType.CHIQIM) expenseCategories else incomeCategories,
            selected = category,
            onSelect = { category = it; error = null }
        )
        SheetFieldLabel("Hamyon")
        ChoiceChips(
            options = listOf(Wallet.NAQD to "Naqd", Wallet.KARTA to "Karta"),
            selected = wallet,
            onSelect = { wallet = it }
        )
        DatePickerField(label = "Sana", date = date, onDateChange = { it?.let { picked -> date = picked } })
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Izoh (ixtiyoriy)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val amount = amountText.toLongOrNull()
                when {
                    amount == null || amount <= 0 -> error = "Summani kiriting"
                    category.isBlank() -> error = "Kategoriyani tanlang yoki yozing"
                    else -> onSave(amount, type, category, wallet, date, note.trim().ifBlank { null })
                }
            },
            onDelete = if (transaction != null) onDelete else null
        )
    }
}

@Composable
fun BudgetSheet(
    budget: CategoryBudget?,
    expenseCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (category: String, monthlyLimit: Long) -> Unit,
    onDelete: () -> Unit
) {
    val key = budget?.id
    var category by rememberSaveable(key) { mutableStateOf(budget?.category ?: "") }
    var limitText by rememberSaveable(key) { mutableStateOf(budget?.monthlyLimit?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (budget == null) "Oylik limit" else "Limitni tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = category,
            onValueChange = { category = it; error = null },
            label = { Text("Kategoriya") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SuggestionChips(options = expenseCategories, selected = category, onSelect = { category = it; error = null })
        AmountField(value = limitText, onValueChange = { limitText = it; error = null }, label = "Oylik limit")
        SheetFieldLabel("Limitning 90% iga yetganda ogohlantirish ko'rsatiladi.")
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val limit = limitText.toLongOrNull()
                when {
                    category.isBlank() -> error = "Kategoriyani kiriting"
                    limit == null || limit <= 0 -> error = "Limit summasini kiriting"
                    else -> onSave(category, limit)
                }
            },
            onDelete = if (budget != null) onDelete else null
        )
    }
}

@Composable
fun DebtSheet(
    debt: Debt?,
    onDismiss: () -> Unit,
    onSave: (personName: String, amount: Long, direction: DebtDirection, dueDate: LocalDate?, status: DebtStatus) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val key = debt?.id
    var personName by rememberSaveable(key) { mutableStateOf(debt?.personName ?: "") }
    var amountText by rememberSaveable(key) { mutableStateOf(debt?.amount?.toString() ?: "") }
    var direction by rememberSaveable(key) { mutableStateOf(debt?.direction ?: DebtDirection.MENGA_QARZDOR) }
    var dueDate by rememberSaveable(key) { mutableStateOf(debt?.dueDate) }
    var isClosed by rememberSaveable(key) { mutableStateOf(debt?.status == DebtStatus.YOPILGAN) }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (debt == null) "Yangi qarz" else "Qarzni tahrirlash", onDismiss = onDismiss) {
        ChoiceChips(
            options = listOf(DebtDirection.MENGA_QARZDOR to "Menga qarzdor", DebtDirection.MEN_QARZDORMAN to "Men qarzdorman"),
            selected = direction,
            onSelect = { direction = it }
        )
        OutlinedTextField(
            value = personName,
            onValueChange = { personName = it; error = null },
            label = { Text(if (direction == DebtDirection.MENGA_QARZDOR) "Kim qarzdor" else "Kimga qarzdorman") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AmountField(value = amountText, onValueChange = { amountText = it; error = null }, label = "Summa")
        DatePickerField(label = "Qaytarish muddati", date = dueDate, onDateChange = { dueDate = it }, allowClear = true)
        if (debt != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Qarz yopilgan",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = isClosed, onCheckedChange = { isClosed = it })
            }
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val amount = amountText.toLongOrNull()
                when {
                    personName.isBlank() -> error = "Ismni kiriting"
                    amount == null || amount <= 0 -> error = "Summani kiriting"
                    else -> onSave(
                        personName,
                        amount,
                        direction,
                        dueDate,
                        if (isClosed) DebtStatus.YOPILGAN else DebtStatus.OCHIQ
                    )
                }
            },
            onDelete = if (debt != null) onDelete else null
        )
    }
}

@Composable
fun GoalSheet(
    goal: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (name: String, targetAmount: Long, currentAmount: Long, deadline: LocalDate?) -> Unit,
    onDelete: () -> Unit
) {
    val key = goal?.id
    var name by rememberSaveable(key) { mutableStateOf(goal?.name ?: "") }
    var targetText by rememberSaveable(key) { mutableStateOf(goal?.targetAmount?.toString() ?: "") }
    var currentText by rememberSaveable(key) { mutableStateOf(goal?.currentAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var deadline by rememberSaveable(key) { mutableStateOf(goal?.deadline) }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = if (goal == null) "Yangi maqsad" else "Maqsadni tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Nima uchun (masalan: noutbuk)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        AmountField(value = targetText, onValueChange = { targetText = it; error = null }, label = "Maqsad summasi")
        AmountField(value = currentText, onValueChange = { currentText = it }, label = "Hozir yig'ilgan (ixtiyoriy)")
        DatePickerField(label = "Muddat", date = deadline, onDateChange = { deadline = it }, allowClear = true)
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val target = targetText.toLongOrNull()
                when {
                    name.isBlank() -> error = "Maqsad nomini kiriting"
                    target == null || target <= 0 -> error = "Maqsad summasini kiriting"
                    else -> onSave(name, target, currentText.toLongOrNull() ?: 0L, deadline)
                }
            },
            onDelete = if (goal != null) onDelete else null
        )
    }
}

/** Add money to (or withdraw from) a savings goal. */
@Composable
fun GoalAdjustSheet(goal: SavingsGoal, onDismiss: () -> Unit, onAdjust: (delta: Long) -> Unit) {
    val colors = LocalOnIkkiColors.current
    var amountText by rememberSaveable(goal.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    OnIkkiSheet(title = goal.name, onDismiss = onDismiss) {
        Text(
            text = "${formatSom(goal.currentAmount)} / ${formatSom(goal.targetAmount)} so'm",
            color = colors.text.muted(0.6f),
            fontSize = 13.sp,
            fontFamily = OnIkkiFontFamily
        )
        AmountField(value = amountText, onValueChange = { amountText = it; error = null }, label = "Summa")
        SheetErrorText(error)
        Row(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnIkkiButton(
                text = "Yechib olish",
                variant = OnIkkiButtonVariant.SECONDARY,
                onClick = {
                    val amount = amountText.toLongOrNull()
                    if (amount == null || amount <= 0) error = "Summani kiriting" else onAdjust(-amount)
                },
                modifier = Modifier.weight(1f)
            )
            OnIkkiButton(
                text = "Qo'shish",
                onClick = {
                    val amount = amountText.toLongOrNull()
                    if (amount == null || amount <= 0) error = "Summani kiriting" else onAdjust(amount)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
