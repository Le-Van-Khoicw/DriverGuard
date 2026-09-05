package com.example.driverguard.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onBackToLogin: () -> Unit,
    onVerificationRequired: () -> Unit,
    onRegistrationCompleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(
        uiState.isAuthenticated,
        uiState.canEnterApp,
        uiState.isLoading
    ) {
        when {
            uiState.canEnterApp -> onRegistrationCompleted()
            uiState.isAuthenticated && !uiState.isLoading -> onVerificationRequired()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.c.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tạo tài khoản",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.c.text
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Đăng ký tài khoản DriverGuard",
            color = MaterialTheme.c.textMuted,
            fontSize = MaterialTheme.font.base
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.email,
            enabled = !uiState.isLoading,
            singleLine = true,
            label = { Text("Email", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
            placeholder = { Text("example@gmail.com", color = MaterialTheme.c.textMuted, fontSize = MaterialTheme.font.sm) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            onValueChange = viewModel::onEmailChange
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.password,
            enabled = !uiState.isLoading,
            singleLine = true,
            label = { Text("Mật khẩu", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            onValueChange = viewModel::onPasswordChange
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.confirmPassword,
            enabled = !uiState.isLoading,
            singleLine = true,
            label = { Text("Xác nhận mật khẩu", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            onValueChange = viewModel::onConfirmPasswordChange
        )

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
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
            onClick = viewModel::register
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.c.textOnColor
                )
            } else {
                Text(
                    text = "Đăng ký",
                    color = MaterialTheme.c.textOnColor,
                    fontSize = MaterialTheme.font.base,
                    fontWeight = MaterialTheme.font.semibold
                )
            }
        }

        if (uiState.isVerificationEmailSent) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Đã gửi thư xác minh đến ${uiState.email}. " +
                    "Hãy mở email và bấm vào đường dẫn xác minh.",
                color = MaterialTheme.c.safe,
                fontSize = MaterialTheme.font.sm
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading,
                onClick = viewModel::checkEmailVerification
            ) {
                Text(
                    text = "Tôi đã xác minh email",
                    color = MaterialTheme.c.primary,
                    fontSize = MaterialTheme.font.base,
                    fontWeight = MaterialTheme.font.medium
                )
            }
            TextButton(
                enabled = !uiState.isLoading,
                onClick = viewModel::resendVerificationEmail
            ) {
                Text(
                    text = "Gửi lại email xác minh",
                    color = MaterialTheme.c.primary,
                    fontSize = MaterialTheme.font.sm
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            enabled = !uiState.isLoading,
            onClick = onBackToLogin
        ) {
            Text(
                text = "Đã có tài khoản? Quay lại đăng nhập",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.sm
            )
        }
    }
}
