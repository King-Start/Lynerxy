package com.agon.app.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Sleep timer — otomatis pause player setelah durasi tertentu.
 * Port dari RythimMusic dengan modifikasi untuk La project.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    var player: Player,
    private val onVolumeMultiplierChanged: (Float) -> Unit = {}
) : Player.Listener {

    companion object {
        private const val TICK_MS = 1000L
        private const val FADE_WINDOW_MS = 60_000L
    }

    private var job: Job? = null

    var triggerTime by mutableLongStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    var fadeOutEnabled by mutableStateOf(false)
        private set

    val isActive: Boolean get() = triggerTime != -1L || pauseWhenSongEnd

    /** Mulai timer sederhana tanpa fade */
    fun start(minutes: Int) = start(minutes, stopAfterCurrentSong = false, fadeOut = false)

    /** Mulai timer dengan opsi stop setelah lagu selesai dan/atau fade out */
    fun start(minutes: Int, stopAfterCurrentSong: Boolean, fadeOut: Boolean) {
        job?.cancel(); job = null
        updateVolume(1f)
        fadeOutEnabled = fadeOut

        if (minutes == -1) {
            // Mode "pause setelah lagu ini selesai"
            pauseWhenSongEnd = true
            triggerTime = -1L
            if (fadeOutEnabled) job = scope.launch {
                while (isActive) { fadeForCurrentSong(); delay(TICK_MS) }
            }
        } else {
            pauseWhenSongEnd = false
            triggerTime = System.currentTimeMillis() + minutes.minutes.inWholeMilliseconds
            job = scope.launch {
                while (isActive) {
                    if (triggerTime != -1L) {
                        val remaining = triggerTime - System.currentTimeMillis()
                        if (remaining <= 0L) {
                            if (stopAfterCurrentSong) {
                                triggerTime = -1L
                                pauseWhenSongEnd = true
                                if (!fadeOutEnabled) break
                            } else {
                                doStop(); break
                            }
                        } else if (fadeOutEnabled) {
                            updateVolume(volumeForRemaining(remaining))
                        }
                    } else if (pauseWhenSongEnd && fadeOutEnabled) {
                        fadeForCurrentSong()
                    }
                    delay(TICK_MS)
                }
            }
        }
    }

    fun clear() {
        job?.cancel(); job = null
        pauseWhenSongEnd = false
        fadeOutEnabled = false
        triggerTime = -1L
        updateVolume(1f)
    }

    /** Sisa waktu dalam ms, -1 jika tidak aktif */
    fun remainingMs(): Long = if (triggerTime == -1L) -1L else (triggerTime - System.currentTimeMillis()).coerceAtLeast(0L)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (pauseWhenSongEnd) doStop()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && pauseWhenSongEnd) doStop()
    }

    private fun doStop() {
        job?.cancel(); job = null
        pauseWhenSongEnd = false
        fadeOutEnabled = false
        triggerTime = -1L
        updateVolume(1f)
        player.pause()
    }

    private fun fadeForCurrentSong() {
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur <= 0) { updateVolume(1f); return }
        val rem = (dur - player.currentPosition).coerceAtLeast(0L)
        updateVolume(volumeForRemaining(rem))
    }

    private fun volumeForRemaining(ms: Long): Float {
        if (ms >= FADE_WINDOW_MS) return 1f
        return (ms.toFloat() / FADE_WINDOW_MS).coerceIn(0f, 1f)
    }

    private fun updateVolume(v: Float) = onVolumeMultiplierChanged(v)
}

    /** Dipanggil MusicViewModel saat lagu berganti */
    fun notifySongTransition() {
        if (pauseWhenSongEnd) doStop()
    }
