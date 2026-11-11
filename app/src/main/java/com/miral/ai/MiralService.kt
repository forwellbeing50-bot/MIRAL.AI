package com.miral.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MiralService : Service() {

companion object {
    private const val CHANNEL_ID = "miral_background_channel"
    private const val NOTIFICATION_ID = 1001

    fun start(context: Context) {
        val intent = Intent(context, MiralService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, MiralService::class.java)
        context.stopService(intent)
        MiralHub.backgroundRunning = false
    }
}

override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, createNotification())
    MiralHub.backgroundRunning = true
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    MiralHub.backgroundRunning = true
    return START_STICKY
}

override fun onDestroy() {
    MiralHub.backgroundRunning = false
    super.onDestroy()
}

override fun onBind(intent: Intent?): IBinder? = null

private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MIRAL Background Assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "MIRAL background service status"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

private fun createNotification(): Notification {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

    val pendingIntent = launchIntent?.let {
        PendingIntent.getActivity(
            this,
            0,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("MIRAL AI")
        .setContentText("Background service active")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}

}
