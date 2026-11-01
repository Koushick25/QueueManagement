package com.example.adaptivequeue.data.location

data class LocationState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val isAvailable: Boolean = false,
    val hasPermission: Boolean = false,
    val errorMessage: String? = null
)
