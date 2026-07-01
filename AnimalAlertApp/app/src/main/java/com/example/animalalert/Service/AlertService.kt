package com.example.animalalert.Service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.animalalert.R
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.EmailHelper
import com.example.animalalert.utils.NotificationHelper
import com.example.animalalert.utils.PreferenceManager
import com.example.animalalert.utils.ServerSyncManager
import com.example.animalalert.utils.SMSHelper
import kotlinx.coroutines.*
import retrofit2.awaitResponse
import java.util.*

class AlertService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var mediaPlayer: MediaPlayer? = null
    private var userStopped = false
    private var lastNotificationTimestamp: Long = 0
    private val notificationCooldown = 60000L // 1 minute cooldown between notifications
    private var lastDetectionId: String? = null

    private var isMonitoring = false

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "alert_service_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        ServerSyncManager.configureRetrofit(this)
        startForegroundWithNotification()
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Alert Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the animal alert monitoring service running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Animal Alert Active")
            .setContentText("Monitoring for animal detections...")
            .setSmallIcon(R.drawable.ic_alert)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "STOP_SIREN") {
            userStopped = true
            stopSiren()
            return START_STICKY
        }

        if (!isMonitoring) {
            isMonitoring = true
            scope.launch {
                val prefs = PreferenceManager(this@AlertService)
                while (true) {
                    try {
                        val response = RetrofitClient.api.getLatestAlert(null).awaitResponse()

                        if (response.isSuccessful) {
                            val alert = response.body()
                            if (alert != null) {
                                Log.d("AlertService", "Alert fetched: $alert")

                                val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
                                val meetsConfidence = alert.confidence >= prefs.getConfidenceThreshold()
                                val isWild = DetectionHistory.isWildAnimal(alert.animal_type)

                                val isAlertActive = alert.animal_detected && meetsConfidence && (!prefs.isDangerOnly() || (isWild && dangerLevel >= 3))

                                if (isAlertActive && !userStopped) {
                                    if (prefs.isInAppSound()) {
                                        playSiren()
                                    } else {
                                        stopSiren()
                                    }
                                    
                                    // Save to detection history (avoid duplicates)
                                    val detectionId = "${alert.timestamp}_${alert.animal_type}"
                                    if (detectionId != lastDetectionId) {
                                        // Update statistics
                                        prefs.incrementTotalDetections()
                                        prefs.incrementTodayDetections()
                                        saveDetectionToHistory(alert, prefs)
                                        lastDetectionId = detectionId
                                    }
                                    
                                    // Send notifications
                                    sendNotifications(alert, prefs)
                                } else {
                                    stopSiren()
                                    userStopped = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AlertService", "Error: ${e.localizedMessage}")
                    }

                    val pollInterval = prefs.getPollIntervalSec().coerceIn(1, 10)
                    delay(pollInterval * 1000L)
                }
            }
        }

        return START_STICKY
    }


    private fun playSiren() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
                val afd = resources.openRawResourceFd(R.raw.siren)
                if (afd == null) return
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                mediaPlayer?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer?.isLooping = true
                mediaPlayer?.prepare()
                
                try {
                    // Maximize alarm volume so it is strictly heard
                    val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                } catch (e: Exception) {
                    Log.w("AlertService", "Could not max system volume: ${e.message}")
                }
            }

            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                Log.d("AlertService", "SIREN PLAYING LOUDLY")
            }
        } catch (e: Exception) {
            Log.e("AlertService", "Error playing siren: ${e.message}")
        }
    }

    private fun stopSiren() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AlertService", "Error stopping siren: ${e.message}")
        } finally {
            mediaPlayer = null
            Log.d("AlertService", "SIREN STOPPED")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun saveDetectionToHistory(alert: AlertResponse, prefs: PreferenceManager) {
        // Prefer direct lat/lng fields from server, fallback to parsing location string
        val latitude = alert.latitude ?: alert.location?.split(",")?.getOrNull(0)?.trim()?.toDoubleOrNull()
        val longitude = alert.longitude ?: alert.location?.split(",")?.getOrNull(1)?.trim()?.toDoubleOrNull()
        
        // Normalize timestamp to milliseconds (Python sends seconds)
        val normalizedTimestamp = if (alert.timestamp < 1000000000000L) alert.timestamp * 1000 else alert.timestamp
        
        val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
        
        val detection = DetectionHistory(
            id = UUID.randomUUID().toString(),
            animalType = alert.animal_type,
            confidence = alert.confidence,
            location = alert.location,
            latitude = latitude,
            longitude = longitude,
            timestamp = normalizedTimestamp,
            dangerLevel = dangerLevel,
            image = alert.image
        )
        
        prefs.addDetectionHistory(detection)
    }
    
    private fun sendNotifications(alert: AlertResponse, prefs: PreferenceManager) {
        val currentTime = System.currentTimeMillis()
        
        // Check cooldown to avoid spam
        if (currentTime - lastNotificationTimestamp < notificationCooldown) {
            return
        }
        
        if (!prefs.isNotificationEnabled()) {
            return
        }
        
        lastNotificationTimestamp = currentTime
        
        // In-app notification
        if (prefs.isInAppNotificationEnabled()) {
            val notificationHelper = NotificationHelper(this)
            notificationHelper.showAnimalDetectionNotification(alert)
        }
        
        // Email notification via Backend API
        if (prefs.isEmailNotificationEnabled()) {
            val email = prefs.getUserEmail()
            if (email.isNotEmpty()) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timeStr = dateFormat.format(java.util.Date(if (alert.timestamp < 1000000000000L) alert.timestamp * 1000 else alert.timestamp))
                
                val body = """
                    Animal Detection Alert
                    
                    Animal Type: ${alert.animal_type ?: "Unknown"}
                    Confidence: ${alert.confidence}%
                    Location: ${alert.location ?: "Not available"}
                    Detection Time: $timeStr
                    
                    This is an automated alert from WildTrack.
                """.trimIndent()
                
                RetrofitClient.api.sendEmailNotification(
                    com.example.animalalert.model.EmailRequest(
                        to = email,
                        subject = "🚨 Animal Detection Alert - ${alert.animal_type ?: "Unknown"}",
                        body = body
                    )
                ).enqueue(object : retrofit2.Callback<com.example.animalalert.model.GenericBackendResponse> {
                    override fun onResponse(call: retrofit2.Call<com.example.animalalert.model.GenericBackendResponse>, response: retrofit2.Response<com.example.animalalert.model.GenericBackendResponse>) {
                        Log.d("AlertService", "Backend Email API called successfully")
                    }
                    override fun onFailure(call: retrofit2.Call<com.example.animalalert.model.GenericBackendResponse>, t: Throwable) {
                        Log.e("AlertService", "Backend Email API failed: ${t.message}")
                    }
                })
            }
        }
        
        // SMS notification
        if (prefs.isSmsNotificationEnabled()) {
            val phone = prefs.getUserPhone()
            if (phone.isNotEmpty()) {
                val smsHelper = SMSHelper(this)
                smsHelper.sendSMSNotification(phone, alert)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSiren()
        job.cancel()
    }
}
