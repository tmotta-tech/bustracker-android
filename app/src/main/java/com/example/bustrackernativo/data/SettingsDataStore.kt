package com.example.bustrackernativo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple settings storage using SharedPreferences.
 * Manages app preferences including Low Performance Mode.
 */
class SettingsDataStore(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "bus_tracker_settings",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_LOW_PERFORMANCE_MODE = "low_performance_mode"
        
        @Volatile
        private var INSTANCE: SettingsDataStore? = null
        
        fun getInstance(context: Context): SettingsDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Low Performance Mode settings:
     * - Max 30 markers (vs 100)
     * - Animations disabled
     * - Route lines hidden
     * - Update interval 30s (vs 15s)
     */
    var lowPerformanceMode: Boolean
        get() = prefs.getBoolean(KEY_LOW_PERFORMANCE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_LOW_PERFORMANCE_MODE, value).apply()
    
    /**
     * Debug Mode: Shows log panel with API calls and responses
     */
    var debugMode: Boolean
        get() = prefs.getBoolean("debug_mode", false)
        set(value) = prefs.edit().putBoolean("debug_mode", value).apply()
    
    /**
     * Icon Style: "drop" (teardrop) or "bus" (bus with arrow)
     * Default is "drop" for simplicity
     */
    var iconStyle: String
        get() = prefs.getString("icon_style", "drop") ?: "drop"
        set(value) = prefs.edit().putString("icon_style", value).apply()
}
