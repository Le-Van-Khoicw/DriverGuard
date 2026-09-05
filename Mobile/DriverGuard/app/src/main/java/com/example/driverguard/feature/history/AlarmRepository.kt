package com.example.driverguard.feature.history

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    val locationAddress: String? = null      // Tên địa chỉ/đoạn đường
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
 * Quản lý danh sách cảnh báo buồn ngủ tập trung.
 * Tự động đồng bộ 2 chiều với Cloud Firestore để không bao giờ bị mất dữ liệu lịch sử.
 */
object AlarmRepository {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private val _events = MutableStateFlow<List<AlertEvent>>(emptyList())
    val events: StateFlow<List<AlertEvent>> = _events.asStateFlow()

    init {
        startRealtimeSync()
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
                        locationAddress = doc.getString("locationAddress")
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
                "locationAddress" to eventWithUser.locationAddress
            )
            firestore.collection("alerts").document(eventWithUser.id).set(data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
