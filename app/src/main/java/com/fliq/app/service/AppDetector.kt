package com.fliq.app.service

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

enum class AppCategory {
    VIDEO_CALL, STREAMING, EDTECH, BACKGROUND, UNKNOWN
}

data class ActiveAppInfo(
    val packageName: String,
    val category: AppCategory,
    val appName: String
)

class AppDetector(private val context: Context) {

    companion object {
        const val TAG = "AppDetector"
        val VIDEO_CALL_APPS = setOf(
            "us.zoom.videomeetings",
            "com.google.android.apps.meetings",
            "com.microsoft.teams",
            "com.discord",
            "com.whatsapp"
        )
        val STREAMING_APPS = setOf(
            "com.google.android.youtube",
            "in.startv.hotstar",
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.sonyliv",
            "com.jio.media.jiocinema"
        )
        val EDTECH_APPS = setOf(
            "com.physicswallah.live",
            "com.unacademy",
            "com.byjus.thelearningapp",
            "com.vedantu.student"
        )
    }

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun getActiveApp(): ActiveAppInfo {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 5000
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, startTime, endTime
            )
            val activePackage = stats
                ?.filter { it.lastTimeUsed > startTime }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName ?: "unknown"
            Log.d(TAG, "Active app: $activePackage")
            categorize(activePackage)
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            ActiveAppInfo("unknown", AppCategory.UNKNOWN, "Unknown")
        }
    }

    private fun categorize(packageName: String): ActiveAppInfo {
        return when (packageName) {
            in VIDEO_CALL_APPS -> ActiveAppInfo(packageName, AppCategory.VIDEO_CALL, "Video Call")
            in STREAMING_APPS -> ActiveAppInfo(packageName, AppCategory.STREAMING, "Streaming")
            in EDTECH_APPS -> ActiveAppInfo(packageName, AppCategory.EDTECH, "EdTech")
            "com.fliq.app" -> ActiveAppInfo(packageName, AppCategory.UNKNOWN, "Fliq")
            else -> ActiveAppInfo(packageName, AppCategory.BACKGROUND, "Background")
        }
    }

    fun shouldPrioritize(appInfo: ActiveAppInfo): Boolean {
        return appInfo.category in listOf(
            AppCategory.VIDEO_CALL,
            AppCategory.STREAMING,
            AppCategory.EDTECH
        )
    }
}