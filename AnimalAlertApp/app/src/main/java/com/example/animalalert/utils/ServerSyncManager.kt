package com.example.animalalert.utils

import android.content.Context
import android.util.Log
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Syncs local SharedPrefs data with the WildTrack Flask server (MongoDB).
 */
object ServerSyncManager {

    private const val TAG = "ServerSyncManager"

    fun configureRetrofit(context: Context) {
        RetrofitClient.configure(context)
    }

    suspend fun syncHistoryFromServer(context: Context): Int {
        val prefs = PreferenceManager(context)
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.getAlertHistory().execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "History sync failed: HTTP ${response.code()}")
                    return@withContext 0
                }

                val serverAlerts = response.body() ?: emptyList()
                val existing = prefs.getDetectionHistory()
                var added = 0

                serverAlerts
                    .filter { it.animal_detected && DetectionHistory.isWildAnimal(it.animal_type) }
                    .forEach { serverAlert ->
                        val detection = serverAlert.toDetectionHistory()
                        val alreadyHave = existing.any { it.timestamp == detection.timestamp }
                        if (!alreadyHave) {
                            prefs.addDetectionHistory(detection)
                            added++
                        }
                    }

                Log.d(TAG, "History sync complete: $added new detections")
                added
            } catch (e: Exception) {
                Log.w(TAG, "History sync error: ${e.message}")
                0
            }
        }
    }
}
