package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class ImageRepository {
    // Pollinations AI - gratis tanpa API key
    fun generateImageUrl(prompt: String, width: Int = 512, height: Int = 512): String {
        val encoded = URLEncoder.encode(prompt, "UTF-8")
        return "https://image.pollinations.ai/prompt/$encoded?width=$width&height=$height&nologo=true"
    }

    suspend fun generateImagePromptFromText(text: String, style: String = "realistic"): String =
        withContext(Dispatchers.IO) {
            val styleMap = mapOf(
                "realistic" to "photorealistic, 8k, detailed",
                "anime" to "anime style, Studio Ghibli, vibrant colors",
                "dark" to "dark fantasy, cinematic, moody lighting",
                "minimal" to "minimalist, clean, modern design",
                "music" to "album cover art, professional, music industry"
            )
            val suffix = styleMap[style] ?: "high quality, detailed"
            "$text, $suffix"
        }
}
