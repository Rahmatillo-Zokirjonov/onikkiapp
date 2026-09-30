package com.onikki.app.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.ui.dailyplan.DailyPlanRoute
import com.onikki.app.ui.goals.GoalsRoute
import com.onikki.app.ui.habits.HabitsRoute
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes

// NOTE: the mockup shows Odatlar (1c) sharing the tab bar's "plan" active
// state with Kunlik reja (1b), but doesn't show how you switch between
// them. This segmented switcher is our own addition to make that real —
// not something pulled directly from the mockup.
private enum class PlanSubTab(val label: String) {
    DAILY_PLAN("Kunlik reja"),
    GOALS("Maqsadlar"),
    HABITS("Odatlar")
}

@Composable
fun PlanSectionRoute() {
    var subTab by rememberSaveable { mutableStateOf(PlanSubTab.DAILY_PLAN) }
    val colors = LocalOnIkkiColors.current

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        PlanSubTabSwitcher(selected = subTab, onSelect = { subTab = it })
        Box(modifier = Modifier.weight(1f)) {
            when (subTab) {
                PlanSubTab.DAILY_PLAN -> DailyPlanRoute()
                PlanSubTab.GOALS -> GoalsRoute()
                PlanSubTab.HABITS -> HabitsRoute()
            }
        }
    }
}

@Composable
private fun PlanSubTabSwitcher(selected: PlanSubTab, onSelect: (PlanSubTab) -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth()
            .border(BorderStroke(1.dp, colors.divider), OnIkkiShapes.medium)
    ) {
        PlanSubTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) colors.accent else colors.text,
                    fontSize = 13.sp,
                    fontFamily = OnIkkiFontFamily
                )
            }
        }
    }
}
