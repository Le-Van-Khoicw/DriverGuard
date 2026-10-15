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
fun PendingApprovalScreen(
    onLogout: () -> Unit
) {
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
            text = "Đang chờ duyệt",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.c.text
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tài khoản của bạn đã đăng ký thành công! Vui lòng chờ quản trị viên kích hoạt. " +
                    "Màn hình này sẽ tự chuyển tiếp ngay khi được duyệt.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.c.textMuted,
            fontSize = MaterialTheme.font.base
        )

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