package com.example.driverguard.feature.auth.monitoring

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverguard.core.location.GpsLocation
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.history.AlertEvent
import com.example.driverguard.feature.monitoring.ai.*
import com.example.driverguard.feature.monitoring.data.ActiveTrip
import com.example.driverguard.feature.monitoring.data.TripLocalStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MonitoringStatus { IDLE, CALIBRATING, MONITORING, DROWSY, PAUSED, STOPPED }

data class MonitoringUiState(
    val status: MonitoringStatus = MonitoringStatus.IDLE,
    val ear: Double? = null,
    val confidence: Double? = null,
    val warningCount: Int = 0,
    val message: String = "Chưa bắt đầu chuyến",
    val earBaseline: Double? = null,
    val earThreshold: Double? = null,
    val calibrationProgress: Float = 0f,
    val calibrationSecondsLeft: Int = 5,
    val gpsLocation: GpsLocation? = null,
    val drivingDurationSec: Long = 0,
    val isCriticalRestRequired: Boolean = false,
    val criticalRestReason: String = "",
    val tripSummary: TripSummary? = null,
    val hasActiveTrip: Boolean = false,
    val isRestoring: Boolean = true,
    val isCompleting: Boolean = false,
    val persistenceError: String? = null,
    val dispatchTripId: String? = null,
    val assignmentVersion: Long = 0
)

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private var ownerUid = ""
    private var restorationVersion = 0L
    private var tripId: String? = null
    private var dispatchTripId: String? = null
    private var assignmentVersion = 0L
    private var startWallMs = 0L
    private var revision = 0L
    private val alerts = mutableListOf<AlertEvent>()
    private var observations = TripObservationAccumulator()
    private val classifier = ThresholdClassifier(0.25f)
    private val detector = DrowsinessDetector(classifier)
    private val calibrationSamples = mutableListOf<Float>()
    private var calibrationStartMs = 0L
    private var lastCameraMs = 0L
    private var lastAlertMs = -5_001L
    private var alertWasActive = false
    private var timer: Job? = null

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()
    private val authListener = FirebaseAuth.AuthStateListener { firebase ->
        viewModelScope.launch { switchAccount(firebase.currentUser?.uid.orEmpty()) }
    }

    init {
        TripLocalStore.initialize(application)
        auth.addAuthStateListener(authListener)
    }

    private suspend fun switchAccount(uid: String) {
        if (uid == ownerUid && !_uiState.value.isRestoring) return
        val requestVersion = ++restorationVersion
        pauseMonitoring()
        ownerUid = uid
        tripId = null
        dispatchTripId = null
        assignmentVersion = 0
        alerts.clear()
        observations = TripObservationAccumulator()
        _uiState.value = MonitoringUiState(isRestoring = uid.isNotBlank())
        if (uid.isBlank()) return
        val restored = runCatching { TripLocalStore.readActive(uid) }
        if (requestVersion != restorationVersion || ownerUid != uid || auth.currentUser?.uid != uid) return
        restored.onSuccess { draft ->
            if (draft != null) {
                tripId = draft.id
                dispatchTripId = draft.dispatchTripId
                assignmentVersion = draft.assignmentVersion
                startWallMs = draft.startTime
                revision = draft.revision
                observations = TripObservationAccumulator(draft.stats)
                alerts.addAll(draft.alerts)
                _uiState.value = MonitoringUiState(
                    status = MonitoringStatus.PAUSED, warningCount = alerts.size, hasActiveTrip = true,
                    dispatchTripId = dispatchTripId, assignmentVersion = assignmentVersion,
                    drivingDurationSec = elapsedSeconds(), isRestoring = false,
                    message = "Đã khôi phục chuyến dở. Chọn tiếp tục hoặc hoàn thành chuyến."
                )
            } else _uiState.value = MonitoringUiState(isRestoring = false)
        }.onFailure {
            _uiState.value = MonitoringUiState(isRestoring = false, persistenceError = it.message,
                message = "Không đọc được chuyến đã lưu. Hãy mở lại ứng dụng trước khi tạo chuyến mới.")
        }
    }

    private fun isRunning() = _uiState.value.status in setOf(
        MonitoringStatus.CALIBRATING, MonitoringStatus.MONITORING, MonitoringStatus.DROWSY
    )

    // One monitoring UUID represents one driver's observations, even after a handover.
    fun bindDispatchTrip(id: String, version: Long): Boolean {
        if (_uiState.value.isRestoring || _uiState.value.isCompleting) return false
        if (tripId != null && (dispatchTripId != id || assignmentVersion != version)) {
            _uiState.value = _uiState.value.copy(message = "Kết thúc phiên giám sát đang dở trước khi mở phiên cho phân công khác")
            return false
        }
        dispatchTripId = id
        assignmentVersion = version
        _uiState.value = _uiState.value.copy(dispatchTripId = id, assignmentVersion = version)
        return true
    }

    fun closeForDispatch(id: String, version: Long) {
        if (dispatchTripId == id && assignmentVersion == version) completeTrip()
    }

    fun startMonitoring() {
        if (_uiState.value.isRestoring || _uiState.value.isCompleting || isRunning()) return
        if (ownerUid.isBlank() || auth.currentUser?.uid != ownerUid) return
        if (_uiState.value.persistenceError != null) return
        if (tripId == null) {
            tripId = UUID.randomUUID().toString()
            startWallMs = System.currentTimeMillis()
            revision = 0
            alerts.clear()
            observations = TripObservationAccumulator()
        }
        calibrationSamples.clear()
        calibrationStartMs = SystemClock.elapsedRealtime()
        lastCameraMs = calibrationStartMs
        lastAlertMs = -5_001
        alertWasActive = false
        detector.reset()
        _uiState.value = _uiState.value.copy(
            status = MonitoringStatus.CALIBRATING, hasActiveTrip = true,
            tripSummary = null, ear = null, confidence = null, earBaseline = null, earThreshold = null,
            calibrationProgress = 0f, calibrationSecondsLeft = 5,
            message = "Hiệu chỉnh 5 giây: nhìn thẳng, mở mắt tự nhiên"
        )
        persistCheckpoint()
        timer?.cancel()
        timer = viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                delay(1_000)
                if (!isRunning()) continue
                val now = SystemClock.elapsedRealtime()
                if (_uiState.value.status != MonitoringStatus.CALIBRATING && now - lastCameraMs > 500) {
                    detector.reset()
                    alertWasActive = false
                    observations.record(now, null, EyeState.UNKNOWN)
                    _uiState.value = _uiState.value.copy(status = MonitoringStatus.MONITORING,
                        ear = null, confidence = null, message = "Chưa nhận được hình ảnh mắt hợp lệ")
                }
                _uiState.value = _uiState.value.copy(drivingDurationSec = elapsedSeconds())
                if (++ticks % 2 == 0) persistCheckpoint()
            }
        }
    }

    fun pauseMonitoring() {
        if (!isRunning()) return
        observations.end(SystemClock.elapsedRealtime())
        detector.reset()
        alertWasActive = false
        timer?.cancel()
        _uiState.value = _uiState.value.copy(status = MonitoringStatus.PAUSED, ear = null, confidence = null,
            drivingDurationSec = elapsedSeconds(), message = "Đã tạm dừng giám sát, chuyến vẫn chưa hoàn thành")
        persistCheckpoint()
    }

    fun onLocationUpdated(location: GpsLocation?) {
        _uiState.value = _uiState.value.copy(gpsLocation = location)
    }

    fun onEarDetected(ear: Float?) {
        val timestamp = SystemClock.elapsedRealtime()
        // CameraX callbacks use a worker thread; all trip mutations are serialized on Main.
        viewModelScope.launch { processEar(ear?.takeIf { it.isFinite() }, timestamp) }
    }

    private fun processEar(ear: Float?, now: Long) {
        if (now < calibrationStartMs) return
        if (!isRunning() || _uiState.value.isCompleting || auth.currentUser?.uid != ownerUid) return
        lastCameraMs = now
        if (_uiState.value.status == MonitoringStatus.CALIBRATING) {
            if (ear != null && ear > 0.15f) calibrationSamples.add(ear)
            val elapsed = now - calibrationStartMs
            if (elapsed >= 5_000) {
                if (calibrationSamples.size < 10) {
                    calibrationSamples.clear()
                    calibrationStartMs = now
                    _uiState.value = _uiState.value.copy(calibrationProgress = 0f, calibrationSecondsLeft = 5,
                        message = "Chưa đủ mẫu mắt mở. Hãy chỉnh camera và mở mắt tự nhiên.")
                    return
                }
                val baseline = calibrationSamples.average()
                classifier.threshold = (baseline * 0.75).toFloat()
                detector.reset()
                observations.begin(now)
                _uiState.value = _uiState.value.copy(status = MonitoringStatus.MONITORING,
                    earBaseline = baseline, earThreshold = classifier.threshold.toDouble(),
                    calibrationProgress = 1f, calibrationSecondsLeft = 0, message = "Đang giám sát chuyến")
            } else {
                _uiState.value = _uiState.value.copy(ear = ear?.toDouble(),
                    calibrationProgress = (elapsed / 5_000f).coerceIn(0f, 1f),
                    calibrationSecondsLeft = kotlin.math.ceil((5_000 - elapsed) / 1000.0).toInt().coerceAtLeast(1))
            }
            return
        }
        val result = detector.process(ear, now)
        observations.record(now, ear, if (ear == null) EyeState.UNKNOWN else result.eyeState)
        val newAlert = result.shouldAlert && !alertWasActive && now - lastAlertMs > 5_000
        if (newAlert) {
            lastAlertMs = now
            val gps = _uiState.value.gpsLocation
            val event = AlertEvent(userId = ownerUid, ear = ear?.toDouble() ?: 0.0,
                closedDurationSec = result.closedDurationMs / 1000.0, warningIndex = alerts.size + 1,
                latitude = gps?.latitude, longitude = gps?.longitude, speedKmh = gps?.speedKmh,
                locationAddress = gps?.address, dispatchTripId = dispatchTripId,
                monitoringSessionId = tripId, assignmentVersion = assignmentVersion)
            alerts.add(event)
            AlarmRepository.add(event)
            persistCheckpoint()
        }
        alertWasActive = result.shouldAlert
        val recurring = alerts.size >= 3
        _uiState.value = _uiState.value.copy(
            status = if (result.shouldAlert) MonitoringStatus.DROWSY else MonitoringStatus.MONITORING,
            ear = ear?.toDouble(), confidence = result.features?.belowThresholdRatio?.toDouble(),
            warningCount = alerts.size, isCriticalRestRequired = recurring,
            criticalRestReason = if (recurring) "Ghi nhận nhiều cảnh báo nhắm mắt kéo dài. Hãy dừng xe tại nơi an toàn nếu buồn ngủ." else "",
            message = when {
                ear == null -> "Không thấy rõ mắt; thời gian này không được tính là quan sát hợp lệ"
                result.shouldAlert -> "Cảnh báo: mắt nhắm kéo dài từ 2 giây"
                else -> "Đang giám sát chuyến"
            }
        )
    }

    fun onCameraError(message: String) {
        viewModelScope.launch {
            if (isRunning() && _uiState.value.status != MonitoringStatus.CALIBRATING) {
                observations.record(SystemClock.elapsedRealtime(), null, EyeState.UNKNOWN)
                detector.reset()
                alertWasActive = false
                _uiState.value = _uiState.value.copy(status = MonitoringStatus.MONITORING)
            }
            _uiState.value = _uiState.value.copy(message = message)
        }
    }

    fun completeTrip() {
        val id = tripId ?: return
        if (_uiState.value.isCompleting || _uiState.value.isRestoring) return
        pauseMonitoring()
        val uid = ownerUid
        val summary = TripAiAnalyzer.analyzeTrip(id, uid, startWallMs, System.currentTimeMillis(),
            alerts.toList(), observations.stats).copy(dispatchTripId = dispatchTripId, assignmentVersion = assignmentVersion)
        _uiState.value = _uiState.value.copy(isCompleting = true)
        viewModelScope.launch {
            val saved = AlarmRepository.saveTripSummary(summary)
            if (ownerUid != uid || tripId != id) return@launch
            saved.onSuccess {
                tripId = null
                dispatchTripId = null
                assignmentVersion = 0
                _uiState.value = _uiState.value.copy(status = MonitoringStatus.STOPPED, hasActiveTrip = false,
                    isCompleting = false, tripSummary = summary, persistenceError = null, dispatchTripId = null, assignmentVersion = 0,
                    message = "Đã hoàn thành chuyến · ${summary.scoreLabel}")
            }.onFailure {
                _uiState.value = _uiState.value.copy(isCompleting = false, persistenceError = it.message,
                    message = "Chưa lưu được chuyến. Dữ liệu vẫn giữ để thử hoàn thành lại.")
            }
        }
    }

    private fun elapsedSeconds() = ((System.currentTimeMillis() - startWallMs) / 1000).coerceAtLeast(0)

    private fun persistCheckpoint() {
        val id = tripId ?: return
        val draft = ActiveTrip(id, ownerUid, startWallMs, ++revision, observations.stats, alerts.toList(), dispatchTripId, assignmentVersion)
        // Application scope outlives this screen, so leaving the page cannot cancel the last save.
        AlarmRepository.persistDraft(draft) { error ->
            viewModelScope.launch {
                if (tripId == id) _uiState.value = _uiState.value.copy(persistenceError = error)
            }
        }
    }

    fun dismissTripSummary() {
        _uiState.value = _uiState.value.copy(tripSummary = null)
        AlarmRepository.clearLatestTripSummary()
    }

    fun retryPersistence() {
        if (tripId != null) persistCheckpoint()
        else viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRestoring = true)
            switchAccount(auth.currentUser?.uid.orEmpty())
        }
    }

    override fun onCleared() {
        pauseMonitoring()
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
