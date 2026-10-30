package com.kuromh.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
fun DashboardScreen(viewModel: MainViewModel) {
    val isRootToggleEnabled by viewModel.isRootToggleEnabled.collectAsState()
    val isRootGranted by viewModel.isRootGranted.collectAsState()
    val isModule by viewModel.isModuleActive.collectAsState()
    val version by viewModel.moduleVersion.collectAsState()
    val configPath by viewModel.configPath.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Welcome to\nKUROMH",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = { viewModel.refreshStatus() },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Root Detection & Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Enable Root System", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isRootGranted) "Root Access Granted" else "Awaiting Root Permission",
                        color = if (isRootGranted) Color.White else Color.Gray,
                        fontSize = 12.sp
                    )
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

        Spacer(modifier = Modifier.height(16.dp))

        StatusCard("Module Status", if (isModule) "Active" else "Inactive", isModule)
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard("Module Version", version, version != "Not Detected")
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard("Config Connection", if (configPath != "Not Found") "Connected" else "Not Detected", configPath != "Not Found")
    }
}

@Composable
fun StatusCard(title: String, status: String, isActive: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, fontSize = 16.sp, color = Color.White)
            Text(
                text = status,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color.White else Color.Gray
            )
        }
    }
}
