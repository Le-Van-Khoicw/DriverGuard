package com.example.driverguard.feature.settings

import android.util.Log
import com.example.driverguard.core.device.DeviceUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriverProfile(
    val displayName: String = "Tài xế DriverGuard",
    val phone: String = "",
    val email: String = "",
    val avatarUri: String? = null,
    val vehicleName: String = "",       // Mặc định để trống để người dùng nhập lần đầu
    val licensePlate: String = "",
    val vehicleType: String = "",
    val deviceName: String = DeviceUtils.deviceName,
    val deviceCode: String = DeviceUtils.deviceCode
) {
    val isVehicleConfigured: Boolean
        get() = vehicleName.isNotBlank() && licensePlate.isNotBlank()
}

/**
 * Quản lý thông tin hồ sơ tài xế và phương tiện tập trung.
 * Tự động đồng bộ 2 chiều với Cloud Firestore và Firebase Auth theo thời gian thực.
 */
object UserProfileRepository {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _profile = MutableStateFlow(DriverProfile())
    val profile: StateFlow<DriverProfile> = _profile.asStateFlow()

    init {
        // Tự động lắng nghe khi người dùng đăng nhập để nạp và đồng bộ hồ sơ lên Firestore
        auth.addAuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                loadUserProfile()
            }
        }
        loadUserProfile()
    }

    /** Tải thông tin tài xế từ Firebase Auth & Cloud Firestore. Nếu chưa có trên Firestore -> Tự động khởi tạo ngay! */
    fun loadUserProfile() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val initialName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Tài xế DriverGuard"
        val initialEmail = user.email ?: ""
        val initialPhoto = user.photoUrl?.toString()

        _profile.value = _profile.value.copy(
            displayName = initialName,
            email = initialEmail,
            avatarUri = initialPhoto,
            deviceName = DeviceUtils.deviceName,
            deviceCode = DeviceUtils.deviceCode
        )

        // Lấy và đồng bộ thông tin với Firestore collection "drivers/{uid}"
        val docRef = firestore.collection("drivers").document(uid)
        docRef.get()
            .addOnSuccessListener { doc ->
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
                        deviceCode = doc.getString("deviceCode") ?: DeviceUtils.deviceCode
                    )
                    Log.d("FIREBASE_SYNC", "Đã tải hồ sơ tài xế từ Firestore thành công: ${_profile.value.displayName}")
                } else {
                    // Chưa có document trên Firestore -> Tạo ngay document cho tài xế với thông tin máy thật
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
                        "createdAt" to System.currentTimeMillis(),
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docRef.set(initialData, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("FIREBASE_SYNC", "Đã tự động khởi tạo hồ sơ tài xế lên Cloud Firestore: drivers/$uid")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FIREBASE_SYNC", "Lỗi khởi tạo hồ sơ lên Firestore: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FIREBASE_SYNC", "Lỗi đọc hồ sơ từ Firestore: ${e.message}")
            }
    }

    /** Cập nhật hồ sơ tài xế và đẩy lên Cloud Firestore */
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

        // 1. Cập nhật Firebase Auth
        val user = auth.currentUser
        if (user != null && displayName.isNotBlank()) {
            val request = userProfileChangeRequest {
                this.displayName = newName
            }
            user.updateProfile(request)
        }

        // 2. Cập nhật Firestore document "drivers/{uid}"
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

    /** Cập nhật thông tin phương tiện và đẩy lên Cloud Firestore */
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
