package com.example.driverguard.data

import com.example.driverguard.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object DocumentFiles {
    suspend fun upload(uid: String, kind: String, contentType: String, bytes: ByteArray): String {
        val user = FirebaseAuth.getInstance().currentUser
        check(user?.uid == uid) { "Tài khoản đã thay đổi" }
        if (BuildConfig.FIREBASE_EMULATORS) {
            val path = "driver-documents/$uid/$kind/${UUID.randomUUID()}"
            FirebaseStorage.getInstance().reference.child(path)
                .putBytes(bytes, StorageMetadata.Builder().setContentType(contentType).build()).await()
            return path
        }
        check(BuildConfig.DISPATCH_API_URL.startsWith("https://")) { "Chưa cấu hình backend online" }
        val token = checkNotNull(user).getIdToken(false).await().token ?: error("Cần đăng nhập lại")
        return withContext(Dispatchers.IO) {
            check(FirebaseAuth.getInstance().currentUser?.uid == uid) { "Tài khoản đã thay đổi" }
            val connection = URL("${BuildConfig.DISPATCH_API_URL.trimEnd('/')}/driver-documents?kind=$kind").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 90_000
                connection.readTimeout = 90_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrNull()
                check(status in 200..299) { json?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Chưa tải được giấy tờ (HTTP $status). Thử lại khi có mạng." }
                val path = json?.optString("path").orEmpty()
                check(path.startsWith("supabase:driver-documents/$uid/$kind/")) { "Phản hồi tải tệp không hợp lệ" }
                path
            } finally { connection.disconnect() }
        }
    }
}
