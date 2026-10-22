package com.example.driverguard.feature.dispatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Vehicle identity comes from company assignments, not personal profile fields. */
object CompanyVehicleState {
    private val _current = MutableStateFlow<AssignedTrip?>(null)
    val current = _current.asStateFlow()
    fun update(trips: List<AssignedTrip>) {
        val active = trips.filter { it.status in listOf("in_progress", "handover_pending", "accepted", "assigned") }
        _current.value = active.sortedWith(compareBy<AssignedTrip> {
            when (it.status) { "in_progress" -> 0; "handover_pending" -> 1; "accepted" -> 2; else -> 3 }
        }.thenBy { it.start }).firstOrNull()
    }
    fun clear() { _current.value = null }
}
