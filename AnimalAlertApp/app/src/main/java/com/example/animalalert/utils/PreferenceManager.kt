package com.example.animalalert.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.animalalert.model.DetectionHistory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AnimalAlertPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_TOTAL_DETECTIONS = "total_detections"
        private const val KEY_TODAY_DETECTIONS = "today_detections"
        private const val KEY_LAST_DETECTION_DATE = "last_detection_date"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_EMAIL_NOTIFICATION_ENABLED = "email_notification_enabled"
        private const val KEY_SMS_NOTIFICATION_ENABLED = "sms_notification_enabled"
        private const val KEY_IN_APP_NOTIFICATION_ENABLED = "in_app_notification_enabled"
        private const val KEY_DETECTION_HISTORY = "detection_history"
        private const val MAX_HISTORY_SIZE = 50 // Keep last 50 detections
    }
    
    private val gson = Gson()

    fun setLoggedIn(isLoggedIn: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun saveUserData(name: String, email: String, phone: String) {
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_PHONE, phone)
            apply()
        }
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun getUserPhone(): String {
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }

    fun incrementTotalDetections() {
        val current = prefs.getInt(KEY_TOTAL_DETECTIONS, 0)
        prefs.edit().putInt(KEY_TOTAL_DETECTIONS, current + 1).apply()
    }

    fun getTotalDetections(): Int {
        return prefs.getInt(KEY_TOTAL_DETECTIONS, 0)
    }

    fun incrementTodayDetections() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_DETECTION_DATE, "")
        
        if (lastDate != today) {
            prefs.edit().putInt(KEY_TODAY_DETECTIONS, 1).putString(KEY_LAST_DETECTION_DATE, today).apply()
        } else {
            val current = prefs.getInt(KEY_TODAY_DETECTIONS, 0)
            prefs.edit().putInt(KEY_TODAY_DETECTIONS, current + 1).apply()
        }
    }

    fun getTodayDetections(): Int {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_DETECTION_DATE, "")
        
        return if (lastDate == today) {
            prefs.getInt(KEY_TODAY_DETECTIONS, 0)
        } else {
            0
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // Notification preferences
    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_ENABLED, true)
    }

    fun setEmailNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isEmailNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_NOTIFICATION_ENABLED, false)
    }

    fun setSmsNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMS_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isSmsNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_SMS_NOTIFICATION_ENABLED, false)
    }

    fun setInAppNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IN_APP_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun isInAppNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_IN_APP_NOTIFICATION_ENABLED, true)
    }
    
    // Detection History
    fun addDetectionHistory(detection: DetectionHistory) {
        val history = getDetectionHistory().toMutableList()
        history.add(0, detection) // Add to beginning
        
        // Keep only last MAX_HISTORY_SIZE detections
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_DETECTION_HISTORY, json).apply()
    }
    
    fun getDetectionHistory(): List<DetectionHistory> {
        val json = prefs.getString(KEY_DETECTION_HISTORY, null)
        return if (json != null) {
            val type = object : TypeToken<List<DetectionHistory>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun clearDetectionHistory() {
        prefs.edit().remove(KEY_DETECTION_HISTORY).apply()
    }
}

