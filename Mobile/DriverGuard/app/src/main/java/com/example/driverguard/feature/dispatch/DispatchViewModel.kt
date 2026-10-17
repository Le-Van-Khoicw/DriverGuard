package com.example.driverguard.feature.dispatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CargoItem(val name: String, val quantity: Double, val unit: String)
data class TripStop(val id: String, val kind: String, val address: String, val contact: String,
    val phone: String, val items: List<CargoItem>, val status: String, val quantities: List<Double> = emptyList(), val note: String = "")
data class AssignedTrip(val id: String, val name: String, val driverId: String, val vehiclePlate: String,
    val dispatcherId: String, val start: Long, val end: Long, val status: String, val assignmentVersion: Long,
    val stops: List<TripStop>, val updatedAt: Long)
data class DriverLeave(val id: String, val start: Long, val end: Long, val reason: String, val status: String)
data class DispatchState(val trips: List<AssignedTrip> = emptyList(), val busy: Boolean = false,
    val loaded: Boolean = false, val fromCache: Boolean = true, val pending: Int = 0,
    val error: String? = null, val conflict: Boolean = false, val message: String? = null,
    val leaves: List<DriverLeave> = emptyList())

fun dispatchLabel(status: String): String = mapOf(
    "assigned" to "Chờ nhận chuyến", "accepted" to "Đã nhận", "in_progress" to "Đang thực hiện",
    "handover_pending" to "Chờ nhận bàn giao", "needs_reassignment" to "Cần điều phối lại",
    "declined" to "Đã từ chối", "completed" to "Hoàn thành", "completed_issues" to "Kết thúc có vấn đề",
    "cancelled" to "Đã hủy", "pending" to "Chưa xử lý", "full" to "Đủ", "partial" to "Thiếu", "failed" to "Thất bại"
)[status] ?: status

/** The outbox belongs to the account, not a screen. Stable IDs survive process death.
 * Progress may be queued offline; accept/start always wait for the server's checks.
 */
class DispatchViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-southeast1")
    private val preferences = application.getSharedPreferences("dispatch_outbox_v1", 0)
    private val _state = MutableStateFlow(DispatchState())
    val state = _state.asStateFlow()
    private var uid = ""
    private var listener: ListenerRegistration? = null
    private var leaveListener: ListenerRegistration? = null
    private var worker: Job? = null
    private var serverTrips: List<AssignedTrip> = emptyList()
    private var queue = mutableListOf<Map<String, Any?>>()
    private var rejected = false
    private val authListener = FirebaseAuth.AuthStateListener { source -> switchAccount(source.currentUser?.uid.orEmpty()) }

    init { auth.addAuthStateListener(authListener) }
    private fun switchAccount(next: String) {
        if (next == uid && listener != null) return
        listener?.remove(); leaveListener?.remove(); worker?.cancel(); uid = next; serverTrips = emptyList(); rejected = false
        _state.value = DispatchState()
        if (uid.isBlank()) return
        try {
            val root = JSONObject(preferences.getString("account_$uid", null) ?: "{}")
            val saved = root.optJSONArray("operations") ?: JSONArray()
            queue = (0 until saved.length()).map { saved.getJSONObject(it).toMap() }.toMutableList()
            rejected = root.optBoolean("rejected")
            if (rejected) _state.value = _state.value.copy(error = "Có lệnh nghiệp vụ bị máy chủ từ chối. Kiểm tra lại với điều phối trước khi bỏ và nhập lại.")
        } catch (e: Exception) {
            // Do not overwrite a corrupt outbox and silently lose delivery records.
            queue = mutableListOf(); rejected = true
            _state.value = _state.value.copy(error = "Không đọc được hàng đợi: ${e.message}", conflict = true)
            return
        }
        val account = uid
        leaveListener = db.collection("leaveRequests").whereEqualTo("driverId", account).addSnapshotListener { snapshot, error ->
            if (uid != account) return@addSnapshotListener
            if (error != null) { _state.value = _state.value.copy(error = error.message); return@addSnapshotListener }
            if (snapshot != null) _state.value = _state.value.copy(leaves = snapshot.documents.map { doc ->
                DriverLeave(doc.id, doc.getLong("start") ?: 0, doc.getLong("end") ?: 0, doc.getString("reason").orEmpty(), doc.getString("status").orEmpty())
            }.sortedByDescending { it.start })
        }
        listener = db.collection("dispatchTrips").whereEqualTo("driverId", account)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (uid != account) return@addSnapshotListener
                if (error != null) { _state.value = _state.value.copy(error = error.message); return@addSnapshotListener }
                if (snapshot != null) {
                    serverTrips = snapshot.documents.mapNotNull { d -> runCatching { decodeTrip(d.id, d.data ?: emptyMap()) }.getOrNull() }
                    _state.value = _state.value.copy(loaded = true, fromCache = snapshot.metadata.isFromCache)
                    publish()
                }
            }
        worker = viewModelScope.launch {
            while (isActive && uid == account) { flush(account); delay(10_000) }
        }
        publish()
    }

    fun retrySync() { viewModelScope.launch { flush(uid) } }
    fun discardRejected() {
        if (!rejected || _state.value.busy) return
        val previous = queue.toList()
        queue.clear(); rejected = false
        if (!saveQueue()) { queue.addAll(previous); rejected = true; return }
        _state.value = _state.value.copy(error = null, message = "Đã bỏ lệnh bị từ chối. Kiểm tra lại kết quả với điều phối.")
        publish()
    }
    fun onlineCommand(action: String, trip: AssignedTrip? = null, fields: Map<String, Any?> = emptyMap(), onSuccess: () -> Unit = {}) {
        if (_state.value.busy || uid.isBlank()) return
        if (queue.isNotEmpty()) { _state.value = _state.value.copy(error = "Cần đồng bộ kết quả đang chờ trước khi nhận/bắt đầu chuyến khác"); return }
        val account = uid
        val operation = operation(action, trip, fields)
        _state.value = _state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                withTimeout(25_000) { functions.getHttpsCallable("dispatchCommand").call(operation).await() }
                if (uid == account) { _state.value = _state.value.copy(message = "Máy chủ đã xác nhận. Đang cập nhật chuyến…"); onSuccess() }
            } catch (e: Exception) { if (uid == account) _state.value = _state.value.copy(error = "Chưa nhận xác nhận: ${e.message}. Kiểm tra trạng thái chuyến trước khi thử lại.") }
            finally { if (uid == account) _state.value = _state.value.copy(busy = false) }
        }
    }

    fun recordStop(trip: AssignedTrip, stop: TripStop, outcome: String, quantities: List<Double>, contact: String, note: String) {
        enqueue(operation("updateStop", trip, mapOf("stopId" to stop.id, "outcome" to outcome, "quantities" to quantities, "actualContact" to contact.trim(), "reason" to note.trim())))
    }
    fun finishTrip(trip: AssignedTrip, note: String) { enqueue(operation("finishTrip", trip, mapOf("reason" to note.trim()))) }
    private fun operation(action: String, trip: AssignedTrip?, fields: Map<String, Any?>) = fields + mapOf(
        "action" to action, "operationId" to UUID.randomUUID().toString(), "tripId" to trip?.id,
        "assignmentVersion" to trip?.assignmentVersion)

    private fun enqueue(operation: Map<String, Any?>) {
        if (uid.isBlank() || rejected || _state.value.busy) return
        queue.add(operation)
        if (!saveQueue()) { queue.removeAt(queue.lastIndex); return }
        _state.value = _state.value.copy(message = "Đã lưu trên máy · chờ máy chủ xác nhận", error = null)
        publish(); retrySync()
    }
    private fun saveQueue(): Boolean {
        val saved = preferences.edit().putString("account_$uid", JSONObject().put("rejected", rejected)
            .put("operations", JSONArray(queue.map { JSONObject(it) })).toString()).commit()
        if (!saved) _state.value = _state.value.copy(error = "Không ghi được bộ nhớ máy. Chưa ghi nhận thao tác.")
        return saved
    }
    private suspend fun flush(account: String) {
        if (account.isBlank() || rejected || queue.isEmpty() || _state.value.busy || uid != account) return
        _state.value = _state.value.copy(busy = true)
        try {
            while (uid == account && queue.isNotEmpty() && !rejected) {
                val op = queue.first()
                try {
                    withTimeout(25_000) { functions.getHttpsCallable("dispatchCommand").call(op).await() }
                    if (uid != account) return
                    // Keep the optimistic operation until its authoritative replacement has arrived.
                    // If this read loses connection, replaying the same operationId is safe.
                    val fresh = withTimeout(15_000) { db.collection("dispatchTrips").whereEqualTo("driverId", account).get(Source.SERVER).await() }
                    if (uid != account) return
                    serverTrips = fresh.documents.mapNotNull { d -> runCatching { decodeTrip(d.id, d.data ?: emptyMap()) }.getOrNull() }
                    _state.value = _state.value.copy(fromCache = false, loaded = true)
                    queue.removeAt(0)
                    if (!saveQueue()) { queue.add(0, op); return }
                    // Wait for Firestore listeners to replace optimistic status; no fabricated server time.
                    _state.value = _state.value.copy(message = "Đã gửi kết quả. Chờ dữ liệu xác nhận từ máy chủ.", error = null)
                } catch (e: Exception) {
                    if (uid != account) return
                    rejected = e is FirebaseFunctionsException && e.code in setOf(FirebaseFunctionsException.Code.PERMISSION_DENIED,
                        FirebaseFunctionsException.Code.FAILED_PRECONDITION, FirebaseFunctionsException.Code.INVALID_ARGUMENT)
                    if (rejected) saveQueue()
                    _state.value = _state.value.copy(error = if (rejected) "Máy chủ từ chối lệnh: ${e.message}. Liên hệ điều phối; lệnh còn giữ trên máy."
                        else "Chưa đồng bộ được: ${e.message}. Kết quả vẫn giữ trên máy.")
                    break
                }
            }
        } finally { if (uid == account) { _state.value = _state.value.copy(busy = false); publish() } }
    }
    private fun publish() {
        val projected = serverTrips.map { trip ->
            var current = trip
            if (!rejected) for (op in queue.filter { it["tripId"] == trip.id && (it["assignmentVersion"] as? Number)?.toLong() == trip.assignmentVersion }) {
                if (op["action"] == "updateStop") current = current.copy(stops = current.stops.map { s -> if (s.id == op["stopId"]) s.copy(status = op["outcome"] as String,
                    quantities = (op["quantities"] as List<*>).map { (it as Number).toDouble() }, note = op["reason"] as String) else s })
                if (op["action"] == "finishTrip") current = current.copy(status = "completion_pending")
            }
            current
        }
        _state.value = _state.value.copy(trips = projected.sortedByDescending { it.start }, pending = queue.size, conflict = rejected)
    }
    override fun onCleared() { listener?.remove(); leaveListener?.remove(); worker?.cancel(); auth.removeAuthStateListener(authListener); super.onCleared() }
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> (0 until value.length()).map { value.get(it) }
        else -> value
    }
}
private fun decodeTrip(id: String, m: Map<String, Any?>): AssignedTrip {
    fun number(key: String) = (m[key] as? Number)?.toLong() ?: 0
    val stops = (m["stops"] as? List<*>)?.map { value ->
        @Suppress("UNCHECKED_CAST") val s = value as Map<String, Any?>
        val items = (s["items"] as List<*>).map { raw ->
            @Suppress("UNCHECKED_CAST") val item = raw as Map<String, Any?>
            CargoItem(item["name"] as String, (item["quantity"] as Number).toDouble(), item["unit"] as String)
        }
        TripStop(s["id"] as String, s["kind"] as String, s["address"] as String, s["contact"] as String, s["phone"] as String, items,
            s["status"] as String, (s["quantities"] as? List<*>)?.map { (it as Number).toDouble() } ?: emptyList(), s["note"] as? String ?: "")
    } ?: emptyList()
    return AssignedTrip(id, m["name"] as String, m["driverId"] as String, m["vehiclePlate"] as String,
        m["dispatcherId"] as String, number("start"), number("end"), m["status"] as String, number("assignmentVersion"), stops, number("updatedAt"))
}
