package com.example.driverguard.feature.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.example.driverguard.data.BusinessFunctions
import com.example.driverguard.data.DocumentFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

data class DossierFile(val kind: String, val path: String, val name: String)
data class DriverDossierState(
    val fields: Map<String, String> = emptyMap(), val documents: List<DossierFile> = emptyList(),
    val revision: Long = 0, val status: String = "not_submitted", val reviewNote: String = "",
    val busy: Boolean = false, val loading: Boolean = true, val dirty: Boolean = false,
    val conflict: Boolean = false, val error: String? = null, val message: String? = null,
    val submissionMethod: String = "online", val receiptCode: String = "", val receivedAt: Long = 0
)

class DriverDossierViewModel(application: Application) : AndroidViewModel(application) {
    private val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    private val db = FirebaseFirestore.getInstance()
    private val doc = db.collection("driverDossiers").document(uid.ifBlank { "signed-out" })
    private val preferences = application.getSharedPreferences("driver_dossier_drafts_v1", 0)
    private val _state = MutableStateFlow(DriverDossierState())
    val state = _state.asStateFlow()
    private var operationId: String? = null
    private var latestRemote: Map<String, Any?>? = null
    private fun stillOwner() = uid.isNotBlank() && FirebaseAuth.getInstance().currentUser?.uid == uid
    private val listener = doc.addSnapshotListener { snapshot, error ->
        if (!stillOwner()) return@addSnapshotListener
        if (error != null) { _state.value = _state.value.copy(loading = false, error = error.message); return@addSnapshotListener }
        latestRemote = snapshot?.data
        if (_state.value.busy) return@addSnapshotListener
        if (!_state.value.dirty) applyRemote()
        else {
            val revision = (latestRemote?.get("revision") as? Number)?.toLong() ?: 0
            _state.value = _state.value.copy(loading = false, conflict = revision != _state.value.revision,
                status = latestRemote?.get("status") as? String ?: "not_submitted", reviewNote = latestRemote?.get("reviewNote") as? String ?: "",
                submissionMethod = latestRemote?.get("submissionMethod") as? String ?: "online",
                receiptCode = latestRemote?.get("receiptCode") as? String ?: "", receivedAt = (latestRemote?.get("receivedAt") as? Number)?.toLong() ?: 0)
        }
    }
    init {
        // A draft is separate from the submitted dossier. Editing never changes review status remotely.
        runCatching {
            preferences.getString(uid, null)?.let { raw ->
                val root = JSONObject(raw)
                val fields = root.getJSONObject("fields")
                val files = root.getJSONArray("documents")
                _state.value = _state.value.copy(fields = fields.keys().asSequence().associateWith { fields.getString(it) },
                    documents = (0 until files.length()).map { val f = files.getJSONObject(it); DossierFile(f.getString("kind"), f.getString("path"), f.getString("name")) },
                    revision = root.optLong("revision"), dirty = true)
                operationId = root.optString("operationId").takeIf { it.isNotBlank() }
            }
        }.onFailure { _state.value = _state.value.copy(error = "Không đọc được bản nháp: ${it.message}", conflict = true, dirty = true, loading = false) }
    }
    private fun applyRemote() {
        val remote = latestRemote
        @Suppress("UNCHECKED_CAST") val docs = remote?.get("documents") as? List<Map<String, Any?>> ?: emptyList()
        val authUser = FirebaseAuth.getInstance().currentUser
        _state.value = DriverDossierState(
            fields = dossierFields.associate { it.key to (remote?.get(it.key) as? String ?: when(it.key) {
                "fullName" -> authUser?.displayName.orEmpty(); else -> ""
            }) }, documents = docs.map { DossierFile(it["kind"] as String, it["path"] as String, it["name"] as String) },
            revision = (remote?.get("revision") as? Number)?.toLong() ?: 0,
            status = remote?.get("status") as? String ?: "not_submitted", reviewNote = remote?.get("reviewNote") as? String ?: "", loading = false,
            submissionMethod = remote?.get("submissionMethod") as? String ?: "online",
            receiptCode = remote?.get("receiptCode") as? String ?: "", receivedAt = (remote?.get("receivedAt") as? Number)?.toLong() ?: 0)
    }
    private fun saveDraft() {
        val s = _state.value
        val saved = preferences.edit().putString(uid, JSONObject().put("fields", JSONObject(s.fields))
            .put("revision", s.revision).put("operationId", operationId)
            .put("documents", JSONArray(s.documents.map { JSONObject(mapOf("kind" to it.kind, "path" to it.path, "name" to it.name)) })).toString()).commit()
        check(saved) { "Không lưu được bản nháp trên máy" }
    }
    fun edit(key: String, value: String) {
        if (!stillOwner() || _state.value.busy) return
        operationId = null
        _state.value = _state.value.copy(fields = _state.value.fields + (key to value), dirty = true, error = null, message = null)
        runCatching { saveDraft() }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }
    fun reloadRemote() {
        if (!stillOwner() || _state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { doc.get(Source.SERVER).await() }.onSuccess {
                if (stillOwner()) { latestRemote = it.data; preferences.edit().remove(uid).commit(); operationId = null; applyRemote() }
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
            if (stillOwner()) _state.value = _state.value.copy(busy = false)
        }
    }
    fun removeDocument(kind: String) {
        if (!stillOwner() || _state.value.busy) return
        operationId = null
        // Remove from draft only; previously submitted/approved file objects stay immutable.
        _state.value = _state.value.copy(documents = _state.value.documents.filterNot { it.kind == kind }, dirty = true, error = null, message = "Đã bỏ tệp khỏi bản nháp. Hồ sơ đã nộp vẫn giữ cho đến khi nộp lại.")
        runCatching { saveDraft() }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }
    fun fileSelectionError(message: String) {
        _state.value = _state.value.copy(error = message, message = null)
    }
    fun upload(kind: String, uri: Uri) {
        if (!stillOwner() || _state.value.busy || !dossierDocumentNames.containsKey(kind)) return
        _state.value = _state.value.copy(busy = true, error = null, message = "Đang tải giấy tờ…")
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val (bytes, name) = withContext(Dispatchers.IO) {
                    var fileName = "giay-to"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) fileName = cursor.getString(0) ?: fileName }
                    val data = resolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val count = input.read(buffer); if (count < 0) break; check(output.size() + count <= 5 * 1024 * 1024) { "Tệp vượt 5 MB" }; output.write(buffer, 0, count) }
                        output.toByteArray()
                    } ?: error("Không mở được tệp")
                    check(data.isNotEmpty()) { "Tệp trống" }
                    data to fileName.take(180)
                }
                // Some Android file providers return null or application/octet-stream for valid photos.
                // Detect the actual file signature, matching the server's validation rather than its name.
                val mime = when {
                    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
                    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
                    bytes.size >= 5 && bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) -> "application/pdf"
                    else -> error("Tệp không phải JPG, PNG hoặc PDF. Nếu ảnh HEIC/WebP, hãy xuất sang JPG trước.")
                }
                check(stillOwner()) { "Tài khoản đã thay đổi" }
                val path = withTimeout(190_000) { DocumentFiles.upload(uid, kind, mime, bytes) }
                if (!stillOwner()) return@launch
                operationId = null
                _state.value = _state.value.copy(documents = _state.value.documents.filterNot { it.kind == kind } + DossierFile(kind, path, name), dirty = true, message = "Đã tải $name. Cần bấm Nộp hồ sơ để quản lý nhận được.")
                saveDraft()
            } catch (e: Exception) { if (stillOwner()) _state.value = _state.value.copy(error = e.message, message = null) }
            finally { if (stillOwner()) _state.value = _state.value.copy(busy = false) }
        }
    }
    fun submit() {
        val s = _state.value
        if (!stillOwner() || s.busy || s.loading || s.conflict) return
        if (dossierFields.any { s.fields[it.key].isNullOrBlank() } || requiredDossierDocuments.any { kind -> s.documents.none { it.kind == kind } }) {
            _state.value = s.copy(error = "Điền đủ thông tin và tải hai mặt GPLX trước khi nộp. Tệp căn cước/sức khỏe là tùy chọn."); return
        }
        operationId = operationId ?: UUID.randomUUID().toString()
        runCatching { saveDraft() }.onFailure { _state.value = s.copy(error = it.message); return }
        _state.value = s.copy(busy = true, error = null, message = "Đang nộp hồ sơ…")
        viewModelScope.launch {
            try {
                val request = s.fields + mapOf("action" to "submitDriverDossier", "operationId" to operationId, "revision" to s.revision,
                    "documents" to s.documents.map { mapOf("kind" to it.kind, "path" to it.path, "name" to it.name) })
                withTimeout(100_000) { BusinessFunctions.command().call(request).await() }
                val confirmed = doc.get(Source.SERVER).await()
                if (!stillOwner()) return@launch
                latestRemote = confirmed.data; check(preferences.edit().remove(uid).commit()) { "Đã nộp nhưng chưa xóa được bản nháp" }
                operationId = null; applyRemote()
                _state.value = _state.value.copy(message = "Hồ sơ đã được máy chủ nhận, chờ quản lý đối chiếu.")
            } catch (e: Exception) { if (stillOwner()) _state.value = _state.value.copy(error = "Chưa xác nhận nộp hồ sơ: ${e.message}. Bản nháp vẫn giữ trên máy.", message = null) }
            finally { if (stillOwner()) _state.value = _state.value.copy(busy = false) }
        }
    }
    override fun onCleared() { listener.remove(); super.onCleared() }
}

data class DossierField(val key: String, val label: String, val example: String, val group: String)
val dossierFields = listOf(
    DossierField("fullName", "Họ tên theo giấy tờ", "VD: Nguyễn Văn Nam", "Thông tin cá nhân"),
    DossierField("phone", "Điện thoại", "VD: 0901234567", "Thông tin cá nhân"),
    DossierField("birthDate", "Ngày sinh", "yyyy-MM-dd, VD: 1995-06-15", "Thông tin cá nhân"),
    DossierField("address", "Địa chỉ liên hệ", "VD: 12 Nguyễn Trãi, TP.HCM", "Thông tin cá nhân"),
    DossierField("identityNumber", "Số căn cước", "Nhập đúng trên giấy tờ", "Thông tin cá nhân"),
    DossierField("licenseNumber", "Số giấy phép lái xe", "Nhập đúng trên GPLX", "Giấy phép và sức khỏe"),
    DossierField("licenseClass", "Hạng GPLX", "VD: C1, C, CE; ghi đúng trên giấy", "Giấy phép và sức khỏe"),
    DossierField("licenseIssuedDate", "Ngày cấp GPLX", "yyyy-MM-dd", "Giấy phép và sức khỏe"),
    DossierField("licenseExpiresDate", "GPLX có giá trị đến ngày", "yyyy-MM-dd", "Giấy phép và sức khỏe"),
    DossierField("healthCheckedDate", "Ngày khám sức khỏe", "yyyy-MM-dd", "Giấy phép và sức khỏe"),
    DossierField("healthExpiresDate", "Hạn hồ sơ sức khỏe theo giấy / yêu cầu công ty", "yyyy-MM-dd; quản lý đối chiếu", "Giấy phép và sức khỏe"),
    DossierField("emergencyName", "Người liên hệ khẩn cấp", "VD: Nguyễn Thị An — vợ", "Liên hệ khẩn cấp"),
    DossierField("emergencyPhone", "Điện thoại khẩn cấp", "VD: 0912345678", "Liên hệ khẩn cấp")
)
val requiredDossierDocuments = setOf("license_front", "license_back")
val dossierDocumentNames = linkedMapOf("license_front" to "GPLX — mặt trước (bắt buộc)", "license_back" to "GPLX — mặt sau (bắt buộc)",
    "identity_front" to "Căn cước — mặt trước (tùy chọn)", "identity_back" to "Căn cước — mặt sau (tùy chọn)", "health" to "Giấy khám / xác nhận sức khỏe (tùy chọn)")
