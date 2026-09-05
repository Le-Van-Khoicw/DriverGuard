package com.example.driverguard.feature.monitoring.ai

enum class EyeState { UNKNOWN, OPEN, CLOSED }

data class DetectionResult(
    val eyeState: EyeState,
    val ear: Float?,
    val closedDurationMs: Long,
    val shouldAlert: Boolean,
    val features: TemporalFeatures?
)

fun interface EyeStateClassifier {
    fun classify(features: TemporalFeatures): EyeState
}

/** Baseline chạy được ngay; thay bằng RandomForestClassifier khi đã export model Python. */
class ThresholdClassifier(var threshold: Float = 0.25f) : EyeStateClassifier {
    override fun classify(features: TemporalFeatures) =
        if (features.current < threshold) EyeState.CLOSED else EyeState.OPEN
}

class DrowsinessDetector(
    val classifier: EyeStateClassifier = ThresholdClassifier(),
    private val alarmAfterMs: Long = 3_000L   // 3 giây nhắm mắt mới kêu
) {
    private val extractor = TemporalFeatureExtractor()
    private var closedSince: Long? = null

    fun process(ear: Float?, timestampMs: Long): DetectionResult {
        val features = extractor.add(ear)
        val state = features?.let(classifier::classify) ?: EyeState.UNKNOWN
        if (state == EyeState.CLOSED && closedSince == null) closedSince = timestampMs
        if (state != EyeState.CLOSED) closedSince = null
        val duration = closedSince?.let { (timestampMs - it).coerceAtLeast(0) } ?: 0L
        return DetectionResult(state, ear, duration, duration >= alarmAfterMs, features)
    }

    fun reset() { extractor.reset(); closedSince = null }
}
