package com.agon.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import timber.log.Timber

class LyronixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                "music_playback", "Music Playback", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Music player controls" })
            nm.createNotificationChannel(NotificationChannel(
                "music_alarm", "Music Alarm", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Music alarm notifications" })
            nm.createNotificationChannel(NotificationChannel(
                "download", "Downloads", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Download progress" })
        }
    }
}
