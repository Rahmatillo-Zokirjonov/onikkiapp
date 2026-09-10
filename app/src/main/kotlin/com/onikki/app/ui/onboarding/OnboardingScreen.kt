package com.onikki.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onikki.app.OnIkkiApplication
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.LocationStore
import com.onikki.app.data.local.OnboardingStore
import com.onikki.app.ui.components.CityPickerSheet
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.components.PermissionStatusCard
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted
import com.onikki.app.ui.util.OnResumeEffect

@Composable
fun OnboardingRoute() {
    val context = LocalContext.current
    val app = context.applicationContext as OnIkkiApplication
    val locationStore = remember { LocationStore(app) }
    val onboardingStore = remember { OnboardingStore(app) }
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModel.factory(app, locationStore, onboardingStore)
    )
    val state by viewModel.uiState.collectAsState()

    OnResumeEffect { viewModel.refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    OnboardingScreen(
        state = state,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onOpenUsageAccessSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
        onOpenAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        onOpenCityPicker = viewModel::openCityPicker,
        onDismissCityPicker = viewModel::dismissCityPicker,
        onSelectCity = viewModel::selectCity,
        onFinish = viewModel::finish
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onRequestNotifications: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenCityPicker: () -> Unit,
    onDismissCityPicker: () -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onFinish: () -> Unit
) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 32.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "On ikki",
                color = colors.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily
            )
            Text(
                text = "Kunlik reja, odatlar, moliya va ilova nazorati — bitta joyda, to'liq offline.",
                color = colors.text.muted(0.6f),
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Text(
            text = "Bir martalik sozlash",
            color = colors.text,
            style = OnIkkiType.sectionHeader,
            modifier = Modifier.padding(top = 8.dp)
        )

        CityCard(city = state.selectedCity, onClick = onOpenCityPicker)

        PermissionStatusCard(
            title = "Bildirishnomalar",
            description = "Vazifa va odatlar uchun eslatmalar yuborish uchun kerak.",
            isGranted = state.hasNotificationPermission,
            actionLabel = "Ruxsat berish",
            onAction = onRequestNotifications
        )
        PermissionStatusCard(
            title = "Ilova ishlatish statistikasi",
            description = "Ilovalar nazorati bo'limida ekran vaqtini ko'rsatish uchun kerak (Sozlamalar > Maxsus ruxsatlar).",
            isGranted = state.hasUsageAccess,
            actionLabel = "Sozlamalarga o'tish",
            onAction = onOpenUsageAccessSettings
        )
        PermissionStatusCard(
            title = "Maxsus imkoniyat xizmati",
            description = "\"Zararli\" deb belgilangan ilovalarni limitdan oshganda bloklash uchun kerak (Sozlamalar > Maxsus imkoniyatlar).",
            isGranted = state.hasAccessibilityEnabled,
            actionLabel = "Sozlamalarga o'tish",
            onAction = onOpenAccessibilitySettings
        )

        Text(
            text = "Ruxsatlarni keyinroq Sozlamalar bo'limidan ham yoqishingiz mumkin.",
            color = colors.text.muted(0.45f),
            fontSize = 11.sp,
            fontFamily = OnIkkiFontFamily
        )

        OnIkkiButton(
            text = "Boshlash",
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    if (state.isCityPickerOpen) {
        CityPickerSheet(
            selectedCityName = state.selectedCity.name,
            onDismiss = onDismissCityPicker,
            onSelect = onSelectCity
        )
    }
}

@Composable
private fun CityCard(city: CityLocation, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Shahar", color = colors.text.muted(0.5f), fontSize = 11.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = city.name,
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(text = "O'zgartirish", color = colors.accent, fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
    }
}
