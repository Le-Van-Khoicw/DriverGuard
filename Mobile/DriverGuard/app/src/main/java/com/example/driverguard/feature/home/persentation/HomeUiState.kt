package com.example.driverguard.feature.home.persentation

data class HomeUiState(
    val driverName: String = "Tài xế DriverGuard",
    val deviceName: String = "Camera điện thoại",
    val deviceCode: String = "PHONE-001",
    val isDeviceOnline: Boolean = true,
    val todayAlertCount: Int = 0,
    val latestAlert: String? = null
)
