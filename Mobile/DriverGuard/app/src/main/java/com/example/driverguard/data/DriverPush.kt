package com.example.driverguard.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.driverguard.BuildConfig
import com.example.driverguard.MainActivity
import com.example.driverguard.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

object DriverPush {
    private const val CHANNEL = "driver_assignments"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun initialize(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Chuyến được giao", NotificationManager.IMPORTANCE_HIGH))
        if (!BuildConfig.FIREBASE_EMULATORS) FirebaseAuth.getInstance().addAuthStateListener { register(context) }
    }
    fun register(context: Context, refreshedToken: String? = null) {
        if (BuildConfig.FIREBASE_EMULATORS) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val token = refreshedToken ?: FirebaseMessaging.getInstance().token.await()
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@launch
                val preferences = context.getSharedPreferences("push_device_v1", Context.MODE_PRIVATE)
                val installation = preferences.getString("installation", null) ?: UUID.randomUUID().toString().also {
                    preferences.edit().putString("installation", it).commit()
                }
                FirebaseFirestore.getInstance().collection("pushDevices").document(uid).collection("devices").document(installation)
                    .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp())).await()
            }.onFailure { Log.w("DriverPush", "Device registration failed; will retry when app resumes") }
        }
    }
    @Synchronized
    fun show(context: Context, uid: String, tripId: String, version: Long) {
        // A device can switch accounts: never display another user's assignment.
        if (FirebaseAuth.getInstance().currentUser?.uid != uid || tripId.isBlank() || version < 1) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) return
        val preferences = context.getSharedPreferences("push_seen_v1", Context.MODE_PRIVATE)
        val key = "$uid:$tripId"
        if (preferences.getLong(key, 0) >= version) return
        val intent = Intent(context, MainActivity::class.java).putExtra("notificationTripId", tripId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_trip_notification)
            .setContentTitle("Bạn có chuyến được giao")
            .setContentText("Chạm để xem chuyến và xác nhận nhận chuyến.")
            .setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        manager.notify(key, 1, notification)
        // Both Firestore foreground updates and FCM use the same per-assignment deduplication key.
        preferences.edit().putLong(key, version).commit()
    }
}

class DriverMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { DriverPush.register(applicationContext, token) }
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] == "trip_assignment") DriverPush.show(applicationContext, data["driverId"].orEmpty(),
            data["tripId"].orEmpty(), data["assignmentVersion"]?.toLongOrNull() ?: 0)
    }
}
