package com.agon.app.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    fun saveApiKey(key: String) = prefs.edit().putString("api_key", key).apply()
    fun getApiKey(): String = prefs.getString("api_key", "") ?: ""

    fun saveApiProvider(provider: String) = prefs.edit().putString("api_provider", provider).apply()
    fun getApiProvider(): String = prefs.getString("api_provider", "OpenAI") ?: "OpenAI"

    fun saveThemeMode(mode: String) = prefs.edit().putString("theme_mode", mode).apply()
    fun getThemeMode(): String = prefs.getString("theme_mode", "dark") ?: "dark"

    fun saveFavorites(json: String) = prefs.edit().putString("favorites", json).apply()
    fun getFavorites(): String = prefs.getString("favorites", "[]") ?: "[]"

    fun saveRecentlyPlayed(json: String) = prefs.edit().putString("recently_played", json).apply()
    fun getRecentlyPlayed(): String = prefs.getString("recently_played", "[]") ?: "[]"

    fun savePlaylists(json: String) = prefs.edit().putString("playlists", json).apply()
    fun getPlaylists(): String = prefs.getString("playlists", "[]") ?: "[]"

    fun validateApiKey(): Boolean = getApiKey().length > 10
}
