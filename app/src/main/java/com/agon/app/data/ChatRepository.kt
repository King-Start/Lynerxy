package com.agon.app.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatRepository(context: Context) {
    private val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveHistory(messages: List<ChatMessage>) {
        try {
            val jsonString = json.encodeToString(messages)
            prefs.edit().putString("history", jsonString).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadHistory(): List<ChatMessage> {
        val jsonString = prefs.getString("history", null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
