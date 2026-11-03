package com.example.driverguard.feature.dispatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.location.GpsLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "assigned" -> Triple(Color(0xFFFFF3CD), Color(0xFF856404), "Chờ nhận")
        "handover_pending" -> Triple(Color(0xFFFFF3CD), Color(0xFF856404), "Chờ nhận bàn giao")
        "accepted" -> Triple(Color(0xFFCCE5FF), Color(0xFF004085), "Đã nhận")
        "in_progress" -> Triple(Color(0xFFD4EDDA), Color(0xFF155724), "Đang thực hiện")
        "completion_pending" -> Triple(Color(0xFFE2E3E5), Color(0xFF383D41), "Chờ xác nhận")
        "completed" -> Triple(Color(0xFFD1E7DD), Color(0xFF0F5132), "Đã hoàn thành")
        "completed_issues" -> Triple(Color(0xFFF8D7DA), Color(0xFF842029), "Có sự cố")
        "declined" -> Triple(Color(0xFFF8D7DA), Color(0xFF842029), "Đã từ chối")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, dispatchLabel(status))
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DispatchScreen(
    viewModel: DispatchViewModel,
    onMonitor: (AssignedTrip) -> Unit,
    onFinishMonitoring: (AssignedTrip) -> Unit,
    onStartTrip: (AssignedTrip) -> Unit = {},
    monitoringLinkedTripId: String? = null,
    currentGps: GpsLocation? = null
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editingStop by remember { mutableStateOf<TripStop?>(null) }
    var reasonAction by remember { mutableStateOf<String?>(null) }
    var showLeave by remember { mutableStateOf(false) }
    val trip = state.trips.find { it.id == selectedId }
    fun date(time: Long) = SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).format(Date(time))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- HEADER ---
        if (trip == null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Chuyến của bạn",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.fromCache) {
                        Text(
                            "Chế độ xem ngoại tuyến",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showLeave = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.EventBusy, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Nghỉ phép", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            // Thanh TopBar quay lại chuẩn
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { selectedId = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
                Text(
                    text = "Chi tiết chuyến hàng",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- CÁC THÔNG BÁO ĐỒNG BỘ (CHỈ HIỆN KHI CẦN) ---
        if (!state.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.pending > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Có ${state.pending} thao tác đang chờ đồng bộ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(onClick = viewModel::retrySync, enabled = !state.busy && !state.conflict) {
                        Text("Đồng bộ ngay")
                    }
                }
            }
        }
        state.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }
        if (state.conflict) {
            OutlinedButton(onClick = { reasonAction = "discard" }, enabled = !state.busy) {
                Text("Xử lý lệnh bị từ chối")
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ===================== DANH SÁCH CHUYẾN XE =====================
        if (trip == null) {
            if (state.loaded && state.trips.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Chưa có chuyến hàng nào", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Khi có chuyến mới, điều phối viên sẽ chỉ định cho bạn.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            state.trips.forEach { item ->
                val doneStops = item.stops.count { it.status != "pending" }
                val totalStops = item.stops.size
                val progress = if (totalStops > 0) doneStops.toFloat() / totalStops else 0f
                val shortCode = if (item.id.length >= 6) item.id.takeLast(6).uppercase() else item.id

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = { selectedId = item.id }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(item.status)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.LocalShipping, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(item.vehiclePlate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            if (item.vehicleName.isNotBlank()) {
                                Text("(${item.vehicleName})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("#$shortCode", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("${date(item.start)}  →  ${date(item.end)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Person, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("Điều phối: ${item.dispatcherName.ifBlank { "Điều phối viên" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Outlined.Place, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text(
                                    "Tiến độ: $doneStops/$totalStops điểm dừng",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                        if (totalStops > 0) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            // ===================== CHI TIẾT CHUYẾN XE =====================
            val shortCode = if (trip.id.length >= 6) trip.id.takeLast(6).uppercase() else trip.id

            // Thẻ tóm tắt thông tin chuyến (Nhỏ gọn, thanh lịch)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            trip.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadge(trip.status)
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mã: #TRIP-$shortCode", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Xe: ${trip.vehiclePlate}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        "Điều phối: ${trip.dispatcherName.ifBlank { "Điều phối viên" }} · Kế hoạch: ${date(trip.start)} → ${date(trip.end)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Nút thao tác theo trạng thái
                    if (trip.status in listOf("assigned", "handover_pending")) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.onlineCommand("acceptTrip", trip) },
                                enabled = !state.busy && state.pending == 0,
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(if (trip.status == "handover_pending") "Nhận bàn giao" else "Nhận chuyến")
                            }
                            if (trip.status == "assigned") {
                                OutlinedButton(
                                    onClick = { reasonAction = "declineTrip" },
                                    enabled = !state.busy,
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Từ chối")
                                }
                            }
                        }
                    }
                    if (trip.status == "accepted") {
                        Button(
                            onClick = { viewModel.onlineCommand("startTrip", trip, onSuccess = { onStartTrip(trip) }) },
                            enabled = !state.busy && state.pending == 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(top = 2.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bắt đầu chuyến đi", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (trip.status == "in_progress") {
                        Button(
                            onClick = { onMonitor(trip) },
                            enabled = !state.busy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .padding(top = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.Navigation, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tiếp tục lái xe & Dẫn đường", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- LỘ TRÌNH CÁC ĐIỂM DỪNG (TIMELINE GỌN GÀNG) ---
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Lộ trình các điểm dừng (${trip.stops.size} điểm)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            val nextStop = trip.stops.firstOrNull { it.status == "pending" }

            trip.stops.forEachIndexed { index, stop ->
                val isPickup = stop.kind == "pickup"
                val isCurrentTarget = nextStop?.id == stop.id

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isPickup) Color(0xFFD4EDDA) else Color(0xFFFFEBEA),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Mốc ${index + 1}: ${if (isPickup) "Nhận hàng" else "Giao hàng"}",
                                    color = if (isPickup) Color(0xFF155724) else Color(0xFF900C3F),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            StatusBadge(stop.status)
                        }

                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Place, contentDescription = null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stop.address, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Person, contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "${stop.contact} · ${stop.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Danh sách kiện hàng
                        stop.items.forEachIndexed { i, item ->
                            val actual = stop.quantities.getOrNull(i)?.toString() ?: "—"
                            Text(
                                "📦 ${item.name}: $actual / ${item.quantity} ${item.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (stop.note.isNotBlank()) {
                            Text("Ghi chú: ${stop.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }

                        // Nút Dẫn đường & Ghi kết quả
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (trip.status == "in_progress") {
                                        onMonitor(trip)
                                    } else {
                                        openInAppNavigation(context, stop.address)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Outlined.Navigation, contentDescription = null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (trip.status == "in_progress") "Vào bản đồ" else "Dẫn đường", style = MaterialTheme.typography.labelMedium)
                            }

                            if (trip.status == "in_progress" && isCurrentTarget) {
                                Button(
                                    onClick = { editingStop = stop },
                                    enabled = !state.busy && !state.conflict,
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ghi kết quả", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Nút hoàn thành toàn bộ chuyến
            if (trip.status == "in_progress" && nextStop == null) {
                Button(
                    onClick = { reasonAction = "finishTrip" },
                    enabled = !state.busy && !state.conflict,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Xác nhận hoàn thành chuyến")
                }
            }
            if (trip.status == "completion_pending") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Đã lưu kết quả. Đang chờ hệ thống máy chủ xác nhận.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // ===================== POPUP GHI KẾT QUẢ GIAO/NHẬN =====================
    if (editingStop != null && trip != null) {
        val stop = editingStop!!
        var outcome by remember(stop.id) { mutableStateOf("full") }
        var quantities by remember(stop.id) { mutableStateOf(stop.items.map { it.quantity.toString() }) }
        var contact by remember(stop.id) { mutableStateOf(stop.contact) }
        var note by remember(stop.id) { mutableStateOf("") }
        val numbers = quantities.map { it.toDoubleOrNull() }
        val pickup = trip.stops.find { it.kind == "pickup" && it.id.substringBefore(':') == stop.id.substringBefore(':') }
        val maximum = stop.items.mapIndexed { i, item -> if (stop.kind == "delivery") pickup?.quantities?.getOrNull(i) ?: 0.0 else item.quantity }
        val measuredUnits = setOf("kg", "tấn", "tan", "lít", "lit", "l", "m", "m²", "m2", "m³", "m3")
        val valid = numbers.all { it != null && it.isFinite() && it >= 0 && it <= 1_000_000_000 } && numbers.indices.all {
            val quantity = numbers[it] ?: return@all false
            quantity <= maximum[it] && (stop.items[it].unit.trim().lowercase(Locale.ROOT) in measuredUnits || quantity % 1.0 == 0.0)
        }
        val full = numbers.indices.all { numbers[it] == stop.items[it].quantity }
        val zero = numbers.all { it == 0.0 }
        val consistent = when (outcome) { "full" -> full; "failed" -> zero; else -> !full && !zero }

        AlertDialog(
            onDismissRequest = { editingStop = null },
            title = { Text("Kết quả ${if (stop.kind == "pickup") "nhận" else "giao"} hàng") },
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
                        viewModel.recordStop(trip, stop, outcome, numbers.map { it!! }, contact, note)
                        editingStop = null
                    }
                ) { Text("Lưu kết quả") }
            },
            dismissButton = {
                TextButton(onClick = { editingStop = null }) { Text("Đóng") }
            }
        )
    }

    // ===================== POPUP LÝ DO HOÀN THÀNH / TỪ CHỐI =====================
    if (reasonAction != null) {
        val action = reasonAction!!
        var reason by remember(action) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reasonAction = null },
            title = { Text(when (action) { "discard" -> "Xử lý lệnh"; "finishTrip" -> "Hoàn thành chuyến"; else -> "Lý do từ chối" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (action == "discard") {
                        Text("Bỏ các lệnh thao tác chưa đồng bộ trên máy để tránh xung đột dữ liệu.")
                    } else {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text(if (action == "finishTrip") "Ghi chú kết thúc (không bắt buộc)" else "Nhập lý do từ chối") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = action != "declineTrip" || reason.isNotBlank(),
                    onClick = {
                        if (action == "discard") viewModel.discardRejected()
                        else if (trip != null && action == "finishTrip") {
                            viewModel.finishTrip(trip, reason)
                            onFinishMonitoring(trip)
                        } else if (trip != null) {
                            viewModel.onlineCommand(action, trip, mapOf("reason" to reason))
                        }
                        reasonAction = null
                    }
                ) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = { reasonAction = null }) { Text("Đóng") }
            }
        )
    }

    // ===================== POPUP NGHỈ PHÉP =====================
    if (showLeave) {
        var start by remember { mutableStateOf("") }
        var end by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        fun parse(v: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).apply { isLenient = false }.parse(v)?.time }.getOrNull()
        val from = parse(start)
        val until = parse(end)

        AlertDialog(
            onDismissRequest = { showLeave = false },
            title = { Text("Gửi đơn xin nghỉ phép") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Từ: dd/MM/yyyy HH:mm") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Đến: dd/MM/yyyy HH:mm") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Lý do nghỉ") }, modifier = Modifier.fillMaxWidth())

                    if (state.leaves.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Lịch sử đơn nghỉ gần đây:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        state.leaves.take(3).forEach { leave ->
                            val statusLabel = mapOf("pending" to "Chờ duyệt", "approved" to "Đã duyệt", "rejected" to "Từ chối")[leave.status] ?: leave.status
                            Text("• ${date(leave.start)} → ${date(leave.end)}: $statusLabel\n  ${leave.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = from != null && until != null && until > from && reason.isNotBlank() && !state.busy,
                    onClick = {
                        viewModel.onlineCommand("requestLeave", fields = mapOf("start" to from, "end" to until, "reason" to reason), onSuccess = { showLeave = false })
                    }
                ) { Text("Gửi đơn") }
            },
            dismissButton = {
                TextButton(onClick = { showLeave = false }) { Text("Đóng") }
            }
        )
    }
}
