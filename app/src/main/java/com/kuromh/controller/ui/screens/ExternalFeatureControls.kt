package com.kuromh.controller.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.ExternalModControls
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.SwitchCard
import java.util.Locale
import kotlin.math.round

/** Displays controls that a third-party module explicitly declares and implements. */
@Composable
fun ExternalFeatureControls(viewModel: MainViewModel, moduleId: String) {
    val panel by viewModel.externalPanel.collectAsState()
    val message by viewModel.externalMessage.collectAsState()
    val busy by viewModel.externalBusy.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        val current = panel?.takeIf { it.moduleId == moduleId }
        if (current == null) {
            Text(
                if (message.isBlank()) "Loading controls..." else message,
                color = Color.Gray,
                fontSize = 12.sp
            )
        } else {
            Text(current.title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Config: " + current.configFile,
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            current.controls.forEach { control ->
                if (control.type == "switch") {
                    SwitchCard(
                        title = control.label,
                        subtitle = control.key,
                        checked = control.value == control.on,
                        enabled = !busy,
                        onCheckedChange = { enabled ->
                            viewModel.saveExternalSetting(
                                moduleId,
                                control.key,
                                if (enabled) control.on else control.off
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    ExternalSlider(control, busy) { value ->
                        viewModel.saveExternalSetting(moduleId, control.key, value)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (message.isNotBlank()) {
                Text(message, fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                "Changes work only if this mod reads these config keys. Restart the game or reboot if needed.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ExternalSlider(
    control: ExternalModControls.Control,
    busy: Boolean,
    onApply: (String) -> Unit
) {
    val initial = (control.value.toFloatOrNull() ?: control.min).coerceIn(control.min, control.max)
    var chosen by remember(control.key, control.value) { mutableFloatStateOf(initial) }
    KuroCard {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(control.label, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    String.format(Locale.US, "%.2f", chosen),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Slider(
                value = chosen,
                onValueChange = { raw ->
                    val stepNumber = round((raw - control.min) / control.step)
                    chosen = (control.min + stepNumber * control.step).coerceIn(control.min, control.max)
                },
                valueRange = control.min..control.max,
                enabled = !busy
            )
            Button(
                onClick = {
                    onApply(String.format(Locale.US, "%.4f", chosen).trimEnd('0').trimEnd('.'))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apply " + control.label) }
        }
    }
}
