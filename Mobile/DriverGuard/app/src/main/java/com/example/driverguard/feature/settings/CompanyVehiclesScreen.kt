package com.example.driverguard.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.driverguard.feature.dispatch.DispatchViewModel
import com.example.driverguard.feature.dispatch.dispatchLabel

@Composable
fun CompanyVehiclesScreen(viewModel: DispatchViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val trips = state.trips.filter { it.status in listOf("assigned", "accepted", "in_progress", "handover_pending") }
        .sortedBy { it.start }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Quay lại") }
        Text("Phương tiện được phân công", style = MaterialTheme.typography.headlineSmall)
        Text("Thông tin xe do công ty quản lý và phân công theo từng chuyến.")
        if (!state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.loaded && trips.isEmpty()) Text("Chưa có xe được phân công. Xe sẽ xuất hiện khi điều phối giao chuyến cho bạn.")
        if (state.fromCache && trips.isNotEmpty()) Text("Đang xem thông tin đã lưu. Kết nối mạng để nhận thay đổi mới.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        trips.forEach { trip ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(trip.vehicleName.ifBlank { "Xe công ty" }, style = MaterialTheme.typography.titleLarge)
                    Text("Biển số: ${trip.vehiclePlate}", style = MaterialTheme.typography.titleMedium)
                    Text("Chuyến: ${trip.name}")
                    Text(dispatchLabel(trip.status), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
