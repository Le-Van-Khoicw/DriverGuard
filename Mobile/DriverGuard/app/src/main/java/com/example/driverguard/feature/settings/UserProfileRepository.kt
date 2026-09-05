package com.example.driverguard.feature.settings

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriverProfile(
    val displayName: String = "Tài xế DriverGuard",
    val phone: String = "",
    val email: String = "",
    val avatarUri: String? = null,
    val vehicleName: String = "VinFast VF8",
    val licensePlate: String = "51H - 888.88",
    val vehicleType: String = "Ô tô con"
)

/**
 * Quản lý thông tin hồ sơ tài xế và phương tiện tập trung.
 * Giúp HomeScreen, ProfileScreen, VehiclesScreen luôn đồng bộ dữ liệu theo thời gian thực.
 */
object UserProfileRepository {
    private val auth = FirebaseAuth.getInstance()
    private val initialName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Tài xế DriverGuard"
    private val initialEmail = auth.currentUser?.email ?: ""
    private val initialPhoto = auth.currentUser?.photoUrl?.toString()

    private val _profile = MutableStateFlow(
        DriverProfile(
            displayName = initialName,
            email = initialEmail,
            avatarUri = initialPhoto
        )
    )
    val profile: StateFlow<DriverProfile> = _profile.asStateFlow()

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

        // Cập nhật lên Firebase Auth profile
        val user = auth.currentUser
        if (user != null && displayName.isNotBlank()) {
            val request = userProfileChangeRequest {
                this.displayName = newName
            }
            user.updateProfile(request)
        }
    }

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
    }
}
