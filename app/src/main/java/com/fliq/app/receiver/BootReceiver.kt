package com.fliq.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fliq.app.service.FliqVpnService

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Phone restarted — starting Fliq automatically")

            val vpnIntent = Intent(context, FliqVpnService::class.java).apply {
                action = FliqVpnService.ACTION_START
            }
            context.startForegroundService(vpnIntent)
        }
    }
}