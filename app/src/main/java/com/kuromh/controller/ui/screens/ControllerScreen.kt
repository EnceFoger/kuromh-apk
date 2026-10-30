package com.kuromh.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.core.SaveStatus
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.SwitchCard
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControllerScreen(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    // State lokal; di-reset setiap config dari modul berubah
    var unitySpeed by remember(config) { mutableFloatStateOf(config.unitySpeed) }
    var damageRate by remember(config) { mutableFloatStateOf(config.damageRate) }
    var godMode by remember(config) { mutableStateOf(config.godMode) }
    var dump by remember(config) { mutableStateOf(config.dump) }

    val maxSpeed = config.maxUnitySpeed.coerceAtLeast(1f)
    val maxDamage = config.maxDamageRate.coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Controller", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (!isRootGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enable Root System in Home first to edit the module config.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        ValueSliderCard(
            title = "Unity Speed",
            valueText = String.format(Locale.US, "%.1fx", unitySpeed),
            value = unitySpeed,
            range = 0.1f..maxSpeed,
            enabled = isRootGranted,
            onValueChange = { unitySpeed = it }
        )
        Spacer(modifier = Modifier.height(12.dp))

        ValueSliderCard(
            title = "Damage Rate",
            valueText = String.format(Locale.US, "%.1fx", damageRate),
            value = damageRate,
            range = 0.1f..maxDamage,
            enabled = isRootGranted,
            onValueChange = { damageRate = it }
        )
        Spacer(modifier = Modifier.height(12.dp))

        SwitchCard(
            title = "God Mode",
            subtitle = if (godMode) "On" else "Off",
            checked = godMode,
            onCheckedChange = { godMode = it },
            enabled = isRootGranted,
            highlightSubtitle = godMode
        )
        Spacer(modifier = Modifier.height(12.dp))

        SwitchCard(
            title = "Diagnostic Dump",
            subtitle = if (dump) "On" else "Off",
            checked = dump,
            onCheckedChange = { dump = it },
            enabled = isRootGranted,
            highlightSubtitle = dump
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.clearSaveStatus()
                    unitySpeed = config.unitySpeed
                    damageRate = config.damageRate
                    godMode = config.godMode
                    dump = config.dump
                },
                enabled = isRootGranted,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset", color = if (isRootGranted) Color.White else Color.Gray)
            }
            Button(
                onClick = {
                    viewModel.clearSaveStatus()
                    viewModel.updateConfig(
                        config.copy(
                            unitySpeed = unitySpeed,
                            damageRate = damageRate,
                            godMode = godMode,
                            dump = dump
                        )
                    )
                },
                enabled = isRootGranted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply")
            }
        }

        when (saveStatus) {
            SaveStatus.SUCCESS -> StatusText("Config saved.", Color.White)
            SaveStatus.FAILED -> StatusText("Failed to write config. Check root access.", Color.Gray)
            SaveStatus.IDLE -> Unit
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(text, color = color, fontSize = 14.sp)
}

@Composable
private fun ValueSliderCard(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    KuroCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(valueText, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = value.coerceIn(range),
                onValueChange = { onValueChange((it * 10).roundToInt() / 10f) }, // langkah 0.1
                valueRange = range,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }
    }
}
