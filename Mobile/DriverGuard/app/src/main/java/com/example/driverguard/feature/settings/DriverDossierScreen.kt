package com.example.driverguard.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun DriverDossierScreen(viewModel: DriverDossierViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var uploadKind by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf("Thông tin cá nhân") }
    var confirmReload by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        state.message?.let { Text(it) }; state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.conflict) Text("Hồ sơ máy chủ đã thay đổi hoặc bản nháp có lỗi. Tải lại hồ sơ trước khi nộp.", color = MaterialTheme.colorScheme.error)
        dossierFields.groupBy { it.group }.forEach { (group, fields) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { section = if (section == group) "" else group }) { Text("${if (section == group) "▾" else "▸"} $group (${fields.count { !state.fields[it.key].isNullOrBlank() }}/${fields.size})") }
                    if (section == group) fields.forEach { field ->
                        OutlinedTextField(value = state.fields[field.key].orEmpty(), onValueChange = { viewModel.edit(field.key, it) },
                            label = { Text(field.label) }, placeholder = { Text(field.example) }, modifier = Modifier.fillMaxWidth(),
                            enabled = !state.busy && !state.loading, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = when(field.key) { "phone", "emergencyPhone" -> KeyboardType.Phone; "identityNumber", "licenseNumber" -> KeyboardType.Number; else -> KeyboardType.Text }))
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("GPLX bắt buộc (${state.documents.count { it.kind in requiredDossierDocuments }}/2)", style = MaterialTheme.typography.titleMedium)
                Text("Hai mặt GPLX cần có để nộp. Tệp căn cước và sức khỏe chỉ đính kèm khi công ty yêu cầu.", style = MaterialTheme.typography.bodySmall)
                Text("JPG, PNG hoặc PDF, tối đa 5 MB/tệp. Cần mạng để tải tệp và nộp hồ sơ. Chỉ bạn và quản trị được xem giấy tờ.", style = MaterialTheme.typography.bodySmall)
                dossierDocumentNames.forEach { (kind, name) ->
                    Text(name)
                    state.documents.find { it.kind == kind }?.let {
                        Text("Đã tải: ${it.name}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.removeDocument(kind) }, enabled = !state.busy && !state.loading) { Text("Bỏ tệp khỏi bản nháp") }
                    }
                    OutlinedButton(onClick = { uploadKind = kind; picker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) }, enabled = !state.busy && !state.loading) { Text(if (state.documents.any { it.kind == kind }) "Chọn tệp thay thế" else "Chọn tệp") }
                }
            }
        }
        Text("Hồ sơ phục vụ đối chiếu nội bộ công ty. Ứng dụng không tự xác thực giấy tờ hoặc thay thế việc kiểm tra điều kiện lái xe.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = viewModel::submit, enabled = !state.busy && !state.loading && !state.conflict && (state.dirty || state.status == "not_submitted"), modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Đang xử lý…" else "Nộp hồ sơ cho quản lý") }
        OutlinedButton(onClick = { confirmReload = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Tải lại hồ sơ máy chủ") }
    }
    if (confirmReload) AlertDialog(onDismissRequest = { confirmReload = false }, title = { Text("Tải lại hồ sơ?") }, text = { Text("Bản nháp chưa nộp trên máy sẽ được thay bằng hồ sơ máy chủ. Các giấy tờ đã nộp vẫn giữ.") }, confirmButton = { TextButton(onClick = { confirmReload = false; viewModel.reloadRemote() }) { Text("Tải lại") } }, dismissButton = { TextButton(onClick = { confirmReload = false }) { Text("Giữ bản nháp") } })
}
