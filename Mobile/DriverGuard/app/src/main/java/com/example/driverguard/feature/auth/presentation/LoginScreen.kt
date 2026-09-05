package com.example.driverguard.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.driverguard.core.theme.c
import com.example.driverguard.core.theme.font

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onVerificationRequired: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onGoogleLoginClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.isAuthenticated, uiState.canEnterApp) {
        when {
            uiState.canEnterApp -> onLoginSuccess()
            uiState.isAuthenticated -> onVerificationRequired()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DriverGuard",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.c.text
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Đăng nhập để tiếp tục",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.c.textMuted
        )
        Spacer(Modifier.height(32.dp))

        EmailTextField(
            email = uiState.email,
            enabled = !uiState.isLoading,
            onEmailChange = viewModel::onEmailChange
        )
        Spacer(Modifier.height(16.dp))

        PasswordTextField(
            password = uiState.password,
            isPasswordVisible = isPasswordVisible,
            enabled = !uiState.isLoading,
            onPasswordChange = viewModel::onPasswordChange,
            onTogglePasswordVisibility = { isPasswordVisible = !isPasswordVisible }
        )

        TextButton(
            modifier = Modifier.align(Alignment.End),
            enabled = !uiState.isLoading,
            onClick = onNavigateToForgotPassword
        ) {
            Text(
                text = "Quên mật khẩu?",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.sm
            )
        }

        uiState.errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.c.danger,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !uiState.isLoading,
            onClick = viewModel::login
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.c.textOnColor
                )
            } else {
                Text(
                    text = "Đăng nhập",
                    color = MaterialTheme.c.textOnColor,
                    fontSize = MaterialTheme.font.base,
                    fontWeight = MaterialTheme.font.semibold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.c.divider)
        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !uiState.isLoading,
            onClick = onGoogleLoginClick
        ) {
            Text(
                text = "Tiếp tục với Google",
                color = MaterialTheme.c.text,
                fontSize = MaterialTheme.font.base,
                fontWeight = MaterialTheme.font.medium
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            enabled = !uiState.isLoading,
            onClick = onNavigateToRegister
        ) {
            Text(
                text = "Chưa có tài khoản? Đăng ký",
                color = MaterialTheme.c.primary,
                fontSize = MaterialTheme.font.sm
            )
        }
    }
}

@Composable
private fun EmailTextField(
    email: String,
    enabled: Boolean,
    onEmailChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = email,
        enabled = enabled,
        singleLine = true,
        label = { Text("Email", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
        placeholder = { Text("example@gmail.com", color = MaterialTheme.c.textMuted, fontSize = MaterialTheme.font.sm) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        onValueChange = onEmailChange
    )
}

@Composable
private fun PasswordTextField(
    password: String,
    isPasswordVisible: Boolean,
    enabled: Boolean,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = password,
        enabled = enabled,
        singleLine = true,
        label = { Text("Mật khẩu", color = MaterialTheme.c.textSubtle, fontSize = MaterialTheme.font.sm) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        visualTransformation = if (isPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(
                enabled = enabled,
                onClick = onTogglePasswordVisibility
            ) {
                Text(
                    text = if (isPasswordVisible) "Ẩn" else "Hiện",
                    color = MaterialTheme.c.primary,
                    fontSize = MaterialTheme.font.sm
                )
            }
        },
        onValueChange = onPasswordChange
    )
}
