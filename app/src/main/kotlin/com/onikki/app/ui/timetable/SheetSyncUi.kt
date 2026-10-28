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
import com.onikki.app.data.repository.SheetConnection
import com.onikki.app.data.repository.SheetSyncRepository
import com.onikki.app.data.repository.SheetSyncResult
import com.onikki.app.domain.timetable.SheetTab
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
    val connections: List<SheetConnection> = emptyList(),
    val lastSync: Long = 0,
    val error: String? = null,
    val busy: Boolean = false,
    /** The connect flow: tabs of the pasted link, the chosen tab's groups, groups with the user's students. */
    val tabs: List<SheetTab> = emptyList(),
    val groups: List<String> = emptyList(),
    val starred: Set<String> = emptySet(),
    val message: String? = null
)

class SheetSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SheetSyncRepository(application, (application as OnIkkiApplication).database)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SheetSyncState> = _state

    private fun read() = SheetSyncState(connections = repo.connections, lastSync = repo.lastSync, error = repo.lastError)

    /** On opening the timetable: a quiet sync if the last one is old. */
    fun syncIfStale() {
        if (repo.connections.isEmpty()) return
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

    fun findTabs(link: String) {
        _state.value = _state.value.copy(busy = true, tabs = emptyList(), groups = emptyList(), message = null)
        viewModelScope.launch {
            val r = repo.tabs(link)
            _state.value = _state.value.copy(busy = false, tabs = r.getOrDefault(emptyList()), message = r.exceptionOrNull()?.message)
        }
    }

    fun findGroups(link: String, tab: SheetTab) {
        _state.value = _state.value.copy(busy = true, groups = emptyList(), starred = emptySet(), message = null)
        // Groups that have students of the already-connected academic group get a star ("26-202 (20)").
        val academic = repo.connections.firstOrNull()?.group
        viewModelScope.launch {
            val r = repo.groups(link, tab.gid, academic)
            val (groups, starred) = r.getOrDefault(emptyList<String>() to emptySet())
            _state.value = _state.value.copy(
                busy = false,
                groups = groups.sortedBy { if (it in starred) 0 else 1 },
                starred = starred,
                message = r.exceptionOrNull()?.message ?: if (r.isSuccess && groups.isEmpty()) "Bu varaqda guruh topilmadi" else null
            )
        }
    }

    fun connect(link: String, tab: SheetTab, group: String, onDone: () -> Unit) {
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val r = repo.connect(link, tab.gid, tab.name, group)
            _state.value = read().copy(message = when (r) {
                is SheetSyncResult.Updated -> "✓ $group: ${r.lessons} ta dars jadvalga qo'shildi"
                is SheetSyncResult.Failed -> r.message
                else -> null
            })
            if (r is SheetSyncResult.Updated) onDone()
        }
    }

    fun setupFromId(link: String, id: String, onDone: () -> Unit) {
        if (link.isBlank() || id.isBlank()) { _state.value = _state.value.copy(message = "Havola va talaba ID ni yozing"); return }
        _state.value = _state.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val r = repo.setupFromStudentId(link, id)
            _state.value = read().copy(message = r.fold(
                onSuccess = { s -> "✓ ${s.student.course} · ${s.student.academicGroup} · ingliz: ${s.student.englishGroup}" + (s.student.level?.let { " ($it)" } ?: "") + "\n" + s.connected.joinToString(", ") },
                onFailure = { it.message ?: "Topilmadi" }
            ))
            if (r.isSuccess) onDone()
        }
    }

    fun disconnect(c: SheetConnection) {
        viewModelScope.launch { repo.disconnect(c); _state.value = read().copy(message = "${c.group} uzildi") }
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

/** The line under the view switch: connected groups + refresh, or "connect a Google Sheet". */
@Composable
fun SheetSyncRow() {
    val vm: SheetSyncViewModel = viewModel()
    val s by vm.state.collectAsState()
    val colors = LocalOnIkkiColors.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.syncIfStale() }
    val connected = s.connections.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).clickable { open = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔗", fontSize = 14.sp)
        Text(
            text = if (connected) s.connections.joinToString(" + ") { it.group } + " · ${s.error ?: ago(s.lastSync)}" else "Google Sheets jadvalini ulash",
            color = if (s.error != null) colors.warmAccent else if (connected) colors.text.muted(0.75f) else colors.accent,
            fontSize = 12.sp,
            fontFamily = OnIkkiFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        if (connected) {
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
    var studentId by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf<SheetTab?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    OnIkkiSheet(title = "Google Sheets jadvali", onDismiss = onDismiss) {
        if (s.connections.isNotEmpty()) {
            Text(text = "Ulangan · ${ago(s.lastSync)}", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
            s.connections.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = c.label, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = OnIkkiFontFamily, modifier = Modifier.weight(1f))
                    Text(text = "Uzish", color = colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.disconnect(c) }.padding(6.dp))
                }
            }
            s.message?.let { Text(text = it, color = colors.accent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            OnIkkiButton(text = if (s.busy) "Yangilanmoqda…" else "Hozir yangilash", onClick = vm::syncNow, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp))
            Text(
                text = "Yana qo'shish (masalan, ingliz tili jadvali): jadval havolasini qo'ying, varaqni va guruhingizni tanlang.",
                color = colors.text.muted(0.55f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Text(
                text = "Jadval havolasini qo'ying (\"Havolaga ega har kim ko'ra oladi\" bo'lishi kerak), keyin varaqni va guruhingizni tanlang. Jadval o'zi yangilanib turadi, o'zgarsa bildirishnoma keladi.",
                color = colors.text.muted(0.65f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        OutlinedTextField(value = link, onValueChange = { link = it.trim(); tab = null; picked = null }, label = { Text("https://docs.google.com/spreadsheets/…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // Fastest: the student ID finds the course, the academic group and the English group.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it.filter(Char::isDigit).take(12) },
                label = { Text("Talaba ID") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OnIkkiButton(text = if (s.busy) "…" else "Avto ulash", onClick = { if (!s.busy) vm.setupFromId(link, studentId, onDone = {}) })
        }
        Text(
            text = "ID bo'yicha: kursingiz, akademik guruhingiz va ingliz tili guruhingiz ro'yxatdan topiladi, ikkala jadval o'zi ulanadi. Yoki pastda varaq va guruhni qo'lda tanlang.",
            color = colors.text.muted(0.5f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )
        OnIkkiButton(text = if (s.busy && s.tabs.isEmpty()) "Yuklanmoqda…" else "Varaqlarni ko'rsatish", onClick = { if (link.isNotBlank() && !s.busy) vm.findTabs(link) }, variant = OnIkkiButtonVariant.SECONDARY, modifier = Modifier.fillMaxWidth())
        if (s.tabs.isNotEmpty()) {
            Text(text = "Varaq:", color = colors.text.muted(0.6f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.tabs.forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t; picked = null; vm.findGroups(link, t) }, label = { Text(t.name) }) }
            }
        }
        if (tab != null && s.groups.isNotEmpty()) {
            Text(
                text = if (s.starred.isNotEmpty()) "Guruhingiz (★ — ${s.connections.firstOrNull()?.group?.substringBefore(' ')} talabalari bor):" else "Guruhingiz:",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.groups.forEach { g -> FilterChip(selected = picked == g, onClick = { picked = g }, label = { Text(if (g in s.starred) "★ $g" else g) }) }
            }
            OnIkkiButton(
                text = picked?.let { "Ulash: $it" } ?: "Guruhni tanlang",
                onClick = { val t = tab; val g = picked; if (t != null && g != null) vm.connect(link, t, g, onDone = onDismiss) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            )
        }
        if (s.connections.isEmpty() || s.tabs.isNotEmpty()) s.message?.let { Text(text = it, color = if (it.startsWith("✓")) colors.accent else colors.warmAccent, fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
    }
}
