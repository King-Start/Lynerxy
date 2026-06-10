package com.agon.app.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import timber.log.Timber

@UnstableApi
object MusicPlayerManager {

    var player: ExoPlayer? = null
        private set

    var SONG_URL: String = ""
    var MUSIC_ID: String = ""
    var MUSIC_TITLE: String = ""
    var MUSIC_DESCRIPTION: String = ""
    var IMAGE_URL: String = ""
    var trackQueue: MutableList<String> = mutableListOf()
    var track_position: Int = 0

    var onTrackChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (player != null) return
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpDataSource)))
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) onTrackChanged?.invoke()
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Timber.e(error, "ExoPlayer error: ${error.message}")
                    }
                })
            }
        Timber.d("MusicPlayerManager initialized")
    }

    fun prepareMediaPlayer() {
        val url = SONG_URL.trim()
        if (url.isBlank()) { Timber.w("prepareMediaPlayer: empty URL"); return }
        try {
            val item = MediaItem.Builder().setUri(Uri.parse(url)).build()
            player?.apply {
                setMediaItem(item)
                prepare()
                play()
            }
            Timber.d("Playing: $url")
        } catch (e: Exception) {
            Timber.e(e, "prepareMediaPlayer failed")
        }
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun release() {
        player?.release()
        player = null
    }
}
