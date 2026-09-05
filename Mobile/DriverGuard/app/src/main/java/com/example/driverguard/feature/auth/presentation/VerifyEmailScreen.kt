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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun VerifyEmailScreen(
    viewModel: AuthViewModel,
    onVerificationCompleted: () -> Unit,
    onUseAnotherAccount: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val email = uiState.currentUser?.email ?: uiState.email

    LaunchedEffect(uiState.canEnterApp) {
        if (uiState.canEnterApp) onVerificationCompleted()
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
            text = "Xác minh email",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.c.text
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Hãy mở hộp thư và bấm vào đường dẫn xác minh được gửi đến:",
            textAlign = TextAlign.Center,
            color = MaterialTheme.c.textMuted,
            fontSize = MaterialTheme.font.base
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = email.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.c.primary
        )

        if (uiState.isVerificationEmailSent && uiState.errorMessage == null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Đã gửi thư xác minh. Hãy kiểm tra cả thư rác.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.c.safe,
                fontSize = MaterialTheme.font.sm
            )
        }

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                color = MaterialTheme.c.danger,
                fontSize = MaterialTheme.font.sm
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !uiState.isLoading,
            onClick = viewModel::checkEmailVerification
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.c.textOnColor
                )
            } else {
                Text(
                    text = "Tôi đã xác minh",
                    color = MaterialTheme.c.textOnColor,
                    fontSize = MaterialTheme.font.base,
                    fontWeight = MaterialTheme.font.semibold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !uiState.isLoading,
            onClick = viewModel::resendVerificationEmail
        ) {
            Text(
                text = "Gửi lại email xác minh",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.base,
                fontWeight = MaterialTheme.font.medium
            )
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            enabled = !uiState.isLoading,
            onClick = onUseAnotherAccount
        ) {
            Text(
                text = "Dùng tài khoản khác",
                color = MaterialTheme.c.textSubtle,
                fontSize = MaterialTheme.font.sm
            )
        }
    }
}
