package com.example.driverguard.data

import com.example.driverguard.BuildConfig
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import java.net.URL
import java.util.concurrent.TimeUnit

/** Firebase Auth signs requests; Render verifies the ID token before business handlers run. */
object BusinessFunctions {
    fun command(): HttpsCallableReference {
        val functions = FirebaseFunctions.getInstance("asia-southeast1")
        val call = if (BuildConfig.FIREBASE_EMULATORS) {
            functions.getHttpsCallable("dispatchCommand")
        } else {
            check(BuildConfig.DISPATCH_API_URL.startsWith("https://")) {
                "Chưa cấu hình URL backend online. Cần dựng lại APK với dispatchApiUrl."
            }
            functions.getHttpsCallableFromUrl(URL("${BuildConfig.DISPATCH_API_URL.trimEnd('/')}/dispatchCommand"))
        }
        call.setTimeout(if (BuildConfig.FIREBASE_EMULATORS) 25L else 90L, TimeUnit.SECONDS)
        return call
    }
}
