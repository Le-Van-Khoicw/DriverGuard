package com.example.driverguard.feature.monitoring

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font
import com.example.driverguard.feature.monitoring.ai.TripRiskLevel
import com.example.driverguard.feature.monitoring.ai.TripSummary

@Composable
fun TripSummaryDialog(
    summary: TripSummary,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font

    val badgeColor = when (summary.riskLevel) {
        TripRiskLevel.SAFE -> c.safe
        TripRiskLevel.CAUTION -> c.warning
        TripRiskLevel.HIGH_RISK, TripRiskLevel.CRITICAL -> c.danger
    }

    val badgeBgColor = when (summary.riskLevel) {
        TripRiskLevel.SAFE -> c.safeBg
        TripRiskLevel.CAUTION -> c.warningBg
        TripRiskLevel.HIGH_RISK, TripRiskLevel.CRITICAL -> c.dangerBg
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp)),
            color = c.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header Icon & Title ──
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.primaryBg)
                        .border(1.5.dp, c.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Report",
                        tint = c.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "BÁO CÁO AI TỔNG KẾT",
                        color = c.primary,
                        fontSize = font.xs,
                        fontWeight = font.bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Đánh Giá Phiên Lái Xe",
                        color = c.text,
                        fontSize = font.xl,
                        fontWeight = font.bold
                    )
                    Text(
                        text = summary.timeRangeLabel,
                        color = c.textMuted,
                        fontSize = font.xs
                    )
                }

                // ── Card Điểm số & Xếp hạng ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = badgeBgColor),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Điểm An Toàn Lái Xe",
                                color = c.textMuted,
                                fontSize = font.xs,
                                fontWeight = font.medium
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${summary.safetyScore}",
                                    color = badgeColor,
                                    fontSize = font.xxl,
                                    fontWeight = font.black
                                )
                                Text(
                                    text = " / 100",
                                    color = c.textMuted,
                                    fontSize = font.base,
                                    fontWeight = font.semibold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        // Tag xếp hạng
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeColor)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Hạng ${summary.scoreGrade} · ${summary.riskLevel.label}",
                                color = c.textOnColor,
                                fontSize = font.sm,
                                fontWeight = font.bold
                            )
                        }
                    }
                }

                // ── Grid 3 thông số chính (Thời gian, Cảnh báo, PERCLOS) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        label = "Thời gian",
                        value = summary.durationLabel,
                        tint = c.primary
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Warning,
                        label = "Số cảnh báo",
                        value = "${summary.totalAlerts} lần",
                        tint = if (summary.totalAlerts > 0) c.danger else c.safe
                    )
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "PERCLOS",
                        value = "${"%.1f".format(summary.perclosEstimatedPercent)}%",
                        tint = c.text
                    )
                }

                if (!summary.topLocation.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.inputBg)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = summary.topLocation,
                            color = c.textMuted,
                            fontSize = font.xs,
                            maxLines = 1
                        )
                    }
                }

                // ── Khung Nhận xét Chẩn đoán AI (AI Diagnosis) ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠", fontSize = font.base)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Nhận xét từ AI Bác Sĩ An Toàn",
                            color = c.text,
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                    }
                    Text(
                        text = summary.aiDiagnosis,
                        color = c.text,
                        fontSize = font.sm,
                        lineHeight = 20.sp
                    )
                }

                // ── Khung Lời khuyên hành động (Recommendations) ──
                if (summary.aiRecommendations.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.card)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "💡 Khuyến nghị hồi phục:",
                            color = c.primary,
                            fontSize = font.sm,
                            fontWeight = font.bold
                        )
                        summary.aiRecommendations.forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("•", color = c.primary, fontWeight = font.bold, modifier = Modifier.padding(end = 6.dp))
                                Text(
                                    text = rec,
                                    color = c.text,
                                    fontSize = font.xs,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // ── Nút xác nhận hoàn thành ──
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.primary,
                        contentColor = c.textOnColor
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Đã hiểu & Tiếp tục",
                        fontSize = font.base,
                        fontWeight = font.semibold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    val c = MaterialTheme.c
    val font = MaterialTheme.font

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = c.textMuted,
            fontSize = font.xs,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = c.text,
            fontSize = font.sm,
            fontWeight = font.bold,
            textAlign = TextAlign.Center
        )
    }
}
