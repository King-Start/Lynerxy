package com.agon.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class CloudRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun saveHistoryToCloud(messages: List<ChatMessage>) {
        val userId = getCurrentUserId() ?: return
        try {
            // Convert to a map to avoid serialization issues with Firestore directly
            val messagesMap = messages.map { msg ->
                mapOf(
                    "id" to msg.id,
                    "text" to msg.text,
                    "isUser" to msg.isUser,
                    "hasImage" to msg.hasImage,
                    "hasAudio" to msg.hasAudio,
                    "hasCode" to msg.hasCode,
                    "codeSnippet" to (msg.codeSnippet ?: ""),
                    "isSunoAudio" to msg.isSunoAudio,
                    "sunoAudioUrl" to (msg.sunoAudioUrl ?: "")
                )
            }
            
            db.collection("users").document(userId)
                .set(mapOf("chatHistory" to messagesMap))
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadHistoryFromCloud(): List<ChatMessage> {
        val userId = getCurrentUserId() ?: return emptyList()
        return try {
            val document = db.collection("users").document(userId).get().await()
            if (document.exists()) {
                val historyList = document.get("chatHistory") as? List<Map<String, Any>> ?: emptyList()
                historyList.map { map ->
                    ChatMessage(
                        id = map["id"] as? String ?: "",
                        text = map["text"] as? String ?: "",
                        isUser = map["isUser"] as? Boolean ?: false,
                        hasImage = map["hasImage"] as? Boolean ?: false,
                        hasAudio = map["hasAudio"] as? Boolean ?: false,
                        hasCode = map["hasCode"] as? Boolean ?: false,
                        codeSnippet = (map["codeSnippet"] as? String)?.takeIf { it.isNotEmpty() },
                        isSunoAudio = map["isSunoAudio"] as? Boolean ?: false,
                        sunoAudioUrl = (map["sunoAudioUrl"] as? String)?.takeIf { it.isNotEmpty() }
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
