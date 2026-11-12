package com.agon.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
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
@UnstableApi
object MusicPlayerManager {

    var player: ExoPlayer? = null
        private set

    var SONG_URL          = ""
    var MUSIC_TITLE       = ""
    var MUSIC_DESCRIPTION = ""
    var IMAGE_URL         = ""
    var onTrackChanged: (() -> Unit)? = null

    @MainThread
    fun init(context: Context) {
        if (player != null) return
        try {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/112 Mobile Safari/537.36")
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(40_000)
                .setAllowCrossProtocolRedirects(true)

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context.applicationContext, http)))
                .build()
                .also { p ->
                    p.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            Timber.d("ExoPlayer state: $state")
                            if (state == Player.STATE_ENDED) onTrackChanged?.invoke()
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            Timber.e(error, "ExoPlayer error: ${error.errorCode} ${error.message}")
                        }
                    })
                }
            Timber.d("MusicPlayerManager init OK")
        } catch (e: Exception) {
            Timber.e(e, "MusicPlayerManager init FAILED")
        }
    }

    /** Harus dipanggil dari Main thread */
    @MainThread
    fun play(url: String) {
        if (url.isBlank()) { Timber.w("play() called with blank URL"); return }
        val p = player ?: run { Timber.e("play() player is null"); return }
        try {
            SONG_URL = url
            p.stop()
            p.clearMediaItems()
            p.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            p.prepare()
            p.playWhenReady = true
            Timber.d("play() -> $url")
        } catch (e: Exception) {
            Timber.e(e, "play() failed")
        }
    }

    @MainThread
    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    @MainThread
    fun seekTo(ms: Long) {
        player?.seekTo(ms)
    }

    fun isPlaying() = player?.isPlaying == true
    fun currentPosition() = player?.currentPosition ?: 0L
    fun duration() = player?.duration?.takeIf { it > 0 } ?: 0L

    fun release() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
