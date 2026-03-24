package com.example.animalalert.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.animalalert.R
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.ui.MainActivity

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Animal Detection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for animal detection alerts"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAnimalDetectionNotification(alert: AlertResponse) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("🚨 Animal Detected!")
            .setContentText("${alert.animal_type ?: "Unknown animal"} detected with ${alert.confidence}% confidence")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Animal Type: ${alert.animal_type ?: "Unknown"}\n" +
                        "Confidence: ${alert.confidence}%\n" +
                        "Location: ${alert.location ?: "N/A"}\n" +
                        "Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(if (alert.timestamp < 1000000000000L) alert.timestamp * 1000 else alert.timestamp))}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "animal_alert_channel"
        private const val NOTIFICATION_ID = 1001
    }
}


