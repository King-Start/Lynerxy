package com.agon.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.agon.app.MainActivity
import com.agon.app.services.MusicService
import java.net.URL
import kotlin.concurrent.thread

object MusicPlayerManager {
    private const val CHANNEL_ID = "music_player_channel"
    private const val NOTIFICATION_ID = 1001

    var player: ExoPlayer? = null
    var appContext: Context? = null
    var mediaSession: MediaSessionCompat? = null

    // Track info
    var MUSIC_ID: String = ""
    var MUSIC_TITLE: String = ""
    var MUSIC_DESCRIPTION: String = ""
    var IMAGE_URL: String = ""
    var SONG_URL: String = ""

    // Queue
    var trackQueue: MutableList<String> = mutableListOf()
    var track_position: Int = 0

    // Callbacks
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onTrackChanged: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel(context)

        if (player == null) {
            player = ExoPlayer.Builder(context).build()
            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    onPlaybackStateChanged?.invoke(isPlaying)
                    if (isPlaying) updateNotification()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onTrackChanged?.invoke()
                    }
                }
            })
        }

        mediaSession = MediaSessionCompat(context, "MusicPlayer")
    }

    fun prepareMediaPlayer() {
        val url = SONG_URL.takeIf { it.isNotBlank() } ?: return
        try {
            player?.apply {
                val httpsUrl = if (url.startsWith("http:")) url.replace("http:", "https:") else url
                setMediaItem(MediaItem.fromUri(httpsUrl))
                prepare()
                play()
            }
            startMusicService()
            updateNotification()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
            updateNotification()
        }
    }

    private fun startMusicService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, MusicService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setSound(null, null)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun updateNotification() {
        val ctx = appContext ?: return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val isPlaying = player?.isPlaying ?: false

            val contentIntent = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPauseIntent = PendingIntent.getBroadcast(
                ctx, 0, Intent("play").setPackage(ctx.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevIntent = PendingIntent.getBroadcast(
                ctx, 1, Intent("prev").setPackage(ctx.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIntent = PendingIntent.getBroadcast(
                ctx, 2, Intent("next").setPackage(ctx.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(MUSIC_TITLE.ifBlank { "Now Playing" })
                .setContentText(MUSIC_DESCRIPTION.ifBlank { "LyronixAi" })
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play", playPauseIntent
                )
                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2))
                .setOngoing(isPlaying)
                .setSilent(true)
                .build()

            nm.notify(NOTIFICATION_ID, notification)

            // Load album art in background
            if (IMAGE_URL.isNotBlank()) {
                thread {
                    try {
                        val bmp = BitmapFactory.decodeStream(URL(IMAGE_URL).openStream())
                        Handler(Looper.getMainLooper()).post {
                            val n2 = NotificationCompat.Builder(ctx, CHANNEL_ID)
                                .setSmallIcon(android.R.drawable.ic_media_play)
                                .setContentTitle(MUSIC_TITLE.ifBlank { "Now Playing" })
                                .setContentText(MUSIC_DESCRIPTION.ifBlank { "LyronixAi" })
                                .setLargeIcon(bmp)
                                .setContentIntent(contentIntent)
                                .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
                                .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, if (isPlaying) "Pause" else "Play", playPauseIntent)
                                .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
                                .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                                .setOngoing(isPlaying)
                                .setSilent(true)
                                .build()
                            nm.notify(NOTIFICATION_ID, n2)
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun release() {
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
    }
}
