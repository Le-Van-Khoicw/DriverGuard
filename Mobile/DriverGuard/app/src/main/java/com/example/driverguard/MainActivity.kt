package com.example.driverguard

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.example.driverguard.core.pip.PipManager
import com.example.driverguard.core.theme.DriverGuardTheme
import com.example.driverguard.core.theme.ThemeManager
import com.example.driverguard.feature.settings.UserProfileRepository
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.driverguard.core.theme.c
import com.example.driverguard.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Tự động nạp hồ sơ người dùng sau khi cấp quyền
        UserProfileRepository.loadUserProfile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        UserProfileRepository.loadUserProfile()

        // 🌟 Tự động xin các quyền thiết yếu (Camera, Vị trí GPS, Thông báo) ngay khi vừa tải/mở ứng dụng
        requestEssentialPermissions()

        setContent {
            val isDark by ThemeManager.isDarkMode.collectAsState()
            DriverGuardTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.c.bg
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun requestEssentialPermissions() {
        val required = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /** Khi tài xế bấm nút Home hoặc chuyển sang app Grab/Shopee/Google Maps -> Tự động thu nhỏ thành Cửa sổ nổi */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipManager.isMonitoringActive) {
            PipManager.enterPipMode(this)
        }
    }

    /** Cập nhật trạng thái PiP cho giao diện Compose */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipManager.updatePipMode(isInPictureInPictureMode)
    }
}