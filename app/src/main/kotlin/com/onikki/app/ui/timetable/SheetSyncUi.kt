package com.onikki.app.ui.timetable

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.repository.SheetSyncRepository
import com.onikki.app.data.repository.SheetSyncResult
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SheetSyncState(
    val group: String? = null,
    val lastSync: Long = 0,
    val error: String? = null,
    val busy: Boolean = false,
    /** The connect sheet: groups found in the pasted link, and a message. */
    val groups: List<String> = emptyList(),
    val message: String? = null
)

class SheetSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SheetSyncRepository(application, (application as OnIkkiApplication).database)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SheetSyncState> = _state

    private fun read() = SheetSyncState(group = repo.group, lastSync = repo.lastSync, error = repo.lastError)

    /** On opening the timetable: a quiet sync if the last one is old. */
    fun syncIfStale() {
        if (repo.group == null) return
        viewModelScope.launch { repo.sync(); _state.value = read() }
    }

    fun syncNow() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val r = repo.sync(force = true)
            _state.value = read().copy(message = when (r) {
                is SheetSyncResult.Updated -> if (r.changes.isEmpty()) "O'zgarish yo'q · ${r.lessons} ta dars" else "${r.changes.size} ta o'zgarish: ${r.changes.first()}"
                is SheetSyncResult.Failed -> r.message
                else -> null
            })
        }
    }

    fun findGroups(link: String) {
        _state.value = _state.value.copy(busy = true, groups = emptyList(), message = null)
        viewModelScope.launch {
            val r = repo.groups(link)
            _state.value = _state.value.copy(
                busy = false,
                groups = r.getOrDefault(emptyList()),
                message = r.exceptionOrNull()?.let { it.message ?: "Jadvalni o'qib bo'lmadi" } ?: if (r.getOrNull().isNullOrEmpty()) "Jadvalda guruh topilmadi" else null
            )
        }
    }

    fun connect(link: String, group: String, onDone: () -> Unit) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val r = repo.connect(link, group)
            _state.value = read().copy(message = when (r) {
                is SheetSyncResult.Updated -> "✓ ${r.lessons} ta dars jadvalga qo'shildi"
                is SheetSyncResult.Failed -> r.message
                else -> null
            })
            if (r is SheetSyncResult.Updated) onDone()
        }
    }

    fun disconnect() {
        viewModelScope.launch { repo.disconnect(); _state.value = SheetSyncState(message = "Jadval uzildi") }
    }
}

private fun ago(millis: Long): String {
    if (millis <= 0) return "hali yangilanmagan"
    val m = (System.currentTimeMillis() - millis) / 60_000
    return when {
        m < 1 -> "hozirgina yangilandi"
        m < 60 -> "$m daqiqa oldin yangilandi"
        m < 24 * 60 -> "${m / 60} soat oldin yangilandi"
        else -> "${m / (24 * 60)} kun oldin yangilandi"
    }
}

/** The line under the view switch: connected group + refresh, or "connect a Google Sheet". */
@Composable
fun SheetSyncRow() {
    val vm: SheetSyncViewModel = viewModel()
    val s by vm.state.collectAsState()
    val colors = LocalOnIkkiColors.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.syncIfStale() }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔗", fontSize = 14.sp)
        Text(
            text = s.group?.let { "$it · ${s.error ?: ago(s.lastSync)}" } ?: "Google Sheets jadvalini ulash",
            color = if (s.error != null) colors.warmAccent else if (s.group != null) colors.text.muted(0.75f) else colors.accent,
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        if (s.group != null) {
            if (s.busy) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.accent)
            else Text(text = "↻", color = colors.accent, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.syncNow() }.padding(horizontal = 6.dp))
        }
    }
    if (open) SheetConnectSheet(s, vm, onDismiss = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetConnectSheet(s: SheetSyncState, vm: SheetSyncViewModel, onDismiss: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    var link by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    OnIkkiSheet(title = "Google Sheets jadvali", onDismiss = onDismiss) {
        if (s.group != null) {
            Text(text = "Ulangan: ${s.group} · ${ago(s.lastSync)}", color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily)
            Text(
                text = "Jadval ilova ochilganda va kuniga bir necha marta o'zi yangilanadi; o'zgarsa bildirishnoma keladi. Qo'lda qo'shgan darslaringiz o'zgarmaydi.",
                color = colors.text.muted(0.6f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
            s.message?.let { Text(text = it, color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            OnIkkiButton(text = if (s.busy) "Yangilanmoqda…" else "Hozir yangilash", onClick = vm::syncNow, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp))
            OnIkkiButton(text = "Uzish (jadvaldan kelgan darslar o'chadi)", onClick = { vm.disconnect(); onDismiss() }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
            Text(text = "Boshqa jadval yoki guruhni ulash:", color = colors.text.muted(0.55f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text(
                text = "Jadval havolasini qo'ying (\"Havolaga ega har kim ko'ra oladi\" bo'lishi kerak), keyin guruhingizni tanlang.",
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        OutlinedTextField(value = link, onValueChange = { link = it.trim(); picked = null }, label = { Text("https://docs.google.com/spreadsheets/…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OnIkkiButton(text = if (s.busy) "Yuklanmoqda…" else "Guruhlarni ko'rsatish", onClick = { if (link.isNotBlank() && !s.busy) vm.findGroups(link) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        if (s.groups.isNotEmpty()) {
            Text(text = "Guruhingiz:", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.groups.forEach { g -> FilterChip(selected = picked == g, onClick = { picked = g }, label = { Text(g) }) }
            }
            OnIkkiButton(
                text = picked?.let { "Ulash: $it" } ?: "Guruhni tanlang",
                onClick = { picked?.let { g -> vm.connect(link, g, onDone = onDismiss) } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            )
        }
        if (s.group == null) s.message?.let { Text(text = it, color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
    }
}
