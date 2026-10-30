package com.kuromh.controller.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Refresh
import com.kuromh.controller.ui.components.InfoButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.InstallStatus
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.core.ModuleManager
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.StatusCard
import com.kuromh.controller.ui.components.SwitchCard

/**
 * Menu Controller:
 * - modul belum terpasang -> tombol Install Module (+ Upload Module jika installer bermasalah)
 * - modul sudah terpasang -> toggle ON/OFF lalu kontrol config (Unity Speed, Damage Rate, dll),
 *   installer tetap ada di bagian bawah untuk Reinstall.
 */
@Composable
fun ControllerScreen(viewModel: MainViewModel) {
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val installed by viewModel.moduleInstalled.collectAsState()
    val enabled by viewModel.moduleEnabled.collectAsState()
    val rebootPending by viewModel.moduleRebootPending.collectAsState()
    val install by viewModel.installState.collectAsState()
    val toggleFailed by viewModel.toggleFailed.collectAsState()
    val moduleToggleBusy by viewModel.moduleToggleBusy.collectAsState()
    val otherTogglePending by viewModel.otherTogglePending.collectAsState()
    val otherModules by viewModel.otherModules.collectAsState()
    val otherBusy by viewModel.otherModuleBusy.collectAsState()
    val otherResult by viewModel.otherModuleResult.collectAsState()
    var selectedModuleId by rememberSaveable { mutableStateOf(ModuleManager.MODULE_ID) }

    LaunchedEffect(isRootGranted) {
        if (isRootGranted) viewModel.refreshOtherModules()
    }
    LaunchedEffect(selectedModuleId, isRootGranted) {
        viewModel.detectGame(selectedModuleId)
        if (selectedModuleId == ModuleManager.MODULE_ID) viewModel.closeExternalControls()
        else viewModel.openExternalControls(selectedModuleId)
    }

    val extraZipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.installOtherModule(uri)
    }


    // Pilih file zip modul sendiri (fallback jika installer bermasalah)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.installFromUri(uri)
    }

    val running = install.status == InstallStatus.RUNNING
    val active = installed && !rebootPending
    val statusText = when {
        !installed -> "Not Installed"
        rebootPending -> "Reboot Required"
        enabled -> "Active"
        else -> "Disabled"
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
                "Enable Root System in Home first to install or control the module.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Modules",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            InfoButton(
                "Module Controls",
                "Tap a module to open its own controls. Enable or disable takes effect after a reboot for other root modules. Only modules exposing config values can offer feature controls."
            )
            IconButton(
                onClick = { viewModel.refreshOtherModules() },
                enabled = isRootGranted && !otherBusy
            ) { Icon(Icons.Default.Refresh, contentDescription = "Refresh modules", tint = Color.White) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        KuroCard {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { selectedModuleId = ModuleManager.MODULE_ID }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kuro Mirror High", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (!installed) "Not installed" else if (enabled) "Enabled" else "Disabled",
                                color = Color.Gray, fontSize = 12.sp
                            )
                        }
                        Text(if (selectedModuleId == ModuleManager.MODULE_ID) "Selected" else "Open",
                            color = Color.LightGray, fontSize = 12.sp)
                    }
                }
                otherModules.forEach { module ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedModuleId = module.id }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(module.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    module.version + "  •  " + if (module.enabled) "Enabled" else "Disabled",
                                    color = Color.Gray, fontSize = 12.sp
                                )
                            }
                            Text(if (selectedModuleId == module.id) "Selected" else "Open",
                                color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
                Button(
                    onClick = { extraZipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = isRootGranted && !otherBusy && otherTogglePending.isEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install Module ZIP")
                }
                if (otherResult.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Module status", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        InfoButton("Module status", otherResult)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedModuleId == ModuleManager.MODULE_ID) {
        StatusCard("Kuro Mirror High", statusText, active && enabled)
        Spacer(modifier = Modifier.height(12.dp))
        ModuleGameSection(viewModel, selectedModuleId, installed)

        // ---- Modul sudah terpasang: toggle + kontrol config ----
        if (installed) {
            SwitchCard(
                title = "Module",
                subtitle = when {
                    rebootPending -> "Reboot to activate"
                    enabled -> "On"
                    else -> "Off - module stopped"
                },
                checked = enabled,
                onCheckedChange = { viewModel.setModuleEnabled(it) },
                enabled = isRootGranted && active && !running && !moduleToggleBusy,
                highlightSubtitle = enabled && active
            )
            if (toggleFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Failed to change module state. Check root access and try again.", color = Color.Gray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))

            ModuleConfigControls(viewModel)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ---- Install ----
        KuroCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Module Installer", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                InfoButton("Kuro Mirror High", "Installs the built-in module. Removing KUROMH also removes that module. Reboot after installation.")
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.clearInstallState()
                        viewModel.installBundledModule()
                    },
                    enabled = isRootGranted && !running,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (installed) "Reinstall Module" else "Install Module")
                }

                if (install.message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        install.message,
                        fontSize = 14.sp,
                        color = if (install.status == InstallStatus.FAILED) Color.Gray else Color.White
                    )
                }

                // Muncul hanya jika installer bawaan bermasalah
                if (install.status == InstallStatus.FAILED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { picker.launch("*/*") },
                        enabled = isRootGranted && !running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Upload Module", color = Color.White)
                    }
                }
            }
        }
        } else {
            OtherModuleControllerPanel(viewModel, selectedModuleId)
        }

    }
}
