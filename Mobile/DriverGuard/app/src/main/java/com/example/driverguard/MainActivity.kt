package com.example.driverguard

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.driverguard.core.pip.PipManager
import com.example.driverguard.core.theme.DriverGuardTheme
import com.example.driverguard.core.theme.ThemeManager
import com.example.driverguard.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val isDark by ThemeManager.isDarkMode.collectAsState()
            DriverGuardTheme(darkTheme = isDark) {
                AppNavigation()
            }
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