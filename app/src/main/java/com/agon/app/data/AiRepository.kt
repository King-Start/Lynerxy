package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiRepository {
    suspend fun generateResponse(prompt: String, apiKey: String, provider: String): String =
        withContext(Dispatchers.IO) {
            try {
                when (provider) {
                    "Gemini" -> callGemini(prompt, apiKey)
                    "Claude" -> callClaude(prompt, apiKey)
                    "DeepSeek" -> callOpenAiStyle(prompt, apiKey, "https://api.deepseek.com/chat/completions", "deepseek-chat")
                    "GLM" -> callOpenAiStyle(prompt, apiKey, "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash")
                    "Kimi" -> callOpenAiStyle(prompt, apiKey, "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k")
                    "Mistral" -> callOpenAiStyle(prompt, apiKey, "https://api.mistral.ai/v1/chat/completions", "mistral-small")
                    "GoAPI" -> callOpenAiStyle(prompt, apiKey, "https://api.goapi.ai/v1/chat/completions", "gpt-3.5-turbo")
                    else -> callOpenAiStyle(prompt, apiKey, "https://api.openai.com/v1/chat/completions", "gpt-3.5-turbo")
                }
            } catch (e: Exception) {
                "ERROR_CONNECTION: Gagal konek ke $provider. Cek internet & API Key lo."
            }
        }

    private fun callOpenAiStyle(prompt: String, apiKey: String, url: String, model: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            connectTimeout = 30000
            readTimeout = 30000
            doOutput = true
        }
        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 2000)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return if (conn.responseCode == 200) {
            JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } else {
            "ERROR_API_KEY: ${conn.responseCode} - Cek API Key ${ url.substringAfter("//").substringBefore("/") } lo."
        }
    }

    private fun callClaude(prompt: String, apiKey: String): String {
        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            connectTimeout = 30000; readTimeout = 30000; doOutput = true
        }
        val body = JSONObject().apply {
            put("model", "claude-3-haiku-20240307")
            put("max_tokens", 2000)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user"); put("content", prompt)
            }))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return if (conn.responseCode == 200) {
            JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("content").getJSONObject(0).getString("text")
        } else "ERROR_API_KEY: ${conn.responseCode} - Cek Claude API Key lo."
    }

    private fun callGemini(prompt: String, apiKey: String): String {
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30000; readTimeout = 30000; doOutput = true
        }
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
            }))
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return if (conn.responseCode == 200) {
            JSONObject(conn.inputStream.bufferedReader().readText())
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")
        } else "ERROR_API_KEY: ${conn.responseCode} - Cek Gemini API Key lo."
    }
}
