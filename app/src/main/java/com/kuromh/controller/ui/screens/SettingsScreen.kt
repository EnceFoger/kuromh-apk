package com.kuromh.controller.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuromh.controller.core.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val configPath by viewModel.configPath.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val isRootToggleEnabled by viewModel.isRootToggleEnabled.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(20.dp))

        SettingsItem("App Information", "KUROMH Controller v1.0")
        Spacer(modifier = Modifier.height(8.dp))
        
        // Root Control in Settings
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Root Authorization", color = Color.White, fontWeight = FontWeight.Medium)
                    Text(if (isRootGranted) "Granted via Magisk/Zygisk" else "Not Authorized", color = Color.Gray, fontSize = 14.sp)
                }
                Switch(
                    checked = isRootToggleEnabled,
                    onCheckedChange = { viewModel.setRootToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem("Configuration Location", configPath)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsItem("About KUROMH", "Designed for Kuro Mirror High Zygisk Module.")
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        }
    }
}
