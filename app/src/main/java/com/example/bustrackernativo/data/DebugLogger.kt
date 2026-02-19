package com.example.bustrackernativo.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple in-memory debug logger for displaying logs in the app UI.
 * Keeps last 50 log entries.
 */
object DebugLogger {
    private val logs = mutableListOf<String>()
    private const val MAX_LOGS = 50
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    var onLogAdded: (() -> Unit)? = null
    
    fun log(tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = "[$timestamp] $tag: $message"
        synchronized(logs) {
            logs.add(entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
        // Also log to Android logcat
        android.util.Log.d(tag, message)
        onLogAdded?.invoke()
    }
    
    fun getLogs(): List<String> {
        synchronized(logs) {
            return logs.toList()
        }
    }
    
    fun clear() {
        synchronized(logs) {
            logs.clear()
        }
    }
}
