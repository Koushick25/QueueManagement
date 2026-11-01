package com.example.adaptivequeue.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return finePermission || coarsePermission
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(): Flow<LocationState> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(
                LocationState(
                    hasPermission = false,
                    errorMessage = "Location permission not granted"
                )
            )
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).setMinUpdateIntervalMillis(2000L).build()

        var lastValidState: LocationState? = null

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val newState = LocationState(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        isAvailable = true,
                        hasPermission = true
                    )
                    lastValidState = newState
                    trySend(newState)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable && lastValidState == null) {
                    trySend(
                        LocationState(
                            isAvailable = false,
                            hasPermission = true,
                            errorMessage = "Searching for location..."
                        )
                    )
                }
            }
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val newState = LocationState(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    isAvailable = true,
                    hasPermission = true
                )
                lastValidState = newState
                trySend(newState)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }
}
