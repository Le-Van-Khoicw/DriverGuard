package com.example.driverguard.feature.monitoring

import com.example.driverguard.feature.monitoring.ai.*
import com.example.driverguard.feature.monitoring.data.TripSummaryCodec
import org.junit.Assert.*
import org.junit.Test

class TripScoringTest {
    private fun score(stats: TripObservationStats, durationMs: Long = 40_000) =
        TripAiAnalyzer.analyzeTrip("test-trip", "driver", 0, durationMs, emptyList(), stats)

    @Test fun missingCameraDoesNotEarnPerfectScore() {
        val result = score(TripObservationStats(monitoredMs = 40_000))
        assertNull(result.safetyScore)
        assertFalse(result.hasSufficientData)
        assertNull(result.eyeClosedPercent)
    }

    @Test fun tooShortOrPoorlyCoveredTripIsUnscored() {
        assertNull(score(TripObservationStats(validMs = 29_000), 30_000).safetyScore)
        assertNull(score(TripObservationStats(validMs = 35_000), 100_000).safetyScore)
    }

    @Test fun wellObservedOpenEyesHaveScoreAndActualAverageEar() {
        val result = score(TripObservationStats(validMs = 35_000, weightedEarMs = 10_500.0))
        assertEquals(100, result.safetyScore)
        assertEquals(.875, result.measurementCoverage, .0001)
        assertEquals(.3, result.averageEar, .0001)
    }

    @Test fun sameClosuresAreNormalizedByObservedTime() {
        val short = score(TripObservationStats(validMs = 55_000, prolongedClosedMs = 3_000,
            longestClosedMs = 3_000, prolongedClosureCount = 1), 60_000)
        val long = score(TripObservationStats(validMs = 590_000, prolongedClosedMs = 3_000,
            longestClosedMs = 3_000, prolongedClosureCount = 1), 600_000)
        assertTrue(long.safetyScore!! > short.safetyScore!!)
    }

    @Test fun aContinuousClosureIsCountedOnceWithItsWholeDuration() {
        val counter = TripObservationAccumulator()
        counter.begin(0)
        for (t in 0L..3_000L step 100) counter.record(t, .1f, EyeState.CLOSED)
        assertEquals(1, counter.stats.prolongedClosureCount)
        assertEquals(3_000L, counter.stats.prolongedClosedMs)
        assertEquals(3_000L, counter.stats.longestClosedMs)
        counter.record(3_100, .3f, EyeState.OPEN)
        assertEquals(1, counter.stats.prolongedClosureCount)
    }

    @Test fun cameraLossBreaksTheClosureAndDoesNotIncreaseValidTime() {
        val counter = TripObservationAccumulator()
        counter.begin(0)
        for (t in 0L..1_000L step 100) counter.record(t, .1f, EyeState.CLOSED)
        counter.record(5_000, null, EyeState.UNKNOWN)
        counter.record(5_100, .1f, EyeState.CLOSED)
        assertEquals(0, counter.stats.prolongedClosureCount)
        assertEquals(1_000L, counter.stats.validMs)
        assertEquals(5_100L, counter.stats.monitoredMs)
    }

    @Test fun pauseAndResumeKeepTotalsWithoutCountingThePauseAsObserved() {
        val counter = TripObservationAccumulator()
        counter.begin(0)
        counter.record(0, .3f, EyeState.OPEN)
        counter.record(100, .3f, EyeState.OPEN)
        counter.end(200)
        val restored = TripObservationAccumulator(counter.stats)
        restored.begin(10_000)
        restored.record(10_000, .3f, EyeState.OPEN)
        restored.record(10_100, .3f, EyeState.OPEN)
        assertEquals(200L, restored.stats.validMs)
        assertEquals(300L, restored.stats.monitoredMs)
    }

    @Test fun outOfOrderCameraCallbacksCannotDoubleCountTime() {
        val counter = TripObservationAccumulator()
        counter.begin(0)
        counter.record(0, .3f, EyeState.OPEN)
        counter.record(100, .3f, EyeState.OPEN)
        counter.record(50, .1f, EyeState.CLOSED)
        counter.record(200, .3f, EyeState.OPEN)
        assertEquals(200L, counter.stats.validMs)
        assertEquals(0L, counter.stats.closedMs)
    }

    @Test fun nullableScoreAndSchemaSurviveMapRoundTrip() {
        val original = score(TripObservationStats())
        assertEquals(original, TripSummaryCodec.fromMap(TripSummaryCodec.toMap(original)))
    }

    @Test fun oldDocumentsAreNotMistakenForQualityCheckedScores() {
        val old = TripSummaryCodec.fromMap(mapOf("id" to "old", "safetyScore" to 100))
        assertNull(old.safetyScore)
        assertEquals("legacy", old.scoringVersion)
    }
}
