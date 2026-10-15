package com.example.driverguard.feature.auth.presentation

import com.example.driverguard.feature.auth.domain.AuthUser

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    val isLoading: Boolean = false,

    val currentUser: AuthUser? = null,

    val isVerificationEmailSent: Boolean = false,
    val isPasswordResetEmailSent: Boolean = false,

    // Đồng bộ từ UserProfileRepository (Firestore drivers/{uid}.accountStatus)
    val accountStatus: String = "",
    val rejectReason: String = "",
    val isAccountStatusLoaded: Boolean = false,

    val errorMessage: String? = null
) {
    val isAuthenticated: Boolean
        get() = currentUser != null

    private val isEmailOk: Boolean
        get() = currentUser?.isEmailVerified == true

    /** Email đã xác minh VÀ admin đã duyệt (accountStatus == "active"). */
    val canEnterApp: Boolean
        get() = isEmailOk && accountStatus == "active"

    /** Bị chặn vào app: "locked" hoặc "rejected" (chung màn hình, khác nội dung). */
    val isAccountLocked: Boolean
        get() = isEmailOk && isAccountStatusLoaded && (accountStatus == "locked" || accountStatus == "rejected")

    /** Chỉ dùng để chọn nội dung màn hình bị chặn. */
    val isAccountRejected: Boolean
        get() = isEmailOk && isAccountStatusLoaded && accountStatus == "rejected"

    val isPendingApproval: Boolean
        get() = isEmailOk && isAccountStatusLoaded && accountStatus != "active" && accountStatus != "locked" && accountStatus != "rejected"
}