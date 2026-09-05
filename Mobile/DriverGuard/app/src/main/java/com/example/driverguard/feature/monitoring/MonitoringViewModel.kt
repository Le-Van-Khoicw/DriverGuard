package com.example.driverguard.feature.auth.monitoring

import androidx.lifecycle.ViewModel
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.history.AlertEvent
import com.example.driverguard.feature.monitoring.ai.DrowsinessDetector
import com.example.driverguard.feature.monitoring.ai.ThresholdClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.driverguard.core.location.GpsLocation

enum class MonitoringStatus {
    IDLE,
    CALIBRATING,   // 3 giây đầu: đo EAR baseline của từng người
    MONITORING,
    DROWSY,
    STOPPED
}

data class MonitoringUiState(
    val status: MonitoringStatus = MonitoringStatus.IDLE,
    val ear: Double? = null,
    val confidence: Double? = null,
    val warningCount: Int = 0,
    val message: String = "Camera chưa bắt đầu giám sát",
    val earBaseline: Double? = null,       // EAR mắt mở của người này
    val calibrationProgress: Float = 0f,  // 0.0 → 1.0 trong 3 giây calibration
    val gpsLocation: GpsLocation? = null   // Tọa độ và tốc độ xe hiện tại
)

class MonitoringViewModel : ViewModel() {
    // ── EAR calibration ──────────────────────────────────────────────────────
    // Ý tưởng: mỗi người có ngưỡng EAR khác nhau.
    //   • 3 giây đầu → thu thập EAR baseline (trung bình lúc mắt mở bình thường)
    //   • threshold  = baseline × 0.75  (mắt nhắm thì EAR giảm còn ~60-70% baseline)
    private val calibrationDurationMs = 3_000L
    private val earSamplesForCalib    = mutableListOf<Float>()
    private var calibStartMs: Long    = 0L
    private var isCalibrated          = false

    private val classifier = ThresholdClassifier(threshold = 0.25f)  // sẽ bị ghi đè sau calibration
    private val detector   = DrowsinessDetector(classifier = classifier)

    private var alertWasActive = false
    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    fun startMonitoring() {
        detector.reset()
        alertWasActive = false
        earSamplesForCalib.clear()
        calibStartMs   = android.os.SystemClock.elapsedRealtime()
        isCalibrated   = false
        classifier.threshold = 0.25f   // reset về mặc định trước khi calibrate

        _uiState.value = _uiState.value.copy(
            status  = MonitoringStatus.CALIBRATING,
            message = "Đang đo EAR cơ sở… hãy nhìn thẳng vào camera"
        )
    }

    fun onLocationUpdated(location: GpsLocation?) {
        _uiState.value = _uiState.value.copy(gpsLocation = location)
    }

    fun onEarDetected(ear: Float?) {
        val status = _uiState.value.status
        if (status == MonitoringStatus.IDLE || status == MonitoringStatus.STOPPED) return

        val nowMs = android.os.SystemClock.elapsedRealtime()

        // ── Giai đoạn CALIBRATING ────────────────────────────────────────────
        if (status == MonitoringStatus.CALIBRATING) {
            if (ear != null && ear > 0.15f) {
                earSamplesForCalib.add(ear)
            }
            val elapsed  = nowMs - calibStartMs
            val progress = (elapsed.toFloat() / calibrationDurationMs).coerceIn(0f, 1f)

            if (elapsed >= calibrationDurationMs) {
                // Tính baseline và cập nhật threshold cho detector
                val baseline = if (earSamplesForCalib.isNotEmpty())
                    earSamplesForCalib.average()
                else
                    0.25               // fallback nếu không nhận được frame nào

                classifier.threshold = (baseline * 0.75).toFloat()
                isCalibrated = true
                detector.reset()

                _uiState.value = _uiState.value.copy(
                    status             = MonitoringStatus.MONITORING,
                    earBaseline        = baseline,
                    calibrationProgress = 1f,
                    message            = "Baseline EAR: ${"%.3f".format(baseline)} · Ngưỡng: ${"%.3f".format(classifier.threshold)}"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    ear                 = ear?.toDouble(),
                    calibrationProgress = progress,
                    message             = "Đang hiệu chỉnh… (${(progress * 100).toInt()}%)"
                )
            }
            return
        }

        // ── Giai đoạn MONITORING / DROWSY ────────────────────────────────────
        val result   = detector.process(ear, nowMs)
        val newAlert = result.shouldAlert && !alertWasActive
        alertWasActive = result.shouldAlert

        if (newAlert) {
            val gps = _uiState.value.gpsLocation
            // Lưu sự kiện cảnh báo kèm tọa độ GPS vào AlarmRepository
            AlarmRepository.add(
                AlertEvent(
                    ear               = ear?.toDouble() ?: 0.0,
                    closedDurationSec = result.closedDurationMs / 1000.0,
                    warningIndex      = _uiState.value.warningCount + 1,
                    latitude          = gps?.latitude,
                    longitude         = gps?.longitude,
                    speedKmh          = gps?.speedKmh,
                    locationAddress   = gps?.address
                )
            )
        }

        _uiState.value = _uiState.value.copy(
            status       = if (result.shouldAlert) MonitoringStatus.DROWSY else MonitoringStatus.MONITORING,
            ear          = ear?.toDouble(),
            confidence   = result.features?.belowThresholdRatio?.toDouble(),
            warningCount = _uiState.value.warningCount + if (newAlert) 1 else 0,
            message      = when {
                ear == null       -> "Không thấy rõ khuôn mặt"
                result.shouldAlert -> "⚠️ Cảnh báo: mắt nhắm quá 3 giây!"
                else               -> "Đang phân tích mắt tài xế"
            }
        )
    }

    fun onCameraError(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    fun simulateDrowsiness() {
        val current = _uiState.value
        val gps = current.gpsLocation
        AlarmRepository.add(
            AlertEvent(
                ear               = 0.18,
                closedDurationSec = 3.2,
                warningIndex      = current.warningCount + 1,
                latitude          = gps?.latitude ?: 10.7769,
                longitude         = gps?.longitude ?: 106.7009,
                speedKmh          = gps?.speedKmh ?: 52.0f,
                locationAddress   = gps?.address ?: "Quận 1, TP. Hồ Chí Minh"
            )
        )
        _uiState.value = current.copy(
            status       = MonitoringStatus.DROWSY,
            ear          = 0.18,
            confidence   = 0.91,
            warningCount = current.warningCount + 1,
            message      = "⚠️ Cảnh báo: phát hiện dấu hiệu buồn ngủ"
        )
    }

    fun stopMonitoring() {
        detector.reset()
        _uiState.value = _uiState.value.copy(
            status  = MonitoringStatus.STOPPED,
            message = "Đã dừng giám sát · ${_uiState.value.warningCount} cảnh báo trong phiên này"
        )
    }
}

