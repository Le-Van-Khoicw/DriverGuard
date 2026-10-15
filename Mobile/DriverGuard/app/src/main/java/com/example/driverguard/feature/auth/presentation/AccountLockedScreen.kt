package com.example.driverguard.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun AccountLockedScreen(
    isRejected: Boolean,
    rejectReason: String,
    onLogout: () -> Unit
) {
    val title = if (isRejected) "Yêu cầu đăng ký bị từ chối" else "Tài khoản bị khóa"
    val message = if (isRejected) {
        "Yêu cầu đăng ký tài khoản của bạn không được quản trị viên chấp thuận. " +
                "Nếu cho rằng đây là nhầm lẫn, vui lòng liên hệ quản trị viên."
    } else {
        "Tài khoản của bạn hiện đã bị khóa. Vui lòng liên hệ quản trị viên để biết thêm chi tiết."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.c.danger
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.c.textMuted,
            fontSize = MaterialTheme.font.base
        )

        if (isRejected && rejectReason.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Lý do: $rejectReason",
                textAlign = TextAlign.Center,
                color = MaterialTheme.c.text,
                fontSize = MaterialTheme.font.sm
            )
        }

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            onClick = onLogout
        ) {
            Text(
                text = "Đăng xuất",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.base,
                fontWeight = MaterialTheme.font.medium
            )
        }
    }
}