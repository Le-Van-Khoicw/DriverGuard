package com.example.driverguard.feature.auth.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.driverguard.R
import com.example.driverguard.feature.auth.domain.AuthUser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.tasks.await

/** Mở bộ chọn tài khoản Google hoặc mở trang web đăng nhập Google chính thức. */
class GoogleAuthClient(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)
    private val firebaseAuth = FirebaseAuth.getInstance()

    suspend fun signIn(activity: Activity): Result<AuthUser> = runCatching {
        // 1. Thử dùng Credential Manager (Native Dialog)
        val nativeIdTokenResult = runCatching {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getServerClientId())
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = response.credential
            if (
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                error("Google credential format unsupported")
            }

            GoogleIdTokenCredential.createFrom(credential.data).idToken
        }

        val firebaseUser = if (nativeIdTokenResult.isSuccess) {
            val idToken = nativeIdTokenResult.getOrThrow()
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(authCredential).await()
            authResult.user ?: error("Lỗi! Không lấy được tài khoản Google")
        } else {
            // 2. Nếu Credential Manager không có tài khoản trên máy ảo (No credentials available) ->
            // Mở thẳng trang web đăng nhập/đăng ký chính thức của Google trong Chrome Custom Tab!
            val provider = OAuthProvider.newBuilder("google.com")
            provider.addCustomParameters(mapOf("prompt" to "select_account"))
            val authResult = firebaseAuth.startActivityForSignInWithProvider(activity, provider.build()).await()
            authResult.user ?: error("Lỗi! Không lấy được tài khoản Google")
        }

        firebaseUser.toAuthUser()
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

    private fun getServerClientId(): String {
        return context.getString(R.string.default_web_client_id)
    }
}
