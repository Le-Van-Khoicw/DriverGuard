package com.example.driverguard.ui.monitoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MonitoringScreen(viewModel: MonitoringViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("DriverGuard", style = MaterialTheme.typography.headlineMedium)
        Text("Mô phỏng giám sát buồn ngủ", style = MaterialTheme.typography.titleMedium)

        HorizontalDivider()

        Text("Trạng thái: ${statusLabel(state.status)}")
        Text(state.message)
        Text("EAR: ${state.ear?.let { "%.2f".format(it) } ?: "--"}")
        Text("Độ tin cậy: ${state.confidence?.let { "${(it * 100).toInt()}%" } ?: "--"}")
        Text("Số cảnh báo: ${state.warningCount}")

        Button(
            onClick = viewModel::startMonitoring,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bắt đầu giám sát")
        }

        Button(
            onClick = viewModel::simulateDrowsiness,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Giả lập cảnh báo buồn ngủ")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = viewModel::stopMonitoring) {
                Text("Dừng")
            }
        }
    }
}

private fun statusLabel(status: MonitoringStatus): String = when (status) {
    MonitoringStatus.IDLE -> "CHƯA BẮT ĐẦU"
    MonitoringStatus.MONITORING -> "ĐANG GIÁM SÁT"
    MonitoringStatus.DROWSY -> "BUỒN NGỦ"
    MonitoringStatus.STOPPED -> "ĐÃ DỪNG"
}
