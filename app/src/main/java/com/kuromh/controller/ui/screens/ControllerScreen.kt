package com.kuromh.controller.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.StatusCard
import com.kuromh.controller.ui.components.SwitchCard

/**
 * Menu Controller (sementara): install modul yang dibundel di APK, toggle ON/OFF modul,
 * dan opsi "Upload Module" jika installer bermasalah.
 * Kontrol config lama (slider dll) ada di ConfigControls.kt -> ModuleConfigControls().
 */
@Composable
fun ControllerScreen(viewModel: MainViewModel) {
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val installed by viewModel.moduleInstalled.collectAsState()
    val enabled by viewModel.moduleEnabled.collectAsState()
    val rebootPending by viewModel.moduleRebootPending.collectAsState()
    val install by viewModel.installState.collectAsState()
    val toggleFailed by viewModel.toggleFailed.collectAsState()

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

        StatusCard("Kuro Mirror High", statusText, active && enabled)
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Install ----
        KuroCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Module Installer", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Installs the module bundled in this app. Removing this app also removes the module.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
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
        Spacer(modifier = Modifier.height(12.dp))

        // ---- Toggle ON/OFF ----
        SwitchCard(
            title = "Module",
            subtitle = when {
                !installed -> "Install the module first"
                rebootPending -> "Reboot to activate"
                enabled -> "On"
                else -> "Off - module stopped"
            },
            checked = enabled,
            onCheckedChange = { viewModel.setModuleEnabled(it) },
            enabled = isRootGranted && active && !running,
            highlightSubtitle = enabled && active
        )
        if (toggleFailed) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Failed to change module state. Check root access and try again.", color = Color.Gray, fontSize = 14.sp)
        }
        if (active && !enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Module is off: a running game returns to normal within a few seconds, and the game will not load the module on next launch.",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
