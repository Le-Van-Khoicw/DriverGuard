package com.example.driverguard.feature.home.persentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    viewModel: HomeViewModel = viewModel()
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font
    val state by viewModel.uiState.collectAsState()

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

        // ── Camera Info Card ──
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
                        text = "Camera đang liên kết",
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
                    Text("Trạng thái xe", color = c.textMuted, fontSize = font.xs)
                    Text(
                        text = "Sẵn sàng",
                        color = c.primary,
                        fontSize = font.xl,
                        fontWeight = font.bold
                    )
                    Text("Bảo hộ AI tự động", color = c.textSubtle, fontSize = font.xs)
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
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Bắt đầu giám sát",
                    fontSize = font.base,
                    fontWeight = font.semibold
                )
            }
        }

        // ── Latest Alert Card ──
        Card(
            onClick = onOpenHistory,
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.warningBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Alert",
                        tint = c.warningText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Cảnh báo gần nhất",
                        color = c.text,
                        fontSize = font.sm,
                        fontWeight = font.semibold
                    )
                    Text(
                        text = state.latestAlert ?: "Chưa có cảnh báo nào trong ngày",
                        color = c.textMuted,
                        fontSize = font.xs
                    )
                }

                Text("›", color = c.textMuted, fontSize = font.xl)
            }
        }
    }
}
