package com.agon.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.agon.app.MainActivity
import com.agon.app.R
import com.agon.app.utils.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URL

/**
 * Foreground service yang menampilkan lagu yang sedang diputar di status bar / notification shade
 * (judul, artis, cover, tombol Prev / Play-Pause / Next) dan menjaga musik tetap jalan di background.
 */
@UnstableApi
class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback"
        const val NOTIF_ID = 1001

        const val ACTION_UPDATE = "com.agon.app.UPDATE"
        const val ACTION_PLAY   = "com.agon.app.PLAY"
        const val ACTION_PAUSE  = "com.agon.app.PAUSE"
        const val ACTION_TOGGLE = "com.agon.app.TOGGLE"
        const val ACTION_NEXT   = "com.agon.app.NEXT"
        const val ACTION_PREV   = "com.agon.app.PREV"
        const val ACTION_STOP   = "com.agon.app.STOP"

        const val EXTRA_TITLE   = "title"
        const val EXTRA_ARTIST  = "artist"
        const val EXTRA_ARTWORK = "artwork"

        /** Panggil saat lagu baru dipilih. Aman: tidak akan melempar exception. */
        fun updateTrack(context: Context, title: String, artist: String, artworkUrl: String) {
            try {
                val intent = Intent(context, MusicService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_ARTIST, artist)
                    putExtra(EXTRA_ARTWORK, artworkUrl)
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Timber.e(t, "MusicService.updateTrack failed")
            }
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, MusicService::class.java)) } catch (_: Throwable) {}
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var artworkJob: Job? = null

    private lateinit var session: MediaSessionCompat
    private lateinit var nm: NotificationManager

    private var title = "Now Playing"
    private var artist = ""
    private var artwork: Bitmap? = null
    private var isForeground = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refresh()
    }

    // ─────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        createChannel()

        session = MediaSessionCompat(this, "LyronixAiSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { MusicPlayerManager.player?.play() }
                override fun onPause() { MusicPlayerManager.player?.pause() }
                override fun onSkipToNext()     { MusicPlayerManager.onSkipNext?.invoke() }
                override fun onSkipToPrevious() { MusicPlayerManager.onSkipPrevious?.invoke() }
                override fun onSeekTo(pos: Long) { MusicPlayerManager.seekTo(pos) }
                override fun onStop() { stopPlaybackAndService() }
            })
            isActive = true
        }

        try {
            MusicPlayerManager.init(applicationContext)
            MusicPlayerManager.player?.addListener(playerListener)
        } catch (t: Throwable) {
            Timber.e(t, "MusicService: attach player listener failed")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                title  = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Now Playing"
                artist = intent.getStringExtra(EXTRA_ARTIST) ?: ""
                artwork = null
                // WAJIB: langsung jadi foreground setelah startForegroundService()
                promoteToForeground()
                loadArtwork(intent.getStringExtra(EXTRA_ARTWORK).orEmpty())
            }
            ACTION_TOGGLE -> MusicPlayerManager.player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_PLAY   -> MusicPlayerManager.player?.play()
            ACTION_PAUSE  -> MusicPlayerManager.player?.pause()
            ACTION_NEXT   -> MusicPlayerManager.onSkipNext?.invoke()
            ACTION_PREV   -> MusicPlayerManager.onSkipPrevious?.invoke()
            ACTION_STOP   -> stopPlaybackAndService()
            else -> {
                // Service dijalankan ulang tanpa data lagu → tidak ada yang ditampilkan
                if (!isForeground) { stopSelf(); return START_NOT_STICKY }
            }
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { MusicPlayerManager.player?.removeListener(playerListener) } catch (_: Throwable) {}
        artworkJob?.cancel()
        scope.cancel()
        try { session.isActive = false; session.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────
    private fun isPlayingNow(): Boolean {
        val p = MusicPlayerManager.player ?: return false
        return p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
    }

    /** Perbarui notifikasi sesuai status player saat ini. */
    private fun refresh() {
        try {
            val playing = isPlayingNow()
            updateSession(playing)
            val n = buildNotification(playing)
            if (playing) {
                promoteToForeground(n)
            } else {
                // Saat pause: lepas dari foreground (notifikasi tetap tampil & bisa di-swipe)
                if (isForeground) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    isForeground = false
                }
                nm.notify(NOTIF_ID, n)
            }
        } catch (t: Throwable) {
            Timber.e(t, "MusicService.refresh failed")
        }
    }

    private fun promoteToForeground(n: Notification = buildNotification(isPlayingNow())) {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, NOTIF_ID, n, type)
            isForeground = true
        } catch (t: Throwable) {
            Timber.e(t, "startForeground failed")
            // Tetap tampilkan notifikasi biasa kalau foreground ditolak sistem
            try { nm.notify(NOTIF_ID, n) } catch (_: Throwable) {}
        }
    }

    private fun updateSession(playing: Boolean) {
        val p = MusicPlayerManager.player
        val duration = (p?.duration ?: 0L).takeIf { it > 0 } ?: -1L
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .apply { artwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        val state = when {
            playing && p?.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            playing -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
                .setState(state, p?.currentPosition ?: 0L, 1f)
                .build()
        )
    }

    private fun buildNotification(playing: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("LyronixAi")
            .setLargeIcon(artwork)
            .setContentIntent(openIntent)
            .setDeleteIntent(actionIntent(ACTION_STOP, 9))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(playing)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", actionIntent(ACTION_PREV, 1))
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play",
                actionIntent(ACTION_TOGGLE, 2)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", actionIntent(ACTION_NEXT, 3))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        return builder.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun loadArtwork(url: String) {
        artworkJob?.cancel()
        if (url.isBlank()) return
        val titleAtStart = title
        artworkJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    val conn = URL(url.replace("100x100", "500x500")).openConnection().apply {
                        connectTimeout = 8_000; readTimeout = 8_000
                    }
                    conn.getInputStream().use { BitmapFactory.decodeStream(it) }
                } catch (t: Throwable) {
                    Timber.w("loadArtwork failed: ${t.message}")
                    null
                }
            }
            // Pastikan lagu belum berganti saat cover selesai diunduh
            if (bmp != null && titleAtStart == title) {
                artwork = bmp
                refresh()
            }
        }
    }

    private fun stopPlaybackAndService() {
        try { MusicPlayerManager.player?.pause() } catch (_: Throwable) {}
        try { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        isForeground = false
        try { nm.cancel(NOTIF_ID) } catch (_: Throwable) {}
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Menampilkan lagu yang sedang diputar"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }
}
