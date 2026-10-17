package com.example.driverguard.feature.history

import android.util.Log
import com.example.driverguard.feature.monitoring.data.TripLocalStore
import com.example.driverguard.feature.monitoring.data.ActiveTrip
import com.example.driverguard.feature.monitoring.data.TripSummaryCodec
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import android.content.Context
import com.example.driverguard.feature.monitoring.ai.TripSummary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Một sự kiện cảnh báo buồn ngủ được ghi lại khi đang giám sát kèm tọa độ GPS. */
data class AlertEvent(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ear: Double = 0.0,
    val closedDurationSec: Double = 0.0,
    val warningIndex: Int = 1,               // Số thứ tự cảnh báo trong phiên
    val latitude: Double? = null,            // Vĩ độ GPS
    val longitude: Double? = null,           // Kinh độ GPS
    val speedKmh: Float? = null,             // Tốc độ xe khi buồn ngủ
    val locationAddress: String? = null,     // Tên địa chỉ/đoạn đường
    val imageUrl: String? = null,            // Ảnh minh chứng khuôn mặt
    val dispatchTripId: String? = null,
    val monitoringSessionId: String? = null,
    val assignmentVersion: Long = 0
) {
    val timeLabel: String
        get() {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val fmt = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
            return when {
                diff < 24 * 60 * 60 * 1000 && isSameDay(timestamp, now) -> "Hôm nay ${fmt.format(Date(timestamp))}"
                diff < 48 * 60 * 60 * 1000 && isYesterday(timestamp) -> "Hôm qua ${fmt.format(Date(timestamp))}"
                else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).format(Date(timestamp))
            }
        }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(t: Long): Boolean {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }
}

/** Thống kê số lượng cảnh báo của từng ngày trong 7 ngày qua */
data class DayStat(
    val dayName: String,     // Ví dụ: "T2", "T3", "T4", "H.nay"
    val dateLabel: String,   // Ví dụ: "02/09"
    val count: Int,          // Số lần cảnh báo trong ngày đó
    val isToday: Boolean     // Có phải hôm nay không
)

/**
 * Quản lý danh sách cảnh báo buồn ngủ và các chuyến đi đã phân tích AI.
 * Tự động đồng bộ 2 chiều với Cloud Firestore.
 */
object AlarmRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initialized = false
    private var alertListener: ListenerRegistration? = null
    private var tripListener: ListenerRegistration? = null
    private val inFlight = mutableSetOf<String>()
    private var remoteTrips = emptyList<TripSummary>()
    private val _events = MutableStateFlow<List<AlertEvent>>(emptyList())
    val events: StateFlow<List<AlertEvent>> = _events.asStateFlow()
    private val _latestTripSummary = MutableStateFlow<TripSummary?>(null)
    val latestTripSummary: StateFlow<TripSummary?> = _latestTripSummary.asStateFlow()
    private val _savedTrips = MutableStateFlow<List<TripSummary>>(emptyList())
    val savedTrips: StateFlow<List<TripSummary>> = _savedTrips.asStateFlow()
    private val _pendingTripIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingTripIds: StateFlow<Set<String>> = _pendingTripIds.asStateFlow()
    private val _tripSyncErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val tripSyncErrors: StateFlow<Map<String, String>> = _tripSyncErrors.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        TripLocalStore.initialize(context)
        auth.addAuthStateListener {
            _events.value = emptyList()
            _savedTrips.value = emptyList()
            _pendingTripIds.value = emptySet()
            _tripSyncErrors.value = emptyMap()
            _latestTripSummary.value = null
            remoteTrips = emptyList()
            startRealtimeSync()
            syncSavedTrips()
            val uid = auth.currentUser?.uid ?: return@addAuthStateListener
            scope.launch {
                refreshLocal(uid)
                runCatching { TripLocalStore.listCompleted(uid) }
                    .onSuccess { rows -> rows.filter { it.pending }.forEach { queueSync(it.summary) } }
                    .onFailure { Log.e("TRIP_STORE", "Không đọc được hàng đợi chuyến", it) }
            }
        }
    }

    fun persistDraft(draft: ActiveTrip, onResult: (String?) -> Unit) {
        scope.launch {
            runCatching { TripLocalStore.saveActive(draft) }
                .onSuccess { onResult(null) }
                .onFailure { Log.e("TRIP_STORE", "Không lưu được checkpoint", it); onResult(it.message ?: "Lỗi lưu trên máy") }
        }
    }

    private suspend fun refreshLocal(uid: String) {
        val local = runCatching { TripLocalStore.listCompleted(uid) }
            .onFailure { Log.e("TRIP_STORE", "Không đọc được lịch sử trên máy", it) }.getOrElse { return }
        if (auth.currentUser?.uid != uid) return
        _pendingTripIds.value = local.filter { it.pending }.map { it.summary.id }.toSet()
        _savedTrips.value = (local.map { it.summary } + remoteTrips).distinctBy { it.id }.sortedByDescending { it.startTime }
    }

    fun startRealtimeSync() {
        alertListener?.remove()
        val uid = auth.currentUser?.uid ?: return
        alertListener = firestore.collection("alerts").whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || auth.currentUser?.uid != uid) return@addSnapshotListener
                val remote = snapshot.documents.mapNotNull { doc ->
                    runCatching { AlertEvent(
                        id = doc.getString("id") ?: doc.id, userId = uid,
                        timestamp = doc.getLong("timestamp") ?: 0,
                        ear = doc.getDouble("ear") ?: 0.0, closedDurationSec = doc.getDouble("closedDurationSec") ?: 0.0,
                        warningIndex = doc.getLong("warningIndex")?.toInt() ?: 1,
                        latitude = doc.getDouble("latitude"), longitude = doc.getDouble("longitude"),
                        speedKmh = doc.getDouble("speedKmh")?.toFloat(),
                        locationAddress = doc.getString("locationAddress"), imageUrl = doc.getString("imageUrl"),
                        dispatchTripId = doc.getString("dispatchTripId"), monitoringSessionId = doc.getString("monitoringSessionId"),
                        assignmentVersion = doc.getLong("assignmentVersion") ?: 0
                    ) }.getOrNull()
                }
                _events.value = (remote + _events.value.filter { it.userId == uid }).distinctBy { it.id }.sortedByDescending { it.timestamp }
            }
    }

    fun syncSavedTrips() {
        tripListener?.remove()
        val uid = auth.currentUser?.uid ?: return
        tripListener = firestore.collection("trips").whereEqualTo("userId", uid)
            .orderBy("startTime", Query.Direction.DESCENDING).addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || auth.currentUser?.uid != uid) return@addSnapshotListener
                remoteTrips = snapshot.documents.mapNotNull { doc ->
                    runCatching {
                        TripSummaryCodec.fromMap(
                            (doc.data ?: emptyMap()) + mapOf("id" to (doc.getString("id") ?: doc.id))
                        )
                    }.getOrNull()
                }
                scope.launch { refreshLocal(uid) }
            }
    }

    fun add(event: AlertEvent) {
        if (event.userId.isBlank()) return
        if (auth.currentUser?.uid == event.userId) _events.value = listOf(event) + _events.value.filterNot { it.id == event.id }
        firestore.collection("alerts").document(event.id).set(mapOf(
            "id" to event.id, "userId" to event.userId, "timestamp" to event.timestamp, "ear" to event.ear,
            "closedDurationSec" to event.closedDurationSec, "warningIndex" to event.warningIndex,
            "latitude" to event.latitude, "longitude" to event.longitude, "speedKmh" to event.speedKmh,
            "locationAddress" to event.locationAddress, "imageUrl" to event.imageUrl,
            "dispatchTripId" to event.dispatchTripId, "monitoringSessionId" to event.monitoringSessionId,
            "assignmentVersion" to event.assignmentVersion
        )).addOnFailureListener { Log.e("FIREBASE_SYNC", "Không đồng bộ được cảnh báo", it) }
    }

    suspend fun saveTripSummary(summary: TripSummary): Result<Unit> {
        // Persist the summary and clear the draft BEFORE showing successful completion.
        val saved = runCatching { TripLocalStore.complete(summary); Unit }
        if (saved.isSuccess) {
            if (auth.currentUser?.uid == summary.userId) {
                _latestTripSummary.value = summary
                _pendingTripIds.value += summary.id
                _savedTrips.value = (listOf(summary) + _savedTrips.value).distinctBy { it.id }
            }
            scope.launch {
                refreshLocal(summary.userId)
                runCatching { queueSync(summary) }.onFailure { Log.e("FIREBASE_SYNC", "Chuyến đã lưu trên máy, chờ đồng bộ", it) }
            }
        }
        return saved
    }

    private fun queueSync(summary: TripSummary) {
        if (auth.currentUser?.uid != summary.userId) return
        val key = "${summary.userId}:${summary.id}"
        if (!inFlight.add(key)) return
        _tripSyncErrors.value -= summary.id
        // Stable trip ID makes SDK retries and next-launch retries idempotent.
        runCatching { firestore.collection("trips").document(summary.id)
            .set(TripSummaryCodec.toMap(summary), SetOptions.merge())
            .addOnSuccessListener {
                scope.launch {
                    runCatching { TripLocalStore.markSynced(summary.userId, summary.id) }
                        .onFailure { syncFailed(summary, key, it) }
                    inFlight.remove(key)
                    refreshLocal(summary.userId)
                }
            }
            .addOnFailureListener {
                syncFailed(summary, key, it)
            }
        }.onFailure { syncFailed(summary, key, it) }
    }

    private fun syncFailed(summary: TripSummary, key: String, error: Throwable) {
        inFlight.remove(key)
        Log.e("FIREBASE_SYNC", "Chuyến vẫn lưu trên máy, chưa đồng bộ", error)
        if (auth.currentUser?.uid == summary.userId) {
            _tripSyncErrors.value += (summary.id to "Máy chủ chưa xác nhận dữ liệu. Báo cáo vẫn được giữ trên điện thoại.")
        }
    }

    fun retryPendingTrips() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            runCatching { TripLocalStore.listCompleted(uid) }
                .onSuccess { rows -> rows.filter { it.pending }.forEach { queueSync(it.summary) } }
                .onFailure { Log.e("TRIP_STORE", "Không đọc được hàng đợi chuyến", it) }
        }
    }

    fun clearLatestTripSummary() { _latestTripSummary.value = null }

    /** Tính toán thống kê 7 ngày qua để vẽ biểu đồ */
    fun getSevenDaysStats(eventList: List<AlertEvent>): List<DayStat> {
        val result = mutableListOf<DayStat>()
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()
        val dayFormatter = SimpleDateFormat("dd/MM", Locale("vi", "VN"))

        // Duyệt từ 6 ngày trước đến hôm nay (tổng cộng 7 ngày)
        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }

            val targetYear = targetCal.get(Calendar.YEAR)
            val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
            val isToday = (i == 0)

            val countForDay = eventList.count { ev ->
                calendar.timeInMillis = ev.timestamp
                calendar.get(Calendar.YEAR) == targetYear &&
                        calendar.get(Calendar.DAY_OF_YEAR) == targetDayOfYear
            }

            val dayName = when (i) {
                0 -> "H.nay"
                1 -> "H.qua"
                else -> when (targetCal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "T2"
                    Calendar.TUESDAY -> "T3"
                    Calendar.WEDNESDAY -> "T4"
                    Calendar.THURSDAY -> "T5"
                    Calendar.FRIDAY -> "T6"
                    Calendar.SATURDAY -> "T7"
                    Calendar.SUNDAY -> "CN"
                    else -> "T${targetCal.get(Calendar.DAY_OF_WEEK)}"
                }
            }

            result.add(
                DayStat(
                    dayName = dayName,
                    dateLabel = dayFormatter.format(targetCal.time),
                    count = countForDay,
                    isToday = isToday
                )
            )
        }

        return result
    }

    fun clear() {
        _events.value = emptyList()
    }
}
