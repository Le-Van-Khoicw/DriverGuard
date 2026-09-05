package com.example.driverguard.feature.auth.monitoring

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.driverguard.core.location.LocationTracker
import com.example.driverguard.core.pip.PipManager
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font
import java.util.concurrent.Executors

@Composable
fun MonitoringScreen(viewModel: MonitoringViewModel) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font
    val state by viewModel.uiState.collectAsState()
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

    // Bật/tắt GPS Tracker theo trạng thái giám sát
    DisposableEffect(isRunning, hasLocationPermission) {
        if (isRunning && hasLocationPermission) {
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

        // ── Khung Camera Preview ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isRunning && hasCameraPermission) {
                    CameraPreview(
                        onEar = viewModel::onEarDetected,
                        onError = viewModel::onCameraError,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay chỉ báo Live
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.safe)
                            )
                            Text(
                                "AI FACE MESH LIVE",
                                color = c.textOnColor,
                                fontSize = font.xs,
                                fontWeight = font.bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(c.surface)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📷", fontSize = font.xxl)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Camera chưa hoạt động",
                            color = c.text,
                            fontSize = font.md,
                            fontWeight = font.semibold
                        )
                        Text(
                            "Bấm 'Bắt đầu giám sát' để kích hoạt AI",
                            color = c.textMuted,
                            fontSize = font.xs,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── Thanh tiến trình hiệu chỉnh EAR (3 giây đầu) ──
        if (state.status == MonitoringStatus.CALIBRATING) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = c.primaryBg)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "🎯 Đang hiệu chỉnh EAR cơ sở cá nhân: ${(state.calibrationProgress * 100).toInt()}%",
                        color = c.primary,
                        fontSize = font.sm,
                        fontWeight = font.bold
                    )
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
                        "Hãy nhìn thẳng vào camera và giữ mắt mở tự nhiên",
                        color = c.primaryText,
                        fontSize = font.xs
                    )
                }
            }
        }

        // ── Thông điệp hệ thống ──
        Text(
            text = state.message,
            color = if (state.status == MonitoringStatus.DROWSY) c.danger else c.textMuted,
            fontSize = font.sm,
            fontWeight = if (state.status == MonitoringStatus.DROWSY) font.bold else font.regular
        )

        // ── Bảng Điều Khiển Chỉ Số AI (4 Grid Cards) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = "👁️",
                title = "Chỉ số EAR",
                value = state.ear?.let { "%.3f".format(it) } ?: "--",
                subtitle = when {
                    state.ear == null -> "Chưa phát hiện"
                    state.ear!! < 0.20 -> "⚠️ Nhắm mắt"
                    else -> "Bình thường"
                },
                accentColor = when {
                    state.ear == null -> c.textMuted
                    state.ear!! < 0.20 -> c.danger
                    else -> c.safe
                }
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = "🎯",
                title = "Độ tin cậy",
                value = state.confidence?.let { "${(it * 100).toInt()}%" } ?: "--",
                subtitle = "Model AI",
                accentColor = c.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = "🚨",
                title = "Số cảnh báo",
                value = "${state.warningCount}",
                subtitle = "Lần buồn ngủ",
                accentColor = if (state.warningCount > 0) c.danger else c.safe
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = "⚡",
                title = "Vận tốc xe",
                value = state.gpsLocation?.speedDisplay ?: "0 km/h",
                subtitle = "Định vị GPS",
                accentColor = c.primary
            )
        }

        // ── Card Vị trí GPS Tương Tác (Bấm vào mở Google Maps ngay lập tức) ──
        val gps = state.gpsLocation

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val lat = gps?.latitude ?: 10.7769
                    val lng = gps?.longitude ?: 106.7009
                    val mapUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Vị trí xe DriverGuard)")
                    val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        context.startActivity(mapIntent)
                    } catch (_: Exception) {
                        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(c.primaryBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📍", fontSize = font.lg)
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Vị trí GPS thời gian thực",
                        color = c.text,
                        fontSize = font.sm,
                        fontWeight = font.semibold
                    )
                    Text(
                        text = gps?.address ?: gps?.coordinateDisplay ?: "Đang lấy tín hiệu vệ tinh GPS...",
                        color = c.textMuted,
                        fontSize = font.xs
                    )
                    Text(
                        text = "Chạm để mở Google Maps ↗",
                        color = c.primary,
                        fontSize = font.xs,
                        fontWeight = font.medium
                    )
                }

                Text("›", color = c.textMuted, fontSize = font.xl)
            }
        }

        // ── Nút Bắt đầu / Dừng giám sát & Cửa sổ nổi ──
        Spacer(Modifier.height(4.dp))

        if (!isRunning) {
            Button(
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
                Text(
                    text = "Bắt đầu giám sát",
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
                    onClick = viewModel::stopMonitoring,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.dangerBg,
                        contentColor = c.danger
                    )
                ) {
                    Text(
                        text = "Dừng",
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
                    Text(
                        text = "🔲 Cửa sổ nổi",
                        fontSize = font.base,
                        fontWeight = font.semibold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Card Thống Kê Chỉ Số
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    icon: String,
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
                Text(icon, fontSize = font.sm)
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
    MonitoringStatus.DROWSY      -> "🚨 BUỒN NGỦ!"
    MonitoringStatus.STOPPED     -> "ĐÃ DỪNG"
}
