package com.limi.tvdesktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class PlexProvider(override val account: MediaAccount) : MediaSourceProvider {

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

    private fun request(path: String): Request {
        val sep = if (path.contains("?")) "&" else "?"
        val url = if (path.startsWith("http")) path else "$baseUrl$path$sep" + "X-Plex-Token=${account.token}"
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Client-Identifier", "limi-desktop-tv")
            .header("X-Plex-Product", "LimiTV")
            .header("X-Plex-Version", "1.0.0")
            .build()
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/")
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val container = json.optJSONObject("MediaContainer")
                val name = container?.optString("friendlyName", "Plex Media Server") ?: "Plex Media Server"
                val ver = container?.optString("version", "") ?: ""
                "$name ($ver)"
            } else {
                throw Exception("HTTP ${resp.code}: 连接 Plex 失败")
            }
        }
    }

    override suspend fun getResumeWatching(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/library/onDeck")
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
            parseMetadata(meta)
        }.getOrDefault(emptyList())
    }

    override suspend fun getLatest(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=16")
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
            parseMetadata(meta)
        }.getOrDefault(emptyList())
    }

    override suspend fun getCategories(): List<MediaCategoryInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/library/sections")
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val dir = json.optJSONObject("MediaContainer")?.optJSONArray("Directory") ?: JSONArray()
            val list = mutableListOf<MediaCategoryInfo>()
            for (i in 0 until dir.length()) {
                val obj = dir.getJSONObject(i)
                list.add(
                    MediaCategoryInfo(
                        id = obj.optString("key"),
                        title = obj.optString("title"),
                        collectionType = obj.optString("type", "movie"),
                        accountId = account.id
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/library/sections/$categoryId/all?X-Plex-Container-Start=0&X-Plex-Container-Size=30")
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
            parseMetadata(meta)
        }.getOrDefault(emptyList())
    }

    override suspend fun getEpisodes(seriesId: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val req = request("/library/metadata/$seriesId/allLeaves")
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val meta = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
            val list = mutableListOf<EpisodeInfo>()
            for (i in 0 until meta.length()) {
                val obj = meta.getJSONObject(i)
                val ratingKey = obj.optString("ratingKey")
                val sIndex = obj.optInt("parentIndex", 1)
                val eIndex = obj.optInt("index", i + 1)
                val durMs = obj.optLong("duration", 0L)
                val viewOffset = obj.optLong("viewOffset", 0L)
                val thumb = obj.optString("thumb")
                val thumbUrl = if (thumb.isNotBlank()) "$baseUrl$thumb?X-Plex-Token=${account.token}" else ""

                list.add(
                    EpisodeInfo(
                        id = ratingKey,
                        title = "S${sIndex.toString().padStart(2, '0')} E${eIndex.toString().padStart(2, '0')}  ${obj.optString("title")}",
                        seasonNumber = sIndex,
                        episodeNumber = eIndex,
                        durationText = if (durMs > 0) "${durMs / 60000} 分钟" else "",
                        overview = obj.optString("summary"),
                        thumbUrl = thumbUrl,
                        streamUrl = getStreamUrl(ratingKey),
                        playbackPositionMs = viewOffset,
                        durationMs = durMs
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getStreamUrl(itemId: String): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = request("/library/metadata/$itemId")
                val resp = client.newCall(req).execute()
                val json = JSONObject(resp.body?.string().orEmpty())
                val item = json.optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)
                val part = item?.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)
                val key = part?.optString("key").orEmpty()
                if (key.isNotBlank()) {
                    "$baseUrl$key?X-Plex-Token=${account.token}"
                } else {
                    "$baseUrl/library/parts/$itemId/file.mp4?X-Plex-Token=${account.token}"
                }
            }.getOrDefault("$baseUrl/library/parts/$itemId/file.mp4?X-Plex-Token=${account.token}")
        }
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPlaying: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching {
                val state = if (isPlaying) "playing" else "stopped"
                val req = request("/:/progress?key=$itemId&identifier=com.plexapp.plugins.library&time=$positionMs&state=$state")
                client.newCall(req).execute().close()
            }
        }
    }

    private fun parseMetadata(array: JSONArray): List<MediaItemInfo> {
        val list = mutableListOf<MediaItemInfo>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val ratingKey = obj.optString("ratingKey")
            val title = obj.optString("title")
            val type = obj.optString("type", "movie")
            val year = obj.optString("year", "")
            val durMs = obj.optLong("duration", 0L)
            val viewOffset = obj.optLong("viewOffset", 0L)
            val progress = if (durMs > 0) (viewOffset.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) else 0f

            val detail = when (type) {
                "episode" -> {
                    val s = obj.optInt("parentIndex", 1)
                    val e = obj.optInt("index", 1)
                    "${obj.optString("grandparentTitle")} S${s}E$e"
                }
                "show" -> if (year.isNotBlank()) "$year · 剧集" else "剧集"
                "movie" -> if (year.isNotBlank()) "$year · 电影" else "电影"
                else -> type
            }

            val thumb = obj.optString("thumb")
            val art = obj.optString("art")
            val posterUrl = if (thumb.isNotBlank()) "$baseUrl$thumb?X-Plex-Token=${account.token}" else ""
            val backdropUrl = if (art.isNotBlank()) "$baseUrl$art?X-Plex-Token=${account.token}" else posterUrl

            list.add(
                MediaItemInfo(
                    id = ratingKey,
                    accountId = account.id,
                    serverType = account.type,
                    title = title,
                    detail = detail,
                    overview = obj.optString("summary"),
                    posterUrl = posterUrl,
                    backdropUrl = backdropUrl,
                    progress = progress,
                    playbackPositionMs = viewOffset,
                    totalDurationMs = durMs,
                    mediaType = type,
                    year = year,
                    rating = obj.optString("rating", ""),
                    seriesId = if (type == "episode") obj.optString("grandparentRatingKey") else if (type == "show") ratingKey else null,
                    streamUrl = "$baseUrl/library/parts/$ratingKey/file.mp4?X-Plex-Token=${account.token}"
                )
            )
        }
        return list
    }
}
