package com.example.driverguard.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object NavigationDestination {
    var address by mutableStateOf<String?>(null)
}

/** Used for preview navigation before a trip starts; monitoring has its own inline map. */
@Composable
fun NavigationDestinationHost() {
    val address = NavigationDestination.address ?: return
    BackHandler { NavigationDestination.address = null }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TextButton(onClick = { NavigationDestination.address = null }) { Text("Đóng dẫn đường") }
            Text(address, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
            key(address) { GoogleNavigationMap(address, Modifier.weight(1f).fillMaxWidth(), stateKey = "previewNavigation") }
        }
    }
}
