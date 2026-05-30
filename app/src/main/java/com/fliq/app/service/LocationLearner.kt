package com.fliq.app.service

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.util.Log

class LocationLearner(private val context: Context) {

    companion object {
        const val TAG = "LocationLearner"
        const val PREFS_NAME = "fliq_location_prefs"
        const val GRID_SIZE = 0.01 // ~1km grid
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Save best network for current location
    fun saveNetworkForLocation(networkName: String, networkType: String, signalStrength: Int) {
        val location = getCurrentLocation() ?: return
        val key = locationKey(location.latitude, location.longitude)

        val existing = prefs.getInt("${key}_signal", -1)
        if (signalStrength > existing) {
            prefs.edit().apply {
                putString("${key}_network", networkName)
                putString("${key}_type", networkType)
                putInt("${key}_signal", signalStrength)
                apply()
            }
            Log.d(TAG, "Saved: $networkName (signal: $signalStrength) for location $key")
        }
    }

    // Get best network for current location
    fun getBestNetworkForLocation(): String? {
        val location = getCurrentLocation() ?: return null
        val key = locationKey(location.latitude, location.longitude)
        return prefs.getString("${key}_network", null)
    }

    // Get all learned locations count
    fun getLearnedLocationsCount(): Int {
        return prefs.all.keys
            .filter { it.endsWith("_network") }
            .size
    }

    // Convert lat/lng to grid key
    private fun locationKey(lat: Double, lng: Double): String {
        val gridLat = (lat / GRID_SIZE).toInt()
        val gridLng = (lng / GRID_SIZE).toInt()
        return "${gridLat}_${gridLng}"
    }

    // Get current location
    private fun getCurrentLocation(): Location? {
        return try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            providers.mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }.maxByOrNull { it.accuracy }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}")
            null
        }
    }
}