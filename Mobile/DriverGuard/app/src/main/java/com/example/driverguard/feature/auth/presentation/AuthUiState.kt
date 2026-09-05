package com.example.driverguard.feature.auth.presentation

import com.example.driverguard.feature.auth.domain.AuthUser

data class AuthUiState(
    // Dữ liệu nhập
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",

    // Trạng thái xử lý chung
    val isLoading: Boolean = false,

    // Người dùng hiện tại
    val currentUser: AuthUser? = null,

    // Trạng thái xác minh tài khoản sau khi đăng ký
    val isVerificationEmailSent: Boolean = false,

    // Trạng thái gửi email đặt lại mật khẩu
    val isPasswordResetEmailSent: Boolean = false,

    // Thông báo lỗi
    val errorMessage: String? = null
) {
    val isAuthenticated: Boolean
        get() = currentUser != null

    val canEnterApp: Boolean
        get() = currentUser?.isEmailVerified == true
}