package com.onikki.app.ui.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onikki.app.data.repository.AppUsageRow
import com.onikki.app.data.repository.BlockRuleFlag
import com.onikki.app.data.repository.parseBlockRules
import com.onikki.app.ui.components.OnIkkiButton
import com.onikki.app.ui.components.OnIkkiButtonVariant
import com.onikki.app.ui.theme.LocalOnIkkiColors
import com.onikki.app.ui.theme.OnIkkiFontFamily
import com.onikki.app.ui.theme.OnIkkiShapes
import com.onikki.app.ui.theme.muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitSheet(
    app: AppUsageRow,
    onDismiss: () -> Unit,
    onSave: (dailyLimitMinutes: Int, isHarmful: Boolean, rules: Set<BlockRuleFlag>) -> Unit
) {
    val colors = LocalOnIkkiColors.current
    val sheetState = rememberModalBottomSheetState()
    var dailyLimit by rememberSaveable { mutableStateOf((app.limit?.dailyLimitMinutes ?: 30).toFloat()) }
    var isHarmful by rememberSaveable { mutableStateOf(app.limit?.isHarmful ?: false) }
    var rules by remember { mutableStateOf(parseBlockRules(app.limit?.blockedHours)) }

    fun toggleRule(flag: BlockRuleFlag) {
        rules = if (rules.contains(flag)) rules - flag else rules + flag
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = app.appName, color = colors.text, fontFamily = OnIkkiFontFamily, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Zararli", color = colors.text.muted(0.6f), fontSize = 12.sp, fontFamily = OnIkkiFontFamily)
                    Switch(checked = isHarmful, onCheckedChange = { isHarmful = it })
                }
            }

            Text(
                text = "Kunlik limit: ${dailyLimit.toInt()} daqiqa",
                color = colors.text,
                fontSize = 13.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 16.dp)
            )
            Slider(value = dailyLimit, onValueChange = { dailyLimit = it }, valueRange = 5f..120f, steps = 22)

            Text(
                text = "Qaysi soatlarda bloklansin",
                color = colors.text.muted(0.8f),
                fontSize = 12.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 8.dp, bottom = 7.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                RuleToggleRow(
                    title = "Ish / o'quv vaqti",
                    subtitle = "09:00 – 18:00",
                    checked = rules.contains(BlockRuleFlag.WORK_HOURS),
                    onToggle = { toggleRule(BlockRuleFlag.WORK_HOURS) }
                )
                RuleToggleRow(
                    title = "Namoz vaqtlarida",
                    subtitle = "har namozdan 20 daqiqa oldin",
                    checked = rules.contains(BlockRuleFlag.PRAYER_TIMES),
                    onToggle = { toggleRule(BlockRuleFlag.PRAYER_TIMES) }
                )
                RuleToggleRow(
                    title = "Kun yakuni bajarilmaguncha",
                    subtitle = "limitdan oshgach ochilmaydi",
                    checked = rules.contains(BlockRuleFlag.UNTIL_DAY_REVIEW),
                    onToggle = { toggleRule(BlockRuleFlag.UNTIL_DAY_REVIEW) }
                )
            }

            Row(modifier = Modifier.padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnIkkiButton(
                    text = "Bekor",
                    onClick = onDismiss,
                    variant = OnIkkiButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                OnIkkiButton(
                    text = "Saqlash",
                    onClick = { onSave(dailyLimit.toInt(), isHarmful, rules) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RuleToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalOnIkkiColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background, OnIkkiShapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text, fontSize = 13.sp, fontFamily = OnIkkiFontFamily)
            Text(
                text = subtitle,
                color = colors.text.muted(0.5f),
                fontSize = 11.sp,
                fontFamily = OnIkkiFontFamily,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}
