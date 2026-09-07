package com.example.driverguard.feature.history

import android.util.Log
import com.example.driverguard.feature.monitoring.ai.TripRiskLevel
import com.example.driverguard.feature.monitoring.ai.TripSummary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
    val id: String = UUID.randomUUID().toString().take(8),
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val ear: Double = 0.0,
    val closedDurationSec: Double = 0.0,
    val warningIndex: Int = 1,               // Số thứ tự cảnh báo trong phiên
    val latitude: Double? = null,            // Vĩ độ GPS
    val longitude: Double? = null,           // Kinh độ GPS
    val speedKmh: Float? = null,             // Tốc độ xe khi buồn ngủ
    val locationAddress: String? = null,     // Tên địa chỉ/đoạn đường
    val imageUrl: String? = null             // Ảnh minh chứng khuôn mặt
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

    private val _events = MutableStateFlow<List<AlertEvent>>(emptyList())
    val events: StateFlow<List<AlertEvent>> = _events.asStateFlow()

    private val _latestTripSummary = MutableStateFlow<TripSummary?>(null)
    val latestTripSummary: StateFlow<TripSummary?> = _latestTripSummary.asStateFlow()

    private val _savedTrips = MutableStateFlow<List<TripSummary>>(emptyList())
    val savedTrips: StateFlow<List<TripSummary>> = _savedTrips.asStateFlow()

    init {
        startRealtimeSync()
        syncSavedTrips()
    }

    /** Lắng nghe dữ liệu realtime từ Cloud Firestore */
    fun startRealtimeSync() {
        val uid = auth.currentUser?.uid
        val query = if (!uid.isNullOrBlank()) {
            firestore.collection("alerts")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
        } else {
            firestore.collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
        }

        query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val remoteList = snapshot.documents.mapNotNull { doc ->
                try {
                    AlertEvent(
                        id = doc.getString("id") ?: doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        ear = doc.getDouble("ear") ?: 0.0,
                        closedDurationSec = doc.getDouble("closedDurationSec") ?: 0.0,
                        warningIndex = doc.getLong("warningIndex")?.toInt() ?: 1,
                        latitude = doc.getDouble("latitude"),
                        longitude = doc.getDouble("longitude"),
                        speedKmh = doc.getDouble("speedKmh")?.toFloat(),
                        locationAddress = doc.getString("locationAddress"),
                        imageUrl = doc.getString("imageUrl")
                    )
                } catch (_: Exception) {
                    null
                }
            }

            // Hợp nhất dữ liệu remote và local
            if (remoteList.isNotEmpty()) {
                _events.value = remoteList
            }
        }
    }

    /** Lắng nghe danh sách chuyến đi đã lưu từ Firestore */
    fun syncSavedTrips() {
        val uid = auth.currentUser?.uid
        val query = if (!uid.isNullOrBlank()) {
            firestore.collection("trips")
                .whereEqualTo("userId", uid)
                .orderBy("startTime", Query.Direction.DESCENDING)
        } else {
            firestore.collection("trips")
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(20)
        }

        query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val list = snapshot.documents.mapNotNull { doc ->
                try {
                    val riskStr = doc.getString("riskLevel") ?: "SAFE"
                    val riskLevel = try { TripRiskLevel.valueOf(riskStr) } catch (_: Exception) { TripRiskLevel.SAFE }
                    val recs = (doc.get("aiRecommendations") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                    TripSummary(
                        id = doc.getString("id") ?: doc.id,
                        startTime = doc.getLong("startTime") ?: System.currentTimeMillis(),
                        endTime = doc.getLong("endTime") ?: System.currentTimeMillis(),
                        durationSec = doc.getLong("durationSec") ?: 0L,
                        totalAlerts = doc.getLong("totalAlerts")?.toInt() ?: 0,
                        safetyScore = doc.getLong("safetyScore")?.toInt() ?: 100,
                        scoreGrade = doc.getString("scoreGrade") ?: "A",
                        riskLevel = riskLevel,
                        perclosEstimatedPercent = doc.getDouble("perclosEstimatedPercent") ?: 0.0,
                        averageEar = doc.getDouble("averageEar") ?: 0.28,
                        aiDiagnosis = doc.getString("aiDiagnosis").orEmpty(),
                        aiRecommendations = recs,
                        topLocation = doc.getString("topLocation"),
                        isLongDriveFatigue = doc.getBoolean("isLongDriveFatigue") ?: false
                    )
                } catch (_: Exception) {
                    null
                }
            }
            if (list.isNotEmpty()) {
                _savedTrips.value = list
            }
        }
    }

    /** Thêm sự kiện cảnh báo mới và lưu bền vững lên Firestore */
    fun add(event: AlertEvent) {
        val uid = auth.currentUser?.uid.orEmpty()
        val eventWithUser = if (event.userId.isBlank()) event.copy(userId = uid) else event

        // 1. Cập nhật ngay trong Local State để UI hiển thị tức thời (0ms delay)
        _events.value = listOf(eventWithUser) + _events.value

        // 2. Bắn lên Cloud Firestore để lưu vĩnh viễn và Web Admin bắt được
        try {
            val data = hashMapOf(
                "id" to eventWithUser.id,
                "userId" to eventWithUser.userId,
                "timestamp" to eventWithUser.timestamp,
                "ear" to eventWithUser.ear,
                "closedDurationSec" to eventWithUser.closedDurationSec,
                "warningIndex" to eventWithUser.warningIndex,
                "latitude" to eventWithUser.latitude,
                "longitude" to eventWithUser.longitude,
                "speedKmh" to eventWithUser.speedKmh,
                "locationAddress" to eventWithUser.locationAddress,
                "imageUrl" to eventWithUser.imageUrl
            )
            firestore.collection("alerts").document(eventWithUser.id).set(data)
                .addOnSuccessListener {
                    Log.d("FIREBASE_SYNC", "Da ghi thanh cong Alert vao collection 'alerts': ${eventWithUser.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_SYNC", "Loi ghi Alert len Firestore: ${e.message}", e)
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Lưu kết quả phân tích chuyến đi (Trip AI Summary) lên Cloud Firestore */
    fun saveTripSummary(summary: TripSummary) {
        _latestTripSummary.value = summary
        _savedTrips.value = listOf(summary) + _savedTrips.value

        val uid = auth.currentUser?.uid.orEmpty()
        try {
            val data = hashMapOf(
                "id" to summary.id,
                "userId" to uid,
                "startTime" to summary.startTime,
                "endTime" to summary.endTime,
                "durationSec" to summary.durationSec,
                "totalAlerts" to summary.totalAlerts,
                "safetyScore" to summary.safetyScore,
                "scoreGrade" to summary.scoreGrade,
                "riskLevel" to summary.riskLevel.name,
                "perclosEstimatedPercent" to summary.perclosEstimatedPercent,
                "averageEar" to summary.averageEar,
                "aiDiagnosis" to summary.aiDiagnosis,
                "aiRecommendations" to summary.aiRecommendations,
                "topLocation" to (summary.topLocation ?: ""),
                "isLongDriveFatigue" to summary.isLongDriveFatigue,
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("trips").document(summary.id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FIREBASE_SYNC", "Da luu bao cao chuyen di vao collection 'trips': ${summary.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_SYNC", "Loi luu trip len Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Xóa tóm tắt chuyến đi hiện tại sau khi người dùng đóng dialog */
    fun clearLatestTripSummary() {
        _latestTripSummary.value = null
    }

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
