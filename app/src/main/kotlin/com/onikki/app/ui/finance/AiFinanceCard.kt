package com.onikki.app.ui.finance

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.AiOutcome
import com.onikki.app.data.repository.AiRepository
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.dayreview.ApiKeySheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AiFinanceState(
    val text: String? = null,
    /** Day the saved analysis was made; an older one is offered for refresh. */
    val madeOn: LocalDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsKey: Boolean = false,
    val expanded: Boolean = false
)

/** "✨ AI tahlil" on Moliya: one request on demand; the last result is kept until refreshed. */
class AiFinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AiRepository(application, (application as OnIkkiApplication).database)
    private val prefs = application.getSharedPreferences("ai_finance", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        AiFinanceState(
            text = prefs.getString(KEY_TEXT, null),
            madeOn = prefs.getString(KEY_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        )
    )
    val state: StateFlow<AiFinanceState> = _state

    fun analyze() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, expanded = true) }
        viewModelScope.launch {
            when (val r = repository.analyzeMonth()) {
                is AiOutcome.Ok -> {
                    val today = LocalDate.now()
                    prefs.edit().putString(KEY_TEXT, r.value).putString(KEY_DATE, today.toString()).apply()
                    _state.update { it.copy(text = r.value, madeOn = today, isLoading = false) }
                }
                is AiOutcome.Failed -> _state.update { it.copy(isLoading = false, error = r.message) }
                AiOutcome.Offline -> _state.update { it.copy(isLoading = false, error = "Internet yo'q") }
                AiOutcome.NoKey -> _state.update { it.copy(isLoading = false, needsKey = true) }
            }
        }
    }

    fun toggle() = _state.update { it.copy(expanded = !it.expanded) }

    fun saveKey(key: String) {
        viewModelScope.launch {
            repository.saveKey(key)
            _state.update { it.copy(needsKey = false) }
            analyze()
        }
    }

    fun dismissKey() = _state.update { it.copy(needsKey = false) }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_DATE = "date"
    }
}

@Composable
fun AiFinanceCard() {
    val viewModel: AiFinanceViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val colors = LocalOnIkkiColors.current
    val today = LocalDate.now()
    OnIkkiCard(modifier = Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = state.text != null, onClick = viewModel::toggle)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "✨ AI oylik tahlil", color = colors.text, fontSize = 14.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = when {
                        state.isLoading -> "Tahlil qilinmoqda…"
                        state.madeOn == today -> "Bugun tahlil qilingan"
                        state.madeOn != null -> "Oxirgi tahlil: ${state.madeOn}"
                        else -> "Xarajatlar, budjet va to'lovlaringizga qarab maslahat"
                    },
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (state.text != null && !state.isLoading) {
                Text(text = if (state.expanded) "▲" else "▼", color = colors.text.muted(0.5f), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.expanded && state.text != null) {
            SelectionContainer {
                Text(text = state.text!!, color = colors.text.muted(0.85f), fontSize = 13.sp, lineHeight = 19.sp, fontFamily = OnIkkiFontFamily)
            }
        }
        state.error?.let { Text(text = it, color = colors.warmAccent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily) }
        if (!state.isLoading && (state.text == null || state.expanded)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnIkkiButton(
                    text = if (state.text == null) "Tahlil qilish" else "Yangilash",
                    onClick = viewModel::analyze,
                    variant = if (state.text == null) OnIkkiButtonVariant.PRIMARY else OnIkkiButtonVariant.SECONDARY
                )
            }
        }
    }
    if (state.needsKey) ApiKeySheet(onDismiss = viewModel::dismissKey, onSave = viewModel::saveKey)
}
