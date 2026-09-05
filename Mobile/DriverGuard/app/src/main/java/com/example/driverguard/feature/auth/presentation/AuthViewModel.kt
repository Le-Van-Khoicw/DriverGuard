package com.example.driverguard.feature.auth.presentation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.example.driverguard.feature.auth.domain.AuthRepository
import com.example.driverguard.feature.auth.domain.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
): ViewModel(){
    private val _uiState = MutableStateFlow(
        AuthUiState(
            currentUser = authRepository.getCurrentUser()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                errorMessage = null,
                isPasswordResetEmailSent = false,
                isVerificationEmailSent = false
            )
        }
    }
    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                errorMessage = null
            )
        }
    }
    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                errorMessage = null
            )
        }
    }
    fun login() {
        val currentState = _uiState.value

        if (currentState.email.isBlank()) {
            showError("Vui lòng nhập email")
            return
        }

        if (currentState.password.isBlank()) {
            showError("Vui lòng nhập mật khẩu")
            return
        }

        setLoading(true)

        viewModelScope.launch {
            authRepository
                .loginWithEmail(
                    email = currentState.email,
                    password = currentState.password
                )
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            password = "",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getFriendlyError(exception)
                        )
                    }
                }
        }
    }
    fun loginWithGoogle(idToken: String) {
        setLoading(true)

        viewModelScope.launch {
            authRepository
                .loginWithGoogle(idToken)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getFriendlyError(exception)
                        )
                    }
                }
        }
    }

    fun onGoogleLoginSuccess(user: AuthUser) {
        _uiState.update {
            it.copy(
                isLoading = false,
                currentUser = user,
                errorMessage = null
            )
        }
    }

    fun onGoogleSignInError(exception: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = getFriendlyError(exception)
            )
        }
    }

    fun register() {
        val currentState = _uiState.value

        if (currentState.email.isBlank()) {
            showError("Vui lòng nhập email")
            return
        }

        if (currentState.password.length < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự")
            return
        }

        if (currentState.password != currentState.confirmPassword) {
            showError("Mật khẩu xác nhận không khớp")
            return
        }

        setLoading(true)

        viewModelScope.launch {
            authRepository
                .registerWithEmail(
                    email = currentState.email,
                    password = currentState.password
                )
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            password = "",
                            confirmPassword = "",
                            errorMessage = null
                        )
                    }

                    sendEmailVerification()
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getFriendlyError(exception)
                        )
                    }
                }
        }
    }
    private suspend fun sendEmailVerification() {
        authRepository
            .sendEmailVerification()
            .onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVerificationEmailSent = true,
                        errorMessage = null
                    )
                }
            }
            .onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isVerificationEmailSent = false,
                        errorMessage = getFriendlyError(exception)
                    )
                }
            }
    }
    fun resetPassword() {
        val email = _uiState.value.email

        if (email.isBlank()) {
            showError("Vui lòng nhập email")
            return
        }

        setLoading(true)

        viewModelScope.launch {
            authRepository
                .sendPasswordResetEmail(email)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPasswordResetEmailSent = true,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getFriendlyError(exception)
                        )
                    }
                }
        }
    }
    fun checkEmailVerification() {
        setLoading(true)

        viewModelScope.launch {
            authRepository
                .reloadCurrentUser()
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentUser = user,
                            errorMessage = if (user?.isEmailVerified == true) {
                                null
                            } else {
                                "Email vẫn chưa được xác minh"
                            }
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getFriendlyError(exception)
                        )
                    }
                }
        }
    }

    fun resendVerificationEmail() {
        _uiState.update {
            it.copy(
                isLoading = true,
                isVerificationEmailSent = false,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            sendEmailVerification()
        }
    }

    fun logout() {
        authRepository.logout()

        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = isLoading,
                errorMessage = null
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message
            )
        }
    }

    private fun getFriendlyError(exception: Throwable): String {
        val rawMessage = exception.message.orEmpty().lowercase()
        return when {
            exception is FirebaseTooManyRequestsException || rawMessage.contains("too many requests") ->
                "Bạn đã thao tác quá nhiều lần. Vui lòng đợi ít phút rồi thử lại."

            exception is FirebaseAuthUserCollisionException || rawMessage.contains("already in use") || rawMessage.contains("collision") ->
                "Email này đã được sử dụng cho một tài khoản khác."

            exception is FirebaseAuthInvalidCredentialsException || rawMessage.contains("invalid credential") || rawMessage.contains("wrong-password") || rawMessage.contains("invalid-email") || rawMessage.contains("badly formatted") ->
                "Email hoặc mật khẩu không chính xác."

            exception is FirebaseNetworkException || rawMessage.contains("network") || rawMessage.contains("timeout") || rawMessage.contains("unreachable") ->
                "Lỗi kết nối mạng. Vui lòng kiểm tra lại đường truyền Internet."

            rawMessage.contains("canceled") || rawMessage.contains("cancelled") || rawMessage.contains("cancel") ->
                "Bạn đã hủy quá trình đăng nhập Google."

            rawMessage.contains("no credentials available") || rawMessage.contains("no credential") ->
                "Không tìm thấy tài khoản Google nào trên thiết bị."

            rawMessage.contains("user-disabled") || rawMessage.contains("disabled") ->
                "Tài khoản của bạn đã bị khóa hoặc vô hiệu hóa."

            rawMessage.contains("user-not-found") || rawMessage.contains("no user record") ->
                "Tài khoản không tồn tại trên hệ thống."

            rawMessage.contains("password") && rawMessage.contains("least 6") ->
                "Mật khẩu phải có tối thiểu 6 ký tự."

            else -> exception.message ?: "Đã xảy ra lỗi. Vui lòng thử lại."
        }
    }
}
