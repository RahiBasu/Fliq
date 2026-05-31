package com.fliq.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fliq.app.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream

class FliqVpnService : VpnService() {

    companion object {
        const val TAG = "FliqVpnService"
        const val CHANNEL_ID = "fliq_vpn_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val SCAN_INTERVAL = 2000L
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var forwardingThread: Thread? = null

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var appDetector: AppDetector
    private lateinit var locationLearner: LocationLearner
    private lateinit var focusMode: FocusMode

    private val handler = Handler(Looper.getMainLooper())
    private var currentNotificationText = "Optimizing your network..."

    private val scanTask = object : Runnable {
        override fun run() {
            if (isRunning) {
                scanAndOptimize()
                handler.postDelayed(this, SCAN_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this)
        appDetector = AppDetector(this)
        locationLearner = LocationLearner(this)
        focusMode = FocusMode(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Starting Fliq VPN Service")
                startForeground(NOTIFICATION_ID, buildNotification(currentNotificationText))
                startVpn()
                START_STICKY
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stopping Fliq VPN Service")
                stopVpn()
                stopSelf()
                START_NOT_STICKY
            }
            "ACTION_FOCUS_EXAM" -> {
                focusMode.activate(FocusModeType.EXAM)
                START_STICKY
            }
            "ACTION_FOCUS_INTERVIEW" -> {
                focusMode.activate(FocusModeType.INTERVIEW)
                START_STICKY
            }
            "ACTION_FOCUS_CALL" -> {
                focusMode.activate(FocusModeType.CALL)
                START_STICKY
            }
            "ACTION_FOCUS_OFF" -> {
                focusMode.deactivate()
                START_STICKY
            }
            else -> START_STICKY
        }
    }

    private fun startVpn() {
        try {
            val builder = Builder()
                .setSession("Fliq")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("8.8.4.4")
                .addRoute("240.0.0.0", 4)
                .setMtu(1500)

            builder.addDisallowedApplication(packageName)
            builder.addDisallowedApplication("com.google.android.youtube")
            builder.addDisallowedApplication("com.android.chrome")
            builder.addDisallowedApplication("com.google.android.gms")
            builder.addDisallowedApplication("com.google.android.googlequicksearchbox")
            builder.addDisallowedApplication("in.startv.hotstar")
            builder.addDisallowedApplication("com.netflix.mediaclient")
            builder.addDisallowedApplication("com.google.android.apps.meetings")
            builder.addDisallowedApplication("us.zoom.videomeetings")
            builder.addDisallowedApplication("com.physicswallah.live")
            builder.addDisallowedApplication("com.unacademy")
            builder.addDisallowedApplication("com.whatsapp")
            builder.addDisallowedApplication("org.telegram.messenger")
            builder.addDisallowedApplication("com.instagram.android")
            builder.addDisallowedApplication("com.twitter.android")
            builder.addDisallowedApplication("com.facebook.katana")
            builder.addDisallowedApplication("com.sec.android.app.sbrowser")
            builder.addDisallowedApplication("com.spotify.music")

            vpnInterface = builder.establish()
            isRunning = true
            Log.d(TAG, "Fliq VPN tunnel established ✅")

            forwardingThread = Thread {
                forwardPackets()
            }.also { it.start() }

            networkMonitor.startMonitoring { status ->
                Log.d(TAG, "Network changed: ${status.networkName} strength: ${status.signalStrength}")
                updateNotification("Connected via ${status.networkName}")
            }

            handler.post(scanTask)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN: ${e.message}")
            isRunning = false
        }
    }

    private fun forwardPackets() {
        val vpnFd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(vpnFd)
        val outputStream = FileOutputStream(vpnFd)
        val buffer = ByteArray(32767)

        Log.d(TAG, "Packet forwarding started ✅")

        while (isRunning) {
            try {
                val length = inputStream.read(buffer)
                if (length > 0) {
                    outputStream.write(buffer, 0, length)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Packet forwarding error: ${e.message}")
                }
                break
            }
        }
        Log.d(TAG, "Packet forwarding stopped")
    }

    private fun scanAndOptimize() {
        try {
            val activeApp = appDetector.getActiveApp()
            val networkStatus = networkMonitor.getCurrentStatus()

            locationLearner.saveNetworkForLocation(
                networkStatus.networkName,
                networkStatus.type.name,
                networkStatus.signalStrength
            )

            Log.d(TAG, "Scan — Network: ${networkStatus.networkName} " +
                    "| Signal: ${networkStatus.signalStrength} " +
                    "| Active app: ${activeApp.appName} " +
                    "| Focus: ${focusMode.currentMode}")

            val notifText = when {
                focusMode.isActive() -> focusMode.getNotificationText()
                appDetector.shouldPrioritize(activeApp) -> {
                    "Priority mode: ${activeApp.appName} gets full bandwidth"
                }
                networkStatus.signalStrength >= 3 -> {
                    "Strong signal on ${networkStatus.networkName}"
                }
                networkStatus.signalStrength in 1..2 -> {
                    "Weak signal — monitoring for better network"
                }
                else -> "Scanning for best network..."
            }

            updateNotification(notifText)

            val broadcastIntent = Intent("com.fliq.app.STATUS_UPDATE").apply {
                putExtra("networkName", networkStatus.networkName)
                putExtra("signalStrength", networkStatus.signalStrength)
                putExtra("activeApp", activeApp.appName)
                putExtra("focusMode", focusMode.currentMode.name)
            }
            sendBroadcast(broadcastIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Error during scan: ${e.message}")
        }
    }

    private fun stopVpn() {
        isRunning = false
        handler.removeCallbacks(scanTask)
        networkMonitor.stopMonitoring()
        forwardingThread?.interrupt()
        forwardingThread = null
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "Fliq VPN tunnel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        currentNotificationText = text
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fliq is active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Fliq Network Service", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps Fliq running in the background" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}