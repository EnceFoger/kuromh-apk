// Kontrol config lama (slider Unity Speed/Damage Rate, God Mode, Dump, batas max). Sementara tidak dipakai di UI;
// panggil ModuleConfigControls(viewModel) dari ControllerScreen untuk menampilkannya lagi.
package com.kuromh.controller.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.core.SaveStatus
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.SwitchCard
import java.util.Locale
import kotlin.math.roundToInt

private enum class LimitTarget { SPEED, DAMAGE }

private const val LIMIT_MIN = 1f
private const val LIMIT_MAX = 100f   // sama dengan kHardMax di config.cpp modul

@Composable
fun ModuleConfigControls(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()
    val configPath by viewModel.configPath.collectAsState()

    // State lokal; di-reset setiap config dari modul berubah
    var unitySpeed by remember(config) { mutableFloatStateOf(config.unitySpeed) }
    var damageRate by remember(config) { mutableFloatStateOf(config.damageRate) }
    var godMode by remember(config) { mutableStateOf(config.godMode) }
    var dump by remember(config) { mutableStateOf(config.dump) }
    var maxSpeed by remember(config) { mutableFloatStateOf(config.maxUnitySpeed.coerceIn(LIMIT_MIN, LIMIT_MAX)) }
    var maxDamage by remember(config) { mutableFloatStateOf(config.maxDamageRate.coerceIn(LIMIT_MIN, LIMIT_MAX)) }

    // Slider mana yang sedang diubah batas maksimumnya (null = dialog tertutup)
    var editingLimit by remember { mutableStateOf<LimitTarget?>(null) }

    // Simpan semua nilai ke config; nilai slider dijepit agar tidak melebihi batas maksimum
    fun save(newMaxSpeed: Float = maxSpeed, newMaxDamage: Float = maxDamage) {
        viewModel.clearSaveStatus()
        viewModel.updateConfig(
            config.copy(
                unitySpeed = unitySpeed.coerceIn(0.1f, newMaxSpeed),
                damageRate = damageRate.coerceIn(0.1f, newMaxDamage),
                godMode = godMode,
                dump = dump,
                maxUnitySpeed = newMaxSpeed,
                maxDamageRate = newMaxDamage
            )
        )
    }

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
            onValueChange = { unitySpeed = it },
            onSettingsClick = { editingLimit = LimitTarget.SPEED }
        )
        Spacer(modifier = Modifier.height(12.dp))

        ValueSliderCard(
            title = "Damage Rate",
            valueText = String.format(Locale.US, "%.1fx", damageRate),
            value = damageRate,
            range = 0.1f..maxDamage,
            enabled = isRootGranted,
            onValueChange = { damageRate = it },
            onSettingsClick = { editingLimit = LimitTarget.DAMAGE }
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
                    maxSpeed = config.maxUnitySpeed.coerceIn(LIMIT_MIN, LIMIT_MAX)
                    maxDamage = config.maxDamageRate.coerceIn(LIMIT_MIN, LIMIT_MAX)
                },
                enabled = isRootGranted,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset", color = if (isRootGranted) Color.White else Color.Gray)
            }
            Button(
                onClick = { save() },
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
            SaveStatus.FAILED -> StatusText("Failed to write config. Make sure the game has been launched once so the config exists.", Color.Gray)
            SaveStatus.IDLE -> Unit
        }
    }

    when (editingLimit) {
        LimitTarget.SPEED -> MaxLimitDialog(
            title = "Max Unity Speed",
            current = maxSpeed,
            onDismiss = { editingLimit = null },
            onConfirm = { newMax ->
                maxSpeed = newMax
                editingLimit = null
                save(newMaxSpeed = newMax)
            }
        )
        LimitTarget.DAMAGE -> MaxLimitDialog(
            title = "Max Damage Rate",
            current = maxDamage,
            onDismiss = { editingLimit = null },
            onConfirm = { newMax ->
                maxDamage = newMax
                editingLimit = null
                save(newMaxDamage = newMax)
            }
        )
        null -> Unit
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
    onValueChange: (Float) -> Unit,
    onSettingsClick: () -> Unit
) {
    KuroCard {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(valueText, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    // Ikon pengaturan di sisi kanan angka: ubah batas maksimum slider
                    IconButton(onClick = onSettingsClick, enabled = enabled) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Set max $title",
                            tint = if (enabled) Color.White else Color.DarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Slider(
                value = value.coerceIn(range),
                onValueChange = {
                    // langkah 0.1; jika batas besar (>50) pakai langkah 1 agar slider tetap presisi
                    onValueChange(
                        if (range.endInclusive > 50f) it.roundToInt().toFloat()
                        else (it * 10).roundToInt() / 10f
                    )
                },
                valueRange = range,
                enabled = enabled,
                modifier = Modifier.padding(end = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.DarkGray
                )
            )
        }
    }
}

/** Dialog untuk mengetik batas maksimum baru sebuah slider. */
@Composable
private fun MaxLimitDialog(
    title: String,
    current: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.1f", current)) }
    val parsed = text.toFloatOrNull()?.takeIf { it in LIMIT_MIN..LIMIT_MAX }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Maximum value of the slider (${LIMIT_MIN.toInt()} - ${LIMIT_MAX.toInt()}). Saved to config immediately.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeLimit(it) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Hanya izinkan angka dan satu titik desimal; koma diganti titik (aman untuk locale Indonesia). */
private fun sanitizeLimit(input: String): String {
    val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned
    else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
}
