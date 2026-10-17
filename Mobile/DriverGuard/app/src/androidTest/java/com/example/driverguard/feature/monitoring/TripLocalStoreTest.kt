package com.example.driverguard.feature.monitoring

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.driverguard.feature.monitoring.ai.TripObservationStats
import com.example.driverguard.feature.history.AlertEvent
import com.example.driverguard.feature.monitoring.ai.TripSummary
import com.example.driverguard.feature.monitoring.data.ActiveTrip
import com.example.driverguard.feature.monitoring.data.TripLocalStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TripLocalStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val uid = "storage-test-${UUID.randomUUID()}"
    private val otherUid = "$uid-other"
    private fun draft(revision: Long = 1) = ActiveTrip("trip-id", uid, 1234, revision,
        TripObservationStats(validMs = 500, weightedEarMs = 150.0), emptyList())

    @Before fun initialize() { TripLocalStore.initialize(context) }
    @After fun cleanup() {
        context.getSharedPreferences("driver_trips_v1", 0).edit()
            .remove("account_$uid").remove("account_$otherUid").commit()
    }

    @Test fun checkpointRoundTripsOnAndroidStorage() = runBlocking {
        val expected = draft().copy(alerts = listOf(AlertEvent(id = "warning", userId = uid,
            timestamp = 1234, ear = .2, latitude = 10.1, longitude = 106.2, speedKmh = 42.5f)))
        TripLocalStore.saveActive(expected)
        assertEquals(expected, TripLocalStore.readActive(uid))
        val file = java.io.File(context.applicationInfo.dataDir, "shared_prefs/driver_trips_v1.xml")
        assertTrue(file.readText().contains(uid))
    }

    @Test fun olderCheckpointCannotOverwriteNewerData() = runBlocking {
        TripLocalStore.saveActive(draft(4))
        TripLocalStore.saveActive(draft(2))
        assertEquals(4L, TripLocalStore.readActive(uid)!!.revision)
    }

    @Test fun completingIsAtomicAndIdempotent() = runBlocking {
        TripLocalStore.saveActive(draft())
        val summary = TripSummary(id = "trip-id", userId = uid, scoringVersion = "observation-rules-v1")
        TripLocalStore.complete(summary)
        TripLocalStore.complete(summary)
        assertNull(TripLocalStore.readActive(uid))
        val rows = TripLocalStore.listCompleted(uid)
        assertEquals(1, rows.size)
        assertEquals(summary, rows.single().summary)
        assertTrue(rows.single().pending)
    }

    @Test fun checkpointArrivingAfterCompletionCannotResurrectTrip() = runBlocking {
        TripLocalStore.complete(TripSummary(id = "trip-id", userId = uid, scoringVersion = "observation-rules-v1"))
        TripLocalStore.saveActive(draft(100))
        assertNull(TripLocalStore.readActive(uid))
    }

    @Test fun pendingSyncSurvivesReadUntilAcknowledged() = runBlocking {
        TripLocalStore.complete(TripSummary(id = "trip-id", userId = uid, scoringVersion = "observation-rules-v1"))
        assertTrue(TripLocalStore.listCompleted(uid).single().pending)
        TripLocalStore.markSynced(uid, "trip-id")
        assertFalse(TripLocalStore.listCompleted(uid).single().pending)
    }

    @Test fun draftsAndReportsAreIsolatedByAccount() = runBlocking {
        TripLocalStore.saveActive(draft())
        TripLocalStore.complete(TripSummary(id = "other-trip", userId = otherUid, scoringVersion = "observation-rules-v1"))
        assertNull(TripLocalStore.readActive(otherUid))
        assertTrue(TripLocalStore.listCompleted(uid).isEmpty())
        assertEquals("trip-id", TripLocalStore.readActive(uid)!!.id)
    }
}
