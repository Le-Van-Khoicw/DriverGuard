package com.example.driverguard.feature.auth.domain

interface AuthRepository{
    fun getCurrentUser(): AuthUser?
    suspend fun loginWithEmail(
        email: String,
        password: String
    ): Result<AuthUser>

    suspend fun registerWithEmail(
        email: String,
        password: String
    ): Result<AuthUser>

    suspend fun loginWithGoogle(
        idToken: String
    ): Result<AuthUser>

    suspend fun sendEmailVerification(): Result<Unit>

    suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit>

    suspend fun reloadCurrentUser(): Result<AuthUser?>

    fun logout()
}
