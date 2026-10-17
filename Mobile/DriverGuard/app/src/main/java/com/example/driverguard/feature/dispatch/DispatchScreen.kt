package com.example.driverguard.feature.dispatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DispatchScreen(viewModel: DispatchViewModel, onMonitor: (AssignedTrip) -> Unit, onFinishMonitoring: (AssignedTrip) -> Unit) {
    val state by viewModel.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editingStop by remember { mutableStateOf<TripStop?>(null) }
    var reasonAction by remember { mutableStateOf<String?>(null) }
    var showLeave by remember { mutableStateOf(false) }
    val trip = state.trips.find { it.id == selectedId }
    fun date(time: Long) = SimpleDateFormat("dd/MM HH:mm", Locale("vi", "VN")).format(Date(time))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chuyến được phân công", style = MaterialTheme.typography.headlineSmall)
        Text("Chỉ thao tác nhận/giao hàng khi đã dừng xe an toàn.")
        Text(if (state.fromCache) "Đang xem dữ liệu lưu trên máy; chưa xác nhận thông tin mới nhất." else "Đã nhận dữ liệu từ máy chủ.")
        if (!state.loaded) CircularProgressIndicator()
        if (state.pending > 0) {
            Text("${state.pending} thao tác chờ đồng bộ. Chưa coi là máy chủ đã chấp nhận.")
            OutlinedButton(onClick = viewModel::retrySync, enabled = !state.busy && !state.conflict) { Text("Thử đồng bộ") }
        }
        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.conflict) OutlinedButton(onClick = { reasonAction = "discard" }, enabled = !state.busy) { Text("Xử lý lệnh bị từ chối") }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = { showLeave = true }, enabled = !state.busy) { Text("Gửi đơn xin nghỉ") }
        state.leaves.take(5).forEach { leave -> Text("Nghỉ ${date(leave.start)} → ${date(leave.end)}: ${mapOf("pending" to "Chờ duyệt", "approved" to "Đã duyệt", "rejected" to "Từ chối")[leave.status] ?: leave.status}\n${leave.reason}") }
        if (trip == null) {
            if (state.loaded && state.trips.isEmpty()) Text("Chưa có chuyến. Điều phối viên sẽ phân công từ web.")
            state.trips.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { selectedId = item.id }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("${item.vehiclePlate} · ${dispatchLabel(item.status)}")
                        Text("${date(item.start)} → ${date(item.end)}")
                        Text("${item.stops.count { it.status != "pending" }}/${item.stops.size} điểm đã xử lý · phân công v${item.assignmentVersion}")
                    }
                }
            }
        } else {
            TextButton(onClick = { selectedId = null }) { Text("← Danh sách chuyến") }
            Text(trip.name, style = MaterialTheme.typography.titleLarge)
            Text("${trip.vehiclePlate} · ${dispatchLabel(trip.status)}")
            Text("Điều phối: ${trip.dispatcherId}\nMã chuyến: ${trip.id}\nKế hoạch: ${date(trip.start)} → ${date(trip.end)}")
            Text("Cập nhật nghiệp vụ cuối: ${date(trip.updatedAt)}")
            if (trip.status in listOf("assigned", "handover_pending")) {
                Button(onClick = { viewModel.onlineCommand("acceptTrip", trip) }, enabled = !state.busy && state.pending == 0) { Text(if (trip.status == "handover_pending") "Nhận bàn giao" else "Nhận chuyến") }
                if (trip.status == "assigned") OutlinedButton(onClick = { reasonAction = "declineTrip" }, enabled = !state.busy) { Text("Từ chối có lý do") }
            }
            if (trip.status == "accepted") Button(onClick = { viewModel.onlineCommand("startTrip", trip) }, enabled = !state.busy && state.pending == 0) { Text("Bắt đầu chuyến (cần mạng)") }
            if (trip.status == "in_progress") {
                Button(onClick = { onMonitor(trip) }, enabled = !state.busy) { Text("Mở giám sát buồn ngủ cho chuyến") }
                Text("Camera chỉ giám sát khi mày mở màn hình giám sát / chế độ cửa sổ nổi. Rời màn hình sẽ tạm dừng.")
            }
            val nextStop = trip.stops.firstOrNull { it.status == "pending" }
            trip.stops.forEachIndexed { index, stop ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${index + 1}. ${if (stop.kind == "pickup") "Nhận hàng" else "Giao hàng"} · ${dispatchLabel(stop.status)}", style = MaterialTheme.typography.titleMedium)
                        Text(stop.address); Text("${stop.contact} · ${stop.phone}")
                        stop.items.forEachIndexed { i, item -> Text("${item.name}: ${stop.quantities.getOrNull(i)?.toString() ?: "—"}/${item.quantity} ${item.unit}") }
                        if (stop.note.isNotBlank()) Text(stop.note)
                        if (trip.status == "in_progress" && nextStop?.id == stop.id) Button(onClick = { editingStop = stop }, enabled = !state.busy && !state.conflict) { Text("Ghi kết quả thực tế") }
                    }
                }
            }
            if (trip.status == "in_progress" && nextStop == null) Button(onClick = { reasonAction = "finishTrip" }, enabled = !state.busy && !state.conflict) { Text("Xác nhận hoàn thành chuyến") }
            if (trip.status == "completion_pending") Text("Đã lưu yêu cầu kết thúc trên máy. Đang chờ máy chủ kiểm tra và xác nhận.")
        }
    }
    if (editingStop != null && trip != null) {
        val stop = editingStop!!
        var outcome by remember(stop.id) { mutableStateOf("full") }
        var quantities by remember(stop.id) { mutableStateOf(stop.items.map { it.quantity.toString() }) }
        var contact by remember(stop.id) { mutableStateOf(stop.contact) }
        var note by remember(stop.id) { mutableStateOf("") }
        val numbers = quantities.map { it.toDoubleOrNull() }
        val pickup = trip.stops.find { it.kind == "pickup" && it.id.substringBefore(':') == stop.id.substringBefore(':') }
        val maximum = stop.items.mapIndexed { i, item -> if (stop.kind == "delivery") pickup?.quantities?.getOrNull(i) ?: 0.0 else item.quantity }
        val valid = numbers.all { it != null && it.isFinite() && it >= 0 } && numbers.indices.all { (numbers[it] ?: 0.0) <= maximum[it] }
        val full = numbers.indices.all { numbers[it] == stop.items[it].quantity }
        val zero = numbers.all { it == 0.0 }
        val consistent = when (outcome) { "full" -> full; "failed" -> zero; else -> !full && !zero }
        AlertDialog(onDismissRequest = { editingStop = null }, title = { Text("Kết quả ${if (stop.kind == "pickup") "nhận" else "giao"} hàng") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("full" to "Đủ", "partial" to "Thiếu", "failed" to "Thất bại").forEach { (key, label) ->
                    Row { RadioButton(selected = outcome == key, onClick = { outcome = key }); Text(label, Modifier.padding(top = 12.dp)) }
                }
                stop.items.forEachIndexed { i, item -> OutlinedTextField(value = quantities[i], onValueChange = { v -> quantities = quantities.mapIndexed { j, old -> if (i == j) v else old } }, label = { Text("${item.name} (${item.unit}), tối đa ${maximum[i]}") }) }
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Người giao/nhận thực tế") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Lý do nếu thiếu/thất bại") })
                Text("Điền đúng số lượng thực tế. GPS đến nơi không tự xác nhận giao hàng.")
            }
        }, confirmButton = { TextButton(enabled = valid && consistent && contact.isNotBlank() && (outcome == "full" || note.isNotBlank()), onClick = {
            viewModel.recordStop(trip, stop, outcome, numbers.map { it!! }, contact, note); editingStop = null
        }) { Text("Lưu kết quả") } }, dismissButton = { TextButton(onClick = { editingStop = null }) { Text("Đóng") } })
    }
    if (reasonAction != null) {
        val action = reasonAction!!
        var reason by remember(action) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { reasonAction = null }, title = { Text(when(action) { "discard" -> "Bỏ các lệnh đang giữ?"; "finishTrip" -> "Kết thúc chuyến"; else -> "Lý do từ chối" }) }, text = {
            Column {
                if (action == "discard") Text("Chỉ bỏ lệnh nghiệp vụ chưa xác nhận trên máy. Kiểm tra lại dữ liệu máy chủ với điều phối trước khi nhập lại kết quả; báo cáo giám sát vẫn được giữ.")
                else OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Lý do / ghi chú kết thúc") })
            }
        }, confirmButton = { TextButton(enabled = action == "discard" || reason.isNotBlank(), onClick = {
            if (action == "discard") viewModel.discardRejected()
            else if (trip != null && action == "finishTrip") { viewModel.finishTrip(trip, reason); onFinishMonitoring(trip) }
            else if (trip != null) viewModel.onlineCommand(action, trip, mapOf("reason" to reason))
            reasonAction = null
        }) { Text("Xác nhận") } }, dismissButton = { TextButton(onClick = { reasonAction = null }) { Text("Đóng") } })
    }
    if (showLeave) {
        var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }
        fun parse(v: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN")).apply { isLenient = false }.parse(v)?.time }.getOrNull()
        val from = parse(start); val until = parse(end)
        AlertDialog(onDismissRequest = { showLeave = false }, title = { Text("Xin nghỉ theo khoảng thời gian") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Từ: dd/MM/yyyy HH:mm") })
                OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Đến: dd/MM/yyyy HH:mm") })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Lý do nghỉ") })
                Text("Cần mạng để gửi. Chỉ coi là được nghỉ sau khi quản lý duyệt.")
            }
        }, confirmButton = { TextButton(enabled = from != null && until != null && until > from && reason.isNotBlank() && !state.busy, onClick = {
            viewModel.onlineCommand("requestLeave", fields = mapOf("start" to from, "end" to until, "reason" to reason), onSuccess = { showLeave = false })
        }) { Text("Gửi đơn") } }, dismissButton = { TextButton(onClick = { showLeave = false }) { Text("Đóng") } })
    }
}
