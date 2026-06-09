package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agon.app.utils.MusicPlayerManager

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "play" -> {
                MusicPlayerManager.togglePlayPause()
            }
            "next" -> {
                MusicPlayerManager.onTrackChanged?.invoke()
            }
            "prev" -> {
                val player = MusicPlayerManager.player ?: return
                if (player.currentPosition > 3000) {
                    player.seekTo(0)
                } else {
                    MusicPlayerManager.onTrackChanged?.invoke()
                }
            }
        }
        MusicPlayerManager.updateNotification()
    }
}
