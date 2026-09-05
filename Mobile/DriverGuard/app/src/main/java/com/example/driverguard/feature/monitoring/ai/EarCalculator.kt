package com.example.driverguard.feature.monitoring.ai

import kotlin.math.hypot

data class Point2D(val x: Float, val y: Float)

object EarCalculator {
    val RIGHT_EYE = intArrayOf(33, 160, 158, 133, 153, 144)
    val LEFT_EYE = intArrayOf(362, 385, 387, 263, 373, 380)

    /** Cùng công thức trong notebook: (|p2-p6| + |p3-p5|) / (2 * |p1-p4|). */
    fun calculate(points: List<Point2D>): Float? {
        require(points.size == 6) { "EAR cần đúng 6 landmark cho một mắt" }
        val horizontal = distance(points[0], points[3])
        if (horizontal <= 0f) return null
        val vertical1 = distance(points[1], points[5])
        val vertical2 = distance(points[2], points[4])
        return (vertical1 + vertical2) / (2f * horizontal)
    }

    fun average(left: List<Point2D>, right: List<Point2D>): Float? {
        val values = listOfNotNull(calculate(left), calculate(right))
        return values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }

    private fun distance(a: Point2D, b: Point2D) = hypot(a.x - b.x, a.y - b.y)
}
