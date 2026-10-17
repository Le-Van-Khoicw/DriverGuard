package com.example.driverguard.feature.monitoring.data

import android.content.Context
import android.content.SharedPreferences
import com.example.driverguard.feature.history.AlertEvent
import com.example.driverguard.feature.monitoring.ai.TripObservationStats
import com.example.driverguard.feature.monitoring.ai.TripRiskLevel
import com.example.driverguard.feature.monitoring.ai.TripSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ActiveTrip(
    val id: String,
    val userId: String,
    val startTime: Long,
    val revision: Long,
    val stats: TripObservationStats,
    val alerts: List<AlertEvent>,
    val dispatchTripId: String? = null,
    val assignmentVersion: Long = 0
)

data class StoredTrip(val summary: TripSummary, val pending: Boolean)

/** One atomic preference transaction completes the trip and removes its draft. */
object TripLocalStore {
    private lateinit var preferences: SharedPreferences
    private val lock = Mutex()

    fun initialize(context: Context) {
        if (!::preferences.isInitialized) {
            preferences = context.applicationContext.getSharedPreferences("driver_trips_v1", Context.MODE_PRIVATE)
        }
    }

    private suspend fun <T> transaction(uid: String, write: Boolean = false, block: (JSONObject) -> T): T =
        withContext(Dispatchers.IO) {
            lock.withLock {
                require(uid.isNotBlank()) { "Chưa đăng nhập" }
                val key = "account_$uid"
                // A malformed store raises an error rather than silently deleting saved trips.
                val root = JSONObject(preferences.getString(key, null) ?: "{}")
                val result = block(root)
                if (write) check(preferences.edit().putString(key, root.toString()).commit()) {
                    "Không thể lưu dữ liệu chuyến vào bộ nhớ máy"
                }
                result
            }
        }

    suspend fun readActive(uid: String): ActiveTrip? = transaction(uid) { root ->
        root.optJSONObject("active")?.let(::decodeActive)
    }

    suspend fun saveActive(trip: ActiveTrip) = transaction(trip.userId, true) { root ->
        val completed = root.optJSONObject("completed") ?: JSONObject()
        val old = root.optJSONObject("active")
        // A delayed checkpoint cannot resurrect a completed trip or overwrite a newer pause.
        if (!completed.has(trip.id) && (old == null || old.optLong("revision") <= trip.revision)) {
            root.put("active", encodeActive(trip))
        }
    }

    suspend fun complete(summary: TripSummary) = transaction(summary.userId, true) { root ->
        val completed = root.optJSONObject("completed") ?: JSONObject()
        if (!completed.has(summary.id)) {
            completed.put(summary.id, JSONObject().put("pending", true).put("summary", JSONObject(TripSummaryCodec.toMap(summary))))
        }
        root.put("completed", completed)
        if (root.optJSONObject("active")?.optString("id") == summary.id) root.remove("active")
    }

    suspend fun listCompleted(uid: String): List<StoredTrip> = transaction(uid) { root ->
        val completed = root.optJSONObject("completed") ?: JSONObject()
        completed.keys().asSequence().map { id ->
            val row = completed.getJSONObject(id)
            StoredTrip(TripSummaryCodec.fromMap(row.getJSONObject("summary").asMap()), row.optBoolean("pending", true))
        }.sortedByDescending { it.summary.startTime }.toList()
    }

    suspend fun markSynced(uid: String, id: String) = transaction(uid, true) { root ->
        root.optJSONObject("completed")?.optJSONObject(id)?.put("pending", false)
        Unit
    }

    private fun encodeActive(trip: ActiveTrip): JSONObject = JSONObject().apply {
        put("dispatchTripId", trip.dispatchTripId); put("assignmentVersion", trip.assignmentVersion)
        put("id", trip.id); put("userId", trip.userId); put("startTime", trip.startTime); put("revision", trip.revision)
        val s = trip.stats
        put("stats", JSONObject().apply {
            put("monitoredMs", s.monitoredMs); put("validMs", s.validMs); put("closedMs", s.closedMs)
            put("prolongedClosedMs", s.prolongedClosedMs); put("longestClosedMs", s.longestClosedMs)
            put("prolongedClosureCount", s.prolongedClosureCount); put("weightedEarMs", s.weightedEarMs)
        })
        put("alerts", JSONArray(trip.alerts.map { event -> JSONObject().apply {
            put("id", event.id); put("userId", event.userId); put("timestamp", event.timestamp)
            put("ear", event.ear); put("closedDurationSec", event.closedDurationSec); put("warningIndex", event.warningIndex)
            put("latitude", event.latitude); put("longitude", event.longitude); put("speedKmh", event.speedKmh)
            put("locationAddress", event.locationAddress); put("imageUrl", event.imageUrl)
            put("dispatchTripId", event.dispatchTripId); put("monitoringSessionId", event.monitoringSessionId); put("assignmentVersion", event.assignmentVersion)
        } }))
    }

    private fun decodeActive(root: JSONObject): ActiveTrip {
        val s = root.getJSONObject("stats")
        val stats = TripObservationStats(s.getLong("monitoredMs"), s.getLong("validMs"), s.getLong("closedMs"),
            s.getLong("prolongedClosedMs"), s.getLong("longestClosedMs"), s.getInt("prolongedClosureCount"), s.getDouble("weightedEarMs"))
        val alerts = root.getJSONArray("alerts")
        return ActiveTrip(root.getString("id"), root.getString("userId"), root.getLong("startTime"), root.getLong("revision"), stats,
            (0 until alerts.length()).map { index ->
                val a = alerts.getJSONObject(index)
                fun number(key: String) = if (a.isNull(key)) null else a.getDouble(key)
                AlertEvent(id = a.getString("id"), userId = a.getString("userId"), timestamp = a.getLong("timestamp"),
                    ear = a.getDouble("ear"), closedDurationSec = a.getDouble("closedDurationSec"), warningIndex = a.getInt("warningIndex"),
                    latitude = number("latitude"), longitude = number("longitude"), speedKmh = number("speedKmh")?.toFloat(),
                    locationAddress = a.optString("locationAddress").takeIf { it.isNotEmpty() },
                    imageUrl = a.optString("imageUrl").takeIf { it.isNotEmpty() },
                    dispatchTripId = a.optString("dispatchTripId").takeIf { it.isNotEmpty() },
                    monitoringSessionId = a.optString("monitoringSessionId").takeIf { it.isNotEmpty() },
                    assignmentVersion = a.optLong("assignmentVersion"))
            }, root.optString("dispatchTripId").takeIf { it.isNotEmpty() }, root.optLong("assignmentVersion"))
    }
}

private fun JSONObject.asMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        JSONObject.NULL -> null
        is JSONArray -> (0 until value.length()).map { value.get(it) }
        else -> value
    }
}

/** Shared schema for local persistence and Firestore; missing legacy fields stay unknown. */
object TripSummaryCodec {
    fun toMap(s: TripSummary): Map<String, Any?> = mapOf(
        "id" to s.id, "userId" to s.userId, "startTime" to s.startTime, "endTime" to s.endTime, "createdAt" to s.endTime,
        "durationSec" to s.durationSec, "totalAlerts" to s.totalAlerts, "safetyScore" to s.safetyScore,
        "scoreGrade" to s.scoreGrade, "riskLevel" to s.riskLevel.name,
        "perclosEstimatedPercent" to s.perclosEstimatedPercent, "averageEar" to s.averageEar,
        "aiDiagnosis" to s.aiDiagnosis, "aiRecommendations" to s.aiRecommendations,
        "topLocation" to s.topLocation, "isLongDriveFatigue" to s.isLongDriveFatigue,
        "hasSufficientData" to s.hasSufficientData, "measurementCoverage" to s.measurementCoverage,
        "validObservationSec" to s.validObservationSec, "totalClosedSec" to s.totalClosedSec,
        "longestClosedSec" to s.longestClosedSec, "prolongedClosureCount" to s.prolongedClosureCount,
        "eyeClosedPercent" to s.eyeClosedPercent, "scoringVersion" to s.scoringVersion,
        "dispatchTripId" to s.dispatchTripId, "assignmentVersion" to s.assignmentVersion
    )

    fun fromMap(m: Map<String, Any?>): TripSummary {
        fun number(key: String) = m[key] as? Number
        val version = m["scoringVersion"] as? String ?: "legacy"
        return TripSummary(
            id = m["id"] as? String ?: error("Missing trip id"), userId = m["userId"] as? String ?: "",
            startTime = number("startTime")?.toLong() ?: 0, endTime = number("endTime")?.toLong() ?: 0,
            durationSec = number("durationSec")?.toLong() ?: 0, totalAlerts = number("totalAlerts")?.toInt() ?: 0,
            // Legacy scores have no observation-quality gate, so do not present them as verified scores.
            safetyScore = if (version != "legacy") number("safetyScore")?.toInt() else null,
            scoreGrade = if (version != "legacy") m["scoreGrade"] as? String ?: "—" else "—",
            riskLevel = runCatching { TripRiskLevel.valueOf(m["riskLevel"] as? String ?: "CAUTION") }.getOrDefault(TripRiskLevel.CAUTION),
            perclosEstimatedPercent = number("perclosEstimatedPercent")?.toDouble() ?: 0.0,
            averageEar = number("averageEar")?.toDouble() ?: 0.0,
            aiDiagnosis = if (version == "legacy") "Báo cáo cũ chưa đo chất lượng quan sát; không chấm lại điểm từ log cảnh báo."
                else m["aiDiagnosis"] as? String ?: "",
            aiRecommendations = if (version == "legacy") emptyList() else
                (m["aiRecommendations"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            topLocation = m["topLocation"] as? String,
            hasSufficientData = m["hasSufficientData"] as? Boolean ?: false,
            measurementCoverage = number("measurementCoverage")?.toDouble() ?: 0.0,
            validObservationSec = number("validObservationSec")?.toDouble() ?: 0.0,
            totalClosedSec = number("totalClosedSec")?.toDouble() ?: 0.0,
            longestClosedSec = number("longestClosedSec")?.toDouble() ?: 0.0,
            prolongedClosureCount = number("prolongedClosureCount")?.toInt() ?: 0,
            eyeClosedPercent = number("eyeClosedPercent")?.toDouble(), scoringVersion = version,
            dispatchTripId = m["dispatchTripId"] as? String, assignmentVersion = number("assignmentVersion")?.toLong() ?: 0
        )
    }
}
