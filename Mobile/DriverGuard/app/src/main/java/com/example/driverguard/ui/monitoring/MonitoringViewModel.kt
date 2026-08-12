package com.example.driverguard.ui.monitoring

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MonitoringStatus {
    IDLE,
    MONITORING,
    DROWSY,
    STOPPED
}

data class MonitoringUiState(
    val status: MonitoringStatus = MonitoringStatus.IDLE,
    val ear: Double? = null,
    val confidence: Double? = null,
    val warningCount: Int = 0,
    val message: String = "Camera chưa bắt đầu giám sát"
)

class MonitoringViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    fun startMonitoring() {
        _uiState.value = MonitoringUiState(
            status = MonitoringStatus.MONITORING,
            message = "Đang theo dõi tài xế"
        )
    }

    fun simulateDrowsiness() {
        val current = _uiState.value
        _uiState.value = current.copy(
            status = MonitoringStatus.DROWSY,
            ear = 0.18,
            confidence = 0.91,
            warningCount = current.warningCount + 1,
            message = "Cảnh báo: phát hiện dấu hiệu buồn ngủ"
        )
    }

    fun stopMonitoring() {
        _uiState.value = _uiState.value.copy(
            status = MonitoringStatus.STOPPED,
            message = "Đã dừng giám sát"
        )
    }
}
