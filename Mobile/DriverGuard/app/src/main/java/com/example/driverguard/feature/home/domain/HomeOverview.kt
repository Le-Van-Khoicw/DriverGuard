package com.example.driverguard.feature.home.domain

import com.example.driverguard.feature.auth.monitoring.MonitoringStatus
import java.time.Instant

data class HomeOverview(
    val driverName: String,
    val avatarUrl: String?,

    //NUll nghia la chua co tai khoan nao lien ket voi camera
    val linkedDevice: LinkedDevice?,
    val monitoringStatus: HomeMonitoringStatus,
    val todayAlertCount: Int,

    //NUll nghia la chua co phien giam sat
    val lastMonitoringAt: Instant?,

    //Null nghia la chua co canh bao nao
    val latestAlert: LatestDrowsinessAlert?

)
// camera duoc ker noi voi tai khan
data class LinkedDevice(
    val id: String,
    val code: String,
    val name: String,
    val source: CameraSource,
    val isOnline: Boolean
)
// nguoon hinh anh dung de giam saat
enum class CameraSource {
    PHONE,
    EMBEDDED
}

// TRANG thai giam sat hien tai tren home
enum class HomeMonitoringStatus{
    NO_DEVICE,
    OFFLINE,
    READY,
    MONITORING,
    WARNING
}
// canh bao buon ngu gan nhat
data class LatestDrowsinessAlert(
    val id: String,
    val deviceName: String,
    val occurredAt: Instant,

    val ear: Double?,
    val confidence: Double?,
    val confidendce: Double?
)