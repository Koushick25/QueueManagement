package com.example.adaptivequeue.data.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VenueLocationStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("venue_prefs", Context.MODE_PRIVATE)

    private val _venueLocation = MutableStateFlow(loadVenueLocation())
    val venueLocation: StateFlow<VenueLocation> = _venueLocation.asStateFlow()

    private fun loadVenueLocation(): VenueLocation {
        val latStr = prefs.getString(KEY_LATITUDE_STR, null)
        val lngStr = prefs.getString(KEY_LONGITUDE_STR, null)

        if (latStr != null && lngStr != null) {
            val lat = latStr.toDoubleOrNull()
            val lng = lngStr.toDoubleOrNull()
            if (lat != null && lng != null) {
                return VenueLocation(latitude = lat, longitude = lng, isSet = true)
            }
        }

        if (prefs.contains(KEY_LATITUDE) && prefs.contains(KEY_LONGITUDE)) {
            val lat = prefs.getFloat(KEY_LATITUDE, 0f).toDouble()
            val lng = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()
            return VenueLocation(latitude = lat, longitude = lng, isSet = true)
        }

        return VenueLocation()
    }

    fun saveVenueLocation(latitude: Double, longitude: Double) {
        prefs.edit()
            .putString(KEY_LATITUDE_STR, latitude.toString())
            .putString(KEY_LONGITUDE_STR, longitude.toString())
            .apply()

        _venueLocation.value = VenueLocation(latitude = latitude, longitude = longitude, isSet = true)
    }

    fun clearVenueLocation() {
        prefs.edit().clear().apply()
        _venueLocation.value = VenueLocation()
    }

    companion object {
        private const val KEY_LATITUDE = "venue_latitude"
        private const val KEY_LONGITUDE = "venue_longitude"
        private const val KEY_LATITUDE_STR = "venue_latitude_str"
        private const val KEY_LONGITUDE_STR = "venue_longitude_str"
    }
}
