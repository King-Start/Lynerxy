package com.agon.innertube

import com.agon.innertube.models.YouTubeClient
import com.agon.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Proxy

private class NewPipeDownloaderImpl(
    proxy: Proxy?,
    private val proxyAuth: String?,
) : Downloader() {

    private val client = OkHttpClient.Builder()
        .proxy(proxy)
        .proxyAuthenticator { _, response ->
            proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        request.headers().forEach { (name, values) ->
            requestBuilder.removeHeader(name)
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val latestUrl = response.request.url.toString()
        
        // FIX: Safe call dengan use() untuk auto-close
        val responseBodyBytes = response.body?.source()?.use { source ->
            source.buffer().readByteArray()
        }
        
        val responseBodyString = responseBodyBytes?.let { bytes ->
            normalizeResponseBody(latestUrl, String(bytes))
        }

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyString,
            responseBodyBytes,
            latestUrl,
        )
    }

    private fun normalizeResponseBody(url: String, body: String?): String? {
        if (!url.contains("returnyoutubedislikeapi.com", ignoreCase = true)) {
            return body
        }

        val trimmed = body?.trimStart().orEmpty()
        return if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            body
        } else {
            "{\"likes\":0,\"dislikes\":0,\"viewCount\":0}"
        }
    }

    override fun executeAsync(request: Request, callback: AsyncCallback?): CancellableCall {
        // FIX: Implementasi proper atau throw UnsupportedOperationException
        return super.executeAsync(request, callback)
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl(YouTube.proxy, YouTube.proxyAuth))
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): Result<String> = 
        runCatching {
            val url = resolveFormatUrl(format, videoId)
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }

    private fun resolveFormatUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String {
        return format.url ?: format.signatureCipher?.let { signatureCipher ->
            val params = parseQueryString(signatureCipher)
            val obfuscatedSignature = params["s"]
                ?: throw ParsingException("Missing cipher signature")
            val signatureParam = params["sp"]
                ?: throw ParsingException("Missing cipher signature parameter")
            val urlBuilder = params["url"]?.let { URLBuilder(it) }
                ?: throw ParsingException("Missing cipher URL")
            
            urlBuilder.parameters[signatureParam] = YoutubeJavaScriptPlayerManager
                .deobfuscateSignature(videoId, obfuscatedSignature)
            urlBuilder.toString()
        } ?: throw ParsingException("No format URL or cipher found")
    }
}

object NewPipeExtractor {
    private const val YOUTUBE_SERVICE_ID = 0
    
    fun newPipePlayer(videoId: String): List<Pair<Int, String>> = runCatching {
        val streamInfo = StreamInfo.getInfo(
            NewPipe.getService(YOUTUBE_SERVICE_ID),
            "https://www.youtube.com/watch?v=$videoId"
        )
        (streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams)
            .mapNotNull { stream ->
                stream.itagItem?.id?.let { it to stream.content }
            }
    }.getOrDefault(emptyList())

    fun getSignatureTimestamp(videoId: String): Result<Int> = 
        NewPipeUtils.getSignatureTimestamp(videoId)

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
        NewPipeUtils.getStreamUrl(format, videoId).getOrNull()
}