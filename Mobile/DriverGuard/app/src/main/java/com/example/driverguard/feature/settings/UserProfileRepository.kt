package com.example.driverguard.feature.settings

import android.util.Log
import com.example.driverguard.core.device.DeviceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriverProfile(
    val displayName: String = "Tài xế DriverGuard",
    val phone: String = "",
    val email: String = "",
    val avatarUri: String? = null,
    val vehicleName: String = "",
    val licensePlate: String = "",
    val vehicleType: String = "",
    val deviceName: String = DeviceUtils.deviceName,
    val deviceCode: String = DeviceUtils.deviceCode,
    // Trạng thái duyệt tài khoản: "pending" | "rejected" | "active" | "locked"
    val accountStatus: String = "",
    val rejectReason: String = "",
    val isLoaded: Boolean = false
) {
    val isVehicleConfigured: Boolean
        get() = vehicleName.isNotBlank() && licensePlate.isNotBlank()
}

/**
 * Quản lý thông tin hồ sơ tài xế và phương tiện tập trung.
 * Lắng nghe realtime Cloud Firestore "drivers/{uid}" — mọi thay đổi từ Web (kể cả accountStatus)
 * được phản ánh ngay vào app mà không cần mở lại.
 */
object UserProfileRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _profile = MutableStateFlow(DriverProfile())
    val profile: StateFlow<DriverProfile> = _profile.asStateFlow()

    private var driverDocListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                loadUserProfile()
            } else {
                driverDocListener?.remove()
                driverDocListener = null
                _profile.value = DriverProfile()
            }
        }
        loadUserProfile()
    }

    /** Nạp thông tin từ Firebase Auth và bắt đầu lắng nghe realtime "drivers/{uid}". */
    fun loadUserProfile() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val initialName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Tài xế DriverGuard"
        val initialEmail = user.email ?: ""
        val initialPhoto = user.photoUrl?.toString()

        // Reset tạm thời khi chuyển sang tài khoản khác, tránh lẫn accountStatus của tài khoản cũ
        _profile.value = DriverProfile(
            displayName = initialName,
            email = initialEmail,
            avatarUri = initialPhoto,
            deviceName = DeviceUtils.deviceName,
            deviceCode = DeviceUtils.deviceCode,
            accountStatus = "",
            isLoaded = false
        )

        driverDocListener?.remove()

        val docRef = firestore.collection("drivers").document(uid)
        driverDocListener = docRef.addSnapshotListener { doc, error ->
            if (error != null) {
                Log.e("FIREBASE_SYNC", "Lỗi lắng nghe hồ sơ Firestore: ${error.message}")
                _profile.value = _profile.value.copy(isLoaded = true)
                return@addSnapshotListener
            }

            if (doc != null && doc.exists()) {
                _profile.value = DriverProfile(
                    displayName = doc.getString("displayName") ?: initialName,
                    phone = doc.getString("phone") ?: "",
                    email = doc.getString("email") ?: initialEmail,
                    vehicleName = doc.getString("vehicleName") ?: "",
                    licensePlate = doc.getString("licensePlate") ?: "",
                    vehicleType = doc.getString("vehicleType") ?: "",
                    avatarUri = doc.getString("avatarUri") ?: initialPhoto,
                    deviceName = doc.getString("deviceName") ?: DeviceUtils.deviceName,
                    deviceCode = doc.getString("deviceCode") ?: DeviceUtils.deviceCode,
                    accountStatus = doc.getString("accountStatus") ?: "pending",
                    rejectReason = doc.getString("rejectReason") ?: "",
                    isLoaded = true
                )
                Log.d("FIREBASE_SYNC", "Đã nhận hồ sơ tài xế (realtime): ${_profile.value.displayName}, status=${_profile.value.accountStatus}")
            } else {
                // Chưa có document -> khởi tạo mới, mặc định "pending" chờ admin duyệt
                val initialData = hashMapOf(
                    "uid" to uid,
                    "displayName" to initialName,
                    "email" to initialEmail,
                    "phone" to "",
                    "avatarUri" to (initialPhoto ?: ""),
                    "vehicleName" to "",
                    "licensePlate" to "",
                    "vehicleType" to "",
                    "deviceName" to DeviceUtils.deviceName,
                    "deviceCode" to DeviceUtils.deviceCode,
                    "accountStatus" to "pending",
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
                docRef.set(initialData, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FIREBASE_SYNC", "Đã khởi tạo hồ sơ tài xế lên Cloud Firestore: drivers/$uid (pending)")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FIREBASE_SYNC", "Lỗi khởi tạo hồ sơ lên Firestore: ${e.message}")
                    }
                // addSnapshotListener sẽ tự nhận bản ghi vừa tạo ở lần callback kế tiếp, không cần xử lý thêm
            }
        }
    }

    /** Cập nhật hồ sơ tài xế và đẩy lên Cloud Firestore (không đụng tới accountStatus). */
    fun updateProfile(
        displayName: String,
        phone: String,
        avatarUri: String? = _profile.value.avatarUri
    ) {
        val newName = displayName.ifBlank { "Tài xế DriverGuard" }
        _profile.value = _profile.value.copy(
            displayName = newName,
            phone = phone,
            avatarUri = avatarUri
        )

        val user = auth.currentUser
        if (user != null && displayName.isNotBlank()) {
            val request = userProfileChangeRequest {
                this.displayName = newName
            }
            user.updateProfile(request)
        }

        val uid = user?.uid
        if (!uid.isNullOrBlank()) {
            val data = hashMapOf(
                "uid" to uid,
                "displayName" to newName,
                "phone" to phone,
                "email" to (user.email ?: ""),
                "avatarUri" to (avatarUri ?: ""),
                "vehicleName" to _profile.value.vehicleName,
                "licensePlate" to _profile.value.licensePlate,
                "vehicleType" to _profile.value.vehicleType,
                "deviceName" to DeviceUtils.deviceName,
                "deviceCode" to DeviceUtils.deviceCode,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("drivers").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FIREBASE_SYNC", "Đã đồng bộ Hồ sơ lên Cloud Firestore thành công!")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_SYNC", "Lỗi ghi hồ sơ lên Firestore: ${e.message}")
                }
        }
    }

    /** Cập nhật thông tin phương tiện và đẩy lên Cloud Firestore. */
    fun updateVehicle(
        vehicleName: String,
        licensePlate: String,
        vehicleType: String
    ) {
        _profile.value = _profile.value.copy(
            vehicleName = vehicleName,
            licensePlate = licensePlate,
            vehicleType = vehicleType
        )

        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            val data = hashMapOf(
                "vehicleName" to vehicleName,
                "licensePlate" to licensePlate,
                "vehicleType" to vehicleType,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("drivers").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FIREBASE_SYNC", "Đã đồng bộ Thông tin xe lên Cloud Firestore thành công!")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_SYNC", "Lỗi ghi xe lên Firestore: ${e.message}")
                }
        }
    }
}