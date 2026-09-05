package com.example.driverguard.feature.auth.data

import com.example.driverguard.feature.auth.domain.AuthRepository
import com.example.driverguard.feature.auth.domain.AuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override fun getCurrentUser(): AuthUser? {
        return firebaseAuth.currentUser?.toAuthUser()
    }

    override suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<AuthUser> {
        return runCatching {
            val result = firebaseAuth
                .signInWithEmailAndPassword(
                    email.trim(),
                    password
                )
                .await()

            val firebaseUser = result.user
                ?: error("Không tìm thấy thông tin người dùng")

            firebaseUser.toAuthUser()
        }
    }

    override suspend fun registerWithEmail(
        email: String,
        password: String
    ): Result<AuthUser> {
        return runCatching {
            val result = firebaseAuth
                .createUserWithEmailAndPassword(
                    email.trim(),
                    password
                )
                .await()

            val firebaseUser = result.user
                ?: error("Lỗi! Không thể tạo tài khoản")

            firebaseUser.toAuthUser()
        }
    }

    override suspend fun loginWithGoogle(
        idToken: String
    ): Result<AuthUser> {
        return runCatching {
            val credential = GoogleAuthProvider.getCredential(
                idToken,
                null
            )

            val result = firebaseAuth
                .signInWithCredential(credential)
                .await()

            val firebaseUser = result.user
                ?: error("Lỗi! Không lấy được tài khoản Google")

            firebaseUser.toAuthUser()
        }
    }

    override suspend fun sendEmailVerification(): Result<Unit> {
        return runCatching {
            val firebaseUser = firebaseAuth.currentUser
                ?: error("Không tìm thấy người dùng đang đăng nhập")

            val email = firebaseUser.email
                ?: error("Tài khoản hiện tại không có email")

            if (firebaseUser.isEmailVerified) {
                error("Email $email đã được xác minh")
            }

            // Dùng ngôn ngữ hiện tại của ứng dụng cho nội dung email Firebase.
            firebaseAuth.useAppLanguage()

            firebaseUser
                .sendEmailVerification()
                .await()

            Unit
        }
    }

    override suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit> {
        return runCatching {
            firebaseAuth
                .sendPasswordResetEmail(email.trim())
                .await()

            Unit
        }
    }

    override suspend fun reloadCurrentUser(): Result<AuthUser?> {
        return runCatching {
            firebaseAuth.currentUser
                ?.reload()
                ?.await()

            firebaseAuth.currentUser?.toAuthUser()
        }
    }

    override fun logout() {
        firebaseAuth.signOut()
    }

    private fun FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl?.toString(),
            isEmailVerified = isEmailVerified
        )
    }
}
