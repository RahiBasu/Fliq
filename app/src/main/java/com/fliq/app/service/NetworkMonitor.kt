package com.fliq.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log

data class NetworkStatus(
    val type: NetworkType,
    val signalStrength: Int,
    val networkName: String,
    val isConnected: Boolean
)

enum class NetworkType {
    WIFI, MOBILE, NONE
}

class NetworkMonitor(private val context: Context) {

    companion object {
        const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var onNetworkChanged: ((NetworkStatus) -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Start listening for network changes
    fun startMonitoring(onChanged: (NetworkStatus) -> Unit) {
        this.onNetworkChanged = onChanged

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                onChanged(getCurrentStatus())
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                onChanged(getCurrentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                Log.d(TAG, "Network capabilities changed")
                onChanged(getCurrentStatus())
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        Log.d(TAG, "Network monitoring started")
    }

    // Stop listening
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
        Log.d(TAG, "Network monitoring stopped")
    }

    // Get current network status
    fun getCurrentStatus(): NetworkStatus {
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)

        if (caps == null) {
            return NetworkStatus(
                type = NetworkType.NONE,
                signalStrength = 0,
                networkName = "No connection",
                isConnected = false
            )
        }

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wifiInfo = wifiManager.connectionInfo
                val rssi = wifiInfo.rssi
                val strength = WifiManager.calculateSignalLevel(rssi, 5)
                NetworkStatus(
                    type = NetworkType.WIFI,
                    signalStrength = strength,
                    networkName = wifiInfo.ssid.replace("\"", ""),
                    isConnected = true
                )
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val strength = getSimSignalStrength()
                val carrier = telephonyManager.networkOperatorName
                NetworkStatus(
                    type = NetworkType.MOBILE,
                    signalStrength = strength,
                    networkName = carrier.ifEmpty { "Mobile Data" },
                    isConnected = true
                )
            }
            else -> NetworkStatus(
                type = NetworkType.NONE,
                signalStrength = 0,
                networkName = "Unknown",
                isConnected = false
            )
        }
    }

    // Get SIM signal strength (0-4)
    private fun getSimSignalStrength(): Int {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val signalStrength = telephonyManager.signalStrength
                signalStrength?.level ?: 2
            } else {
                2
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SIM signal: ${e.message}")
            2
        }
    }

    // Compare two networks and return the better one
    fun getBetterNetwork(a: NetworkStatus, b: NetworkStatus): NetworkStatus {
        return if (a.signalStrength >= b.signalStrength) a else b
    }
}