package com.example.driverguard.feature.monitoring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.driverguard.core.theme.c
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.monitoring.ai.TripSummary

@Composable
fun TripSummaryDialog(summary: TripSummary, onDismiss: () -> Unit) {
    val colors = MaterialTheme.c
    val pending by AlarmRepository.pendingTripIds.collectAsState()
    val syncErrors by AlarmRepository.tripSyncErrors.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(.94f), shape = MaterialTheme.shapes.large, color = colors.surface) {
            Column(
                modifier = Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tổng kết chuyến đi", style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Text(summary.timeRangeLabel, color = colors.textMuted)
                Text(
                    if (summary.id in syncErrors) "Đã lưu trên máy · Chưa đồng bộ được"
                    else if (summary.id in pending) "Đã lưu trên máy · Chờ đồng bộ khi có mạng" else "Báo cáo đã lưu",
                    color = colors.primary, style = MaterialTheme.typography.bodySmall
                )
                if (summary.id in pending) {
                    syncErrors[summary.id]?.let { Text(it, color = colors.textMuted, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = AlarmRepository::retryPendingTrips) { Text("Thử đồng bộ lại") }
                }
                Card(colors = CardDefaults.cardColors(containerColor = colors.primaryBg)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Điểm theo dữ liệu quan sát", color = colors.textMuted)
                        Text(summary.scoreLabel, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                        if (summary.safetyScore != null) Text("Hạng ${summary.scoreGrade} · ${summary.riskLevel.label}", color = colors.text)
                        Text("Thang điểm thử nghiệm; không xác nhận chuyến xe an toàn.", color = colors.textMuted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                SummaryRow("Thời gian chuyến", summary.durationLabel)
                SummaryRow("Thời gian quan sát mắt hợp lệ", "${"%.1f".format(summary.validObservationSec)} giây")
                SummaryRow("Bao phủ thời gian chuyến", "${"%.0f".format(summary.measurementCoverage * 100)}%")
                SummaryRow("Tỷ lệ thời gian mắt nhắm", summary.eyeClosedPercent?.let { "${"%.1f".format(it)}%" } ?: "Chưa có dữ liệu")
                SummaryRow("Đoạn nhắm từ 2 giây", "${summary.prolongedClosureCount}")
                SummaryRow("Lần nhắm dài nhất", "${"%.1f".format(summary.longestClosedSec)} giây")
                SummaryRow("Sự kiện cảnh báo đã ghi", "${summary.totalAlerts}")
                HorizontalDivider()
                Text("Nhận xét từ dữ liệu", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(summary.aiDiagnosis, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                summary.aiRecommendations.forEach { Text("• $it", color = colors.textMuted) }
                summary.topLocation?.takeIf { it.isNotBlank() }?.let { Text("Vị trí sự kiện: $it", color = colors.textMuted) }
                Text(if (summary.scoringVersion == "legacy") "Báo cáo cũ chưa có thống kê quan sát đầy đủ."
                    else "Mất hình, tạm dừng và thời gian ứng dụng không chạy làm giảm độ bao phủ.",
                    style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Đóng báo cáo") }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.c.textMuted,
            style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.c.text, style = MaterialTheme.typography.bodySmall)
    }
}
