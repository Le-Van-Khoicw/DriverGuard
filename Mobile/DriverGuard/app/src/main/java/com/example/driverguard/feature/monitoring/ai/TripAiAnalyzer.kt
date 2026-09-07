package com.example.driverguard.feature.monitoring.ai

import com.example.driverguard.feature.history.AlertEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TripRiskLevel(val label: String, val badgeColorHex: Long) {
    SAFE("An toàn", 0xFF10B981),        // Xanh lá (Safe)
    CAUTION("Cần chú ý", 0xFFF59E0B),    // Vàng cam (Warning)
    HIGH_RISK("Nguy cơ cao", 0xFFEF4444), // Đỏ (Danger)
    CRITICAL("Báo động đỏ", 0xFF991B1B)  // Đỏ sẫm (Critical)
}

/**
 * Bản báo cáo phân tích AI toàn diện sau mỗi chuyến đi (Trip AI Summary).
 */
data class TripSummary(
    val id: String = UUID.randomUUID().toString().take(8),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val durationSec: Long = 0,
    val totalAlerts: Int = 0,
    val safetyScore: Int = 100,             // Điểm an toàn từ 0 - 100
    val scoreGrade: String = "A+",          // A+, A, B, C, D
    val riskLevel: TripRiskLevel = TripRiskLevel.SAFE,
    val perclosEstimatedPercent: Double = 0.0,
    val averageEar: Double = 0.28,
    val aiDiagnosis: String = "",           // Nhận xét hành vi từ AI
    val aiRecommendations: List<String> = emptyList(), // Danh sách lời khuyên từ AI
    val topLocation: String? = null,
    val isLongDriveFatigue: Boolean = false
) {
    val durationLabel: String
        get() {
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            return if (minutes > 0) "${minutes} phút ${seconds} giây" else "${seconds} giây"
        }

    val timeRangeLabel: String
        get() {
            val fmt = SimpleDateFormat("HH:mm", Locale("vi", "VN"))
            val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))
            return "${fmt.format(Date(startTime))} - ${fmt.format(Date(endTime))} (${dateFmt.format(Date(startTime))})"
        }
}

/**
 * Bộ não AI Phân tích chuyến đi (Trip AI Analyzer Engine).
 * Tự động tính toán điểm an toàn, đo lường mệt mỏi và tạo báo cáo chẩn đoán cá nhân hóa.
 */
object TripAiAnalyzer {

    /** Ngưỡng cảnh báo lái xe liên tục (Demo: 60 giây = 1 phút; Thực tế: 2 giờ) */
    const val DEMO_FATIGUE_DURATION_MS = 60_000L // 1 phút cho demo theo yêu cầu

    fun analyzeTrip(
        startTimeMs: Long,
        endTimeMs: Long,
        alerts: List<AlertEvent>,
        avgEar: Double = 0.26
    ): TripSummary {
        val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(1000L)
        val durationSec = durationMs / 1000L
        val alertCount = alerts.size
        val isLongDrive = durationMs >= DEMO_FATIGUE_DURATION_MS

        // 1. Tính toán Driving Safety Score (0 - 100)
        var score = 100

        // Trừ điểm theo số lần ngủ gật
        score -= (alertCount * 18)

        // Trừ điểm nếu lái xe liên tục quá ngưỡng cho phép
        if (isLongDrive && alertCount > 0) {
            score -= 10
        }

        // Trừ điểm nếu có cảnh báo ở tốc độ cao (> 40km/h rất nguy hiểm)
        val highSpeedAlerts = alerts.count { (it.speedKmh ?: 0f) > 40f }
        score -= (highSpeedAlerts * 8)

        // Giới hạn điểm trong khoảng [15, 100]
        val finalScore = score.coerceIn(15, 100)

        // 2. Xếp loại Grade & Mức độ rủi ro (Risk Level)
        val (grade, riskLevel) = when {
            finalScore >= 90 -> "A+" to TripRiskLevel.SAFE
            finalScore >= 80 -> "A" to TripRiskLevel.SAFE
            finalScore >= 65 -> "B" to TripRiskLevel.CAUTION
            finalScore >= 45 -> "C" to TripRiskLevel.HIGH_RISK
            else -> "D" to TripRiskLevel.CRITICAL
        }

        // 3. Ước lượng tỷ lệ nhắm mắt PERCLOS (%)
        val totalClosedTimeSec = alerts.sumOf { it.closedDurationSec }
        val perclos = if (durationSec > 0) {
            ((totalClosedTimeSec / durationSec.toDouble()) * 100.0).coerceIn(0.0, 95.0)
        } else 0.0

        // 4. Sinh nhận xét chẩn đoán AI (AI Diagnosis)
        val diagnosis = StringBuilder()
        val recommendations = mutableListOf<String>()

        when {
            alertCount == 0 -> {
                diagnosis.append("🎉 Chuyến đi hoàn hảo! Tài xế duy trì sự tập trung rất cao, mắt luôn mở rõ và không có bất kỳ dấu hiệu ngủ gật nào.")
                recommendations.add("Tiếp tục duy trì phong độ lái xe an toàn.")
                recommendations.add("Nên duy trì thói quen nghỉ ngơi ngắn sau mỗi chặng đường dài.")
            }
            alertCount in 1..2 -> {
                diagnosis.append("⚠️ Chuyến đi ghi nhận $alertCount lần mí mắt khép sâu (EAR < ngưỡng). Dù đã lấy lại tập trung nhanh nhưng cơ thể đã bắt đầu phát tín hiệu mệt mỏi.")
                recommendations.add("Uống ngay 1 cốc nước mát hoặc cà phê để tăng độ tỉnh táo.")
                recommendations.add("Chỉnh hướng gió điều hòa vào mặt hoặc hạ kính lấy gió tự nhiên.")
                recommendations.add("Nếu chuẩn bị đi tiếp chặng dài, hãy chợp mắt ngắn 10-15 phút.")
            }
            alertCount in 3..4 -> {
                diagnosis.append("🚨 CẢNH BÁO NGUY CƠ CAO: Bạn đã ngủ gật $alertCount lần trong phiên lái này! Hiện tượng vi giấc ngủ (Micro-sleep) xuất hiện liên tục khiến phản xạ phanh giảm đến 80%.")
                recommendations.add("🛑 BẮT BUỘC: Hãy tấp xe vào cây xăng / trạm dừng nghỉ gần nhất ngay lập tức.")
                recommendations.add("Rửa mặt bằng nước lạnh và thực hiện các động tác vươn vai giãn cơ.")
                recommendations.add("Tạm dừng hành trình ít nhất 20 phút trước khi tiếp tục cầm lái.")
            }
            else -> {
                diagnosis.append("⛔ MỨC ĐỘ BÁO ĐỘNG ĐỎ: Hệ thống ghi nhận tới $alertCount lần mắt nhắm quá thời gian an toàn. Đây là trạng thái kiệt sức cực độ, nguy cơ tai nạn giao thông rất nghiêm trọng!")
                recommendations.add("⛔ TUYỆT ĐỐI KHÔNG TIẾP TỤC LÁI XE trong tình trạng hiện tại.")
                recommendations.add("Bàn giao tay lái cho người khác hoặc dừng hẳn xe để ngủ đủ giấc.")
                recommendations.add("Không nên lạm dụng nước tăng lực khi cơ thể đã cạn kiệt năng lượng.")
            }
        }

        if (isLongDrive) {
            diagnosis.append(" Thời gian lái xe liên tục đã đạt ngưỡng cảnh báo mệt mỏi (${durationSec / 60} phút).")
        }

        val topLoc = alerts.firstOrNull { !it.locationAddress.isNullOrBlank() }?.locationAddress

        return TripSummary(
            startTime = startTimeMs,
            endTime = endTimeMs,
            durationSec = durationSec,
            totalAlerts = alertCount,
            safetyScore = finalScore,
            scoreGrade = grade,
            riskLevel = riskLevel,
            perclosEstimatedPercent = perclos,
            averageEar = avgEar,
            aiDiagnosis = diagnosis.toString(),
            aiRecommendations = recommendations,
            topLocation = topLoc,
            isLongDriveFatigue = isLongDrive
        )
    }
}
