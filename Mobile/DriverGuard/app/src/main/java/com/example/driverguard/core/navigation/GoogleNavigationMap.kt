package com.example.driverguard.core.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.driverguard.feature.dispatch.GoogleRoadsTileSourceMini
import com.example.driverguard.feature.dispatch.resolveStopLocation
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** In-app navigation preview using OpenStreetMap + Google Maps Intent. */
@Composable
fun GoogleNavigationMap(address: String?, modifier: Modifier = Modifier, stateKey: String = "monitoringNavigation") {
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }

    var osmDestinationPoint by remember(address) { mutableStateOf<GeoPoint?>(null) }

    LaunchedEffect(address) {
        if (!address.isNullOrBlank()) {
            osmDestinationPoint = resolveStopLocation(context, address, 0)
        }
    }

    val openGoogleMapsVoiceGuidance: () -> Unit = {
        val target = address?.trim().orEmpty()
        if (target.isNotBlank()) {
            try {
                val uri = Uri.parse("google.navigation:q=" + Uri.encode(target))
                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                context.startActivity(mapIntent)
            } catch (_: Exception) {
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(target))
                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = "DriverGuard/1.0 (Android; Graduate Thesis Project)"
                    MapView(ctx).apply {
                        setTileSource(GoogleRoadsTileSourceMini)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(15.0)
                        val center = osmDestinationPoint ?: GeoPoint(10.7769, 106.7009)
                        controller.setCenter(center)
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()
                    osmDestinationPoint?.let { point ->
                        val marker = Marker(mapView).apply {
                            position = point
                            title = "Điểm đến"
                            snippet = address
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mapView.overlays.add(marker)
                        mapView.controller.setCenter(point)
                    }
                    mapView.invalidate()
                }
            )
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (!address.isNullOrBlank()) {
                    Text(
                        text = "Điểm đến: $address",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !address.isNullOrBlank(),
                    onClick = {
                        if (!permitted) {
                            permissions.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        openGoogleMapsVoiceGuidance()
                    }
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bắt đầu dẫn đường (Google Maps giọng nói)")
                }
            }
        }
    }
}
