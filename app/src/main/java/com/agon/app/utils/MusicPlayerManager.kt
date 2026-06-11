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
import com.agon.app.eq.EqualizerManager
import timber.log.Timber

@SuppressLint("StaticFieldLeak")
@OptIn(UnstableApi::class)
object MusicPlayerManager {

    var player: ExoPlayer? = null
        private set

    // Variabel yang dipakai di MusicViewModel
    var SONG_URL = ""
    var MUSIC_ID = ""
    var MUSIC_TITLE = ""
    var MUSIC_DESCRIPTION = ""
    var IMAGE_URL = ""
    var trackQueue = mutableListOf<String>()
    var track_position = 0
    var onTrackChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (player != null) return

        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)

            val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        onTrackChanged?.invoke()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "ExoPlayer Error")
                }
            })

            Timber.d("✅ MusicPlayerManager initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MusicPlayerManager")
        }
    }

    fun prepareMediaPlayer() {
        val url = SONG_URL.trim()
        if (url.isBlank() || player == null) {
            Timber.w("prepareMediaPlayer: URL kosong atau player null")
            return
        }

        try {
            player?.stop()
            player?.clearMediaItems()
            player?.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            player?.prepare()
            player?.playWhenReady = true
            Timber.d("Playing: $url")
        } catch (e: Exception) {
            Timber.e(e, "prepareMediaPlayer failed")
        }
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun release() {
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
    }
}