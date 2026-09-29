package com.onikki.app.ui.settings

import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.onikki.app.ui.home.NameSheet
import com.onikki.app.data.local.ProfileStore
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.ApiKeyStore
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.NotificationSettings
import com.onikki.app.data.local.NotificationSettingsStore
import com.onikki.app.ui.components.CityPickerSheet
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.PermissionStatusCard
import com.onikki.app.ui.dayreview.ApiKeySheet
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.OnResumeEffect

@Composable
fun SettingsRoute(onBack: () -> Unit = {}, onOpenAppControl: () -> Unit = {}, onOpenVocabulary: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as OnIkkiApplication
    val locationStore = remember { LocationStore(app) }
    val apiKeyStore = remember { ApiKeyStore(app) }
    val notificationSettingsStore = remember { NotificationSettingsStore(app) }
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(app, locationStore, apiKeyStore, notificationSettingsStore)
    )
    val state by viewModel.uiState.collectAsState()

    OnResumeEffect { viewModel.refreshPermissions() }

    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenAppControl = onOpenAppControl,
        onOpenVocabulary = onOpenVocabulary,
        onOpenCityPicker = viewModel::openCityPicker,
        onDismissCityPicker = viewModel::dismissCityPicker,
        onSelectCity = viewModel::selectCity,
        onOpenUsageAccessSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        onOpenAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        onOpenAppNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        },
        onOpenApiKeySheet = viewModel::openApiKeySheet,
        onDismissApiKeySheet = viewModel::dismissApiKeySheet,
        onSaveApiKey = viewModel::saveApiKey,
        onClearApiKey = viewModel::clearApiKey,
        onUpdateReminders = viewModel::updateReminders,
        onOpenExactAlarmSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                )
            }
        }
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenAppControl: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenCityPicker: () -> Unit,
    onDismissCityPicker: () -> Unit,
    onSelectCity: (com.onikki.app.data.local.CityLocation) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenApiKeySheet: () -> Unit,
    onDismissApiKeySheet: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onUpdateReminders: ((NotificationSettings) -> NotificationSettings) -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "←",
                color = colors.text.muted(0.6f),
                fontSize = 18.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.clickable(onClick = onBack).padding(4.dp)
            )
            Text(text = "Sozlamalar", color = colors.text, style = OnIkkiType.screenTitle)
        }

        SectionLabel("Profil")
        ProfileNameRow()

        SectionLabel("Shahar va til")
        OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCityPicker)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Shahar", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = state.selectedCity.name,
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(text = "O'zgartirish", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
        }
        OnIkkiRowCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Til", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = "O'zbek (lotin)",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(text = "tez kunda: kirill, rus", color = colors.text.muted(0.4f), fontSize = 10.sp, fontFamily = OnIkkiFontFamily)
        }

        SectionLabel("Eslatmalar")
        ReminderSettingsSection(
            settings = state.reminders,
            canScheduleExact = state.canScheduleExactAlarms,
            onUpdate = onUpdateReminders,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings
        )

        SectionLabel("Ilovalar nazorati")
        OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAppControl)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Bloklash qoidalari", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = "Limit, vaqt, hudud, qat'iy rejim",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Text(text = "→", color = colors.text.muted(0.5f), fontFamily = OnIkkiFontFamily)
        }
        OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenVocabulary)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "So'z yodlash", color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                Text(
                    text = "So'zlar, soni, ochiq turish vaqti, yoqish/o'chirish",
                    color = colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            Text(text = "→", color = colors.text.muted(0.5f), fontFamily = OnIkkiFontFamily)
        }

        SectionLabel("Ruxsatlar holati")
        PermissionStatusCard(
            title = "Bildirishnomalar",
            description = "Vazifa va odatlar uchun eslatmalar yuborish uchun kerak.",
            isGranted = state.hasNotificationPermission,
            actionLabel = "Sozlamalarga o'tish",
            onAction = onOpenAppNotificationSettings
        )
        PermissionStatusCard(
            title = "Ilova ishlatish statistikasi",
            description = "Ilovalar nazorati bo'limi uchun kerak.",
            isGranted = state.hasUsageAccess,
            actionLabel = "Sozlamalarga o'tish",
            onAction = onOpenUsageAccessSettings
        )
        PermissionStatusCard(
            title = "Maxsus imkoniyat xizmati",
            description = "Limitdan oshgan ilovalarni bloklash uchun kerak.",
            isGranted = state.hasAccessibilityEnabled,
            actionLabel = "Sozlamalarga o'tish",
            onAction = onOpenAccessibilitySettings
        )

        SectionLabel("Kun yakuni AI tahlili")
        OnIkkiRowCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Claude API kaliti", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = OnIkkiFontFamily)
                Text(
                    text = if (state.hasApiKey) "Kiritilgan" else "Kiritilmagan",
                    color = if (state.hasApiKey) colors.accent else colors.text.muted(0.5f),
                    fontSize = 11.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (state.hasApiKey) {
                Text(
                    text = "O'chirish",
                    color = colors.text.muted(0.6f),
                    fontSize = 12.sp,
                    fontFamily = OnIkkiFontFamily,
                    modifier = Modifier.clickable(onClick = onClearApiKey).padding(end = 10.dp)
                )
            }
            OnIkkiButton(
                text = if (state.hasApiKey) "O'zgartirish" else "Kiritish",
                onClick = onOpenApiKeySheet,
                variant = OnIkkiButtonVariant.SECONDARY
            )
        }
    }

    if (state.isCityPickerOpen) {
        CityPickerSheet(
            selectedCityName = state.selectedCity.name,
            onDismiss = onDismissCityPicker,
            onSelect = onSelectCity
        )
    }
    if (state.isApiKeySheetOpen) {
        ApiKeySheet(onDismiss = onDismissApiKeySheet, onSave = onSaveApiKey)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalOnIkkiColors.current
    Text(
        text = text,
        color = colors.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = OnIkkiFontFamily,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** The greeting name — self-contained so Settings' big state class doesn't need to grow for it. */
@Composable
private fun ProfileNameRow() {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val store = remember { ProfileStore(context.applicationContext) }
    val name by store.name.collectAsState(initial = "")
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable { editing = true }) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Ism", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = name.ifBlank { "Kiritilmagan" },
                color = if (name.isBlank()) colors.text.muted(0.45f) else colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(text = "O'zgartirish", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
    if (editing) {
        NameSheet(
            current = name,
            onDismiss = { editing = false },
            onSave = { newName ->
                scope.launch { store.setName(newName) }
                editing = false
            }
        )
    }
}
