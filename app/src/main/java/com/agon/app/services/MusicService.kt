package com.agon.app.services

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.agon.app.MainActivity
import timber.log.Timber

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIF_ID = 1001
        const val ACTION_PLAY  = "com.agon.app.PLAY"
        const val ACTION_PAUSE = "com.agon.app.PAUSE"
        const val ACTION_NEXT  = "com.agon.app.NEXT"
        const val ACTION_PREV  = "com.agon.app.PREV"
        const val ACTION_STOP  = "com.agon.app.STOP"
        const val EXTRA_TITLE  = "title"
        const val EXTRA_ARTIST = "artist"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title  = intent?.getStringExtra(EXTRA_TITLE)  ?: "Now Playing"
        val artist = intent?.getStringExtra(EXTRA_ARTIST) ?: ""
        startForeground(NOTIF_ID, buildNotification(title, artist))
        Timber.d("MusicService started: $title")
        return START_STICKY
    }

    private fun buildNotification(title: String, artist: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
