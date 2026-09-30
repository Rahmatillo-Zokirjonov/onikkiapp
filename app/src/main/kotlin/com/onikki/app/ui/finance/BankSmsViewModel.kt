package com.onikki.app.ui.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.db.entity.MerchantCategory
import com.onikki.app.data.db.entity.SmsImport
import com.onikki.app.data.db.entity.SmsImportStatus
import com.onikki.app.data.db.entity.Transaction
import com.onikki.app.data.local.BankSmsSettings
import com.onikki.app.data.local.BankSmsSettingsStore
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.domain.ai.CategorySuggestion
import com.onikki.app.domain.sms.BankSmsImporter
import com.onikki.app.domain.sms.BankSmsParser
import com.onikki.app.domain.sms.ParsedBankSms
import com.onikki.app.domain.sms.SmsImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BankSmsUiState(
    val settings: BankSmsSettings = BankSmsSettings(),
    val unnoted: List<Transaction> = emptyList(),
    val unrecognized: List<SmsImport> = emptyList(),
    val frequentNotes: List<String> = emptyList(),
    /** Result line of the last manual action ("12 ta tranzaksiya qo'shildi"). */
    val message: String? = null,
    val isImporting: Boolean = false,
    /** AI suggestions by transaction id; they only prefill the cards until the user saves. */
    val suggestions: Map<Long, CategorySuggestion> = emptyMap(),
    val isSuggesting: Boolean = false,
    val needsApiKey: Boolean = false
)

/** Default one-tap notes until the user has their own history. */
private val DEFAULT_NOTES = listOf("Oziq-ovqat", "Suv", "Taksi", "Ovqat", "Internet", "Dori", "Kiyim")

class BankSmsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as OnIkkiApplication).database
    private val store = BankSmsSettingsStore(application)
    private val importer = BankSmsImporter(application)
    private val transient = MutableStateFlow(BankSmsUiState())
    private val ai = AiRepository(application, db)

    val uiState: StateFlow<BankSmsUiState> = combine(
        store.settings,
        db.smsImportDao().observeUnnoted(),
        db.smsImportDao().observeByStatus(SmsImportStatus.UNRECOGNIZED),
        db.smsImportDao().observeFrequentNotes(8),
        transient
    ) { settings, unnoted, unrecognized, notes, t ->
        t.copy(
            settings = settings,
            unnoted = unnoted,
            unrecognized = unrecognized,
            frequentNotes = (notes + DEFAULT_NOTES).distinctBy { it.lowercase() }.take(10)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankSmsUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { store.setEnabled(enabled) }
    }

    fun setRates(usd: Long, eur: Long) {
        if (usd <= 0 || eur <= 0) return
        viewModelScope.launch {
            store.setRates(usd, eur)
            transient.update { it.copy(message = "Kurs saqlandi") }
        }
    }

    fun importInbox(days: Long = 30) {
        viewModelScope.launch {
            transient.update { it.copy(isImporting = true, message = null) }
            val count = runCatching { importer.importInbox(days) }.getOrElse { -1 }
            transient.update {
                it.copy(
                    isImporting = false,
                    message = when {
                        count < 0 -> "SMS'larni o'qib bo'lmadi — ruxsatni tekshiring"
                        count == 0 -> "Yangi bank SMS topilmadi"
                        else -> "$count ta tranzaksiya qo'shildi"
                    }
                )
            }
        }
    }

    fun preview(text: String): ParsedBankSms? = BankSmsParser.parse(text)

    /** The paste box's "Qo'shish": imports the pasted SMS as if it had just arrived. */
    fun importPasted(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = importer.import("manual", text.trim(), System.currentTimeMillis())
            transient.update {
                it.copy(
                    message = when (result) {
                        is SmsImportResult.Imported -> "Qo'shildi"
                        is SmsImportResult.Cancelled -> if (result.removed != null) "Bekor qilingan to'lov o'chirildi" else "Mos to'lov topilmadi"
                        SmsImportResult.Duplicate -> "Bu SMS allaqachon qo'shilgan"
                        SmsImportResult.Unrecognized, SmsImportResult.NotBank -> "Tushunilmadi"
                    }
                )
            }
        }
    }

    /** Saves the note; a changed category is also remembered for this shop next time. */
    fun saveReview(tx: Transaction, note: String, category: String) {
        viewModelScope.launch {
            val dao = db.smsImportDao()
            dao.setNote(tx.id, note.trim().ifBlank { null })
            if (category.isNotBlank() && category != tx.category) {
                dao.setCategory(tx.id, category.trim())
                tx.merchant?.let { dao.rememberCategory(MerchantCategory(it.uppercase(), category.trim())) }
            }
        }
    }

    fun dismissUnrecognized(item: SmsImport) {
        viewModelScope.launch { db.smsImportDao().setStatus(item.hash, SmsImportStatus.DISMISSED, null) }
    }

    fun clearMessage() = transient.update { it.copy(message = null) }

    fun suggestWithAi(expense: List<String>, income: List<String>) {
        if (transient.value.isSuggesting) return
        val pending = uiState.value.unnoted.filter { it.id !in transient.value.suggestions }
        if (pending.isEmpty()) return
        transient.update { it.copy(isSuggesting = true, message = null) }
        viewModelScope.launch {
            when (val r = ai.suggestCategories(pending, expense, income)) {
                is AiOutcome.Ok -> transient.update { s ->
                    s.copy(
                        isSuggesting = false,
                        suggestions = s.suggestions + r.value.associateBy { it.transactionId },
                        message = if (r.value.isEmpty()) "AI taklif bera olmadi" else "${r.value.size} ta taklif — tekshirib saqlang"
                    )
                }
                is AiOutcome.Failed -> transient.update { it.copy(isSuggesting = false, message = r.message) }
                AiOutcome.Offline -> transient.update { it.copy(isSuggesting = false, message = "Internet yo'q") }
                AiOutcome.NoKey -> transient.update { it.copy(isSuggesting = false, needsApiKey = true) }
            }
        }
    }

    /** Saves every AI suggestion as is (each card can still be saved one by one instead). */
    fun acceptAllSuggestions() {
        val byId = uiState.value.unnoted.associateBy { it.id }
        val accepted = transient.value.suggestions.values.mapNotNull { s -> byId[s.transactionId]?.let { it to s } }
        accepted.forEach { (tx, s) -> saveReview(tx, s.note.ifBlank { s.category }, s.category) }
        transient.update { it.copy(suggestions = emptyMap(), message = "${accepted.size} ta saqlandi") }
    }

    fun saveApiKey(key: String, expense: List<String>, income: List<String>) {
        viewModelScope.launch {
            ai.saveKey(key)
            transient.update { it.copy(needsApiKey = false) }
            suggestWithAi(expense, income)
        }
    }

    fun dismissApiKey() = transient.update { it.copy(needsApiKey = false) }
}
