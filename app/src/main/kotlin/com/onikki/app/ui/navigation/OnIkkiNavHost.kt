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
import com.onikki.app.ui.dayreview.DayReviewRoute
import com.onikki.app.ui.finance.FinanceSectionRoute
import com.onikki.app.ui.home.HomeRoute
import com.onikki.app.ui.notes.NotesRoute
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

private const val ROUTE_DAY_REVIEW = "day_review"

private fun OnIkkiTab.route(): String = name.lowercase()

@Composable
fun OnIkkiNavHost(navController: NavHostController = rememberNavController()) {
    val colors = LocalOnIkkiColors.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val activeTab = OnIkkiTab.entries.firstOrNull { it.route() == currentRoute } ?: OnIkkiTab.HOME
    val isTabRoute = currentRoute == null || OnIkkiTab.entries.any { it.route() == currentRoute }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = OnIkkiTab.HOME.route()) {
                composable(OnIkkiTab.HOME.route()) {
                    HomeRoute(onNavigateToDayReview = { navController.navigate(ROUTE_DAY_REVIEW) })
                }
                composable(OnIkkiTab.PLAN.route()) { PlanSectionRoute() }
                composable(OnIkkiTab.MONEY.route()) { FinanceSectionRoute() }
                composable(OnIkkiTab.NOTES.route()) { NotesRoute() }
                composable(OnIkkiTab.PROFILE.route()) { ProfileSectionRoute() }
                composable(ROUTE_DAY_REVIEW) { DayReviewRoute(onBack = { navController.popBackStack() }) }
            }
        }
        if (isTabRoute) {
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
