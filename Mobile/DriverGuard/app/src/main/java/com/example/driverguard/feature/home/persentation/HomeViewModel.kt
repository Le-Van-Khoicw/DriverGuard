package com.example.driverguard.feature.home.persentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverguard.core.device.DeviceUtils
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.settings.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        UserProfileRepository.profile,
        AlarmRepository.savedTrips,
        AlarmRepository.events
    ) { profile, trips, alerts ->
        val latestTrip = trips.firstOrNull()
        val totalAlertsInTrips = trips.sumOf { it.totalAlerts }
        val latestLabel = latestTrip?.let {
            "${it.safetyScore}đ (${it.scoreGrade}) · ${it.totalAlerts} cảnh báo"
        } ?: if (alerts.isNotEmpty()) "EAR ${"%.3f".format(alerts.first().ear)}" else "Chưa có cảnh báo"

        HomeUiState(
            driverName = profile.displayName,
            deviceName = profile.deviceName.ifBlank { DeviceUtils.deviceName },
            deviceCode = profile.deviceCode.ifBlank { DeviceUtils.deviceCode },
            isDeviceOnline = true,
            todayAlertCount = totalAlertsInTrips.coerceAtLeast(alerts.size),
            latestAlert = latestLabel,
            vehicleName = profile.vehicleName,
            isVehicleConfigured = profile.isVehicleConfigured
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}
