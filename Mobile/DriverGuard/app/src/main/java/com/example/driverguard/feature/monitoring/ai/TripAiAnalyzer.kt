package com.example.driverguard.feature.monitoring.ai

import com.example.driverguard.feature.history.AlertEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class TripRiskLevel(val label: String, val badgeColorHex: Long) {
    SAFE("Tỉnh táo - An toàn", 0xFF10B981),
    CAUTION("Cần chú ý", 0xFFF59E0B),
    HIGH_RISK("Nhiều dấu hiệu mệt mỏi", 0xFFEF4444),
    CRITICAL("Nguy cơ ngủ gật cao", 0xFF991B1B)
}

data class TripSummary(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val durationSec: Long = 0,
    val totalAlerts: Int = 0,
    val safetyScore: Int? = null,
    val scoreGrade: String = "—",
    val riskLevel: TripRiskLevel = TripRiskLevel.CAUTION,
    // Legacy compatibility only; new reports use the observed eyeClosedPercent.
    val perclosEstimatedPercent: Double = 0.0,
    val averageEar: Double = 0.0,
    val aiDiagnosis: String = "",
    val aiRecommendations: List<String> = emptyList(),
    val topLocation: String? = null,
    val isLongDriveFatigue: Boolean = false,
    val hasSufficientData: Boolean = false,
    val measurementCoverage: Double = 0.0,
    val validObservationSec: Double = 0.0,
    val totalClosedSec: Double = 0.0,
    val longestClosedSec: Double = 0.0,
    val prolongedClosureCount: Int = 0,
    val eyeClosedPercent: Double? = null,
    val scoringVersion: String = "legacy",
    val dispatchTripId: String? = null,
    val assignmentVersion: Long = 0
) {
    val durationLabel: String get() = "${durationSec / 60} phút ${durationSec % 60} giây"
    val timeRangeLabel: String get() {
        val time = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
        val date = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
        return "${time.format(Date(startTime))} - ${time.format(Date(endTime))} (${date.format(Date(startTime))})"
    }
    val scoreLabel: String get() = safetyScore?.let { "$it / 100" } ?: "Chưa đủ dữ liệu"
}

/** Transparent experimental scoring policy, not a trained safety predictor. */
object TripAiAnalyzer {
    const val SCORING_VERSION = "observation-rules-v2"
    const val MIN_VALID_MS = 10_000L
    const val MIN_COVERAGE = 0.40

    fun analyzeTrip(
        id: String,
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long,
        alerts: List<AlertEvent>,
        observations: TripObservationStats
    ): TripSummary {
        // Thời gian giám sát thực tế được tính từ thời gian camera thực sự hoạt động,
        // tránh việc thời gian chờ nhàn rỗi (idle) làm loãng tỷ lệ bao phủ.
        val monitoredMs = if (observations.monitoredMs > 0) observations.monitoredMs else maxOf(endTimeMs - startTimeMs, 1L)
        val durationMs = maxOf(monitoredMs, 1L)
        val validMs = observations.validMs.coerceIn(0, durationMs)
        val coverage = (validMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        val sufficient = (validMs >= MIN_VALID_MS && coverage >= MIN_COVERAGE) || validMs >= 20_000L
        val ratio = if (validMs > 0) observations.prolongedClosedMs.toDouble() / validMs else 0.0
        val eventsPerHour = if (validMs > 0) observations.prolongedClosureCount * 3_600_000.0 / validMs else 0.0

        val penalty = minOf(50.0, ratio * 200) + minOf(30.0, eventsPerHour) +
            minOf(20.0, observations.longestClosedMs / 1000.0 * 2)

        val score = if (sufficient) {
            (100 - penalty).roundToInt().coerceIn(0, 100)
        } else if (validMs >= 5_000L) {
            // Cho phép đánh giá phiên ngắn (5s - 10s) khi làm bài demo
            (95 - penalty).roundToInt().coerceIn(0, 100)
        } else null

        val risk = when {
            score == null -> TripRiskLevel.CAUTION
            score >= 90 -> TripRiskLevel.SAFE
            score >= 70 -> TripRiskLevel.CAUTION
            score >= 40 -> TripRiskLevel.HIGH_RISK
            else -> TripRiskLevel.CRITICAL
        }

        val diagnosis = when {
            score == null -> {
                "Thời gian quan sát quá ngắn (dưới 5 giây). Hãy giữ camera nhìn rõ khuôn mặt khi lái xe để AI ghi nhận dữ liệu."
            }
            observations.prolongedClosureCount == 0 && alerts.isEmpty() -> {
                "✅ Chuyến xe an toàn! Tài xế duy trì trạng thái tỉnh táo và tập trung cao độ trong suốt hành trình. Không ghi nhận dấu hiệu buồn ngủ hay mắt nhắm kéo dài nguy hiểm."
            }
            observations.prolongedClosureCount in 1..2 -> {
                "⚠️ Ghi nhận ${observations.prolongedClosureCount} lần mắt nhắm kéo dài (lần lâu nhất: ${"%.1f".format(observations.longestClosedMs / 1000.0)}s). Tài xế có dấu hiệu mệt mỏi nhẹ. Khuyến nghị uống nước hoặc dừng xe nghỉ 15 phút nếu đi đường dài."
            }
            else -> {
                "🚨 CẢNH BÁO NGUY CƠ CAO: Ghi nhận ${observations.prolongedClosureCount} lần nhắm mắt kéo dài (lần lâu nhất: ${"%.1f".format(observations.longestClosedMs / 1000.0)}s). Tài xế có dấu hiệu ngủ gật lặp lại, khuyến nghị điều phối viên liên hệ và yêu cầu dừng xe nghỉ ngay!"
            }
        }

        val recommendations = when {
            score == null -> listOf(
                "Giữ camera nhìn rõ mắt trong suốt thời gian lái xe.",
                "Căn chỉnh góc đặt điện thoại ngang tầm mắt."
            )
            observations.prolongedClosureCount == 0 && alerts.isEmpty() -> listOf(
                "Duy trì thói quen lái xe an toàn và tư thế quan sát chuẩn.",
                "Tiếp tục giữ góc camera hướng thẳng vào mắt."
            )
            observations.prolongedClosureCount in 1..2 -> listOf(
                "Nghỉ ngơi 15 - 20 phút trước khi tiếp tục chặng tiếp theo.",
                "Mở hé cửa kính lấy gió tươi hoặc uống thêm nước/cà phê."
            )
            else -> listOf(
                "DỪNG XE NGAY LẬP TỨC tại trạm dừng chân gần nhất.",
                "Chợp mắt 20-30 phút để phục hồi thể trạng trước khi cầm lái."
            )
        }

        return TripSummary(
            id = id, userId = userId, startTime = startTimeMs, endTime = endTimeMs,
            durationSec = durationMs / 1000, totalAlerts = alerts.size,
            safetyScore = score, scoreGrade = when {
                score == null -> "—"; score >= 90 -> "A"; score >= 70 -> "B"; score >= 40 -> "C"; else -> "D"
            }, riskLevel = risk,
            averageEar = if (validMs > 0) observations.weightedEarMs / validMs else 0.0,
            aiDiagnosis = diagnosis,
            aiRecommendations = recommendations,
            topLocation = alerts.firstOrNull { !it.locationAddress.isNullOrBlank() }?.locationAddress,
            hasSufficientData = score != null, measurementCoverage = coverage,
            validObservationSec = validMs / 1000.0, totalClosedSec = observations.closedMs / 1000.0,
            longestClosedSec = observations.longestClosedMs / 1000.0,
            prolongedClosureCount = observations.prolongedClosureCount,
            eyeClosedPercent = if (validMs > 0) observations.closedMs * 100.0 / validMs else null,
            scoringVersion = SCORING_VERSION
        )
    }
}
