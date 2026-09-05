package com.example.driverguard.feature.monitoring.ai

import kotlin.math.sqrt

data class TemporalFeatures(
    val current: Float,
    val mean: Float,
    val min: Float,
    val max: Float,
    val standardDeviation: Float,
    val slope: Float,
    val belowThresholdRatio: Float,
    val maxLowEarRunRatio: Float,
    val validRatio: Float
) {
    fun asArray() = floatArrayOf(current, mean, min, max, standardDeviation, slope,
        belowThresholdRatio, maxLowEarRunRatio, validRatio)
}

class TemporalFeatureExtractor(
    private val windowSize: Int = 13,
    private val threshold: Float = 0.25f,
    private val minimumValidRatio: Float = 0.70f
) {
    private val history = ArrayDeque<Float?>()

    fun add(ear: Float?): TemporalFeatures? {
        history.addLast(ear?.takeIf { it.isFinite() })
        if (history.size > windowSize) history.removeFirst()
        if (history.size < windowSize) return null

        val raw = history.toList()
        val validRatio = raw.count { it != null }.toFloat() / windowSize
        if (validRatio < minimumValidRatio) return null
        val filled = interpolate(raw)
        val mean = filled.average().toFloat()
        val variance = filled.sumOf { ((it - mean) * (it - mean)).toDouble() } / filled.size
        val low = filled.map { it < threshold }
        return TemporalFeatures(
            current = filled.last(), mean = mean,
            min = filled.min(), max = filled.max(),
            standardDeviation = sqrt(variance).toFloat(),
            slope = linearSlope(filled),
            belowThresholdRatio = low.count { it }.toFloat() / filled.size,
            maxLowEarRunRatio = longestRun(low).toFloat() / filled.size,
            validRatio = validRatio
        )
    }

    fun reset() = history.clear()

    private fun interpolate(values: List<Float?>): List<Float> {
        val validIndices = values.indices.filter { values[it] != null }
        return values.indices.map { index ->
            values[index] ?: run {
                val left = validIndices.lastOrNull { it < index }
                val right = validIndices.firstOrNull { it > index }
                when {
                    left == null -> values[right!!]!!
                    right == null -> values[left]!!
                    else -> {
                        val ratio = (index - left).toFloat() / (right - left)
                        values[left]!! + ratio * (values[right]!! - values[left]!!)
                    }
                }
            }
        }
    }

    private fun linearSlope(values: List<Float>): Float {
        val meanX = (values.size - 1) / 2f
        val meanY = values.average().toFloat()
        val numerator = values.indices.sumOf { ((it - meanX) * (values[it] - meanY)).toDouble() }
        val denominator = values.indices.sumOf { ((it - meanX) * (it - meanX)).toDouble() }
        return if (denominator == 0.0) 0f else (numerator / denominator).toFloat()
    }

    private fun longestRun(mask: List<Boolean>): Int {
        var current = 0; var longest = 0
        mask.forEach { value -> current = if (value) current + 1 else 0; longest = maxOf(longest, current) }
        return longest
    }
}
