package com.example.animalalert.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.animalalert.data.AppDatabase
import com.example.animalalert.model.DetectionHistory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = AppDatabase.getDatabase(context).detectionDao()

    companion object {
        private const val PREFS_NAME = "AnimalAlertPrefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_TOTAL_DETECTIONS = "total_detections"
        private const val KEY_TODAY_DETECTIONS = "today_detections"
        private const val KEY_LAST_DETECTION_DATE = "last_detection_date"
        private const val KEY_NOTIFICATION_ENABLED = "notification_enabled"
        private const val KEY_EMAIL_NOTIFICATION_ENABLED = "email_notification_enabled"
        private const val KEY_SMS_NOTIFICATION_ENABLED = "sms_notification_enabled"
        private const val KEY_IN_APP_NOTIFICATION_ENABLED = "in_app_notification_enabled"
        private const val KEY_DETECTION_HISTORY = "detection_history"
        private const val MAX_HISTORY_SIZE = 50 // Keep last 50 detections

        // Settings
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_FOLLOW_SYSTEM_THEME = "follow_system_theme"
        const val KEY_IN_APP_SOUND = "in_app_sound"
        const val KEY_AUTO_START_SERVICE = "auto_start_service"
        const val KEY_DANGER_ONLY = "danger_only"
        const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        const val KEY_POLL_INTERVAL_SEC = "poll_interval_sec"
        const val KEY_SHOW_HISTORY_ON_MAP = "show_history_on_map"
        const val KEY_AUTO_CENTER_MAP = "auto_center_map"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_PERSONAL_FOCUS_CAMERA = "personal_focus_camera"
    }
    
    private val gson = Gson()

    fun setPersonalFocusCamera(cameraId: String?) {
        if (cameraId == null) {
            prefs.edit().remove(KEY_PERSONAL_FOCUS_CAMERA).apply()
        } else {
            prefs.edit().putString(KEY_PERSONAL_FOCUS_CAMERA, cameraId).apply()
        }
    }

    fun getPersonalFocusCamera(): String? {
        return prefs.getString(KEY_PERSONAL_FOCUS_CAMERA, null)
    }

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

    fun setAuthToken(token: String?) {
        if (token == null) {
            prefs.edit().remove(KEY_AUTH_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
        }
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun incrementTotalDetections() {
        val current = prefs.getInt(KEY_TOTAL_DETECTIONS, 0)
        prefs.edit().putInt(KEY_TOTAL_DETECTIONS, current + 1).apply()
    }

    fun decrementTotalDetections() {
        val current = prefs.getInt(KEY_TOTAL_DETECTIONS, 0)
        if (current > 0) {
            prefs.edit().putInt(KEY_TOTAL_DETECTIONS, current - 1).apply()
        }
    }

    fun getTotalDetections(): Int {
        return prefs.getInt(KEY_TOTAL_DETECTIONS, 0)
    }

    fun decrementTodayDetections() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDate = prefs.getString(KEY_LAST_DETECTION_DATE, "")

        if (lastDate == today) {
            val current = prefs.getInt(KEY_TODAY_DETECTIONS, 0)
            if (current > 0) {
                prefs.edit().putInt(KEY_TODAY_DETECTIONS, current - 1).apply()
            }
        }
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

    // ── Theme ────────────────────────────────────────────────
    fun setDarkMode(enabled: Boolean) { prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply() }
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setFollowSystemTheme(enabled: Boolean) { prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM_THEME, enabled).apply() }
    fun isFollowSystemTheme(): Boolean = prefs.getBoolean(KEY_FOLLOW_SYSTEM_THEME, true)

    // ── Sound ───────────────────────────────────────────────
    fun setInAppSound(enabled: Boolean) { prefs.edit().putBoolean(KEY_IN_APP_SOUND, enabled).apply() }
    fun isInAppSound(): Boolean = prefs.getBoolean(KEY_IN_APP_SOUND, true)
    fun isInAppSoundEnabled(): Boolean = isInAppSound()

    // ── Alert System ─────────────────────────────────────────
    fun setAutoStartService(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUTO_START_SERVICE, enabled).apply() }
    fun isAutoStartService(): Boolean = prefs.getBoolean(KEY_AUTO_START_SERVICE, true)

    fun setDangerOnly(enabled: Boolean) { prefs.edit().putBoolean(KEY_DANGER_ONLY, enabled).apply() }
    fun isDangerOnly(): Boolean = prefs.getBoolean(KEY_DANGER_ONLY, false)

    fun setConfidenceThreshold(value: Int) { prefs.edit().putInt(KEY_CONFIDENCE_THRESHOLD, value).apply() }
    fun getConfidenceThreshold(): Int = prefs.getInt(KEY_CONFIDENCE_THRESHOLD, 60)

    fun setPollIntervalSec(value: Int) { prefs.edit().putInt(KEY_POLL_INTERVAL_SEC, value).apply() }
    fun getPollIntervalSec(): Int = prefs.getInt(KEY_POLL_INTERVAL_SEC, 3)

    // ── Map ─────────────────────────────────────────────────
    fun setShowHistoryOnMap(enabled: Boolean) { prefs.edit().putBoolean(KEY_SHOW_HISTORY_ON_MAP, enabled).apply() }
    fun isShowHistoryOnMap(): Boolean = prefs.getBoolean(KEY_SHOW_HISTORY_ON_MAP, true)

    fun setAutoCenterMap(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUTO_CENTER_MAP, enabled).apply() }
    fun isAutoCenterMap(): Boolean = prefs.getBoolean(KEY_AUTO_CENTER_MAP, true)

    // ── Server ───────────────────────────────────────────────
    fun setServerUrl(url: String) { prefs.edit().putString(KEY_SERVER_URL, url).apply() }
    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, "https://10.244.200.127:5000") ?: "https://10.244.200.127:5000"

    // Detection History — migrated to Room DB
    fun addDetectionHistory(detection: DetectionHistory) {
        dao.insertDetectionSync(detection)
    }

    fun getDetectionHistory(): List<DetectionHistory> {
        return dao.getRecentDetectionsSync(MAX_HISTORY_SIZE)
    }

    private fun getDetectionHistoryInternal(): List<DetectionHistory> {
        return dao.getRecentDetectionsSync(MAX_HISTORY_SIZE)
    }

    fun clearDetectionHistory() {
        dao.clearHistorySync()
    }

    fun removeDetectionHistory(detectionId: String) {
        // Need to grab the detection first to handle metrics
        val history = dao.getRecentDetectionsSync(100)
        val detectionToRemove = history.find { it.id == detectionId }
        
        if (detectionToRemove != null) {
            dao.deleteDetectionSync(detectionId)
            
            // Decrement total count
            decrementTotalDetections()
            
            // Decrement today's count if it was from today
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = dateFormat.format(java.util.Date())
            val detectionDate = dateFormat.format(java.util.Date(detectionToRemove.timestamp))
            
            if (today == detectionDate) {
                decrementTodayDetections()
            }
        }
    }
}
