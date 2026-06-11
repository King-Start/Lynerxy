package com.agon.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
@OptIn(UnstableApi::class)
object MusicPlayerManager {

    var player: ExoPlayer? = null
        private set

    var SONG_URL         = ""
    var MUSIC_ID         = ""
    var MUSIC_TITLE      = ""
    var MUSIC_DESCRIPTION= ""
    var IMAGE_URL        = ""
    var trackQueue       = mutableListOf<String>()
    var track_position   = 0
    var onTrackChanged: (() -> Unit)? = null

    // Harus dipanggil dari Main thread
    fun init(context: Context) {
        if (player != null) return
        try {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)

            val ds = DefaultDataSource.Factory(context.applicationContext, http)

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(ds))
                .build()
                .apply {
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) onTrackChanged?.invoke()
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            Timber.e(error, "ExoPlayer error")
                        }
                    })
                }
            Timber.d("MusicPlayerManager init ok")
        } catch (e: Exception) {
            Timber.e(e, "MusicPlayerManager init failed")
        }
    }

    // Harus dipanggil dari Main thread
    fun prepareMediaPlayer() {
        val url = SONG_URL.trim()
        if (url.isBlank()) {
            Timber.w("prepareMediaPlayer: URL kosong")
            return
        }
        try {
            val p = player ?: run { Timber.w("player null"); return }
            p.stop()
            p.clearMediaItems()
            p.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            p.prepare()
            p.playWhenReady = true
            Timber.d("prepareMediaPlayer: $url")
        } catch (e: Exception) {
            Timber.e(e, "prepareMediaPlayer failed")
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
