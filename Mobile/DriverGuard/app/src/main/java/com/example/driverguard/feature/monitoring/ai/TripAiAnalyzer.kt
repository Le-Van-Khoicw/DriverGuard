package com.example.driverguard.feature.monitoring.ai

import com.example.driverguard.feature.history.AlertEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

enum class TripRiskLevel(val label: String, val badgeColorHex: Long) {
    SAFE("Ít dấu hiệu ghi nhận", 0xFF10B981),
    CAUTION("Cần chú ý", 0xFFF59E0B),
    HIGH_RISK("Nhiều dấu hiệu ghi nhận", 0xFFEF4444),
    CRITICAL("Nhiều lần nhắm kéo dài", 0xFF991B1B)
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
    const val SCORING_VERSION = "observation-rules-v1"
    const val MIN_VALID_MS = 30_000L
    const val MIN_COVERAGE = 0.70

    fun analyzeTrip(
        id: String,
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long,
        alerts: List<AlertEvent>,
        observations: TripObservationStats
    ): TripSummary {
        val durationMs = maxOf(endTimeMs - startTimeMs, observations.monitoredMs, 1L)
        val validMs = observations.validMs.coerceIn(0, durationMs)
        val coverage = validMs.toDouble() / durationMs
        val sufficient = validMs >= MIN_VALID_MS && coverage >= MIN_COVERAGE
        val ratio = if (validMs > 0) observations.prolongedClosedMs.toDouble() / validMs else 0.0
        val eventsPerHour = if (validMs > 0) observations.prolongedClosureCount * 3_600_000.0 / validMs else 0.0
        // v1 weights are project policy, not learned probabilities or medical thresholds.
        val penalty = minOf(50.0, ratio * 200) + minOf(30.0, eventsPerHour) +
            minOf(20.0, observations.longestClosedMs / 1000.0 * 2)
        val score = if (sufficient) (100 - penalty).roundToInt().coerceIn(0, 100) else null
        val risk = when {
            score == null -> TripRiskLevel.CAUTION
            score >= 90 -> TripRiskLevel.SAFE
            score >= 70 -> TripRiskLevel.CAUTION
            score >= 40 -> TripRiskLevel.HIGH_RISK
            else -> TripRiskLevel.CRITICAL
        }
        val diagnosis = if (!sufficient) {
            "Chưa đủ dữ liệu để chấm điểm: cần ít nhất 30 giây quan sát mắt hợp lệ và " +
                "quan sát được tối thiểu 70% thời gian chuyến. Đã quan sát ${"%.1f".format(validMs / 1000.0)} giây " +
                "(${"%.0f".format(coverage * 100)}%). Các sự kiện ghi nhận vẫn được lưu."
        } else {
            "Ghi nhận ${observations.prolongedClosureCount} đoạn mắt nhắm từ 2 giây; " +
                "lần dài nhất ${"%.1f".format(observations.longestClosedMs / 1000.0)} giây. " +
                "Điểm phản ánh dữ liệu quan sát theo quy tắc thử nghiệm, không xác nhận mức độ an toàn của chuyến xe."
        }
        return TripSummary(
            id = id, userId = userId, startTime = startTimeMs, endTime = endTimeMs,
            durationSec = durationMs / 1000, totalAlerts = alerts.size,
            safetyScore = score, scoreGrade = when {
                score == null -> "—"; score >= 90 -> "A"; score >= 70 -> "B"; score >= 40 -> "C"; else -> "D"
            }, riskLevel = risk,
            averageEar = if (validMs > 0) observations.weightedEarMs / validMs else 0.0,
            aiDiagnosis = diagnosis,
            aiRecommendations = if (observations.prolongedClosureCount > 0)
                listOf("Nếu cảm thấy buồn ngủ, hãy dừng xe tại nơi an toàn để nghỉ ngơi.")
                else listOf("Giữ camera nhìn rõ mắt trong suốt thời gian giám sát."),
            topLocation = alerts.firstOrNull { !it.locationAddress.isNullOrBlank() }?.locationAddress,
            hasSufficientData = sufficient, measurementCoverage = coverage,
            validObservationSec = validMs / 1000.0, totalClosedSec = observations.closedMs / 1000.0,
            longestClosedSec = observations.longestClosedMs / 1000.0,
            prolongedClosureCount = observations.prolongedClosureCount,
            eyeClosedPercent = if (validMs > 0) observations.closedMs * 100.0 / validMs else null,
            scoringVersion = SCORING_VERSION
        )
    }
}
