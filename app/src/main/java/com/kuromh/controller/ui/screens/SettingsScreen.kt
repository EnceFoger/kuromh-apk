package com.kuromh.controller.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.InfoCard
import com.kuromh.controller.ui.components.SwitchCard

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val configPath by viewModel.configPath.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val isRootToggleEnabled by viewModel.isRootToggleEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(20.dp))

        InfoCard("App Information", "KUROMH Controller v1.0")
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
    }
}
