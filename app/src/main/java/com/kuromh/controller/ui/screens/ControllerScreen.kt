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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    val configPath by viewModel.configPath.collectAsState()

    // State lokal; di-reset setiap config dari modul berubah
    var unitySpeed by remember(config) { mutableFloatStateOf(config.unitySpeed) }
    var damageRate by remember(config) { mutableFloatStateOf(config.damageRate) }
    var godMode by remember(config) { mutableStateOf(config.godMode) }
    var dump by remember(config) { mutableStateOf(config.dump) }

    // Batas maksimum slider (bisa diubah lewat kartu "Slider Limits")
    var maxSpeedText by remember(config) { mutableStateOf(formatLimit(config.maxUnitySpeed)) }
    var maxDamageText by remember(config) { mutableStateOf(formatLimit(config.maxDamageRate)) }

    val maxSpeed = parseLimit(maxSpeedText) ?: config.maxUnitySpeed.coerceIn(LIMIT_MIN, LIMIT_MAX)
    val maxDamage = parseLimit(maxDamageText) ?: config.maxDamageRate.coerceIn(LIMIT_MIN, LIMIT_MAX)
    val limitsValid = parseLimit(maxSpeedText) != null && parseLimit(maxDamageText) != null

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
        } else if (configPath == MainViewModel.NOT_FOUND) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Config not found. Launch the game once with the module active so it creates kuro_mirror_high.cfg, then press Refresh in Home.",
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

        LimitsCard(
            maxSpeedText = maxSpeedText,
            maxDamageText = maxDamageText,
            enabled = isRootGranted,
            onMaxSpeedChange = { maxSpeedText = sanitizeLimit(it) },
            onMaxDamageChange = { maxDamageText = sanitizeLimit(it) }
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
                    maxSpeedText = formatLimit(config.maxUnitySpeed)
                    maxDamageText = formatLimit(config.maxDamageRate)
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
                            // Nilai slider dijepit agar tidak melebihi batas baru
                            unitySpeed = unitySpeed.coerceIn(0.1f, maxSpeed),
                            damageRate = damageRate.coerceIn(0.1f, maxDamage),
                            godMode = godMode,
                            dump = dump,
                            maxUnitySpeed = maxSpeed,
                            maxDamageRate = maxDamage
                        )
                    )
                },
                enabled = isRootGranted && limitsValid,
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
            SaveStatus.FAILED -> StatusText("Failed to write config. Make sure the game has been launched once so the config exists.", Color.Gray)
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
                onValueChange = {
                    // langkah 0.1; jika batas besar (>50) pakai langkah 1 agar slider tetap presisi
                    onValueChange(if (range.endInclusive > 50f) it.roundToInt().toFloat() else (it * 10).roundToInt() / 10f)
                },
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

private const val LIMIT_MIN = 1f
private const val LIMIT_MAX = 999f

private fun formatLimit(v: Float): String = String.format(Locale.US, "%.1f", v)

/** Hanya izinkan angka, satu titik/koma desimal; koma diganti titik agar aman di locale Indonesia. */
private fun sanitizeLimit(input: String): String {
    val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned
    else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}

/** Null jika kosong / bukan angka / di luar rentang [LIMIT_MIN]..[LIMIT_MAX]. */
private fun parseLimit(text: String): Float? =
    text.toFloatOrNull()?.takeIf { it in LIMIT_MIN..LIMIT_MAX }

@Composable
private fun LimitsCard(
    maxSpeedText: String,
    maxDamageText: String,
    enabled: Boolean,
    onMaxSpeedChange: (String) -> Unit,
    onMaxDamageChange: (String) -> Unit
) {
    KuroCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Slider Limits", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Set the maximum value of each slider (${formatLimit(LIMIT_MIN)} - ${formatLimit(LIMIT_MAX)}). Press Apply to save.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LimitField("Max Unity Speed", maxSpeedText, enabled, onMaxSpeedChange, Modifier.weight(1f))
                LimitField("Max Damage Rate", maxDamageText, enabled, onMaxDamageChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LimitField(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        enabled = enabled,
        isError = parseLimit(value) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            cursorColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = Color.White,
            unfocusedLabelColor = Color.Gray
        ),
        modifier = modifier
    )
}
