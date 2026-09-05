package com.example.driverguard.core.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quản lý trạng thái Cửa sổ nổi (Picture-in-Picture) cho toàn ứng dụng.
 */
object PipManager {
    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    // Trạng thái theo dõi camera đang chạy hay không
    var isMonitoringActive: Boolean = false

    fun updatePipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    /** Chủ động kích hoạt chế độ Cửa sổ nổi PiP */
    fun enterPipMode(activity: Activity?) {
        if (activity == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
