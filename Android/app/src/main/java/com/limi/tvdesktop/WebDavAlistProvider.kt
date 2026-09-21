package com.limi.tvdesktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WebDavAlistProvider(override val account: MediaAccount) : MediaSourceProvider {

    private val baseUrl = account.serverUrl.trimEnd('/')

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        if (account.ignoreSslErrors) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        builder.build()
    }

    private val videoExtensions = setOf("mp4", "mkv", "ts", "mov", "flv", "avi", "wmv", "m3u8", "webm", "iso")

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/api/public/settings")
                .header("Authorization", account.token)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optJSONObject("data")?.optString("title", "Alist / WebDAV") ?: "Alist / WebDAV"
                title
            } else {
                // Try WebDAV root
                val webdavReq = Request.Builder().url("$baseUrl/dav").build()
                val webdavResp = client.newCall(webdavReq).execute()
                if (webdavResp.code in 200..404) {
                    "WebDAV 服务已联通"
                } else {
                    throw Exception("连接失败，HTTP ${resp.code}")
                }
            }
        }
    }

    private suspend fun listPath(path: String): List<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("path", path)
                put("password", "")
                put("page", 1)
                put("per_page", 60)
                put("refresh", false)
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$baseUrl/api/fs/list")
                .header("Authorization", account.token)
                .post(body)
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val content = json.optJSONObject("data")?.optJSONArray("content") ?: JSONArray()
            val list = mutableListOf<JSONObject>()
            for (i in 0 until content.length()) {
                list.add(content.getJSONObject(i))
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getResumeWatching(): List<MediaItemInfo> {
        // WebDAV/Alist 无中心化观看记录，展示最新视频作为常用项
        return getLatest().take(6)
    }

    override suspend fun getLatest(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        val rootFiles = listPath("/")
        val result = mutableListOf<MediaItemInfo>()
        for (item in rootFiles) {
            val isDir = item.optBoolean("is_dir", false)
            val name = item.optString("name")
            val ext = name.substringAfterLast('.', "").lowercase()
            if (!isDir && ext in videoExtensions) {
                result.add(createMediaItem("/", item))
            }
        }
        result
    }

    override suspend fun getCategories(): List<MediaCategoryInfo> = withContext(Dispatchers.IO) {
        val rootFiles = listPath("/")
        val result = mutableListOf<MediaCategoryInfo>()
        for (item in rootFiles) {
            val isDir = item.optBoolean("is_dir", false)
            val name = item.optString("name")
            if (isDir) {
                result.add(
                    MediaCategoryInfo(
                        id = "/$name",
                        title = name,
                        collectionType = "folders",
                        accountId = account.id
                    )
                )
            }
        }
        if (result.isEmpty()) {
            result.add(MediaCategoryInfo(id = "/", title = "根目录视频", collectionType = "movies", accountId = account.id))
        }
        result
    }

    override suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        val files = listPath(categoryId)
        val result = mutableListOf<MediaItemInfo>()
        for (item in files) {
            val isDir = item.optBoolean("is_dir", false)
            val name = item.optString("name")
            val ext = name.substringAfterLast('.', "").lowercase()
            if (!isDir && ext in videoExtensions) {
                result.add(createMediaItem(categoryId, item))
            }
        }
        result
    }

    override suspend fun getEpisodes(seriesId: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        val files = listPath(seriesId)
        val result = mutableListOf<EpisodeInfo>()
        var ep = 1
        for (item in files) {
            val isDir = item.optBoolean("is_dir", false)
            val name = item.optString("name")
            val ext = name.substringAfterLast('.', "").lowercase()
            if (!isDir && ext in videoExtensions) {
                val fullPath = "$seriesId/$name".replace("//", "/")
                result.add(
                    EpisodeInfo(
                        id = fullPath,
                        title = "E${ep.toString().padStart(2, '0')}  $name",
                        seasonNumber = 1,
                        episodeNumber = ep++,
                        durationText = "视频文件",
                        overview = "网盘文件：$name",
                        thumbUrl = item.optString("thumb"),
                        streamUrl = getStreamUrl(fullPath)
                    )
                )
            }
        }
        result
    }

    override suspend fun getStreamUrl(itemId: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("path", itemId)
                put("password", "")
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url("$baseUrl/api/fs/get")
                .header("Authorization", account.token)
                .post(body)
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val rawUrl = json.optJSONObject("data")?.optString("raw_url").orEmpty()
                if (rawUrl.isNotBlank()) return@withContext rawUrl
            }
            "$baseUrl/d$itemId"
        }.getOrDefault("$baseUrl/d$itemId")
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPlaying: Boolean) {
        // Alist / WebDAV 仅本地记录
    }

    private fun createMediaItem(parentPath: String, item: JSONObject): MediaItemInfo {
        val name = item.optString("name")
        val fullPath = "$parentPath/$name".replace("//", "/")
        val sizeBytes = item.optLong("size", 0L)
        val sizeMB = sizeBytes / (1024 * 1024)
        val thumb = item.optString("thumb")

        return MediaItemInfo(
            id = fullPath,
            accountId = account.id,
            serverType = ServerType.WEBDAV_ALIST,
            title = name,
            detail = if (sizeMB > 1024) String.format("%.2f GB", sizeMB / 1024f) else "$sizeMB MB",
            overview = "路径: $fullPath",
            posterUrl = thumb,
            backdropUrl = thumb,
            mediaType = "Movie",
            streamUrl = "$baseUrl/d$fullPath"
        )
    }
}
