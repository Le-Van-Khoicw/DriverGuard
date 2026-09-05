package com.example.driverguard.feature.home.persentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.driverguard.feature.history.AlarmRepository
import com.example.driverguard.feature.settings.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        UserProfileRepository.profile,
        AlarmRepository.events
    ) { profile, alerts ->
        val latest = alerts.firstOrNull()
        HomeUiState(
            driverName = profile.displayName,
            deviceName = "Camera điện thoại",
            deviceCode = "PHONE-001",
            isDeviceOnline = true,
            todayAlertCount = alerts.size,
            latestAlert = latest?.let { "EAR ${"%.3f".format(it.ear)} · ${it.timeLabel}" } ?: "Chưa có cảnh báo"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}

