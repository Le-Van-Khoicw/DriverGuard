package com.example.driverguard.feature.auth.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverguard.core.location.GpsLocation
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.history.AlertEvent
import com.example.driverguard.feature.monitoring.ai.DrowsinessDetector
import com.example.driverguard.feature.monitoring.ai.ThresholdClassifier
import com.example.driverguard.feature.monitoring.ai.TripAiAnalyzer
import com.example.driverguard.feature.monitoring.ai.TripSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val earBaseline: Double? = null,          // EAR mắt mở của người này
    val calibrationProgress: Float = 0f,     // 0.0 → 1.0 trong 3 giây calibration
    val gpsLocation: GpsLocation? = null,      // Tọa độ và tốc độ xe hiện tại
    val drivingDurationSec: Long = 0L,        // Thời gian lái xe liên tục của phiên
    val isCriticalRestRequired: Boolean = false, // Cảnh báo khẩn cấp: yêu cầu tấp lề nghỉ ngơi!
    val criticalRestReason: String = "",       // Lý do cảnh báo khẩn cấp
    val tripSummary: TripSummary? = null      // Báo cáo AI tổng kết chuyến đi sau khi dừng
)

class MonitoringViewModel : ViewModel() {
    // ── EAR calibration ──────────────────────────────────────────────────────
    private val calibrationDurationMs = 3_000L
    private val earSamplesForCalib    = mutableListOf<Float>()
    private var calibStartMs: Long    = 0L
    private var isCalibrated          = false

    private val classifier = ThresholdClassifier(threshold = 0.25f)
    private val detector   = DrowsinessDetector(classifier = classifier)

    private var alertWasActive = false
    private var lastAlertTimeWallMs = 0L
    private var sessionStartWallTimeMs: Long = 0L
    private val alertsInThisSession = mutableListOf<AlertEvent>()
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    fun startMonitoring() {
        detector.reset()
        alertWasActive = false
        earSamplesForCalib.clear()
        alertsInThisSession.clear()
        calibStartMs = android.os.SystemClock.elapsedRealtime()
        sessionStartWallTimeMs = System.currentTimeMillis()
        isCalibrated = false
        classifier.threshold = 0.25f

        _uiState.value = _uiState.value.copy(
            status = MonitoringStatus.CALIBRATING,
            message = "Đang đo EAR cơ sở… hãy nhìn thẳng vào camera",
            warningCount = 0,
            drivingDurationSec = 0L,
            isCriticalRestRequired = false,
            criticalRestReason = "",
            tripSummary = null
        )

        // Bắt đầu đếm thời gian lái xe liên tục & kiểm tra mệt mỏi
        startDurationTimer()
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.status == MonitoringStatus.MONITORING || _uiState.value.status == MonitoringStatus.DROWSY) {
                    val currentDuration = (System.currentTimeMillis() - sessionStartWallTimeMs) / 1000L
                    
                    // Cảnh báo lái xe quá ngưỡng demo (60 giây) hoặc liên tục
                    val isTimeOver = (currentDuration * 1000L) >= TripAiAnalyzer.DEMO_FATIGUE_DURATION_MS
                    val isRecurringDrowsy = _uiState.value.warningCount >= 3

                    val needsRest = isTimeOver || isRecurringDrowsy
                    val restReason = when {
                        isRecurringDrowsy -> "🚨 CẢNH BÁO NGUY HIỂM: Bạn đã ngủ gật ${_uiState.value.warningCount} lần! Yêu cầu tấp xe vào lề nghỉ ngơi ngay!"
                        isTimeOver -> "⚠️ CẢNH BÁO MỆT MỎI: Bạn đã lái xe liên tục hơn 1 phút! Vui lòng nghỉ ngơi."
                        else -> ""
                    }

                    _uiState.value = _uiState.value.copy(
                        drivingDurationSec = currentDuration,
                        isCriticalRestRequired = needsRest,
                        criticalRestReason = restReason
                    )
                }
            }
        }
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
                val baseline = if (earSamplesForCalib.isNotEmpty())
                    earSamplesForCalib.average()
                else
                    0.25

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
        val nowWallTime = System.currentTimeMillis()
        val isCooldownOver = (nowWallTime - lastAlertTimeWallMs) > 5_000L
        val newAlert = result.shouldAlert && (!alertWasActive && isCooldownOver)

        if (result.shouldAlert && isCooldownOver && !alertWasActive) {
            lastAlertTimeWallMs = nowWallTime
            val gps = _uiState.value.gpsLocation
            val newAlertEvent = AlertEvent(
                ear               = ear?.toDouble() ?: 0.0,
                closedDurationSec = result.closedDurationMs / 1000.0,
                warningIndex      = _uiState.value.warningCount + 1,
                latitude          = gps?.latitude,
                longitude         = gps?.longitude,
                speedKmh          = gps?.speedKmh,
                locationAddress   = gps?.address
            )
            alertsInThisSession.add(newAlertEvent)
            AlarmRepository.add(newAlertEvent)
        }
        alertWasActive = result.shouldAlert

        val newWarningCount = _uiState.value.warningCount + if (newAlert) 1 else 0
        val isCritical = newWarningCount >= 3 || _uiState.value.isCriticalRestRequired
        val criticalMsg = if (newWarningCount >= 3)
            "🚨 CẢNH BÁO NGUY HIỂM: Bạn đã ngủ gật $newWarningCount lần! Hãy tấp xe vào lề nghỉ ngơi ngay!"
        else _uiState.value.criticalRestReason

        _uiState.value = _uiState.value.copy(
            status       = if (result.shouldAlert) MonitoringStatus.DROWSY else MonitoringStatus.MONITORING,
            ear          = ear?.toDouble(),
            confidence   = result.features?.belowThresholdRatio?.toDouble(),
            warningCount = newWarningCount,
            isCriticalRestRequired = isCritical,
            criticalRestReason = criticalMsg,
            message      = when {
                ear == null       -> "Không thấy rõ khuôn mặt"
                result.shouldAlert -> "⚠️ Cảnh báo: mắt nhắm quá 2 giây!"
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
        val newAlertEvent = AlertEvent(
            ear               = 0.18,
            closedDurationSec = 2.0,
            warningIndex      = current.warningCount + 1,
            latitude          = gps?.latitude ?: 10.7769,
            longitude         = gps?.longitude ?: 106.7009,
            speedKmh          = gps?.speedKmh ?: 52.0f,
            locationAddress   = gps?.address ?: "Quận 1, TP. Hồ Chí Minh"
        )
        alertsInThisSession.add(newAlertEvent)
        AlarmRepository.add(newAlertEvent)

        val newCount = current.warningCount + 1
        val isCritical = newCount >= 3 || current.isCriticalRestRequired
        val reason = if (newCount >= 3) "🚨 CẢNH BÁO NGUY HIỂM: Ngủ gật liên tiếp $newCount lần! Yêu cầu tấp xe nghỉ ngơi!" else current.criticalRestReason

        _uiState.value = current.copy(
            status                 = MonitoringStatus.DROWSY,
            ear                    = 0.18,
            confidence             = 0.91,
            warningCount           = newCount,
            isCriticalRestRequired = isCritical,
            criticalRestReason     = reason,
            message                = "⚠️ Cảnh báo: phát hiện dấu hiệu buồn ngủ"
        )
    }

    fun stopMonitoring() {
        timerJob?.cancel()
        detector.reset()

        val endTimeMs = System.currentTimeMillis()
        val startMs = if (sessionStartWallTimeMs > 0L) sessionStartWallTimeMs else (endTimeMs - 10000L)

        // 🧠 AI Phân tích toàn diện chuyến đi vừa kết thúc
        val summary = TripAiAnalyzer.analyzeTrip(
            startTimeMs = startMs,
            endTimeMs = endTimeMs,
            alerts = alertsInThisSession.toList(),
            avgEar = _uiState.value.earBaseline ?: 0.28
        )

        // Lưu vào Firestore & Local Repository
        AlarmRepository.saveTripSummary(summary)

        _uiState.value = _uiState.value.copy(
            status      = MonitoringStatus.STOPPED,
            tripSummary = summary,
            message     = "Đã kết thúc phiên lái · Điểm an toàn: ${summary.safetyScore}/100"
        )
    }

    fun dismissTripSummary() {
        _uiState.value = _uiState.value.copy(tripSummary = null)
        AlarmRepository.clearLatestTripSummary()
    }
}
