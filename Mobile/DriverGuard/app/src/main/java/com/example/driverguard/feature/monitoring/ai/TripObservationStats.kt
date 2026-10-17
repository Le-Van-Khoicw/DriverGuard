package com.example.driverguard.feature.monitoring.ai

/** Durations come from monotonic camera timestamps, never from alert logs. */
data class TripObservationStats(
    val monitoredMs: Long = 0,
    val validMs: Long = 0,
    val closedMs: Long = 0,
    val prolongedClosedMs: Long = 0,
    val longestClosedMs: Long = 0,
    val prolongedClosureCount: Int = 0,
    val weightedEarMs: Double = 0.0
)

class TripObservationAccumulator(initial: TripObservationStats = TripObservationStats()) {
    var stats = initial
        private set
    private var lastTime: Long? = null
    private var previousState = EyeState.UNKNOWN
    private var previousEar: Float? = null
    private var closedRunMs = 0L

    fun begin(nowMs: Long) {
        lastTime = nowMs
        previousState = EyeState.UNKNOWN
        previousEar = null
        closedRunMs = 0
    }

    fun record(nowMs: Long, ear: Float?, state: EyeState) {
        if (lastTime?.let { nowMs < it } == true) return
        val delta = lastTime?.let { (nowMs - it).coerceAtLeast(0) } ?: 0
        val valid = delta <= MAX_SAMPLE_GAP_MS && ear?.isFinite() == true &&
            previousEar?.isFinite() == true && state != EyeState.UNKNOWN && previousState != EyeState.UNKNOWN
        val closed = valid && state == EyeState.CLOSED && previousState == EyeState.CLOSED
        val oldRun = closedRunMs
        closedRunMs = if (closed) closedRunMs + delta else 0
        // Include the first 2s when a run crosses the prolonged-closure boundary.
        val prolongedDelta = when {
            closedRunMs < PROLONGED_MS -> 0
            oldRun < PROLONGED_MS -> closedRunMs
            else -> delta
        }
        stats = stats.copy(
            monitoredMs = stats.monitoredMs + delta,
            validMs = stats.validMs + if (valid) delta else 0,
            closedMs = stats.closedMs + if (closed) delta else 0,
            prolongedClosedMs = stats.prolongedClosedMs + prolongedDelta,
            longestClosedMs = maxOf(stats.longestClosedMs, closedRunMs),
            prolongedClosureCount = stats.prolongedClosureCount +
                if (oldRun < PROLONGED_MS && closedRunMs >= PROLONGED_MS) 1 else 0,
            weightedEarMs = stats.weightedEarMs + if (valid) previousEar!!.toDouble() * delta else 0.0
        )
        lastTime = nowMs
        previousState = state
        previousEar = ear
    }

    fun end(nowMs: Long) {
        // A final gap is unobserved; never assume the last eye state lasted forever.
        if (lastTime != null) record(nowMs, null, EyeState.UNKNOWN)
        lastTime = null
        closedRunMs = 0
    }

    companion object {
        const val MAX_SAMPLE_GAP_MS = 500L
        const val PROLONGED_MS = 2_000L
    }
}
