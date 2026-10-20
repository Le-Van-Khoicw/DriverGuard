package com.example.driverguard.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.c.bg)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quên mật khẩu",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.c.text
        )
        Text(
            text = if (com.example.driverguard.BuildConfig.FIREBASE_EMULATORS)
                "BẢN LOCAL: chỉ reset tài khoản trong demo-driverguard, không đổi mật khẩu Firebase thật. Emulator không gửi email; đường dẫn reset hiện trong terminal chạy máy chủ."
            else "Nhập email đã đăng ký. Firebase sẽ gửi đường dẫn đặt lại mật khẩu.",
            color = MaterialTheme.c.textMuted,
            fontSize = MaterialTheme.font.base
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        state.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.c.danger,
                fontSize = MaterialTheme.font.sm
            )
        }
        if (state.isPasswordResetEmailSent) {
            Text(
                text = if (com.example.driverguard.BuildConfig.FIREBASE_EMULATORS)
                    "Đã gửi yêu cầu local. Nếu email có tài khoản phù hợp, nhờ người chạy Start-Local.ps1 mở đường dẫn reset trong terminal; không có thư gửi tới hộp mail."
                else "Nếu email có tài khoản phù hợp, hướng dẫn đặt lại mật khẩu sẽ được gửi đến hộp thư. Kiểm tra cả thư rác.",
                color = MaterialTheme.c.safe,
                fontSize = MaterialTheme.font.sm
            )
        }
        Button(
            onClick = viewModel::resetPassword,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.c.textOnColor)
            } else {
                Text(
                    text = if (com.example.driverguard.BuildConfig.FIREBASE_EMULATORS) "Yêu cầu reset mật khẩu local" else "Gửi email đặt lại mật khẩu",
                    color = MaterialTheme.c.textOnColor,
                    fontSize = MaterialTheme.font.base,
                    fontWeight = MaterialTheme.font.semibold
                )
            }
        }
        OutlinedButton(onClick = onBack) {
            Text(
                text = "Quay lại đăng nhập",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.base,
                fontWeight = MaterialTheme.font.medium
            )
        }
    }
}
