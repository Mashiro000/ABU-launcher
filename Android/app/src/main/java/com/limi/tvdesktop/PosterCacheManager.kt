package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class PosterQuality(val displayName: String, val maxHeight: Int) {
    AUTO("自适应 (推荐)", 720),
    BALANCED("流畅 540P", 540),
    ORIGINAL("原始高清", 0)
}

object PosterCacheManager {
    private var cacheDir: File? = null
    private val memoryCache = object : LruCache<String, ImageBitmap>(80 * 1024 * 1024) { // 80MB
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return value.width * value.height * 4
        }
    }

    private val unsafeClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (cacheDir == null) {
            cacheDir = File(context.cacheDir, "poster_cache").apply { mkdirs() }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    fun getCacheSizeMB(): Float {
        val bytes = cacheDir?.listFiles()?.sumOf { it.length() } ?: 0L
        return bytes / (1024f * 1024f)
    }

    suspend fun loadPoster(url: String, quality: PosterQuality = PosterQuality.AUTO): ImageBitmap? {
        if (url.isBlank()) return null
        val cacheKey = "${quality.name}_${url.hashCode()}"
        memoryCache.get(cacheKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            val diskFile = File(cacheDir, cacheKey)
            if (diskFile.exists() && diskFile.length() > 0) {
                runCatching {
                    val bitmap = decodeSampledBitmap(diskFile.absolutePath, quality.maxHeight)
                    bitmap?.asImageBitmap()?.also { memoryCache.put(cacheKey, it) }
                }.getOrNull()?.let { return@withContext it }
            }

            // Fetch from network
            runCatching {
                val request = Request.Builder().url(url).build()
                val response = unsafeClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null

                val targetFile = File(cacheDir, cacheKey)
                FileOutputStream(targetFile).use { it.write(bytes) }

                val bitmap = decodeSampledBitmap(targetFile.absolutePath, quality.maxHeight)
                bitmap?.asImageBitmap()?.also { memoryCache.put(cacheKey, it) }
            }.getOrNull()
        }
    }

    private fun decodeSampledBitmap(path: String, maxHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        var sampleSize = 1
        if (maxHeight > 0 && options.outHeight > maxHeight) {
            sampleSize = options.outHeight / maxHeight
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }
}

@Composable
fun rememberPosterImage(url: String, placeholder: ImageBitmap? = null): ImageBitmap? {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(placeholder) }
    LaunchedEffect(url) {
        if (url.isNotBlank()) {
            val loaded = PosterCacheManager.loadPoster(url)
            if (loaded != null) image = loaded
        }
    }
    return image ?: placeholder
}
