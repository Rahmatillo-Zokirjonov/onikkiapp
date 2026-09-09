package com.onikki.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onikki.app.ui.components.OnIkkiBottomNavBar
import com.onikki.app.ui.components.OnIkkiTab
import com.onikki.app.ui.dailyplan.DailyPlanRoute
import com.onikki.app.ui.home.HomeRoute
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

private fun OnIkkiTab.route(): String = name.lowercase()

@Composable
fun OnIkkiNavHost(navController: NavHostController = rememberNavController()) {
    val colors = LocalOnIkkiColors.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val activeTab = OnIkkiTab.entries.firstOrNull { it.route() == currentRoute } ?: OnIkkiTab.HOME

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = OnIkkiTab.HOME.route()) {
                composable(OnIkkiTab.HOME.route()) { HomeRoute() }
                composable(OnIkkiTab.PLAN.route()) { DailyPlanRoute() }
                composable(OnIkkiTab.MONEY.route()) { ComingSoonScreen("Moliya") }
                composable(OnIkkiTab.NOTES.route()) { ComingSoonScreen("Qaydlar") }
                composable(OnIkkiTab.PROFILE.route()) { ComingSoonScreen("Profil") }
            }
        }
        OnIkkiBottomNavBar(
            active = activeTab,
            onSelect = { tab ->
                if (tab.route() != currentRoute) {
                    navController.navigate(tab.route()) {
                        popUpTo(OnIkkiTab.HOME.route()) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        )
    }
}

@Composable
private fun ComingSoonScreen(title: String) {
    val colors = LocalOnIkkiColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$title — tez kunda", color = colors.text, fontFamily = OnIkkiFontFamily)
    }
}
