package com.onikki.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.components.OnIkkiRowCard
import com.onikki.app.ui.screentime.ScreenTimeRoute
import com.onikki.app.ui.settings.SettingsRoute
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiType
import com.onikki.app.ui.theme.muted

private enum class ProfileDestination { MENU, APP_CONTROL, SETTINGS }

@Composable
fun ProfileSectionRoute() {
    var destination by rememberSaveable { mutableStateOf(ProfileDestination.MENU) }

    when (destination) {
        ProfileDestination.MENU -> ProfileMenu(
            onOpenAppControl = { destination = ProfileDestination.APP_CONTROL },
            onOpenSettings = { destination = ProfileDestination.SETTINGS }
        )
        ProfileDestination.APP_CONTROL -> ScreenTimeRoute()
        ProfileDestination.SETTINGS -> SettingsRoute(onBack = { destination = ProfileDestination.MENU })
    }
}

@Composable
private fun ProfileMenu(onOpenAppControl: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Profil", color = colors.text, style = OnIkkiType.screenTitle)

        MenuRow(title = "Ilovalar nazorati", subtitle = "Ekran vaqti va limitlar", onClick = onOpenAppControl)
        MenuRow(title = "Sozlamalar", subtitle = "Shahar, ruxsatlar, AI kaliti", onClick = onOpenSettings)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    OnIkkiRowCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 15.sp)
            Text(
                text = subtitle,
                color = colors.text.muted(0.5f),
                fontFamily = OnIkkiFontFamily,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(text = "→", color = colors.text.muted(0.5f), fontFamily = OnIkkiFontFamily, fontSize = 14.sp)
    }
}
