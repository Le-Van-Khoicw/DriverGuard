package com.example.driverguard.feature.home.persentation

import com.example.driverguard.core.device.DeviceUtils

data class HomeUiState(
    val driverName: String = "Tài xế",
    val deviceName: String = DeviceUtils.deviceName,
    val deviceCode: String = DeviceUtils.deviceCode,
    val isDeviceOnline: Boolean = true,
    val todayAlertCount: Int = 0,
    val latestAlert: String = "Chưa có chuyến đi",
    val vehicleName: String = "",
    val isVehicleConfigured: Boolean = false
)
