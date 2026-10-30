package com.kuromh.controller.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.InfoCard
import com.kuromh.controller.ui.components.SwitchCard

@Composable
fun ModuleGameSection(viewModel: MainViewModel, moduleId: String, moduleInstalled: Boolean) {
    val game by viewModel.selectedGame.collectAsState()
    val result by viewModel.gameLaunchMessage.collectAsState()
    val context = LocalContext.current

    val available = game.packageName.isNotBlank()
    val status = when {
        !available -> "Unknown target. A valid package.txt is needed for this module."
        game.installed -> "Detected: " + game.displayName + " (" + game.packageName + ")"
        else -> "Game not installed: " + game.displayName + " (" + game.packageName + ")"
    }

    Spacer(modifier = Modifier.height(12.dp))
    InfoCard("Target Game", status)
    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = { viewModel.openSelectedGame() },
        enabled = moduleInstalled && game.launchable,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Open the Game") }

    if (moduleInstalled && available && !game.launchable) {
        Text(
            "Install/enable the target game to open it. Check the game's package name.",
            fontSize = 12.sp, color = Color.Gray
        )
    }
    if (result.isNotEmpty()) Text(result, fontSize = 12.sp, color = Color.Gray)

    if (moduleId == "zygisk-zeromodzbb" && game.installed) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:" + game.packageName)
                        }
                    )
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    } catch (_: Exception) { }
                }
            }
        ) { Text("Overlay Permission Settings") }
        Text(
            "Opens Android overlay settings for the game. ZeroModz uses an in-game native menu; Android overlay permission may not be required by that menu.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun OtherModuleControllerPanel(viewModel: MainViewModel, moduleId: String) {
    val modules by viewModel.otherModules.collectAsState()
    val busy by viewModel.otherModuleBusy.collectAsState()
    val message by viewModel.otherModuleResult.collectAsState()
    val root by viewModel.isRootGranted.collectAsState()
    val module = modules.firstOrNull { it.id == moduleId } ?: return

    Text(module.name, color = Color.White, fontSize = 20.sp)
    Spacer(modifier = Modifier.height(8.dp))
    InfoCard("Version", module.version)
    Spacer(modifier = Modifier.height(8.dp))
    SwitchCard(
        title = "Enable " + module.name,
        subtitle = "Root module ON/OFF (reboot required)",
        checked = module.enabled,
        enabled = root && !busy,
        onCheckedChange = { viewModel.toggleOtherModule(module.id, it) }
    )
    ModuleGameSection(viewModel, module.id, true)
    Spacer(modifier = Modifier.height(20.dp))

    Text("Feature Controls", color = Color.White, fontSize = 18.sp)
    Spacer(modifier = Modifier.height(8.dp))
    if (module.id == "zygisk-zeromodzbb") {
        InfoCard(
            "ZeroModz In-Game Menu",
            "The supplied v1.0.7 ZIP has a native ImGui menu but no external config or API. Open the game to use its menu. External feature toggles cannot be reliably controlled from KUROMH without integration in the module itself."
        )
    } else {
        ExternalFeatureControls(viewModel, module.id)
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (message.isNotEmpty()) Text(message, fontSize = 12.sp, color = Color.Gray)
    OutlinedButton(
        onClick = { viewModel.removeOtherModule(module.id) },
        enabled = root && !busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Remove Module (after reboot)") }
}
