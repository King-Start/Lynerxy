package com.agon.app.eq

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.agon.app.eq.audio.CustomEqualizerAudioProcessor
import com.agon.app.eq.data.EQProfileRepository
import com.agon.app.eq.data.ParametricEQ
import com.agon.app.eq.data.SavedEQProfile
import timber.log.Timber

/**
 * Manager EQ — tidak pakai Hilt, cukup singleton manual
 */
@OptIn(UnstableApi::class)
object EqualizerManager {
    private val processors = mutableListOf<CustomEqualizerAudioProcessor>()
    private var pendingProfile: SavedEQProfile? = null
    private var disabled = false

    fun addProcessor(p: CustomEqualizerAudioProcessor) {
        processors.add(p)
        if (disabled) p.disable()
        else pendingProfile?.let { applyToProcessor(p, it) }
    }

    fun removeProcessor(p: CustomEqualizerAudioProcessor) = processors.remove(p)

    fun applyProfile(profile: SavedEQProfile) {
        pendingProfile = profile
        disabled = false
        processors.forEach { applyToProcessor(it, profile) }
        Timber.d("EQ profile applied: ${profile.name}")
    }

    fun disable() {
        disabled = true
        pendingProfile = null
        processors.forEach { it.disable() }
    }

    fun isEnabled() = processors.any { it.isEnabled() }

    private fun applyToProcessor(p: CustomEqualizerAudioProcessor, profile: SavedEQProfile) {
        p.applyProfile(ParametricEQ(preamp = profile.preamp, bands = profile.bands))
    }

    fun release() = processors.clear()
}
