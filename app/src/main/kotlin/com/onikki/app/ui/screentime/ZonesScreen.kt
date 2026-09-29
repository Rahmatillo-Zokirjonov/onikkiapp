package com.onikki.app.ui.screentime

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onikki.app.data.db.entity.BlockZone
import com.onikki.app.domain.screentime.ZoneLocationTracker
import com.onikki.app.ui.components.AddFab
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.components.OnIkkiCard
import com.onikki.app.ui.components.OnIkkiSheet
import com.onikki.app.ui.components.SheetActions
import com.onikki.app.ui.components.SheetErrorText
import com.onikki.app.ui.components.SheetFieldLabel
import com.onikki.app.ui.components.SubScreenHeader
import com.onikki.app.ui.components.SuggestionChips
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.muted
import kotlinx.coroutines.launch
import java.util.Locale

private val RADIUS_OPTIONS = listOf(100, 300, 500, 1000)

/** Places for place-based blocks. Saved by standing there and tapping "Hozirgi joyim" (or typing coordinates). */
@Composable
fun ZonesScreen(
    zones: List<BlockZone>,
    onBack: () -> Unit,
    onSave: (BlockZone) -> Unit,
    onDelete: (BlockZone) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    var editing by remember { mutableStateOf<BlockZone?>(null) }
    var creating by remember { mutableStateOf(false) }
    var backgroundGranted by remember { mutableStateOf(hasBackgroundLocation(context)) }
    LifecycleResumeEffect(Unit) {
        backgroundGranted = hasBackgroundLocation(context)
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SubScreenHeader(title = "Hududlar", onBack = onBack) }
            item {
                Text(
                    text = "Maktab, ish joyi yoki masjid kabi joylarni saqlang. Keyin istalgan ilovani shu joyda bloklash mumkin.",
                    color = colors.text.muted(0.6f),
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
            if (!backgroundGranted && zones.isNotEmpty()) {
                item {
                    OnIkkiCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.warmBorder, gap = 8.dp) {
                        Text(
                            text = "Hudud bloki boshqa ilova ochiq turganda ishlashi uchun joylashuvga \"Har doim ruxsat berish\" kerak.",
                            color = colors.text.muted(0.75f),
                            fontSize = 13.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        OnIkkiButton(
                            text = "Ruxsatni ochish",
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (zones.isEmpty()) {
                item { Text(text = "Hali hudud yo'q — + bilan qo'shing.", color = colors.text.muted(0.5f), fontSize = 13.sp, fontFamily = OnIkkiFontFamily) }
            }
            items(zones, key = { it.id }) { zone ->
                OnIkkiCard(modifier = Modifier.fillMaxWidth().clickable { editing = zone }, gap = 2.dp) {
                    Text(text = zone.name, color = colors.text, fontSize = 15.sp, fontFamily = OnIkkiFontFamily)
                    Text(
                        text = "radius ${zone.radiusMeters} m · ${"%.5f".format(Locale.US, zone.latitude)}, ${"%.5f".format(Locale.US, zone.longitude)}",
                        color = colors.text.muted(0.5f),
                        fontSize = 11.sp,
                        fontFamily = OnIkkiFontFamily
                    )
                }
            }
        }
        AddFab(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp))
    }

    if (creating || editing != null) {
        ZoneSheet(
            zone = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { onSave(it); creating = false; editing = null },
            onDelete = { editing?.let(onDelete); editing = null }
        )
    }
}

private fun hasBackgroundLocation(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
private fun ZoneSheet(zone: BlockZone?, onDismiss: () -> Unit, onSave: (BlockZone) -> Unit, onDelete: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(zone?.name ?: "") }
    var latText by remember { mutableStateOf(zone?.latitude?.let { "%.6f".format(Locale.US, it) } ?: "") }
    var lonText by remember { mutableStateOf(zone?.longitude?.let { "%.6f".format(Locale.US, it) } ?: "") }
    var radius by remember { mutableStateOf(zone?.radiusMeters ?: 300) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun locate() {
        locating = true
        error = null
        scope.launch {
            val location = ZoneLocationTracker.fetchCurrent(context)
            locating = false
            if (location == null) {
                error = "Joylashuv aniqlanmadi — GPS yoqilganini tekshiring"
            } else {
                latText = "%.6f".format(Locale.US, location.latitude)
                lonText = "%.6f".format(Locale.US, location.longitude)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) locate() else error = "Joylashuv ruxsati berilmadi"
    }

    OnIkkiSheet(title = if (zone == null) "Yangi hudud" else "Hududni tahrirlash", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = { Text("Nomi") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (zone == null) {
            SuggestionChips(options = listOf("Maktab", "Universitet", "Ish", "Masjid", "Uy"), selected = name, onSelect = { name = it })
        }
        OnIkkiButton(
            text = if (locating) "Aniqlanmoqda…" else "📍 Hozirgi joyimni olish",
            onClick = {
                if (locating) return@OnIkkiButton
                if (ZoneLocationTracker.hasPermission(context)) {
                    locate()
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            variant = OnIkkiButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it; error = null },
                label = { Text("Kenglik") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it; error = null },
                label = { Text("Uzunlik") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        SheetFieldLabel("Radius")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RADIUS_OPTIONS.forEach { option ->
                FilterChip(selected = radius == option, onClick = { radius = option }, label = { Text(if (option >= 1000) "1 km" else "$option m") })
            }
        }
        Column {
            Text(
                text = "Joyning o'zida turib \"Hozirgi joyimni olish\"ni bosing — shunda eng aniq bo'ladi.",
                color = colors.text.muted(0.5f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily
            )
        }
        SheetErrorText(error)
        SheetActions(
            onSave = {
                val lat = latText.replace(',', '.').toDoubleOrNull()
                val lon = lonText.replace(',', '.').toDoubleOrNull()
                when {
                    name.isBlank() -> error = "Hudud nomini yozing"
                    lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 -> error = "Joylashuvni oling yoki koordinatalarni yozing"
                    else -> onSave(
                        (zone ?: BlockZone(name = name, latitude = lat, longitude = lon)).copy(
                            name = name.trim(), latitude = lat, longitude = lon, radiusMeters = radius
                        )
                    )
                }
            },
            onDelete = if (zone != null) onDelete else null
        )
    }
}
