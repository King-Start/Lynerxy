package com.agon.app.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.agon.app.db.AppDatabase
import com.agon.app.db.entities.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File

@UnstableApi
class DownloadManager(private val context: Context) {

    private val db by lazy { AppDatabase.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress

    companion object {
        const val DOWNLOAD_STATE_NONE = 0
        const val DOWNLOAD_STATE_QUEUED = 1
        const val DOWNLOAD_STATE_DONE = 2

        @Volatile private var cacheInstance: SimpleCache? = null

        @UnstableApi
        fun getCache(context: Context): SimpleCache {
            return cacheInstance ?: synchronized(this) {
                cacheInstance ?: SimpleCache(
                    File(context.cacheDir, "audio_cache"),
                    LeastRecentlyUsedCacheEvictor(512 * 1024 * 1024L) // 512 MB
                ).also { cacheInstance = it }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService<ConnectivityManager>() ?: return false
        val network = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(network) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Download lagu dan simpan ke cache/DB */
    fun downloadSong(song: SongEntity) {
        if (!isNetworkAvailable()) {
            Timber.w("No network, skipping download for ${song.id}")
            return
        }
        scope.launch {
            try {
                db.musicDao().setDownloadState(song.id, DOWNLOAD_STATE_QUEUED)
                updateProgress(song.id, 0f)

                // Simpan metadata ke DB dulu
                db.musicDao().upsertSong(song.copy(downloadState = DOWNLOAD_STATE_QUEUED))

                // Download audio menggunakan OkHttp ke file lokal
                if (song.audioUrl.isBlank()) {
                    Timber.w("No audio URL for ${song.id}, skipping download")
                    db.musicDao().setDownloadState(song.id, DOWNLOAD_STATE_NONE)
                    return@launch
                }

                val outputDir = File(context.filesDir, "downloads").also { it.mkdirs() }
                val outputFile = File(outputDir, "${song.id}.m4a")

                val client = OkHttpClient()
                val request = okhttp3.Request.Builder().url(song.audioUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    val body = response.body ?: throw Exception("Empty body")
                    val total = body.contentLength()
                    var downloaded = 0L
                    outputFile.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(8192)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                downloaded += read
                                if (total > 0) updateProgress(song.id, downloaded.toFloat() / total)
                            }
                        }
                    }
                }

                db.musicDao().setDownloadState(song.id, DOWNLOAD_STATE_DONE)
                db.musicDao().upsertSong(
                    song.copy(audioUrl = outputFile.absolutePath, downloadState = DOWNLOAD_STATE_DONE)
                )
                updateProgress(song.id, 1f)
                Timber.d("Download complete: ${song.id}")
            } catch (e: Exception) {
                Timber.e(e, "Download failed for ${song.id}")
                db.musicDao().setDownloadState(song.id, DOWNLOAD_STATE_NONE)
                removeProgress(song.id)
            }
        }
    }

    fun cancelDownload(songId: String) {
        scope.launch {
            db.musicDao().setDownloadState(songId, DOWNLOAD_STATE_NONE)
            removeProgress(songId)
            // Hapus file jika ada
            File(File(context.filesDir, "downloads"), "$songId.m4a").delete()
        }
    }

    fun deleteDownload(songId: String) {
        scope.launch {
            db.musicDao().setDownloadState(songId, DOWNLOAD_STATE_NONE)
            File(File(context.filesDir, "downloads"), "$songId.m4a").delete()
            removeProgress(songId)
        }
    }

    fun getDownloadedFilePath(songId: String): String? {
        val f = File(File(context.filesDir, "downloads"), "$songId.m4a")
        return if (f.exists()) f.absolutePath else null
    }

    private fun updateProgress(id: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().also { it[id] = progress }
    }

    private fun removeProgress(id: String) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().also { it.remove(id) }
    }
}
