package com.kuromh.controller.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.InfoCard
import com.kuromh.controller.ui.components.SwitchCard

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var selectedExternalId by remember { mutableStateOf<String?>(null) }
    val otherModules by viewModel.otherModules.collectAsState()
    val otherResult by viewModel.otherModuleResult.collectAsState()
    val otherBusy by viewModel.otherModuleBusy.collectAsState()
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.installOtherModule(uri)
    }
    val configPath by viewModel.configPath.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val isRootToggleEnabled by viewModel.isRootToggleEnabled.collectAsState()
    val context = LocalContext.current
    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(20.dp))

        InfoCard("App Information", "KUROMH Controller v$appVersion")
        Spacer(modifier = Modifier.height(8.dp))

        SwitchCard(
            title = "Root Authorization",
            subtitle = if (isRootGranted) "Granted via su (Magisk/KernelSU)" else "Not Authorized",
            checked = isRootToggleEnabled,
            onCheckedChange = { viewModel.setRootToggle(it) }
        )

        Spacer(modifier = Modifier.height(8.dp))
        InfoCard("Configuration Location", configPath)
        Spacer(modifier = Modifier.height(8.dp))
        InfoCard("About KUROMH", "Designed for Kuro Mirror High Zygisk Module.")

        Spacer(modifier = Modifier.height(24.dp))
        Text("Other Modules", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                enabled = isRootGranted && !otherBusy
            ) { Text("Install ZIP") }
            OutlinedButton(
                onClick = { viewModel.refreshOtherModules() },
                enabled = isRootGranted && !otherBusy
            ) { Text("Refresh") }
        }
        if (otherResult.isNotEmpty()) {
            Text(otherResult, color = Color.LightGray, fontSize = 12.sp)
        }
        if (!isRootGranted) {
            Text("Enable Root System first.", color = Color.Gray)
        } else if (otherModules.isEmpty()) {
            Text("No additional modules detected.", color = Color.Gray)
        }
        otherModules.forEach { module ->
            Spacer(modifier = Modifier.height(8.dp))
            SwitchCard(
                title = module.name,
                subtitle = "${module.id} • ${module.version}",
                checked = module.enabled,
                enabled = !otherBusy,
                onCheckedChange = { viewModel.toggleOtherModule(module.id, it) }
            )
            OutlinedButton(
                onClick = {
                    if (selectedExternalId == module.id) {
                        selectedExternalId = null
                        viewModel.closeExternalControls()
                    } else {
                        selectedExternalId = module.id
                        viewModel.openExternalControls(module.id)
                    }
                },
                enabled = isRootGranted && !otherBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (selectedExternalId == module.id) "Hide Mod Controls" else "Control Mod Features") }
            if (selectedExternalId == module.id) {
                Spacer(modifier = Modifier.height(8.dp))
                ExternalFeatureControls(viewModel, module.id)
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { viewModel.removeOtherModule(module.id) },
                enabled = !otherBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Remove ${module.name} (reboot required)") }
        }
    }
}
