package com.example.driverguard.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font
import com.example.driverguard.feature.auth.domain.AuthUser
import com.google.firebase.auth.FirebaseAuth

import com.example.driverguard.core.theme.ThemeManager

// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Cài đặt chính
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onDevices: () -> Unit,
    onVehicles: () -> Unit,
    onLogout: () -> Unit
) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    val isDark by ThemeManager.isDarkMode.collectAsState()
    var sound     by remember { mutableStateOf(true) }
    var vibration by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Tiêu đề ──
        Text("Cài đặt", color = c.text, fontSize = font.xxl, fontWeight = font.bold)

        // ── Nhóm 1: Tài khoản ──
        SectionLabel("Tài khoản", c.textMuted.copy(alpha = 0.8f), font.sm)
        SettingsGroup {
            SettingsRow(emoji = "👤", label = "Hồ sơ tài xế",   onClick = onProfile)
            SectionDivider()
            SettingsRow(emoji = "📷", label = "Quản lý camera",  onClick = onDevices)
            SectionDivider()
            SettingsRow(emoji = "🚗", label = "Phương tiện",     onClick = onVehicles)
        }

        // ── Nhóm 2: Giao diện ──
        SectionLabel("Giao diện", c.textMuted.copy(alpha = 0.8f), font.sm)
        SettingsGroup {
            SwitchRow(
                emoji   = if (isDark) "🌙" else "☀️",
                label   = if (isDark) "Chế độ tối (Bật)" else "Chế độ tối (Tắt)",
                checked = isDark,
                onCheckedChange = { ThemeManager.setDarkMode(it) }
            )
        }

        // ── Nhóm 3: Cảnh báo ──
        SectionLabel("Cảnh báo", c.textMuted.copy(alpha = 0.8f), font.sm)
        SettingsGroup {
            SwitchRow(
                emoji   = "🔔",
                label   = "Âm thanh cảnh báo",
                checked = sound,
                onCheckedChange = { sound = it }
            )
            SectionDivider()
            SwitchRow(
                emoji   = "📳",
                label   = "Rung cảnh báo",
                checked = vibration,
                onCheckedChange = { vibration = it }
            )
        }

        // ── Nút đăng xuất ──
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape  = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.dangerBg,
                contentColor   = c.danger
            )
        ) {
            Text("Đăng xuất", fontSize = font.base, fontWeight = font.semibold)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Hồ sơ — Lấy email thật từ Firebase & cho phép đổi avatar + chỉnh sửa
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(user: AuthUser? = null, onBack: () -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    val profile by UserProfileRepository.profile.collectAsState()

    // Lấy thông tin thật từ Firebase User
    val firebaseUser = FirebaseAuth.getInstance().currentUser
    val actualEmail = user?.email
        ?: firebaseUser?.email
        ?: profile.email.ifBlank { "Chưa cập nhật email" }

    var displayName by rememberSaveable { mutableStateOf(profile.displayName) }
    var phone       by rememberSaveable { mutableStateOf(profile.phone) }
    var customAvatarUri by rememberSaveable { mutableStateOf(profile.avatarUri) }
    var isEditing   by rememberSaveable { mutableStateOf(false) }
    var saved       by rememberSaveable { mutableStateOf(false) }

    // Launcher chọn ảnh từ bộ sưu tập
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            customAvatarUri = uriStr
            UserProfileRepository.updateProfile(displayName, phone, uriStr)
            saved = true
        }
    }

    val photoUrl = customAvatarUri ?: user?.photoUrl ?: firebaseUser?.photoUrl?.toString()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Tiêu đề & Nút quay lại ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Quay lại", color = c.primary, fontSize = font.base, fontWeight = font.medium)
            }
        }

        // ── Avatar tương tác (có thể bấm để đổi ảnh) ──
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .border(2.dp, c.primary, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(c.primaryBg)
                            .border(2.dp, c.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = displayName.take(1).uppercase(),
                            color = c.primary,
                            fontSize   = font.xxl,
                            fontWeight = font.bold,
                            textAlign  = TextAlign.Center
                        )
                    }
                }

                // Badge icon camera ở góc
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(c.primary)
                        .border(2.dp, c.card, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷", fontSize = font.xs)
                }
            }
        }

        Text(
            text = "Chạm vào ảnh để đổi avatar",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = c.textSubtle,
            fontSize = font.xs
        )

        // ── Card thông tin cá nhân ──
        SectionLabel("Thông tin cá nhân", c.textMuted.copy(alpha = 0.8f), font.sm)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isEditing) {
                    // ── Chế độ sửa ──
                    ProfileField(
                        label  = "Họ và tên",
                        value  = displayName,
                        onValueChange = { displayName = it; saved = false }
                    )
                    ProfileField(
                        label  = "Số điện thoại",
                        value  = phone,
                        onValueChange = { phone = it; saved = false }
                    )
                    ProfileReadOnly(label = "Email đăng nhập", value = actualEmail)
                    ProfileReadOnly(label = "Trạng thái", value = "✅ Đã xác minh")
                } else {
                    // ── Chế độ xem ──
                    ProfileReadOnly(label = "Họ và tên",        value = displayName.ifBlank { "—" })
                    ProfileReadOnly(label = "Số điện thoại",    value = phone.ifBlank { "Chưa cập nhật" })
                    ProfileReadOnly(label = "Email đăng nhập",  value = actualEmail)
                    ProfileReadOnly(label = "Trạng thái tài khoản", value = "✅ Đã xác minh")
                }
            }
        }

        // ── Thông báo lưu thành công ──
        if (saved) {
            Text(
                "✅ Đã lưu thông tin thành công!",
                color = c.safe, fontSize = font.sm, fontWeight = font.medium
            )
        }

        // ── Nút hành động ──
        if (isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        isEditing = false
                        saved = true
                        UserProfileRepository.updateProfile(displayName, phone, customAvatarUri)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.primary,
                        contentColor   = c.textOnColor
                    )
                ) {
                    Text("Lưu lại", fontSize = font.base, fontWeight = font.semibold)
                }
                Button(
                    onClick = { isEditing = false; saved = false; displayName = profile.displayName; phone = profile.phone },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape  = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.surface,
                        contentColor   = c.textMuted
                    )
                ) {
                    Text("Hủy", fontSize = font.base, fontWeight = font.semibold)
                }
            }
        } else {
            Button(
                onClick = { isEditing = true; saved = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.primaryBg,
                    contentColor   = c.primary
                )
            ) {
                Text("✏️  Chỉnh sửa thông tin", fontSize = font.base, fontWeight = font.semibold)
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Quản lý camera
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Quay lại", color = c.primary, fontSize = font.base, fontWeight = font.medium)
        }
        Text("Quản lý camera", color = c.text, fontSize = font.xxl, fontWeight = font.bold)
        SectionLabel("Thiết bị đang liên kết", c.textMuted.copy(alpha = 0.8f), font.sm)
        SettingsGroup {
            InfoRow(emoji = "📱", label = "Camera điện thoại", value = "Đang liên kết", valueColor = MaterialTheme.c.safe)
            SectionDivider()
            InfoRow(emoji = "🔑", label = "Mã thiết bị", value = "PHONE-001")
            SectionDivider()
            InfoRow(emoji = "⚙️", label = "Nguồn video", value = "Android CameraX")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Màn hình Phương tiện — Đầy đủ tính năng Thêm & Chỉnh sửa
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun VehiclesScreen(onBack: () -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    val profile by UserProfileRepository.profile.collectAsState()

    var licensePlate by rememberSaveable { mutableStateOf(profile.licensePlate) }
    var vehicleName  by rememberSaveable { mutableStateOf(profile.vehicleName) }
    var vehicleType  by rememberSaveable { mutableStateOf(profile.vehicleType) }
    var isEditing    by rememberSaveable { mutableStateOf(false) }
    var saved        by rememberSaveable { mutableStateOf(false) }

    val vehicleTypes = listOf(
        "Ô tô con" to "🚗",
        "Xe tải" to "🚚",
        "Xe khách" to "🚌",
        "Container" to "🚛"
    )

    val currentEmoji = vehicleTypes.find { it.first == vehicleType }?.second ?: "🚗"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Nút quay lại ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Quay lại", color = c.primary, fontSize = font.base, fontWeight = font.medium)
            }
        }

        // ── Tiêu đề ──
        Text("Phương tiện", color = c.text, fontSize = font.xxl, fontWeight = font.bold)

        // ── Card thông tin phương tiện ──
        SectionLabel("Thông tin xe đang sử dụng", c.textMuted.copy(alpha = 0.8f), font.sm)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = c.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isEditing) {
                    // ── Chế độ chỉnh sửa ──
                    ProfileField(
                        label = "Biển số xe",
                        value = licensePlate,
                        onValueChange = { licensePlate = it; saved = false }
                    )

                    ProfileField(
                        label = "Tên phương tiện / Dòng xe",
                        value = vehicleName,
                        onValueChange = { vehicleName = it; saved = false }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Loại phương tiện", color = c.textMuted, fontSize = font.xs, fontWeight = font.medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vehicleTypes.forEach { (type, emoji) ->
                                val selected = vehicleType == type
                                FilterChip(
                                    selected = selected,
                                    onClick = { vehicleType = type; saved = false },
                                    label = { Text("$emoji $type", fontSize = font.xs) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = c.primaryBg,
                                        selectedLabelColor = c.primary
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // ── Chế độ xem thông tin ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(c.primaryBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(currentEmoji, fontSize = font.xl)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(vehicleName.ifBlank { "Chưa đặt tên xe" }, color = c.text, fontSize = font.md, fontWeight = font.semibold)
                                Text(vehicleType, color = c.textMuted, fontSize = font.sm)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.safeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Sẵn sàng", color = c.safe, fontSize = font.xs, fontWeight = font.semibold)
                        }
                    }

                    HorizontalDivider(color = c.divider, thickness = 0.5.dp)

                    InfoRow(emoji = "🏷️", label = "Biển số xe", value = licensePlate.ifBlank { "Chưa thiết lập" }, valueColor = c.text)
                    SectionDivider()
                    InfoRow(emoji = "🛡️", label = "Trạng thái bảo hộ", value = "Đã kích hoạt", valueColor = c.safe)
                }
            }
        }

        // ── Thông báo lưu ──
        if (saved) {
            Text(
                "✅ Đã cập nhật phương tiện thành công!",
                color = c.safe, fontSize = font.sm, fontWeight = font.medium
            )
        }

        // ── Nút hành động ──
        if (isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        isEditing = false
                        saved = true
                        UserProfileRepository.updateVehicle(vehicleName, licensePlate, vehicleType)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.primary,
                        contentColor = c.textOnColor
                    )
                ) {
                    Text("Lưu phương tiện", fontSize = font.base, fontWeight = font.semibold)
                }
                Button(
                    onClick = {
                        isEditing = false
                        saved = false
                        licensePlate = profile.licensePlate
                        vehicleName = profile.vehicleName
                        vehicleType = profile.vehicleType
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.surface,
                        contentColor = c.textMuted
                    )
                ) {
                    Text("Hủy", fontSize = font.base, fontWeight = font.semibold)
                }
            }
        } else {
            Button(
                onClick = { isEditing = true; saved = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.primaryBg,
                    contentColor = c.primary
                )
            ) {
                Text("✏️  Chỉnh sửa phương tiện", fontSize = font.base, fontWeight = font.semibold)
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Các widget dùng chung
// ─────────────────────────────────────────────────────────────────────────────

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
private fun SettingsGroup(content: @Composable () -> Unit) {
    val c = MaterialTheme.c
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(emoji: String, label: String, onClick: () -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = c.card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = font.md)
                Spacer(Modifier.width(12.dp))
                Text(label, color = c.text, fontSize = font.base, fontWeight = font.medium)
            }
            Text("›", color = c.textMuted, fontSize = font.lg)
        }
    }
}

@Composable
private fun SwitchRow(emoji: String, label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = font.md)
            Spacer(Modifier.width(12.dp))
            Text(label, color = c.text, fontSize = font.base, fontWeight = font.medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor       = c.textOnColor,
                checkedTrackColor       = c.primary,
                uncheckedThumbColor     = c.textMuted,
                uncheckedTrackColor     = c.surface
            )
        )
    }
}

@Composable
private fun InfoRow(
    emoji: String,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.c.textMuted
) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = font.md)
            Spacer(Modifier.width(12.dp))
            Text(label, color = c.text, fontSize = font.base, fontWeight = font.medium)
        }
        Text(value, color = valueColor, fontSize = font.sm, fontWeight = font.regular)
    }
}

@Composable
private fun SectionDivider() {
    val c = MaterialTheme.c
    HorizontalDivider(
        modifier  = Modifier.padding(start = 44.dp),
        thickness = 0.5.dp,
        color     = c.divider
    )
}

@Composable
private fun ProfileField(label: String, value: String, onValueChange: (String) -> Unit) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = c.textMuted, fontSize = font.xs, fontWeight = font.medium)
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(10.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = c.primary,
                unfocusedBorderColor = c.border,
                focusedTextColor     = c.text,
                unfocusedTextColor   = c.text,
                cursorColor          = c.primary,
                focusedContainerColor   = c.inputBg,
                unfocusedContainerColor = c.inputBg
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color      = c.text,
                fontSize   = font.base,
                fontWeight = font.regular
            )
        )
    }
}

@Composable
private fun ProfileReadOnly(label: String, value: String) {
    val c    = MaterialTheme.c
    val font = MaterialTheme.font
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = c.textMuted, fontSize = font.xs, fontWeight = font.medium)
        Text(value, color = c.text,      fontSize = font.base, fontWeight = font.regular)
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Double.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
private val Float.sp get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
