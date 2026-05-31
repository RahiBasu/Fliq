package com.fliq.app.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

enum class FocusModeType {
    OFF, EXAM, INTERVIEW, CALL
}

class FocusMode(private val context: Context) {

    companion object {
        const val TAG = "FocusMode"
        const val PREFS_NAME = "fliq_focus_prefs"
        const val KEY_MODE = "focus_mode"
        const val KEY_START_TIME = "focus_start_time"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentMode: FocusModeType = FocusModeType.OFF
        private set

    var onModeChanged: ((FocusModeType) -> Unit)? = null

    fun activate(mode: FocusModeType) {
        currentMode = mode
        prefs.edit().apply {
            putString(KEY_MODE, mode.name)
            putLong(KEY_START_TIME, System.currentTimeMillis())
            apply()
        }
        Log.d(TAG, "Focus mode activated: $mode")
        onModeChanged?.invoke(mode)
    }

    fun deactivate() {
        currentMode = FocusModeType.OFF
        prefs.edit().apply {
            putString(KEY_MODE, FocusModeType.OFF.name)
            apply()
        }
        Log.d(TAG, "Focus mode deactivated")
        onModeChanged?.invoke(FocusModeType.OFF)
    }

    fun isActive(): Boolean = currentMode != FocusModeType.OFF

    fun getActiveDuration(): Long {
        if (!isActive()) return 0L
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        return System.currentTimeMillis() - startTime
    }

    fun getNotificationText(): String {
        return when (currentMode) {
            FocusModeType.EXAM -> "Exam mode — full bandwidth locked"
            FocusModeType.INTERVIEW -> "Interview mode — connection secured"
            FocusModeType.CALL -> "Call mode — priority active"
            FocusModeType.OFF -> "Optimizing your network..."
        }
    }

    fun getScanPriority(): Int {
        return when (currentMode) {
            FocusModeType.EXAM, FocusModeType.INTERVIEW -> 5
            FocusModeType.CALL -> 4
            FocusModeType.OFF -> 2
        }
    }
}