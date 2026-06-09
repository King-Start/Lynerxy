package com.agon.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.agon.app.utils.MusicPlayerManager

class MusicService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MusicPlayerManager.updateNotification()
        try {
            startForeground(1001, createDummyNotification())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    private fun createDummyNotification(): android.app.Notification {
        return android.app.Notification.Builder(this, "music_player_channel")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("LyronixAi")
            .setContentText("Music playing...")
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }
}
