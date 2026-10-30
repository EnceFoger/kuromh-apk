package com.kuromh.controller.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.InfoButton
import com.kuromh.controller.ui.components.KuroCard
import com.kuromh.controller.ui.components.SwitchCard

@Composable
fun ModuleGameSection(viewModel: MainViewModel, moduleId: String, moduleInstalled: Boolean) {
    val game by viewModel.selectedGame.collectAsState()
    val result by viewModel.gameLaunchMessage.collectAsState()
    val context = LocalContext.current

    KuroCard {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Target Game", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            game.packageName.isBlank() -> "Unknown game"
                            game.installed -> game.displayName + " • Installed"
                            else -> game.displayName + " • Not installed"
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                InfoButton("Target Game", if (game.packageName.isBlank())
                    "The module needs a valid package.txt to identify its target game."
                    else "Package: " + game.packageName)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.openSelectedGame() },
                modifier = Modifier.fillMaxWidth(),
                enabled = moduleInstalled && game.launchable
            ) { Text("Open the Game") }
            if (result.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Launch failed", color = Color.Gray, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    InfoButton("Game launch", result)
                }
            }
            if (moduleId == "zygisk-zeromodzbb") {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.parse("package:" + game.packageName.ifBlank { context.packageName })
                                }
                            )
                        } catch (_: Exception) {
                            try { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
                            catch (_: Exception) { }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Overlay Settings") }
                InfoButton(
                    "Overlay Permission",
                    "Opens Android overlay settings. The native in-game menu may not use Android overlay permission; grant it only if the mod requires it."
                )
            }
        }
    }
}

@Composable
fun OtherModuleControllerPanel(viewModel: MainViewModel, moduleId: String) {
    val modules by viewModel.otherModules.collectAsState()
    val busy by viewModel.otherModuleBusy.collectAsState()
    val pending by viewModel.otherTogglePending.collectAsState()
    val result by viewModel.otherModuleResult.collectAsState()
    val root by viewModel.isRootGranted.collectAsState()
    val module = modules.firstOrNull { it.id == moduleId } ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.weight(1f)) {
            Text(module.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(module.version, color = Color.Gray, fontSize = 12.sp)
        }
        InfoButton("Module", "ID: " + module.id + "\nRoot module changes apply after reboot.")
    }
    Spacer(Modifier.height(8.dp))
    SwitchCard(
        title = "Module",
        subtitle = if (module.id in pending) "Saving..." else if (module.enabled) "Enabled" else "Disabled",
        checked = module.enabled,
        enabled = root && !busy && module.id !in pending,
        onCheckedChange = { viewModel.toggleOtherModule(module.id, it) }
    )
    Spacer(Modifier.height(12.dp))
    ModuleGameSection(viewModel, module.id, true)
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Features", color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        InfoButton("Feature Controls", "Only features supported by the installed module can be controlled here.")
    }
    Spacer(Modifier.height(8.dp))
    if (module.id == "zygisk-zeromodzbb") {
        KuroCard {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("In-game menu", color = Color.White, modifier = Modifier.weight(1f))
                InfoButton(
                    "ZeroModz Features",
                    "ZeroModz v1.0.7 uses an in-game menu and has no external control API. Use its menu after opening the game."
                )
            }
        }
    } else {
        ExternalFeatureControls(viewModel, module.id)
    }

    if (result.isNotBlank()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Module status", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
            InfoButton("Module status", result)
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { viewModel.removeOtherModule(module.id) },
        enabled = root && !busy && module.id !in pending,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Remove Module") }
}
