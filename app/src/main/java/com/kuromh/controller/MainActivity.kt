package com.kuromh.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kuromh.controller.core.AppUpdateViewModel
import com.kuromh.controller.core.MainViewModel
import com.kuromh.controller.ui.MainScreen
import com.kuromh.controller.ui.theme.KUROMHTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val updates: AppUpdateViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        updates.checkIfDue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KUROMHTheme {
                MainScreen(viewModel, updates)
            }
        }
    }
}
