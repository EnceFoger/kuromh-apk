package com.kuromh.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.kuromh.controller.ui.components.StatusCard
import com.kuromh.controller.ui.components.SwitchCard

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

        SwitchCard(
            title = "Enable Root System",
            subtitle = if (isRootGranted) "Root Access Granted" else "Awaiting Root Permission",
            checked = isRootToggleEnabled,
            onCheckedChange = { viewModel.setRootToggle(it) },
            highlightSubtitle = isRootGranted
        )

        Spacer(modifier = Modifier.height(16.dp))

        StatusCard("Module Status", if (isModule) "Active" else "Inactive", isModule)
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard("Module Version", version, version != MainViewModel.NOT_DETECTED)
        Spacer(modifier = Modifier.height(12.dp))
        val connected = configPath != MainViewModel.NOT_FOUND
        StatusCard("Config Connection", if (connected) "Connected" else "Not Detected", connected)
    }
}
