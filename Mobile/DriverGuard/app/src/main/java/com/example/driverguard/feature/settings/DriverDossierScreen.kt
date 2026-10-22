package com.example.driverguard.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private fun formatDateInput(input: String, previous: String = ""): String {
    if (input.length < previous.length) {
        var digits = input.filter { it.isDigit() }
        if (previous.endsWith('/') && digits.isNotEmpty()) {
            digits = digits.dropLast(1)
        }
        return when {
            digits.length > 4 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
            digits.length > 2 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
            else -> digits
        }
    }
    val digits = input.filter { it.isDigit() }.take(8)
    return when {
        digits.length > 4 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
        digits.length == 4 -> "${digits.substring(0, 2)}/${digits.substring(2, 4)}/"
        digits.length > 2 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
        digits.length == 2 -> "${digits.substring(0, 2)}/"
        else -> digits
    }
}

@Composable
fun DriverDossierScreen(viewModel: DriverDossierViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var uploadKind by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("Thông tin cá nhân") }
    var confirmReload by remember { mutableStateOf(false) }
    var showOptional by rememberSaveable { mutableStateOf(false) }
    var pickingLicense by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && uploadKind.isNotBlank()) viewModel.upload(uploadKind, uri)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uploadKind.isNotBlank()) viewModel.upload(uploadKind, uri)
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !state.busy) { Text("← Quay lại") }
        Text("Hồ sơ nghề nghiệp tài xế", style = MaterialTheme.typography.headlineSmall)
        Text("Có thể nộp online bên dưới hoặc giao bản giấy trực tiếp tại công ty. Trạng thái nộp trực tiếp do quản lý xác nhận trên hệ thống.", style = MaterialTheme.typography.bodySmall)
        if (state.submissionMethod == "in_person") Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Công ty đã ghi nhận nhận bản giấy", style = MaterialTheme.typography.titleSmall)
                Text("Mã phiếu: ${state.receiptCode}")
                if (state.receivedAt > 0) Text("Ghi nhận lúc: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(state.receivedAt))}")
                Text("Tiếp nhận bản giấy và duyệt hồ sơ là hai bước riêng. Trạng thái xét duyệt hiển thị bên dưới.")
            }
        }
        Text(when(state.status) { "approved" -> "Quản lý đã duyệt hồ sơ. Sửa và nộp lại sẽ cần duyệt lại."; "submitted" -> "Đã nộp v${state.revision}, đang chờ quản lý đối chiếu."; "rejected" -> "Cần bổ sung hồ sơ theo ghi chú quản lý."; else -> "Hoàn thiện thông tin và giấy tờ để quản lý xét duyệt." })
        if (state.reviewNote.isNotBlank()) Card { Text("Ghi chú quản lý: ${state.reviewNote}", Modifier.padding(12.dp)) }
        if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.dirty) Text("Có bản nháp lưu trên máy, chưa phải hồ sơ được duyệt.", style = MaterialTheme.typography.bodySmall)
        state.message?.let { Text(it) }
        if (state.conflict) Text("Hồ sơ máy chủ đã thay đổi hoặc bản nháp có lỗi. Tải lại hồ sơ trước khi nộp.", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { showOptional = !showOptional }) { Text(if (showOptional) "Ẩn thông tin bổ sung" else "Thêm thông tin bổ sung (không bắt buộc)") }
        dossierFields.filter { showOptional || it.key in requiredDossierFields }.groupBy { it.group }.forEach { (group, fields) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { section = if (section == group) "" else group }) { Text("${if (section == group) "▾" else "▸"} $group (${fields.count { !state.fields[it.key].isNullOrBlank() }}/${fields.size})") }
                    if (section == group) fields.forEach { field ->
                        val isDate = field.key in dateFields
                        if (field.key == "licenseClass") {
                            OutlinedButton(onClick = { pickingLicense = true }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) {
                                Text(state.fields[field.key]?.takeIf { it.isNotBlank() }?.let { "Hạng GPLX: $it ▾" } ?: "Chọn hạng GPLX * ▾")
                            }
                        } else OutlinedTextField(
                            value = state.fields[field.key].orEmpty(),
                            onValueChange = { input ->
                                val finalVal = if (isDate) formatDateInput(input, state.fields[field.key].orEmpty()) else input
                                viewModel.edit(field.key, finalVal)
                            },
                            label = { Text(field.label + if (field.key in requiredDossierFields) " *" else " (không bắt buộc)") },
                            placeholder = { Text(field.example) },
                            trailingIcon = if (isDate) {
                                { IconButton(onClick = { pickingDate = field.key }, enabled = !state.busy && !state.loading) { Icon(Icons.Default.DateRange, contentDescription = "Chọn ngày trên lịch") } }
                            } else null,
                            supportingText = if (isDate) {
                                { Text("Định dạng: Ngày/Tháng/Năm (VD: 15/06/1995)") }
                            } else null,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy && !state.loading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when {
                                    isDate -> KeyboardType.Number
                                    field.key in setOf("phone", "emergencyPhone") -> KeyboardType.Phone
                                    field.key in setOf("identityNumber", "licenseNumber") -> KeyboardType.Number
                                    else -> KeyboardType.Text
                                }
                            )
                        )
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GPLX bắt buộc (${state.documents.count { it.kind in requiredDossierDocuments }}/2)", style = MaterialTheme.typography.titleMedium)
                Text("Hai mặt GPLX cần có để nộp. Tệp căn cước và sức khỏe chỉ đính kèm khi công ty yêu cầu.", style = MaterialTheme.typography.bodySmall)
                Text("JPG, PNG hoặc PDF, tối đa 5 MB/tệp. Cần mạng để tải tệp và nộp hồ sơ. Chỉ bạn và quản trị được xem giấy tờ.", style = MaterialTheme.typography.bodySmall)
                // Keep feedback beside the pickers; the page header can be far above the current scroll position.
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Đang xử lý, vui lòng chờ. Lần kết nối đầu tiên có thể lâu hơn.")
                }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                dossierDocumentNames.forEach { (kind, name) ->
                    Text(name)
                    state.documents.find { it.kind == kind }?.let {
                        Text("Đã tải: ${it.name}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.removeDocument(kind) }, enabled = !state.busy && !state.loading) { Text("Bỏ tệp khỏi bản nháp") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            uploadKind = kind
                            runCatching { photoPicker.launch("image/*") }
                                .onFailure { viewModel.fileSelectionError("Không mở được thư viện ảnh. Thử nút Chọn tệp.") }
                        }, enabled = !state.busy && !state.loading) { Text("Chọn ảnh") }
                        OutlinedButton(onClick = {
                            uploadKind = kind
                            runCatching { picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }
                                .onFailure { viewModel.fileSelectionError("Không mở được trình chọn tệp trên điện thoại.") }
                        }, enabled = !state.busy && !state.loading) { Text(if (state.documents.any { it.kind == kind }) "Thay tệp" else "Chọn tệp / PDF") }
                    }
                }
            }
        }
        Text("Hồ sơ phục vụ đối chiếu nội bộ công ty. Ứng dụng không tự xác thực giấy tờ hoặc thay thế việc kiểm tra điều kiện lái xe.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = viewModel::submit, enabled = !state.busy && !state.loading && !state.conflict && (state.dirty || state.status == "not_submitted"), modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Đang xử lý…" else "Nộp hồ sơ cho quản lý") }
        OutlinedButton(onClick = { confirmReload = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Tải lại hồ sơ máy chủ") }
    }
    state.error?.let { message ->
        AlertDialog(onDismissRequest = viewModel::clearError, title = { Text("Chưa thực hiện được") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Đã hiểu") } })
    }
    if (pickingLicense) AlertDialog(onDismissRequest = { pickingLicense = false }, title = { Text("Chọn hạng GPLX") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            Text("Chọn đúng hạng trên giấy phép. Mỗi hồ sơ ghi một hạng.")
            val saved = state.fields["licenseClass"].orEmpty()
            (listOf("B", "B2", "C1", "C", "BE", "C1E", "CE", "FC", "D1", "D2", "D", "E", "D1E", "D2E", "DE", "FB2", "FD", "FE") + listOf(saved).filter { it.isNotBlank() }).distinct().forEach { code ->
                val choose = { viewModel.edit("licenseClass", code); pickingLicense = false }
                Row(Modifier.fillMaxWidth().clickable(enabled = !state.busy, onClick = choose).padding(vertical = 4.dp)) {
                    RadioButton(selected = saved == code, onClick = choose, enabled = !state.busy)
                    Text("Hạng $code", Modifier.padding(top = 12.dp))
                }
            }
        } }, confirmButton = { TextButton(onClick = { pickingLicense = false }) { Text("Đóng") } })
    pickingDate?.let { key -> DossierDateDialog(value = state.fields[key].orEmpty(), dismiss = { pickingDate = null }, choose = { value -> viewModel.edit(key, value); pickingDate = null }) }
    if (confirmReload) AlertDialog(onDismissRequest = { confirmReload = false }, title = { Text("Tải lại hồ sơ?") }, text = { Text("Bản nháp chưa nộp trên máy sẽ được thay bằng hồ sơ máy chủ. Các giấy tờ đã nộp vẫn giữ.") }, confirmButton = { TextButton(onClick = { confirmReload = false; viewModel.reloadRemote() }) { Text("Tải lại") } }, dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("Giữ bản nháp") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DossierDateDialog(value: String, dismiss: () -> Unit, choose: (String) -> Unit) {
    val format = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    val initial = runCatching { LocalDate.parse(value, format).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = dismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            picker.selectedDateMillis?.let { choose(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(format)) }
        }) { Text("Chọn ngày") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Hủy") } }
    ) { DatePicker(state = picker, title = { Text("Chọn ngày", Modifier.padding(16.dp)) }) }
}
