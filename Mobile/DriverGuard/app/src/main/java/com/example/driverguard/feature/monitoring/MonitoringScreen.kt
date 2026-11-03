package com.example.driverguard.feature.auth.monitoring

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder
import com.example.driverguard.core.location.GpsLocation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import com.example.driverguard.feature.dispatch.resolveStopLocation
import com.example.driverguard.feature.dispatch.AssignedTrip
import com.example.driverguard.feature.dispatch.TripStop
import com.example.driverguard.feature.dispatch.openInAppNavigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.material.icons.outlined.Place
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.driverguard.core.location.LocationTracker
import com.example.driverguard.core.pip.PipManager
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font
import com.example.driverguard.feature.monitoring.TripSummaryDialog
import java.util.concurrent.Executors

@Composable
fun MonitoringScreen(
    viewModel: MonitoringViewModel,
    activeTrip: AssignedTrip? = null,
    onRecordStop: ((TripStop, String, List<Double>, String, String) -> Unit)? = null,
    onFinishTrip: ((String) -> Unit)? = null
) {
    var editingActiveStop by remember { mutableStateOf<TripStop?>(null) }
    var showFinishActiveTripDialog by remember { mutableStateOf(false) }
    val c = MaterialTheme.c
    val font = MaterialTheme.font
    val state by viewModel.uiState.collectAsState()
    var confirmCompletion by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.pauseMonitoring() }
    }
    val inPip by PipManager.isInPipMode.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Khởi tạo Location Tracker
    val locationTracker = remember { LocationTracker(context) }
    val currentGps by locationTracker.currentLocation.collectAsState()

    // Cập nhật vị trí GPS vào ViewModel
    LaunchedEffect(currentGps) {
        viewModel.onLocationUpdated(currentGps)
    }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: hasLocationPermission
        if (hasCameraPermission) {
            viewModel.startMonitoring()
        } else {
            viewModel.onCameraError("Cần cấp quyền Camera để giám sát")
        }
    }

    val isRunning = state.status == MonitoringStatus.CALIBRATING ||
            state.status == MonitoringStatus.MONITORING ||
            state.status == MonitoringStatus.DROWSY

    // Đồng bộ trạng thái theo dõi cho PipManager (để khi bấm Home tự động vào PiP)
    LaunchedEffect(isRunning) {
        PipManager.isMonitoringActive = isRunning
    }

    // Bật/tắt GPS Tracker khi có quyền vị trí (cập nhật vị trí thực tế ngay cả khi chưa bấm bắt đầu)
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            locationTracker.startTracking()
        }
        onDispose {
            locationTracker.stopTracking()
        }
    }

    // Giữ màn hình luôn sáng khi đang trong trạng thái giám sát
    DisposableEffect(isRunning) {
        if (isRunning) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hú còi và rung bần bật khi tài xế ngủ gật (DROWSY)
    LaunchedEffect(state.status) {
        if (state.status == MonitoringStatus.DROWSY) {
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            var toneGenerator: android.media.ToneGenerator? = null
            try {
                toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                while (true) {
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                        vibratorManager?.defaultVibrator?.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    kotlinx.coroutines.delay(1000)
                }
            } catch (_: Exception) {
            } finally {
                toneGenerator?.release()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 🌟 GIAO DIỆN CỬA SỔ NỔI PICTURE-IN-PICTURE (Khi chạy đè trên Grab/Shopee)
    // ─────────────────────────────────────────────────────────────────────────
    if (inPip) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isRunning && hasCameraPermission) {
                CameraPreview(
                    onEar = viewModel::onEarDetected,
                    onError = viewModel::onCameraError,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Badge nhỏ báo EAR
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (state.status == MonitoringStatus.DROWSY) c.danger else c.safe)
                )
                Text(
                    text = "EAR: ${state.ear?.let { "%.2f".format(it) } ?: "--"}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = font.bold
                )
            }

            // Báo động đỏ toàn màn hình PiP khi buồn ngủ
            if (state.status == MonitoringStatus.DROWSY) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(c.danger.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🚨 BUỒN NGỦ!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = font.black
                    )
                }
            }
        }
        return
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 🌟 GIAO DIỆN MÀN HÌNH ĐẦY ĐỦ (FULL DASHBOARD)
    // ─────────────────────────────────────────────────────────────────────────
    val statusColor = when (state.status) {
        MonitoringStatus.CALIBRATING -> c.primary
        MonitoringStatus.MONITORING  -> c.safe
        MonitoringStatus.DROWSY      -> c.danger
        else                         -> c.textMuted
    }
    val statusBgColor = when (state.status) {
        MonitoringStatus.CALIBRATING -> c.primaryBg
        MonitoringStatus.MONITORING  -> c.safeBg
        MonitoringStatus.DROWSY      -> c.dangerBg
        else                         -> c.surface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Tiêu đề ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Giám sát tài xế",
                color = c.text,
                fontSize = font.xxl,
                fontWeight = font.bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    val m = state.drivingDurationSec / 60
                    val s = state.drivingDurationSec % 60
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.primaryBg)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Timer, contentDescription = null, tint = c.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "%02d:%02d".format(m, s),
                                color = c.primary,
                                fontSize = font.xs,
                                fontWeight = font.bold
                            )
                        }
                    }
                }

                // Badge trạng thái nhỏ gọn
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBgColor)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusLabel(state.status),
                        color = statusColor,
                        fontSize = font.xs,
                        fontWeight = font.bold
                    )
                }
            }
        }

        // ── CẢNH BÁO NGUY HIỂM CAO / LÁI XE LÂU CẦN NGHỈ NGƠI ──
        if (state.isCriticalRestRequired) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.dangerBg),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, c.danger, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.danger, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "CẢNH BÁO: TÀI XẾ CẦN NGHỈ NGƠI!",
                            color = c.danger,
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                        Text(
                            text = state.criticalRestReason,
                            color = c.dangerText,
                            fontSize = font.xs,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ── KHUNG BẢN ĐỒ DẪN ĐƯỜNG NATIVE OPENSTREETMAP (CON ĐƯỜNG 1 - CHUẨN GRAB) ──
        var showCameraCheckModal by remember { mutableStateOf(false) }
        val nextStop = activeTrip?.stops?.firstOrNull { it.status == "pending" }
        val targetAddress = nextStop?.address
        val currentGps = state.gpsLocation

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(460.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. BẢN ĐỒ NATIVE OPENSTREETMAP (Sạch 100%, có xe 🚗 và lộ trình nối các kho)
                NativeMonitoringMapView(
                    stops = activeTrip?.stops ?: emptyList(),
                    activeStop = nextStop,
                    currentGps = currentGps,
                    onOpenVoiceNavigation = if (targetAddress.isNullOrBlank()) null else {
                        {
                            try {
                                val uri = android.net.Uri.parse("google.navigation:q=" + android.net.Uri.encode(targetAddress))
                                val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                context.startActivity(mapIntent)
                            } catch (_: Exception) {
                                val browserUri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + android.net.Uri.encode(targetAddress))
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, browserUri))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 2. CHIP ĐIỂM TIẾP THEO (Góc trên bên trái bản đồ)
                Surface(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart),
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Navigation,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (targetAddress != null) {
                                val shortAddr = if (targetAddress.length > 25) targetAddress.take(23) + "…" else targetAddress
                                "Tới: $shortAddr"
                            } else {
                                "Lộ trình dẫn đường"
                            },
                            color = Color.White,
                            fontSize = font.xs,
                            fontWeight = font.bold
                        )
                    }
                }

                // 3. NÚT ĐÈN BÁO AI LIVE + CHẠM ĐỂ XEM GÓC SOI CAMERA (Góc trên bên phải)

                Surface(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 72.dp)
                        .clickable { showCameraCheckModal = true },
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.status == MonitoringStatus.DROWSY) c.danger else if (isRunning) c.safe else Color.Gray)
                        )
                        Text(
                            text = if (state.ear != null) "AI: ${"%.2f".format(state.ear)}" else if (isRunning) "AI LIVE" else "AI CHỜ",
                            color = Color.White,
                            fontSize = font.xs,
                            fontWeight = font.bold
                        )
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = "Kiểm tra góc soi",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // 5. CAMERA AI CHẠY NGẦM 100% (ẨN HOÀN TOÀN KHỎI GIAO DIỆN BẢN ĐỒ)
                if (isRunning && hasCameraPermission && !showCameraCheckModal) {
                    Box(
                        modifier = Modifier
                            .size(1.dp)
                            .alpha(0.001f)
                    ) {
                        CameraPreview(
                            onEar = viewModel::onEarDetected,
                            onError = viewModel::onCameraError,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 6. CẢNH BÁO BUỒN NGỦ ĐÈ LÊN TOÀN BẢN ĐỒ (CHỚP ĐỎ TOÀN BỘ KHI DROWSY)
                if (state.status == MonitoringStatus.DROWSY) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(c.danger.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "🚨 PHÁT HIỆN BUỒN NGỦ!",
                                color = Color.White,
                                fontSize = font.xl,
                                fontWeight = font.black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "MẮT ĐÃ NHẮM KÉO DÀI!\nHÃY TẮP XE VÀ NGHỈ NGƠI NGAY LẬP TỨC!",
                                color = Color.White,
                                fontSize = font.sm,
                                fontWeight = font.bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // ── MODAL KIỂM TRA GÓC SOI CAMERA KHI TÀI XẾ CẦN CĂN CHỈNH ──
        if (showCameraCheckModal) {
            AlertDialog(
                onDismissRequest = { showCameraCheckModal = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = c.primary)
                        Text("Kiểm tra góc camera AI", fontSize = font.md, fontWeight = font.bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                        ) {
                            if (hasCameraPermission) {
                                CameraPreview(
                                    onEar = viewModel::onEarDetected,
                                    onError = viewModel::onCameraError,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            "Căn chỉnh giá đỡ sao cho khuôn mặt nằm rõ trong khung hình để AI nhận diện chớp mắt chính xác nhất.",
                            fontSize = font.xs,
                            color = c.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showCameraCheckModal = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Đã căn chỉnh xong (Ẩn camera)", fontSize = font.sm, fontWeight = font.bold)
                    }
                }
            )
        }

        // ── Thanh tiến trình hiệu chỉnh EAR (5 giây đầu khi bắt đầu) ──
        if (state.status == MonitoringStatus.CALIBRATING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = c.primaryBg)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🎯 Đang hiệu chỉnh khuôn mặt: còn ${state.calibrationSecondsLeft}s",
                            color = c.primary,
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                        Text(
                            "${(state.calibrationProgress * 100).toInt()}%",
                            color = c.primary,
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { state.calibrationProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = c.primary,
                        trackColor = c.card
                    )
                    Text(
                        "Vui lòng nhìn thẳng vào camera và mở mắt tự nhiên để AI tính toán ngưỡng EAR",
                        color = c.primaryText,
                        fontSize = font.xs
                    )
                }
            }
        }

        // ── CẢNH BÁO MẤT KHUÔN MẶT TRONG LÚC ĐANG LÁI XE ──
        if (isRunning && state.status == MonitoringStatus.MONITORING && state.ear == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCameraCheckModal = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                border = BorderStroke(1.2.dp, Color(0xFFFFC107))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Outlined.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "⚠️ KHÔNG TÌM THẤY KHUÔN MẶT",
                            color = Color(0xFF856404),
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                        Text(
                            "Nhìn thẳng kính lái hoặc chạm vào đây để căn góc camera.",
                            color = Color(0xFF856404),
                            fontSize = font.xs
                        )
                    }
                    Text(
                        "Căn góc ↗",
                        color = Color(0xFF856404),
                        fontSize = font.xs,
                        fontWeight = font.bold
                    )
                }
            }
        }

        // ── HUD HÀNH TRÌNH ĐANG LÁI XE (CHUẨN HÓA TYPOGRAPHY) ──
        if (activeTrip != null) {
            val shortCode = if (activeTrip.id.length >= 6) activeTrip.id.takeLast(6).uppercase() else activeTrip.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (nextStop?.kind == "pickup") Color(0xFFD4EDDA) else Color(0xFFFFEBEA),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (nextStop?.kind == "pickup") "ĐIỂM NHẬN HÀNG" else "ĐIỂM GIAO HÀNG",
                                color = if (nextStop?.kind == "pickup") Color(0xFF155724) else Color(0xFF900C3F),
                                fontSize = font.xs,
                                fontWeight = font.bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text("#TRIP-$shortCode", fontSize = font.xs, color = c.textMuted, fontWeight = font.medium)
                    }

                    if (nextStop != null) {
                        Text(
                            text = nextStop.address,
                            fontSize = font.md,
                            fontWeight = font.bold,
                            color = c.text,
                            lineHeight = 22.sp
                        )
                        Text(
                            text = "Liên hệ: ${nextStop.contact} · ${nextStop.phone}",
                            fontSize = font.sm,
                            color = c.textMuted
                        )
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { editingActiveStop = nextStop },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Ghi kết quả",
                                    fontSize = font.sm,
                                    fontWeight = font.bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.simulateArriveAtStop(nextStop.address)
                                    editingActiveStop = nextStop
                                },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Outlined.Place, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "📍 Đã tới kho",
                                    fontSize = font.xs,
                                    fontWeight = font.bold,
                                    maxLines = 1
                                )
                            }
                        }
                        TextButton(
                            onClick = { showFinishActiveTripDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("🏁 Hoàn thành toàn bộ chuyến", fontSize = font.xs, color = c.primary, fontWeight = font.bold)
                        }
                    } else {
                        Text(
                            "✅ Đã hoàn thành tất cả các điểm dừng!",
                            fontSize = font.base,
                            fontWeight = font.bold,
                            color = c.safe
                        )
                        Button(
                            onClick = { showFinishActiveTripDialog = true },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Xác nhận hoàn thành chuyến đi", fontSize = font.base, fontWeight = font.bold)
                        }
                    }
                }
            }
        }

        // ── BẢNG ĐIỀU KHIỂN CHỈ SỐ AI (4 GRID CARDS CHUẨN TYPOGRAPHY M3) ──
        val currentThreshold = state.earThreshold ?: 0.20
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Visibility,
                title = "Trạng thái mắt",
                value = when {
                    state.ear == null -> "--"
                    state.ear!! < currentThreshold -> "Nhắm mắt"
                    else -> "Tỉnh táo"
                },
                subtitle = state.ear?.let { "EAR ${"%.2f".format(it)} (chuẩn ${"%.2f".format(currentThreshold)})" } ?: "Đang quan sát",
                accentColor = when {
                    state.ear == null -> c.textMuted
                    state.ear!! < currentThreshold -> c.danger
                    else -> c.safe
                }
            )
            MetricCard(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.toggleSimulateDriving() },
                icon = Icons.Outlined.Speed,
                title = if (viewModel.isSimulatingDriving) "Vận tốc (🧪 Demo)" else "Vận tốc xe",
                value = state.gpsLocation?.speedDisplay ?: "0 km/h",
                subtitle = if (viewModel.isSimulatingDriving) "Chạm để tắt test" else "Chạm để test tốc độ",
                accentColor = if (viewModel.isSimulatingDriving) c.safe else c.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.NotificationsActive,
                title = "Số cảnh báo",
                value = "${state.warningCount} lần",
                subtitle = "Sự kiện nhắm kéo dài",
                accentColor = if (state.warningCount > 0) c.danger else c.safe
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timer,
                title = "Thời gian lái",
                value = "%02d:%02d".format(state.drivingDurationSec / 60, state.drivingDurationSec % 60),
                subtitle = if (isRunning) "Phiên đang chạy" else "Chưa bắt đầu",
                accentColor = c.primary
            )
        }

        // ── Nút Bắt đầu / Dừng giám sát & Cửa sổ nổi ──
        Spacer(Modifier.height(4.dp))

        if (!isRunning) {
            Button(
                enabled = !state.isRestoring && !state.isCompleting && state.persistenceError == null,
                onClick = {
                    if (hasCameraPermission && hasLocationPermission) {
                        viewModel.startMonitoring()
                    } else {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.primary,
                    contentColor = c.textOnColor
                )
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.isRestoring) "Đang khôi phục phiên…" else if (state.hasActiveTrip) "Tiếp tục giám sát" else "Bắt đầu giám sát",
                    fontSize = font.base,
                    fontWeight = font.semibold
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = viewModel::pauseMonitoring,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.dangerBg,
                        contentColor = c.danger
                    )
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Tạm dừng",
                        fontSize = font.base,
                        fontWeight = font.semibold
                    )
                }

                Button(
                    onClick = { PipManager.enterPipMode(activity) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.primaryBg,
                        contentColor = c.primary
                    )
                ) {
                    Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Cửa sổ nổi",
                        fontSize = font.base,
                        fontWeight = font.semibold
                    )
                }
            }


        }

        // ── DIALOG BÁO CÁO AI TỔNG KẾT SAU CHUYẾN ĐI ──
        if (state.hasActiveTrip) {
            Button(
                onClick = { confirmCompletion = true },
                enabled = !state.isCompleting && !state.isRestoring,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (state.isCompleting) "Đang lưu phiên…" else "Kết thúc phiên giám sát")
            }
        }
        state.persistenceError?.let {
            Text("Chưa lưu được trên máy: $it", color = c.danger, fontSize = font.sm)
            TextButton(onClick = viewModel::retryPersistence) { Text("Thử lưu / khôi phục lại") }
        }
        if (confirmCompletion && state.hasActiveTrip) {
            AlertDialog(
                onDismissRequest = { confirmCompletion = false },
                title = { Text("Kết thúc phiên giám sát?") },
                text = { Text("Giám sát sẽ dừng. Báo cáo được tính và lưu trên máy ngay cả khi không có mạng.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmCompletion = false
                        if (activeTrip != null) {
                            onFinishTrip?.invoke("Hoàn thành từ màn hình giám sát")
                        } else {
                            viewModel.completeTrip()
                        }
                    }) { Text("Hoàn thành") }
                },
                dismissButton = { TextButton(onClick = { confirmCompletion = false }) { Text("Tiếp tục giám sát") } }
            )
        }
        if (state.tripSummary != null) {
            TripSummaryDialog(
                summary = state.tripSummary!!,
                onDismiss = viewModel::dismissTripSummary
            )
        }

        if (editingActiveStop != null && activeTrip != null) {
            val stop = editingActiveStop!!
            var outcome by remember(stop.id) { mutableStateOf("full") }
            var quantities by remember(stop.id) { mutableStateOf(stop.items.map { it.quantity.toString() }) }
            var contact by remember(stop.id) { mutableStateOf(stop.contact) }
            var note by remember(stop.id) { mutableStateOf("") }
            val numbers = quantities.map { it.toDoubleOrNull() }
            val pickup = activeTrip.stops.find { it.kind == "pickup" && it.id.substringBefore(':') == stop.id.substringBefore(':') }
            val maximum = stop.items.mapIndexed { i, item -> if (stop.kind == "delivery") pickup?.quantities?.getOrNull(i) ?: 0.0 else item.quantity }
            val measuredUnits = setOf("kg", "tấn", "tan", "lít", "lit", "l", "m", "m²", "m2", "m³", "m3")
            val valid = numbers.all { it != null && it.isFinite() && it >= 0 && it <= 1_000_000_000 } && numbers.indices.all {
                val quantity = numbers[it] ?: return@all false
                quantity <= maximum[it] && (stop.items[it].unit.trim().lowercase(java.util.Locale.ROOT) in measuredUnits || quantity % 1.0 == 0.0)
            }
            val full = numbers.indices.all { numbers[it] == stop.items[it].quantity }
            val zero = numbers.all { it == 0.0 }
            val consistent = when (outcome) { "full" -> full; "failed" -> zero; else -> !full && !zero }

            AlertDialog(
                onDismissRequest = { editingActiveStop = null },
                title = { Text("Kết quả " + (if (stop.kind == "pickup") "nhận" else "giao") + " hàng") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf("full" to "Đủ", "partial" to "Thiếu", "failed" to "Thất bại").forEach { (key, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = outcome == key, onClick = { outcome = key })
                                    Text(label)
                                }
                            }
                        }
                        stop.items.forEachIndexed { i, item ->
                            OutlinedTextField(
                                value = quantities[i],
                                onValueChange = { v -> quantities = quantities.mapIndexed { j, old -> if (i == j) v else old } },
                                label = { Text("${item.name} (${item.unit}) · Tối đa: ${maximum[i]}") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = contact,
                            onValueChange = { contact = it },
                            label = { Text("Người giao/nhận thực tế") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (outcome != "full") {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Lý do thiếu / thất bại") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = valid && consistent && contact.isNotBlank() && (outcome == "full" || note.isNotBlank()),
                        onClick = {
                            onRecordStop?.invoke(stop, outcome, numbers.map { it!! }, contact, note)
                            editingActiveStop = null
                        }
                    ) { Text("Lưu kết quả") }
                },
                dismissButton = {
                    TextButton(onClick = { editingActiveStop = null }) { Text("Đóng") }
                }
            )
        }

        if (showFinishActiveTripDialog && activeTrip != null) {
            var reason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showFinishActiveTripDialog = false },
                title = { Text("Hoàn thành chuyến đi") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Xác nhận hoàn thành chuyến đi này và đóng phiên giám sát.")
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text("Ghi chú chuyến đi (không bắt buộc)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onFinishTrip?.invoke(reason)
                        showFinishActiveTripDialog = false
                    }) { Text("Xác nhận") }
                },
                dismissButton = {
                    TextButton(onClick = { showFinishActiveTripDialog = false }) { Text("Đóng") }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Card Thống Kê Chỉ Số
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = c.textMuted, fontSize = font.xs, fontWeight = font.medium)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = value,
                color = accentColor,
                fontSize = font.xl,
                fontWeight = font.bold
            )

            Text(
                text = subtitle,
                color = c.textSubtle,
                fontSize = font.xs
            )
        }
    }
}

@Composable
private fun CameraPreview(onEar: (Float?) -> Unit, onError: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                val analyzer = FaceLandmarkerAnalyzer(context, onEar, onError)
                analysis.setAnalyzer(executor, analyzer)
                val cameraSelector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> throw IllegalStateException("Không tìm thấy camera nào trên thiết bị (Hãy kiểm tra cài đặt máy ảo)")
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, cameraSelector, preview, analysis)
            } catch (error: Exception) {
                onError(error.message ?: "Không mở được camera")
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private fun statusLabel(status: MonitoringStatus): String = when (status) {
    MonitoringStatus.IDLE        -> "CHƯA BẮT ĐẦU"
    MonitoringStatus.CALIBRATING -> "ĐANG HIỆU CHỈNH"
    MonitoringStatus.MONITORING  -> "ĐANG GIÁM SÁT"
    MonitoringStatus.DROWSY      -> "MẮT NHẮM KÉO DÀI"
    MonitoringStatus.PAUSED      -> "ĐÃ TẠM DỪNG"
    MonitoringStatus.STOPPED     -> "ĐÃ DỪNG"
}

private val GoogleRoadsTileSource = object : OnlineTileSourceBase(
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

private fun createCarMarkerDrawable(context: android.content.Context): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val size = (38 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Outer navigation pulse glow
    paint.color = android.graphics.Color.argb(60, 26, 115, 232)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // White rim
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2.5f * density, paint)

    // Blue core
    paint.color = android.graphics.Color.parseColor("#1A73E8")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5f * density, paint)

    // Center white dot
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, 3.5f * density, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private fun createStopMarkerDrawable(context: android.content.Context, number: Int, isPickup: Boolean): android.graphics.drawable.Drawable {
    val density = context.resources.displayMetrics.density
    val width = (34 * density).toInt()
    val height = (44 * density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    val pinColor = if (isPickup) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#D32F2F")
    paint.color = pinColor
    paint.style = android.graphics.Paint.Style.FILL

    val radius = width / 2f - 2 * density
    val centerY = radius + 2 * density
    canvas.drawCircle(width / 2f, centerY, radius, paint)

    val path = android.graphics.Path().apply {
        moveTo(width / 2f - radius * 0.7f, centerY + radius * 0.5f)
        lineTo(width / 2f + radius * 0.7f, centerY + radius * 0.5f)
        lineTo(width / 2f, height.toFloat() - 2 * density)
        close()
    }
    canvas.drawPath(path, paint)

    // Inner white circle
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(width / 2f, centerY, radius * 0.65f, paint)

    // Text number inside
    paint.color = pinColor
    paint.textSize = radius * 0.9f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val textY = centerY - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(number.toString(), width / 2f, textY, paint)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

/**
 * Bản đồ dẫn đường Native chuẩn Google Maps.
 * Hiển thị vị trí xe 🚗 dạng radar GPS, các điểm dừng 📍 được đánh số 1, 2 và đường đi polyline.
 * 100% tiếng Việt, sắc nét, không watermark, không lag.
 */
@Composable
fun NativeMonitoringMapView(
    stops: List<TripStop>,
    activeStop: TripStop?,
    currentGps: GpsLocation?,
    onOpenVoiceNavigation: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stopCoordinates by remember(stops) { mutableStateOf<List<Pair<TripStop, GeoPoint>>>(emptyList()) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Phân giải toạ độ ngầm các điểm dừng
    LaunchedEffect(stops) {
        val resolved = stops.mapIndexed { index, stop ->
            val point = resolveStopLocation(context, stop.address, index)
            Pair(stop, point)
        }
        stopCoordinates = resolved
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = "DriverGuard/1.0 (Android; Graduate Thesis Project)"
                MapView(ctx).apply {
                    setTileSource(GoogleRoadsTileSource)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    controller.setZoom(14.0)

                    val defaultCenter = if (currentGps != null && currentGps.latitude != 0.0) {
                        GeoPoint(currentGps.latitude, currentGps.longitude)
                    } else {
                        GeoPoint(10.7769, 106.7009)
                    }
                    controller.setCenter(defaultCenter)

                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.parent.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                    mapViewInstance = this
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                val allPoints = mutableListOf<GeoPoint>()

                // 1. Tọa độ xe hiện tại
                val carPoint = if (currentGps != null && currentGps.latitude != 0.0 && currentGps.longitude != 0.0) {
                    GeoPoint(currentGps.latitude, currentGps.longitude)
                } else {
                    GeoPoint(10.7769, 106.7009)
                }
                allPoints.add(carPoint)

                // 2. Điểm dừng
                stopCoordinates.forEach { (_, point) ->
                    allPoints.add(point)
                }

                // 3. Vẽ đường Polyline kết nối xe đến các điểm dừng
                if (stopCoordinates.isNotEmpty()) {
                    val polyline = Polyline(mapView).apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#1976D2")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                        val points = mutableListOf(carPoint)
                        stopCoordinates.forEach { points.add(it.second) }
                        setPoints(points)
                    }
                    mapView.overlays.add(polyline)
                }

                // 4. Marker các điểm dừng với cờ ghim đánh số
                stopCoordinates.forEachIndexed { idx, (stop, point) ->
                    val isNext = (activeStop?.address == stop.address)
                    val stopMarker = Marker(mapView).apply {
                        position = point
                        icon = createStopMarkerDrawable(context, idx + 1, stop.kind == "pickup")
                        title = "${idx + 1}. ${if (stop.kind == "pickup") "Kho Nhận" else "Kho Giao"}${if (isNext) " [Điểm kế tiếp]" else ""}"
                        snippet = stop.address
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapView.overlays.add(stopMarker)
                }

                // 5. Marker Xe của bạn dạng radar GPS
                val carMarker = Marker(mapView).apply {
                    position = carPoint
                    icon = createCarMarkerDrawable(context)
                    title = "🚗 Xe của bạn"
                    snippet = "Vận tốc: ${currentGps?.speedKmh?.toInt() ?: 0} km/h"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(carMarker)

                // 6. Tự động zoom vừa vặn toàn bộ lộ trình
                if (allPoints.size > 1) {
                    try {
                        val box = BoundingBox.fromGeoPoints(allPoints)
                        mapView.zoomToBoundingBox(box.increaseByScale(1.35f), true)
                    } catch (_: Exception) {
                        mapView.controller.setCenter(carPoint)
                    }
                } else {
                    mapView.controller.setCenter(carPoint)
                    mapView.controller.setZoom(15.0)
                }
                mapView.invalidate()
            }
        )

        // 7. Nút Định vị lại tâm xe (Góc dưới bên phải)
        FloatingActionButton(
            onClick = {
                val carPoint = if (currentGps != null && currentGps.latitude != 0.0) {
                    GeoPoint(currentGps.latitude, currentGps.longitude)
                } else {
                    GeoPoint(10.7769, 106.7009)
                }
                mapViewInstance?.controller?.animateTo(carPoint, 16.0, 600L)
            },
            modifier = Modifier
                .padding(10.dp)
                .size(38.dp)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            containerColor = Color.White.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(2.dp)
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Tâm xe", modifier = Modifier.size(18.dp))
        }

        // 8. Nút phụ: Dẫn đường giọng nói ngoài Google Maps (Góc dưới bên trái)
        if (onOpenVoiceNavigation != null) {
            Surface(
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.BottomStart)
                    .clickable { onOpenVoiceNavigation() },
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Navigation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        "Dẫn đường giọng nói ↗",
                        color = Color.White,
                        fontSize = MaterialTheme.font.xs,
                        fontWeight = MaterialTheme.font.semibold
                    )
                }
            }
        }
    }
}
