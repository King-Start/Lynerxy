package com.agon.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.agon.app.utils.MusicPlayerManager
import timber.log.Timber

class LyronixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        // Init Firebase
        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}

        // Init ExoPlayer di Main thread
        Handler(Looper.getMainLooper()).post {
            MusicPlayerManager.init(this)
        }

        // Notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            listOf(
                NotificationChannel("music_playback", "Music Playback", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel("music_alarm", "Music Alarm", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("download", "Downloads", NotificationManager.IMPORTANCE_LOW)
            ).forEach { nm.createNotificationChannel(it) }
        }
    }
}
