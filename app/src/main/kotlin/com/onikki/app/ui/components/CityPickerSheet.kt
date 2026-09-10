package com.onikki.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.local.CityLocation
import com.onikki.app.data.local.UZBEKISTAN_CITIES
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerSheet(
    selectedCityName: String?,
    onDismiss: () -> Unit,
    onSelect: (CityLocation) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(text = "Shaharni tanlang", color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 18.sp)
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                UZBEKISTAN_CITIES.forEach { city ->
                    val isSelected = city.name == selectedCityName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(city) }
                            .padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = city.name,
                            color = if (isSelected) colors.accent else colors.text,
                            fontSize = 14.sp,
                            fontFamily = OnIkkiFontFamily
                        )
                        if (isSelected) {
                            Text(text = "✓", color = colors.accent, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
