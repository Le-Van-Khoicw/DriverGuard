package com.example.driverguard.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.pip.PipManager
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        label = "Tổng quan",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    ),
    MONITORING(
        label = "Giám sát",
        selectedIcon = Icons.Filled.RemoveRedEye,
        unselectedIcon = Icons.Outlined.RemoveRedEye
    ),
    HISTORY(
        label = "Lịch sử",
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics
    ),
    SETTINGS(
        label = "Cài đặt",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun MainScaffold(
    selected: MainTab,
    onTabSelected: (MainTab) -> Unit,
    content: @Composable () -> Unit
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font
    val inPip by PipManager.isInPipMode.collectAsState()

    Scaffold(
        bottomBar = {
            if (!inPip) {
                NavigationBar(
                    containerColor = c.bottomNav,
                    tonalElevation = 8.dp
                ) {
                    MainTab.entries.forEach { tab ->
                        val isSelected = tab == selected
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { onTabSelected(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = font.xs,
                                    fontWeight = if (isSelected) font.bold else font.medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1D4ED8),
                                selectedTextColor = Color(0xFF1D4ED8),
                                indicatorColor = Color(0xFFDBEAFE),
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(if (inPip) PaddingValues(0.dp) else padding)) {
            content()
        }
    }
}
