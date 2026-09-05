package com.example.driverguard.feature.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Lịch sử & Biểu đồ thống kê 7 ngày — Đồng bộ Cloud Firestore
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HistoryScreen(onAlertClick: (String) -> Unit) {
    val c      = MaterialTheme.c
    val font   = MaterialTheme.font
    val alerts by AlarmRepository.events.collectAsState()
    val weeklyStats = AlarmRepository.getSevenDaysStats(alerts)
    val totalWeeklyAlerts = weeklyStats.sumOf { it.count }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Tiêu đề ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Lịch sử & Thống kê", color = c.text, fontSize = font.xxl, fontWeight = font.bold)
                Text("Dữ liệu đồng bộ Cloud Firestore", color = c.textMuted, fontSize = font.xs)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.primaryBg)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "${alerts.size} sự kiện",
                    color = c.primary,
                    fontSize = font.xs,
                    fontWeight = font.bold
                )
            }
        }

        // ── Card Biểu đồ cột 7 ngày (7-Day Bar Chart) ──
        WeeklyBarChartCard(
            stats = weeklyStats,
            totalWeeklyAlerts = totalWeeklyAlerts
        )

        // ── Danh sách chi tiết các sự kiện ──
        SectionLabel("Danh sách sự kiện đã lưu", c.textMuted.copy(alpha = 0.8f), font.sm)

        if (alerts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = c.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("😴", fontSize = font.xxl)
                    Text(
                        "Chưa có cảnh báo nào",
                        color = c.text, fontSize = font.md, fontWeight = font.semibold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Bật camera giám sát để ghi nhận\nvà phân tích trạng thái buồn ngủ",
                        color = c.textMuted, fontSize = font.xs,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                alerts.forEachIndexed { index, alert ->
                    AlertCard(
                        alert    = alert,
                        index    = alerts.size - index,
                        onClick  = { onAlertClick(alert.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Biểu đồ cột thống kê 7 ngày
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WeeklyBarChartCard(
    stats: List<DayStat>,
    totalWeeklyAlerts: Int
) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    val maxCount = (stats.maxOfOrNull { it.count } ?: 1).coerceAtLeast(4)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📊", fontSize = font.md)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tần suất buồn ngủ (7 ngày qua)",
                        color = c.text,
                        fontSize = font.sm,
                        fontWeight = font.bold
                    )
                }

                Text(
                    "Tổng: $totalWeeklyAlerts lần",
                    color = if (totalWeeklyAlerts > 5) c.danger else c.safe,
                    fontSize = font.xs,
                    fontWeight = font.bold
                )
            }

            HorizontalDivider(color = c.divider, thickness = 0.5.dp)

            // Khu vực vẽ 7 cột (Bar Chart)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                stats.forEach { stat ->
                    val barRatio = if (maxCount > 0) (stat.count.toFloat() / maxCount).coerceIn(0.08f, 1.0f) else 0.08f
                    val barColor = when {
                        stat.count == 0 -> c.surface
                        stat.isToday -> c.primary
                        stat.count >= 3 -> c.danger
                        else -> c.warning
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Số đếm trên đầu cột
                        Text(
                            text = if (stat.count > 0) "${stat.count}" else "0",
                            color = if (stat.count > 0) c.text else c.textSubtle,
                            fontSize = font.xs,
                            fontWeight = if (stat.count > 0) font.bold else font.regular
                        )

                        Spacer(Modifier.height(4.dp))

                        // Thân cột
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((100 * barRatio).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barColor)
                        )

                        Spacer(Modifier.height(6.dp))

                        // Nhãn ngày (T2, T3... H.nay)
                        Text(
                            text = stat.dayName,
                            color = if (stat.isToday) c.primary else c.textMuted,
                            fontSize = font.xs,
                            fontWeight = if (stat.isToday) font.bold else font.medium
                        )

                        // Ngày tháng (02/09)
                        Text(
                            text = stat.dateLabel,
                            color = c.textSubtle,
                            fontSize = font.xs
                        )
                    }
                }
            }

            HorizontalDivider(color = c.divider, thickness = 0.5.dp)

            // Footer tóm tắt mức độ an toàn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val safetyStatus = when {
                    totalWeeklyAlerts == 0 -> "🛡️ Lái xe an toàn tuyệt đối"
                    totalWeeklyAlerts <= 3 -> "🟢 Trạng thái ổn định"
                    totalWeeklyAlerts <= 6 -> "🟡 Cần nghỉ ngơi nhiều hơn"
                    else -> "🔴 Nguy cơ ngủ gật cao!"
                }
                val statusColor = when {
                    totalWeeklyAlerts <= 3 -> c.safe
                    totalWeeklyAlerts <= 6 -> c.warning
                    else -> c.danger
                }

                Text(safetyStatus, color = statusColor, fontSize = font.xs, fontWeight = font.semibold)
                Text("Cập nhật theo thời gian thực", color = c.textSubtle, fontSize = font.xs)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Item Cảnh Báo
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlertCard(alert: AlertEvent, index: Int, onClick: () -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Số thứ tự trong vòng tròn đỏ
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(c.dangerBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "#$index",
                    color      = c.danger,
                    fontSize   = font.xs,
                    fontWeight = font.bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Phát hiện buồn ngủ", color = c.text, fontSize = font.base, fontWeight = font.semibold)
                Text(
                    "EAR ${"%.3f".format(alert.ear)} · ${"%.1f".format(alert.closedDurationSec)}s nhắm mắt",
                    color = c.textMuted, fontSize = font.sm, fontWeight = font.regular
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(alert.timeLabel, color = c.textSubtle, fontSize = font.xs, fontWeight = font.regular)
                    if (alert.latitude != null) {
                        Text(
                            "📍 ${alert.speedKmh?.toInt() ?: 0} km/h",
                            color = c.primary,
                            fontSize = font.xs,
                            fontWeight = font.medium
                        )
                    }
                }
            }

            Text("›", color = c.textMuted, fontSize = font.lg)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Chi tiết cảnh báo — có tọa độ GPS & nút mở Google Maps
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AlertDetailScreen(alertId: String, onBack: () -> Unit) {
    val c       = MaterialTheme.c
    val font    = MaterialTheme.font
    val context = LocalContext.current
    val alerts by AlarmRepository.events.collectAsState()
    val alert   = alerts.find { it.id == alertId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Nút quay lại ──
        TextButton(onClick = onBack) {
            Text("← Quay lại", color = c.primary, fontSize = font.base, fontWeight = font.medium)
        }

        // ── Tiêu đề ──
        Text("Chi tiết cảnh báo", color = c.text, fontSize = font.xxl, fontWeight = font.bold)

        if (alert == null) {
            Text("Không tìm thấy sự kiện này.", color = c.textMuted, fontSize = font.base)
            return@Column
        }

        // ── Badge trạng thái ──
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.dangerBg)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "⚠️  Phát hiện buồn ngủ",
                color = c.danger, fontSize = font.base, fontWeight = font.semibold
            )
        }

        // ── Card số liệu sự kiện ──
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                DetailRow(label = "Mã sự kiện",         value = "#${alert.id}")
                DividerThin()
                DetailRow(label = "Thời gian",           value = alert.timeLabel)
                DividerThin()
                DetailRow(label = "EAR tại thời điểm",  value = "%.3f".format(alert.ear))
                DividerThin()
                DetailRow(label = "Thời gian nhắm mắt", value = "%.1f giây".format(alert.closedDurationSec))
                DividerThin()
                DetailRow(label = "Nguồn camera",       value = "Camera điện thoại")
            }
        }

        // ── Card thông tin vị trí GPS ──
        SectionLabel("Vị trí & Vận tốc lúc xảy ra", c.textMuted.copy(alpha = 0.8f), font.sm)
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val coords = if (alert.latitude != null && alert.longitude != null) {
                    "${"%.5f".format(alert.latitude)}, ${"%.5f".format(alert.longitude)}"
                } else {
                    "Chưa xác định"
                }

                DetailRow(label = "Tọa độ GPS", value = coords)
                DividerThin()
                DetailRow(label = "Vận tốc xe", value = alert.speedKmh?.let { "${it.toInt()} km/h" } ?: "0 km/h")
                DividerThin()
                DetailRow(label = "Khu vực",    value = alert.locationAddress ?: "Đang cập nhật địa chỉ")
            }
        }

        // ── Nút mở Google Maps ──
        if (alert.latitude != null && alert.longitude != null) {
            Button(
                onClick = {
                    val mapUri = Uri.parse("geo:${alert.latitude},${alert.longitude}?q=${alert.latitude},${alert.longitude}(Vị trí cảnh báo #${alert.id})")
                    val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val webMapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${alert.latitude},${alert.longitude}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, webMapUri))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.primaryBg,
                    contentColor = c.primary
                )
            ) {
                Text("🗺️  Xem vị trí trên Google Maps", fontSize = font.base, fontWeight = font.semibold)
            }
        }

        // ── Card ghi chú ──
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = c.warningBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💡 Lưu ý an toàn", color = c.warningText, fontSize = font.sm, fontWeight = font.bold)
                Text(
                    "Tọa độ GPS được ghi lại chính xác tại thời điểm mắt tài xế nhắm quá 3 giây. " +
                    "Dữ liệu này được lưu trữ vĩnh viễn trên Cloud Firestore và đồng bộ trực tiếp tới Web Admin.",
                    color = c.warningText, fontSize = font.sm, fontWeight = font.regular
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text       = text.uppercase(),
        color      = color,
        fontSize   = fontSize,
        fontWeight = MaterialTheme.font.semibold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c.textMuted, fontSize = font.sm, fontWeight = font.regular)
        Text(value, color = c.text,      fontSize = font.sm, fontWeight = font.semibold)
    }
}

@Composable
private fun DividerThin() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.c.divider)
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
