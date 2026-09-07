package com.example.driverguard.feature.home.persentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun HomeScreen(
    onStartMonitoring: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenVehicles: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font
    val state by viewModel.uiState.collectAsState()
    var dismissedVehicleBanner by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header Lời chào & Tên tài xế ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Xin chào,",
                    color = c.textMuted,
                    fontSize = font.sm,
                    fontWeight = font.regular
                )
                Text(
                    text = state.driverName,
                    color = c.text,
                    fontSize = font.xxl,
                    fontWeight = font.bold
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(c.primaryBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Protection",
                    tint = c.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // ── Banner Nhắc nhở Thiết lập xe nếu chưa cấu hình ──
        if (!state.isVehicleConfigured && !dismissedVehicleBanner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.warningBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.warning.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c.warning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = "Vehicle",
                                tint = c.textOnColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Chưa thiết lập phương tiện",
                                color = c.text,
                                fontSize = font.sm,
                                fontWeight = font.bold
                            )
                            Text(
                                text = "Nhập tên và biển số xe để cá nhân hóa hệ thống",
                                color = c.textMuted,
                                fontSize = font.xs
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { dismissedVehicleBanner = true }) {
                            Text("Để sau", color = c.textMuted, fontSize = font.xs)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = onOpenVehicles,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.primary,
                                contentColor = c.textOnColor
                            )
                        ) {
                            Text("Thiết lập ngay", fontSize = font.xs, fontWeight = font.semibold)
                        }
                    }
                }
            }
        }

        // ── Camera Info Card (Đọc tên máy và mã thiết bị thật) ──
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(c.safeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Camera",
                        tint = c.safe,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Thiết bị giám sát",
                        color = c.textMuted,
                        fontSize = font.xs,
                        fontWeight = font.medium
                    )
                    Text(
                        text = state.deviceName,
                        color = c.text,
                        fontSize = font.base,
                        fontWeight = font.semibold
                    )
                    Text(
                        text = "${state.deviceCode} · ${if (state.isDeviceOnline) "Trực tuyến" else "Ngoại tuyến"}",
                        color = if (state.isDeviceOnline) c.safe else c.textSubtle,
                        fontSize = font.xs,
                        fontWeight = font.regular
                    )
                }
            }
        }

        // ── Stats Summary Row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Cảnh báo hôm nay", color = c.textMuted, fontSize = font.xs)
                    Text(
                        text = "${state.todayAlertCount} lần",
                        color = if (state.todayAlertCount > 0) c.danger else c.safe,
                        fontSize = font.xl,
                        fontWeight = font.bold
                    )
                    Text("Phiên giám sát", color = c.textSubtle, fontSize = font.xs)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Phương tiện", color = c.textMuted, fontSize = font.xs)
                    Text(
                        text = state.vehicleName.ifBlank { "Chưa đặt tên" },
                        color = c.primary,
                        fontSize = font.md,
                        fontWeight = font.bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (state.isVehicleConfigured) "Bảo hộ AI tự động" else "Chạm để cài đặt",
                        color = c.textSubtle,
                        fontSize = font.xs
                    )
                }
            }
        }

        // ── Start Monitoring Action Button ──
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onStartMonitoring,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.primary,
                contentColor = c.textOnColor
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Bắt đầu giám sát",
                    fontSize = font.base,
                    fontWeight = font.semibold
                )
            }
        }

        // ── Recent Activity Card ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenHistory() },
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.warningBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Alert",
                        tint = c.warning,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Chuyến đi gần nhất",
                        color = c.text,
                        fontSize = font.sm,
                        fontWeight = font.semibold
                    )
                    Text(
                        text = state.latestAlert,
                        color = c.textMuted,
                        fontSize = font.xs
                    )
                }

                Text(
                    text = "›",
                    color = c.textMuted,
                    fontSize = font.xl
                )
            }
        }
    }
}
