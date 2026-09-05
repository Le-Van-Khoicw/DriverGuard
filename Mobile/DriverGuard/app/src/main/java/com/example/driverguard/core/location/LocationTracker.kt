package com.example.driverguard.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Looper
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

data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float = 0f,
    val address: String? = null
) {
    val coordinateDisplay: String
        get() = "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"

    val speedDisplay: String
        get() = "${speedKmh.toInt()} km/h"
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
        try {
            // Lấy vị trí nhanh gần nhất trước
            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updateLocation(it) }
            }

            // Đăng ký nhận cập nhật định kỳ mỗi 3 giây
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(2f)
                .build()

            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLocation(loc: Location) {
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val lat = loc.latitude
        val lng = loc.longitude

        // Cập nhật tọa độ trước ngay lập tức
        _currentLocation.value = GpsLocation(
            latitude = lat,
            longitude = lng,
            speedKmh = speedKmh,
            address = _currentLocation.value?.address
        )

        // Tra cứu địa chỉ ngầm (Reverse Geocoding)
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        val addr = addresses.firstOrNull()?.let {
                            listOfNotNull(it.thoroughfare, it.subAdminArea, it.adminArea).joinToString(", ")
                        }
                        if (!addr.isNullOrBlank()) {
                            _currentLocation.value = _currentLocation.value?.copy(address = addr)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()?.let {
                        listOfNotNull(it.thoroughfare, it.subAdminArea, it.adminArea).joinToString(", ")
                    }
                    if (!addr.isNullOrBlank()) {
                        _currentLocation.value = _currentLocation.value?.copy(address = addr)
                    }
                }
            } catch (_: Exception) {
                // Nếu không có mạng hoặc máy ảo không có Geocoding, giữ nguyên tọa độ
            }
        }
    }
}
