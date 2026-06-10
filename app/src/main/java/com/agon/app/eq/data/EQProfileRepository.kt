package com.agon.app.eq.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
data class SavedEQProfile(
    val id: String,
    val name: String,
    val deviceModel: String = "",
    val bands: List<ParametricEQBand>,
    val preamp: Double = 0.0,
    val source: String = "custom",
    val isCustom: Boolean = true,
    val isActive: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis()
)

class EQProfileRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("agon_eq_profiles", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _profiles = MutableStateFlow<List<SavedEQProfile>>(emptyList())
    val profiles: StateFlow<List<SavedEQProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
    val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

    companion object {
        private const val KEY_PROFILES = "eq_profiles"
        private const val KEY_ACTIVE_ID = "active_profile_id"

        // Default 10-band EQ presets
        val PRESETS = listOf(
            SavedEQProfile("flat", "Flat", bands = emptyList(), preamp = 0.0),
            SavedEQProfile("bass_boost", "Bass Boost", bands = listOf(
                ParametricEQBand(60.0, 6.0), ParametricEQBand(170.0, 4.0),
                ParametricEQBand(310.0, 2.0)
            )),
            SavedEQProfile("vocal", "Vocal", bands = listOf(
                ParametricEQBand(1000.0, 3.0), ParametricEQBand(3000.0, 4.0),
                ParametricEQBand(6000.0, 2.0)
            )),
            SavedEQProfile("rock", "Rock", bands = listOf(
                ParametricEQBand(60.0, 4.0), ParametricEQBand(170.0, 2.0),
                ParametricEQBand(1000.0, -1.0), ParametricEQBand(6000.0, 3.0),
                ParametricEQBand(14000.0, 2.0)
            )),
            SavedEQProfile("electronic", "Electronic", bands = listOf(
                ParametricEQBand(60.0, 5.0), ParametricEQBand(170.0, 3.0),
                ParametricEQBand(600.0, -1.0), ParametricEQBand(6000.0, 3.0),
                ParametricEQBand(14000.0, 3.0)
            )),
            SavedEQProfile("classical", "Classical", bands = listOf(
                ParametricEQBand(60.0, 0.0), ParametricEQBand(1000.0, 0.0),
                ParametricEQBand(6000.0, 2.0), ParametricEQBand(14000.0, 3.0)
            ))
        )
    }

    init { loadProfiles() }

    private fun loadProfiles() {
        try {
            val raw = prefs.getString(KEY_PROFILES, null)
            val saved = if (raw != null) json.decodeFromString<List<SavedEQProfile>>(raw) else emptyList()
            _profiles.value = saved
            val activeId = prefs.getString(KEY_ACTIVE_ID, null)
            _activeProfile.value = saved.find { it.id == activeId }
                ?: PRESETS.find { it.id == activeId }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load EQ profiles")
        }
    }

    fun saveProfile(profile: SavedEQProfile) {
        val cur = _profiles.value.toMutableList()
        cur.removeIf { it.id == profile.id }
        cur.add(profile)
        _profiles.value = cur
        prefs.edit { putString(KEY_PROFILES, json.encodeToString(cur)) }
    }

    fun deleteProfile(id: String) {
        val cur = _profiles.value.filter { it.id != id }
        _profiles.value = cur
        prefs.edit { putString(KEY_PROFILES, json.encodeToString(cur)) }
        if (_activeProfile.value?.id == id) setActiveProfile(null)
    }

    fun setActiveProfile(profile: SavedEQProfile?) {
        _activeProfile.value = profile
        prefs.edit { putString(KEY_ACTIVE_ID, profile?.id) }
    }

    fun getAllProfiles(): List<SavedEQProfile> = PRESETS + _profiles.value
}
