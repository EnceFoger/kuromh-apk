package com.kuromh.controller.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kuromh.controller.core.AppUpdateViewModel
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.components.AppUpdateDialog
import com.kuromh.controller.ui.screens.ControllerScreen
import com.kuromh.controller.ui.screens.DashboardScreen
import com.kuromh.controller.ui.screens.SettingsScreen
import com.kuromh.controller.ui.theme.CardDarkGray

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("Home", "Home", Icons.Default.Home),
    Controller("Controller", "Controller", Icons.Default.Tune),
    Settings("Settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen(viewModel: MainViewModel, updates: AppUpdateViewModel) {
    val navController = rememberNavController()
    // Item terpilih diambil dari back stack, jadi selalu sinkron (termasuk saat tombol Back)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = CardDarkGray) {
                Destination.values().forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(navController, startDestination = Destination.Home.route) {
                composable(Destination.Home.route) { DashboardScreen(viewModel) }
                composable(Destination.Controller.route) { ControllerScreen(viewModel) }
                composable(Destination.Settings.route) { SettingsScreen(viewModel, updates) }
            }
        }
    }
    AppUpdateDialog(updates)
}
