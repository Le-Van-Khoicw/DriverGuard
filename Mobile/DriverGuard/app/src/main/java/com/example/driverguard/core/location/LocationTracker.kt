package com.example.driverguard.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_FIX_AGE_MS = 15_000L

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float? = null,
    val address: String? = null,
    val accuracyMeters: Float? = null,
    val elapsedRealtimeMs: Long = SystemClock.elapsedRealtime()
) {
    val coordinateDisplay: String
        get() = "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"

    val speedDisplay: String
        get() = speedKmh?.let { "${it.roundToInt()} km/h" } ?: "—"

    val accuracyDisplay: String
        get() = accuracyMeters?.let { "Độ chính xác ±${it.roundToInt()} m" } ?: "Đã nhận vị trí"

    val isFresh: Boolean
        get() = SystemClock.elapsedRealtime() - elapsedRealtimeMs in 0 until MAX_FIX_AGE_MS
}

/**
 * LocationTracker quản lý việc lấy vị trí GPS theo thời gian thực từ FusedLocationProviderClient.
 * Cung cấp StateFlow để các màn hình lắng nghe toạ độ và tốc độ xe.
 */
class LocationTracker(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableStateFlow<GpsLocation?>(null)
    val currentLocation: StateFlow<GpsLocation?> = _currentLocation.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val expireLocation = Runnable { _currentLocation.value = null }
    private var tracking = false

    private val geocoder: Geocoder = Geocoder(context, Locale("vi", "VN"))
    private val scope = CoroutineScope(Dispatchers.IO)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                updateLocation(location)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (tracking) return
        tracking = true
        try {
            // Dùng vị trí gần nhất nếu mẫu còn mới.
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updateLocation(it) }
            }

            // Đăng ký nhận cập nhật định kỳ mỗi 3 giây
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener { Log.w("DriverGuardLocation", "Không nhận được cập nhật GPS", it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        tracking = false
        handler.removeCallbacks(expireLocation)
        _currentLocation.value = null
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLocation(loc: Location) {
        if (!tracking) return
        val fixTimeMs = loc.elapsedRealtimeNanos / 1_000_000L
        val ageMs = SystemClock.elapsedRealtime() - fixTimeMs
        if (ageMs !in 0 until MAX_FIX_AGE_MS ||
            fixTimeMs <= (_currentLocation.value?.elapsedRealtimeMs ?: Long.MIN_VALUE)) return
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else null
        val lat = loc.latitude
        val lng = loc.longitude

        val previous = _currentLocation.value
        val samePoint = previous?.latitude == lat && previous?.longitude == lng
        handler.removeCallbacks(expireLocation)
        handler.postDelayed(expireLocation, MAX_FIX_AGE_MS - ageMs)

        // Cập nhật tọa độ ngay, địa chỉ có thể đến sau.
        val fix = GpsLocation(
            latitude = lat,
            longitude = lng,
            speedKmh = speedKmh,
            address = previous?.address?.takeIf { samePoint },
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
            elapsedRealtimeMs = fixTimeMs
        )

        _currentLocation.value = fix
        if (samePoint && previous?.address != null) return
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addr = addresses.firstOrNull()?.let {
                            listOfNotNull(it.thoroughfare, it.subAdminArea, it.adminArea).joinToString(", ")
                        }
                        if (!addr.isNullOrBlank()) {
                            _currentLocation.compareAndSet(fix, fix.copy(address = addr))
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()?.let {
                        listOfNotNull(it.thoroughfare, it.subAdminArea, it.adminArea).joinToString(", ")
                    }
                    if (!addr.isNullOrBlank()) {
                        _currentLocation.compareAndSet(fix, fix.copy(address = addr))
                    }
                }
            } catch (_: Exception) {
                // Nếu không có mạng hoặc máy ảo không có Geocoding, giữ nguyên tọa độ
            }
        }
    }
}
