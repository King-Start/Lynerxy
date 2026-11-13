package com.agon.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Satu pesan dalam percakapan. role: "user" atau "assistant". */
data class AiMessage(val role: String, val content: String)

class AiRepository {

    private companion object {
        // Nama model berubah cepat → tiap provider punya rantai cadangan.
        // Kalau model pertama ditolak (404 / "model not found"), otomatis dicoba model berikutnya.
        val GEMINI_MODELS   = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash")
        val CLAUDE_MODELS   = listOf("claude-haiku-5-5", "claude-3-5-haiku-latest")
        val OPENAI_MODELS   = listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-3.5-turbo")
        val GOAPI_MODELS    = listOf("gpt-4o-mini", "gpt-3.5-turbo")
        val DEEPSEEK_MODELS = listOf("deepseek-chat")
        val GLM_MODELS      = listOf("glm-4.5-flash", "glm-4-flash")
        val KIMI_MODELS     = listOf("kimi-latest", "moonshot-v1-8k")
        val MISTRAL_MODELS  = listOf("mistral-small-latest")

        const val MAX_TOKENS = 2000
    }

    /** Kompatibel dengan kode lama: satu prompt tanpa riwayat. */
    suspend fun generateResponse(prompt: String, apiKey: String, provider: String): String =
        generateChat(listOf(AiMessage("user", prompt)), apiKey, provider, null)

    /**
     * Kirim percakapan lengkap (dengan riwayat) ke provider.
     * Mengembalikan teks jawaban, atau string berawalan "ERROR_..." kalau gagal.
     */
    suspend fun generateChat(
        messages: List<AiMessage>,
        apiKey: String,
        provider: String,
        systemPrompt: String? = null
    ): String = withContext(Dispatchers.IO) {
        try {
            val msgs = normalize(messages)
            if (msgs.isEmpty()) return@withContext "ERROR_EMPTY: Pesan kosong"
            val key = apiKey.trim()
            val system = systemPrompt?.takeIf { it.isNotBlank() }

            when (provider.trim()) {
                "Gemini"   -> tryModels(GEMINI_MODELS) { callGemini(msgs, key, it, system) }
                "Claude"   -> tryModels(CLAUDE_MODELS) { callClaude(msgs, key, it, system) }
                "DeepSeek" -> tryModels(DEEPSEEK_MODELS) { callOpenAiStyle(msgs, key, "https://api.deepseek.com/chat/completions", it, system, "max_tokens") }
                "GLM"      -> tryModels(GLM_MODELS) { callOpenAiStyle(msgs, key, "https://open.bigmodel.cn/api/paas/v4/chat/completions", it, system, "max_tokens") }
                "Kimi"     -> tryModels(KIMI_MODELS) { callOpenAiStyle(msgs, key, "https://api.moonshot.cn/v1/chat/completions", it, system, "max_tokens") }
                "Mistral"  -> tryModels(MISTRAL_MODELS) { callOpenAiStyle(msgs, key, "https://api.mistral.ai/v1/chat/completions", it, system, "max_tokens") }
                "GoAPI"    -> tryModels(GOAPI_MODELS) { callOpenAiStyle(msgs, key, "https://api.goapi.ai/v1/chat/completions", it, system, "max_tokens") }
                else       -> tryModels(OPENAI_MODELS) { callOpenAiStyle(msgs, key, "https://api.openai.com/v1/chat/completions", it, system, "max_completion_tokens") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Timber.e(t, "AI call failed: $provider")
            "ERROR_CONNECTION: ${t.message}"
        }
    }

    // ─────────────────────────────────────────────────────────
    // Util
    // ─────────────────────────────────────────────────────────

    /** Coba model satu per satu; lanjut ke model berikutnya hanya kalau errornya soal model. */
    private fun tryModels(models: List<String>, call: (String) -> String): String {
        var last = ""
        for ((i, model) in models.withIndex()) {
            last = call(model)
            val modelProblem = last.startsWith("ERROR_404") ||
                (last.startsWith("ERROR_400") && last.contains("model", ignoreCase = true))
            if (!modelProblem || i == models.lastIndex) return last
            Timber.w("Model $model ditolak, coba model berikutnya: ${last.take(120)}")
        }
        return last
    }

    /** Pastikan percakapan berselang-seling user/assistant dan diawali user (syarat Claude & Gemini). */
    private fun normalize(src: List<AiMessage>): List<AiMessage> {
        val out = mutableListOf<AiMessage>()
        for (m in src) {
            val c = m.content.trim()
            if (c.isEmpty()) continue
            val role = if (m.role == "assistant") "assistant" else "user"
            if (out.isNotEmpty() && out.last().role == role) {
                out[out.lastIndex] = out.last().copy(content = out.last().content + "\n\n" + c)
            } else {
                out.add(AiMessage(role, c))
            }
        }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        return out
    }

    private fun errorMessage(raw: String): String = try {
        val e = JSONObject(raw).opt("error")
        when (e) {
            is JSONObject -> e.optString("message", raw)
            is String -> e
            else -> raw
        }.take(300)
    } catch (_: Exception) {
        raw.take(300)
    }

    /** POST JSON → (kode HTTP, body). */
    private fun post(url: String, headers: Map<String, String>, body: JSONObject): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.let { s -> BufferedReader(InputStreamReader(s)).use { it.readText() } } ?: ""
            Timber.d("${url.substringBefore('?')} -> $code")
            return code to raw
        } finally {
            conn.disconnect()
        }
    }

    // ─────────────────────────────────────────────────────────
    // OpenAI-compatible (OpenAI, DeepSeek, GLM, Kimi, Mistral, GoAPI)
    // ─────────────────────────────────────────────────────────
    private fun callOpenAiStyle(
        msgs: List<AiMessage>, apiKey: String, url: String, model: String,
        system: String?, maxTokensField: String
    ): String {
        val arr = JSONArray()
        if (system != null) arr.put(JSONObject().put("role", "system").put("content", system))
        msgs.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }

        val body = JSONObject()
            .put("model", model)
            .put(maxTokensField, MAX_TOKENS)
            .put("messages", arr)

        val (code, raw) = post(url, mapOf("Authorization" to "Bearer $apiKey"), body)
        return if (code in 200..299) {
            try {
                JSONObject(raw).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } catch (e: Exception) { "ERROR_PARSE: ${raw.take(300)}" }
        } else "ERROR_$code: ${errorMessage(raw)}"
    }

    // ─────────────────────────────────────────────────────────
    // Claude (Anthropic)
    // ─────────────────────────────────────────────────────────
    private fun callClaude(msgs: List<AiMessage>, apiKey: String, model: String, system: String?): String {
        val arr = JSONArray()
        msgs.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", MAX_TOKENS)
            .put("messages", arr)
        if (system != null) body.put("system", system)

        val (code, raw) = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body
        )
        return if (code in 200..299) {
            try {
                val content = JSONObject(raw).getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }.ifBlank { "ERROR_PARSE: ${raw.take(300)}" }
            } catch (e: Exception) { "ERROR_PARSE: ${raw.take(300)}" }
        } else "ERROR_$code: ${errorMessage(raw)}"
    }

    // ─────────────────────────────────────────────────────────
    // Gemini (Google)
    // ─────────────────────────────────────────────────────────
    private fun callGemini(msgs: List<AiMessage>, apiKey: String, model: String, system: String?): String {
        val contents = JSONArray()
        msgs.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.content)))
            )
        }
        val body = JSONObject().put("contents", contents)
        if (system != null) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        body.put("generationConfig", JSONObject().put("maxOutputTokens", MAX_TOKENS))

        val (code, raw) = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),   // key di header, bukan di URL
            body
        )
        return if (code in 200..299) {
            try {
                val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                buildString {
                    for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
                }.ifBlank { "ERROR_PARSE: jawaban kosong" }
            } catch (e: Exception) { "ERROR_PARSE: ${raw.take(300)}" }
        } else "ERROR_$code: ${errorMessage(raw)}"
    }
}
