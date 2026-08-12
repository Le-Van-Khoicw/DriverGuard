package com.example.driverguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverguard.ui.monitoring.MonitoringScreen
import com.example.driverguard.ui.monitoring.MonitoringViewModel
import com.example.driverguard.ui.theme.DriverGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriverGuardTheme {
                val monitoringViewModel: MonitoringViewModel = viewModel()
                MonitoringScreen(viewModel = monitoringViewModel)
            }
        }
    }
}
