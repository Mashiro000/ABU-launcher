package com.limi.tvdesktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class EmbyProvider(override var account: MediaAccount) : MediaSourceProvider {

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

    private fun authHeader(): String {
        return "MediaBrowser Client=\"LimiTV\", Device=\"AndroidTV\", DeviceId=\"limi-desktop-tv\", Version=\"1.0.0\", Token=\"${account.token}\""
    }

    private fun request(url: String, method: String = "GET", body: RequestBody? = null): Request {
        return Request.Builder()
            .url(url)
            .header("X-Emby-Authorization", authHeader())
            .header("X-MediaBrowser-Token", account.token)
            .header("Accept", "application/json")
            .method(method, body)
            .build()
    }

    suspend fun authenticate(password: String): Result<MediaAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val authJson = JSONObject().apply {
                put("Username", account.username)
                put("Pw", password)
            }
            val req = Request.Builder()
                .url("$baseUrl/Users/AuthenticateByName")
                .header("X-Emby-Authorization", "MediaBrowser Client=\"LimiTV\", Device=\"AndroidTV\", DeviceId=\"limi-desktop-tv\", Version=\"1.0.0\"")
                .post(authJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("认证失败，HTTP ${resp.code}")
            val resStr = resp.body?.string().orEmpty()
            val obj = JSONObject(resStr)
            val token = obj.optString("AccessToken")
            val userId = obj.optJSONObject("User")?.optString("Id").orEmpty()
            account = account.copy(token = token, userId = userId)
            account
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/System/Info/Public").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val json = JSONObject(resp.body?.string().orEmpty())
                val name = json.optString("ServerName", account.type.displayName)
                val ver = json.optString("Version", "")
                "$name ($ver)"
            } else {
                throw Exception("HTTP ${resp.code}: 无法连接服务器")
            }
        }
    }

    override suspend fun getResumeWatching(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) {
                "$baseUrl/Users/$userId/Items/Resume?Limit=12&Recursive=true&Fields=Overview,PrimaryImageAspectRatio"
            } else {
                "$baseUrl/Items?Limit=12&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&Filters=IsResumable&Fields=Overview"
            }
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("Items") ?: JSONArray()
            parseItems(items)
        }.getOrDefault(emptyList())
    }

    override suspend fun getLatest(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) {
                "$baseUrl/Users/$userId/Items/Latest?Limit=16&Fields=Overview,PrimaryImageAspectRatio"
            } else {
                "$baseUrl/Items?Limit=16&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Fields=Overview"
            }
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val items = JSONArray(resp.body?.string().orEmpty())
            parseItems(items)
        }.getOrDefault(emptyList())
    }

    override suspend fun getCategories(): List<MediaCategoryInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) "$baseUrl/Users/$userId/Views" else "$baseUrl/Library/MediaFolders"
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("Items") ?: JSONArray()
            val list = mutableListOf<MediaCategoryInfo>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                list.add(
                    MediaCategoryInfo(
                        id = obj.optString("Id"),
                        title = obj.optString("Name"),
                        collectionType = obj.optString("CollectionType", "movies"),
                        accountId = account.id
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = "$baseUrl/Users/$userId/Items?ParentId=$categoryId&Limit=30&Recursive=true&SortBy=SortName&Fields=Overview,PrimaryImageAspectRatio"
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("Items") ?: JSONArray()
            parseItems(items)
        }.getOrDefault(emptyList())
    }

    override suspend fun getEpisodes(seriesId: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = "$baseUrl/Shows/$seriesId/Episodes?UserId=$userId&Fields=Overview,PrimaryImageAspectRatio"
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("Items") ?: JSONArray()
            val list = mutableListOf<EpisodeInfo>()
            for (i in 0 until items.length()) {
                val obj = items.getJSONObject(i)
                val itemId = obj.optString("Id")
                val ticks = obj.optLong("RunTimeTicks", 0L)
                val durationMin = ticks / 10_000_000 / 60
                val userData = obj.optJSONObject("UserData")
                val playedTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
                list.add(
                    EpisodeInfo(
                        id = itemId,
                        title = "S${obj.optInt("ParentIndexNumber", 1).toString().padStart(2, '0')} E${obj.optInt("IndexNumber", i + 1).toString().padStart(2, '0')}  ${obj.optString("Name")}",
                        seasonNumber = obj.optInt("ParentIndexNumber", 1),
                        episodeNumber = obj.optInt("IndexNumber", i + 1),
                        durationText = if (durationMin > 0) "$durationMin 分钟" else "",
                        overview = obj.optString("Overview"),
                        thumbUrl = "$baseUrl/Items/$itemId/Images/Primary?quality=80&maxWidth=500&api_key=${account.token}",
                        streamUrl = getStreamUrl(itemId),
                        playbackPositionMs = playedTicks / 10_000,
                        durationMs = ticks / 10_000
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getStreamUrl(itemId: String): String {
        return "$baseUrl/Videos/$itemId/stream.mp4?static=true&api_key=${account.token}"
    }

    override suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPlaying: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching {
                val path = if (isPlaying) "Progress" else "Stopped"
                val bodyJson = JSONObject().apply {
                    put("ItemId", itemId)
                    put("PositionTicks", positionMs * 10_000)
                    put("IsPaused", !isPlaying)
                }
                val req = request(
                    url = "$baseUrl/Sessions/Playing/$path",
                    method = "POST",
                    body = bodyJson.toString().toRequestBody("application/json".toMediaType())
                )
                client.newCall(req).execute().close()
            }
        }
    }

    private fun parseItems(array: JSONArray): List<MediaItemInfo> {
        val result = mutableListOf<MediaItemInfo>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("Id")
            val name = obj.optString("Name")
            val type = obj.optString("Type", "Movie")
            val year = obj.optString("ProductionYear", "")
            val detail = when (type) {
                "Episode" -> {
                    val s = obj.optInt("ParentIndexNumber", 1)
                    val e = obj.optInt("IndexNumber", 1)
                    "${obj.optString("SeriesName")} S${s}E$e"
                }
                "Series" -> if (year.isNotBlank()) "$year · 电视剧" else "电视剧"
                "Movie" -> if (year.isNotBlank()) "$year · 电影" else "电影"
                else -> type
            }
            val userData = obj.optJSONObject("UserData")
            val playedTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
            val totalTicks = obj.optLong("RunTimeTicks", 0L)
            val progress = if (totalTicks > 0) (playedTicks.toFloat() / totalTicks.toFloat()).coerceIn(0f, 1f) else 0f

            result.add(
                MediaItemInfo(
                    id = id,
                    accountId = account.id,
                    serverType = account.type,
                    title = name,
                    detail = detail,
                    overview = obj.optString("Overview"),
                    posterUrl = "$baseUrl/Items/$id/Images/Primary?quality=85&maxWidth=600&api_key=${account.token}",
                    backdropUrl = "$baseUrl/Items/$id/Images/Backdrop?quality=85&maxWidth=1920&api_key=${account.token}",
                    progress = progress,
                    playbackPositionMs = playedTicks / 10_000,
                    totalDurationMs = totalTicks / 10_000,
                    mediaType = type,
                    year = year,
                    rating = obj.optString("CommunityRating", ""),
                    seriesId = if (type == "Episode") obj.optString("SeriesId") else if (type == "Series") id else null,
                    streamUrl = "$baseUrl/Videos/$id/stream.mp4?static=true&api_key=${account.token}"
                )
            )
        }
        return result
    }
}
