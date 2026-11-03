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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

val GoogleRoadsTileSourceMini = object : OnlineTileSourceBase(
    "GoogleRoads",
    0, 20, 256, ".png",
    arrayOf(
        "https://mt0.google.com/vt/lyrs=m&hl=vi&x=",
        "https://mt1.google.com/vt/lyrs=m&hl=vi&x=",
        "https://mt2.google.com/vt/lyrs=m&hl=vi&x=",
        "https://mt3.google.com/vt/lyrs=m&hl=vi&x="
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + x + "&y=" + y + "&z=" + zoom
    }
}

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
suspend fun resolveStopLocation(context: Context, address: String, fallbackIndex: Int): GeoPoint {
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

        // Dự phòng thông minh theo địa danh có trong chuỗi địa chỉ
        val lower = address.lowercase(Locale("vi", "VN"))
        when {
            lower.contains("nha trang") || lower.contains("khánh hòa") -> GeoPoint(12.2388, 109.1967)
            lower.contains("đà nẵng") -> GeoPoint(16.0544, 108.2022)
            lower.contains("hà nội") -> GeoPoint(21.0285, 105.8542)
            lower.contains("cần thơ") -> GeoPoint(10.0452, 105.7469)
            lower.contains("bình dương") || lower.contains("thủ dầu một") -> GeoPoint(10.9805, 106.6519)
            lower.contains("đồng nai") || lower.contains("biên hòa") -> GeoPoint(10.9574, 106.8427)
            lower.contains("vũng tàu") -> GeoPoint(10.3460, 107.0843)
            lower.contains("quận 1") || lower.contains("bến thành") -> GeoPoint(10.7725, 106.6980)
            lower.contains("thủ đức") -> GeoPoint(10.8500, 106.7720)
            else -> {
                val baseLat = 10.7769 + (fallbackIndex * 0.015)
                val baseLon = 106.7009 + (fallbackIndex * 0.012)
                GeoPoint(baseLat, baseLon)
            }
        }
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
        val resolved = stops.mapIndexed { index, stop ->
            val point = resolveStopLocation(context, stop.address, index)
            Pair(stop, point)
        }
        stopCoordinates = resolved
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
                            setTileSource(GoogleRoadsTileSourceMini)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            controller.setZoom(13.5)
                            val defaultCenter = if (currentGps != null && currentGps.latitude != 0.0) {
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
                        if (currentGps != null && currentGps.latitude != 0.0 && currentGps.longitude != 0.0) {
                            val carPoint = GeoPoint(currentGps.latitude, currentGps.longitude)
                            allPoints.add(carPoint)
                            val carMarker = Marker(mapView).apply {
                                position = carPoint
                                title = "Xe của bạn"
                                snippet = "Vận tốc: ${currentGps.speedKmh?.toInt() ?: 0} km/h"
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
