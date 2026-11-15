package com.example.driverguard.feature.dispatch

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.driverguard.core.location.GpsLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/** Opens Google Navigation inside DriverGuard. Requires API key and Cloud billing. */
fun openInAppNavigation(context: Context, address: String) {
    if (address.isNotBlank()) {
        com.example.driverguard.core.navigation.NavigationDestination.address = address.trim()
    }
}
/**
 * Geocode địa chỉ văn bản thành toạ độ (vĩ độ, kinh độ).
 * Hỗ trợ nhận diện toạ độ từ link Google Maps, chuỗi toạ độ hoặc địa danh.
 */
suspend fun resolveStopLocation(context: Context, address: String): GeoPoint? {
    return withContext(Dispatchers.IO) {
        // 1. Tự động trích xuất toạ độ nếu địa chỉ chứa toạ độ hoặc link Google Maps (VD: q=10.7769,106.7009)
        val coordRegex = Regex("""(-?\d+\.\d+),\s*(-?\d+\.\d+)""")
        val match = coordRegex.find(address)
        if (match != null) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return@withContext GeoPoint(lat, lng)
            }
        }

        try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocationName(address, 1)
            if (!results.isNullOrEmpty()) {
                val loc = results[0]
                return@withContext GeoPoint(loc.latitude, loc.longitude)
            }
        } catch (_: Exception) {
            // Geocoder service có thể không sẵn sàng trên một số máy ảo
        }

        null
    }
}

/**
 * Bản đồ mini tích hợp trong app hiển thị:
 * - Vị trí hiện tại của xe (từ GPS)
 * - Các điểm dừng (Nhận hàng / Giao hàng)
 * - Tự động zoom vừa vặn các điểm
 */
@Composable
fun TripMiniMap(
    stops: List<TripStop>,
    currentGps: GpsLocation?,
    activeStop: TripStop?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stopCoordinates by remember(stops) { mutableStateOf<List<Pair<TripStop, GeoPoint>>>(emptyList()) }

    // Phân giải toạ độ ngầm các điểm dừng
    LaunchedEffect(stops) {
        stopCoordinates = stops.mapNotNull { stop ->
            resolveStopLocation(context, stop.address)?.let { stop to it }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🗺️ Bản đồ lộ trình mini (OpenStreetMap)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (activeStop != null) {
                    FilledTonalButton(
                        onClick = { openInAppNavigation(context, activeStop.address) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Dẫn đường", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE5E3DF))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        Configuration.getInstance().userAgentValue = "DriverGuard/1.0 (Android; Graduate Thesis Project)"
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            controller.setZoom(13.5)
                            val defaultCenter = if (currentGps != null) {
                                GeoPoint(currentGps.latitude, currentGps.longitude)
                            } else {
                                GeoPoint(10.7769, 106.7009)
                            }
                            controller.setCenter(defaultCenter)
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()
                        val allPoints = mutableListOf<GeoPoint>()

                        // 1. Marker vị trí xe hiện tại
                        if (currentGps != null) {
                            val carPoint = GeoPoint(currentGps.latitude, currentGps.longitude)
                            allPoints.add(carPoint)
                            val carMarker = Marker(mapView).apply {
                                position = carPoint
                                title = "Xe của bạn"
                                snippet = "Vận tốc: ${currentGps.speedDisplay}"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(carMarker)
                        }

                        // 2. Markers các điểm dừng
                        stopCoordinates.forEachIndexed { idx, (stop, point) ->
                            allPoints.add(point)
                            val stopMarker = Marker(mapView).apply {
                                position = point
                                title = "${idx + 1}. ${if (stop.kind == "pickup") "Nhận hàng" else "Giao hàng"} (${dispatchLabel(stop.status)})"
                                snippet = stop.address
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            }
                            mapView.overlays.add(stopMarker)
                        }

                        // 3. Zoom vừa vặn tất cả các điểm
                        if (allPoints.isNotEmpty()) {
                            if (allPoints.size == 1) {
                                mapView.controller.setCenter(allPoints[0])
                                mapView.controller.setZoom(15.0)
                            } else {
                                try {
                                    val box = BoundingBox.fromGeoPoints(allPoints)
                                    mapView.zoomToBoundingBox(box.increaseByScale(1.3f), true)
                                } catch (_: Exception) {
                                    mapView.controller.setCenter(allPoints[0])
                                }
                            }
                        }
                        mapView.overlays.add(CopyrightOverlay(mapView.context))
                        mapView.invalidate()
                    }
                )
            }

            if (activeStop != null) {
                Text(
                    "📍 Điểm tiếp theo: ${activeStop.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
