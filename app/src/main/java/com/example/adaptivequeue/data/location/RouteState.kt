package com.example.adaptivequeue.data.location

import java.util.Locale

data class RouteState(
    val distanceMeters: Double? = null,
    val durationSeconds: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val cooldownMessage: String? = null,
    val lastUpdateTime: Long? = null
) {
    val formattedDistance: String?
        get() {
            val meters = distanceMeters ?: return null
            return if (meters >= 1000) {
                String.format(Locale.US, "%.2f km", meters / 1000.0)
            } else {
                String.format(Locale.US, "%.0f m", meters)
            }
        }

    val formattedDuration: String?
        get() {
            val seconds = durationSeconds ?: return null
            val minutes = Math.round(seconds / 60.0)
            return "$minutes min"
        }
}
