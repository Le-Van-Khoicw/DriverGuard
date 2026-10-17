package com.example.driverguard

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

class DriverGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_EMULATORS) {
            // FirebaseInitProvider is disabled for this build: configure local services
            // before any repository can register an auth listener or read real data.
            val resourceOptions = checkNotNull(FirebaseOptions.fromResource(this))
            FirebaseApp.initializeApp(this, FirebaseOptions.Builder(resourceOptions)
                .setProjectId("demo-driverguard").setStorageBucket("demo-driverguard.appspot.com").build())
            FirebaseAuth.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099)
            FirebaseFirestore.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080)
            FirebaseFunctions.getInstance("asia-southeast1").useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 5001)
            com.google.firebase.storage.FirebaseStorage.getInstance().useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9199)
        }
    }
}
