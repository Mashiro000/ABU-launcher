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
import java.time.Instant
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
                "$baseUrl/Users/$userId/Items/Resume?Limit=12&Recursive=true&Fields=Overview,PrimaryImageAspectRatio,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres,MediaSources"
            } else {
                "$baseUrl/Items?Limit=12&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&Filters=IsResumable&Fields=Overview,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
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
                "$baseUrl/Users/$userId/Items/Latest?Limit=16&Fields=Overview,PrimaryImageAspectRatio,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            } else {
                "$baseUrl/Items?Limit=16&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Fields=Overview,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            }
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val items = JSONArray(resp.body?.string().orEmpty())
            parseItems(items)
        }.getOrDefault(emptyList())
    }

    override suspend fun getFavorites(): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) {
                "$baseUrl/Users/$userId/Items?Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series,Episode,Video&Limit=30&Fields=Overview,PrimaryImageAspectRatio,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            } else {
                "$baseUrl/Items?Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series,Episode,Video&Limit=30&Fields=Overview,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            }
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            val items = json.optJSONArray("Items") ?: JSONArray()
            parseItems(items)
        }.getOrDefault(emptyList())
    }

    override suspend fun setFavorite(itemId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) {
                "$baseUrl/Users/$userId/FavoriteItems/$itemId"
            } else {
                "$baseUrl/FavoriteItems/$itemId"
            }
            val method = if (isFavorite) "POST" else "DELETE"
            val body = if (isFavorite) "".toRequestBody("application/json".toMediaType()) else null
            val req = request(url, method, body)
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        }.getOrDefault(false)
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
                val catId = obj.optString("Id")
                list.add(
                    MediaCategoryInfo(
                        id = catId,
                        title = obj.optString("Name"),
                        collectionType = obj.optString("CollectionType", "movies"),
                        thumbUrl = "$baseUrl/Items/$catId/Images/Primary?quality=80&maxWidth=600&api_key=${account.token}",
                        accountId = account.id,
                        primaryImageAspectRatio = obj.optDouble("PrimaryImageAspectRatio", 0.0).toFloat()
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo> =
        getCategoryItemsPage(categoryId, 0, 60).items

    override suspend fun getCategoryItemsPage(categoryId: String, startIndex: Int, limit: Int): MediaPage = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            // Recursive=true alone also returns every Episode; the poster wall must only show
            // library-level items (Series/Movie/...) so it uses portrait posters, not episode stills.
            val includeTypes = "Movie,Series,Video,BoxSet,MusicAlbum,Audio,Photo"
            val url = "$baseUrl/Users/$userId/Items?ParentId=$categoryId&StartIndex=$startIndex&Limit=$limit&Recursive=true&SortBy=SortName&IncludeItemTypes=$includeTypes&Fields=Overview,PrimaryImageAspectRatio,Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            val req = request(url)
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@runCatching MediaPage(emptyList(), startIndex)
            val json = JSONObject(resp.body?.string().orEmpty())
            MediaPage(
                items = parseItems(json.optJSONArray("Items") ?: JSONArray()),
                totalCount = json.optInt("TotalRecordCount", 0)
            )
        }.getOrDefault(MediaPage(emptyList(), startIndex))
    }

    override suspend fun getEpisodes(seriesId: String): List<EpisodeInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = if (userId.isNotBlank()) {
                "$baseUrl/Shows/$seriesId/Episodes?UserId=$userId&Fields=Overview,PrimaryImageAspectRatio,UserData"
            } else {
                "$baseUrl/Shows/$seriesId/Episodes?Fields=Overview,PrimaryImageAspectRatio,UserData"
            }
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
                        // Resolve transcode/direct-play policy only for the episode the user
                        // actually starts; doing it here creates one request per episode.
                        streamUrl = "$baseUrl/Videos/$itemId/stream?static=true&api_key=${account.token}",
                        playbackPositionMs = playedTicks / 10_000,
                        durationMs = ticks / 10_000,
                        mediaSource = null
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    override suspend fun getItemDetail(itemId: String): MediaDetailInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val fields = "Overview,Taglines,Genres,Studios,People,MediaSources,OfficialRating,PremiereDate," +
                "ProductionYear,CommunityRating,RunTimeTicks,ChildCount,RecursiveItemCount,Type,OriginalTitle,ProductionLocations"
            val url = if (userId.isNotBlank()) "$baseUrl/Users/$userId/Items/$itemId?Fields=$fields"
            else "$baseUrl/Items/$itemId?Fields=$fields"
            val resp = client.newCall(request(url)).execute()
            if (!resp.isSuccessful) return@runCatching null
            parseDetail(JSONObject(resp.body?.string().orEmpty()))
        }.getOrNull()
    }

    override suspend fun getSimilar(itemId: String): List<MediaItemInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = account.userId
            val url = "$baseUrl/Items/$itemId/Similar?UserId=$userId&Limit=12&Fields=Overview,PrimaryImageAspectRatio," +
                "Taglines,ImageTags,BackdropImageTags,ParentLogoItemId,ParentBackdropItemId,SeriesId,UserData,Genres"
            val resp = client.newCall(request(url)).execute()
            if (!resp.isSuccessful) return@runCatching emptyList()
            val json = JSONObject(resp.body?.string().orEmpty())
            parseItems(json.optJSONArray("Items") ?: JSONArray())
        }.getOrDefault(emptyList())
    }

    private fun parseDetail(obj: JSONObject): MediaDetailInfo {
        val people = mutableListOf<PersonInfo>()
        obj.optJSONArray("People")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val pid = p.optString("Id")
                val hasImage = p.optString("PrimaryImageTag").isNotBlank()
                people.add(
                    PersonInfo(
                        id = pid,
                        name = p.optString("Name"),
                        role = p.optString("Role"),
                        type = p.optString("Type"),
                        imageUrl = if (pid.isNotBlank() && hasImage) {
                            "$baseUrl/Items/$pid/Images/Primary?maxWidth=240&quality=90&api_key=${account.token}"
                        } else ""
                    )
                )
            }
        }
        val genres = obj.optJSONArray("Genres")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
        val studios = obj.optJSONArray("Studios")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("Name")?.ifBlank { null } }
        }.orEmpty()
        val countries = obj.optJSONArray("ProductionLocations")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }.orEmpty()
        val rating = obj.optDouble("CommunityRating", 0.0)
        return MediaDetailInfo(
            originalTitle = obj.optString("OriginalTitle"),
            overview = obj.optString("Overview"),
            premiereDate = obj.optString("PremiereDate").take(10),
            officialRating = obj.optString("OfficialRating"),
            communityRating = if (rating > 0.0) (kotlin.math.round(rating * 10) / 10.0).toString() else "",
            productionYear = obj.optString("ProductionYear"),
            runtimeMs = obj.optLong("RunTimeTicks", 0L) / 10_000,
            genres = genres,
            studios = studios,
            countries = countries,
            status = obj.optString("Status"),
            recursiveItemCount = obj.optInt("RecursiveItemCount", 0),
            people = people,
            mediaSource = obj.optJSONArray("MediaSources")?.optJSONObject(0)?.let { parseSource(it) }
        )
    }

    private fun parseSource(obj: JSONObject): MediaSourceInfo {
        val streams = obj.optJSONArray("MediaStreams")
        var video: MediaStreamInfo? = null
        val audios = mutableListOf<MediaStreamInfo>()
        val subtitles = mutableListOf<MediaStreamInfo>()
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val s = streams.optJSONObject(i) ?: continue
                val rawDeliveryUrl = s.optString("DeliveryUrl")
                val deliveryUrl = when {
                    rawDeliveryUrl.isBlank() -> ""
                    rawDeliveryUrl.startsWith("http://") || rawDeliveryUrl.startsWith("https://") -> rawDeliveryUrl
                    else -> baseUrl.trimEnd('/') + "/" + rawDeliveryUrl.trimStart('/') +
                        (if (rawDeliveryUrl.contains("api_key=")) "" else if (rawDeliveryUrl.contains('?')) "&api_key=${account.token}" else "?api_key=${account.token}")
                }
                val info = MediaStreamInfo(
                    type = s.optString("Type"),
                    displayTitle = s.optString("DisplayTitle"),
                    codec = s.optString("Codec"),
                    width = s.optInt("Width", 0),
                    height = s.optInt("Height", 0),
                    channels = s.optInt("Channels", 0),
                    language = s.optString("Language"),
                    index = s.optInt("Index", i),
                    isExternal = s.optBoolean("IsExternal", false),
                    deliveryUrl = deliveryUrl
                )
                when (info.type) {
                    "Video" -> if (video == null) video = info
                    "Audio" -> audios.add(info)
                    "Subtitle" -> subtitles.add(info)
                }
            }
        }
        return MediaSourceInfo(
            container = obj.optString("Container"),
            name = obj.optString("Name"),
            sizeBytes = obj.optLong("Size", 0L),
            runTimeMs = obj.optLong("RunTimeTicks", 0L) / 10_000,
            video = video,
            audios = audios,
            subtitles = subtitles
        )
    }

    override suspend fun getStreamUrl(itemId: String): String {
        val policy = AccountManager.embyPlaybackPolicy.value
        if (policy == AccountManager.EmbyPlaybackPolicy.PREFER_DIRECT) {
            return "$baseUrl/Videos/$itemId/stream?static=true&api_key=${account.token}"
        }
        return requestPlaybackUrl(itemId, policy)
            ?: "$baseUrl/Videos/$itemId/stream?static=true&api_key=${account.token}"
    }

    private suspend fun requestPlaybackUrl(
        itemId: String,
        policy: AccountManager.EmbyPlaybackPolicy,
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val allowTranscoding = policy != AccountManager.EmbyPlaybackPolicy.NO_TRANSCODE
            val preferTranscoding = policy == AccountManager.EmbyPlaybackPolicy.PREFER_TRANSCODE
            val profile = JSONObject().apply {
                put("MaxStreamingBitrate", 120_000_000)
                put("MaxStaticBitrate", 120_000_000)
                put("MusicStreamingTranscodingBitrate", 384_000)
                put("DirectPlayProfiles", JSONArray().apply {
                    put(JSONObject().put("Type", "Video").put("Container", "mp4,mkv,webm,mpegts,ts,m2ts,avi,mov"))
                    put(JSONObject().put("Type", "Audio").put("Container", "mp3,aac,m4a,flac,ogg,opus,wav"))
                })
                put("TranscodingProfiles", JSONArray().apply {
                    put(JSONObject().put("Type", "Video").put("Protocol", "hls")
                        .put("Container", "ts").put("VideoCodec", "h264").put("AudioCodec", "aac")
                        .put("Context", "Streaming"))
                })
            }
            val body = JSONObject().apply {
                put("UserId", account.userId)
                put("DeviceProfile", profile)
                put("EnableDirectPlay", !preferTranscoding)
                put("EnableDirectStream", !preferTranscoding)
                put("EnableTranscoding", allowTranscoding)
                put("AllowVideoStreamCopy", !preferTranscoding)
                put("AllowAudioStreamCopy", !preferTranscoding)
            }
            val url = "$baseUrl/Items/$itemId/PlaybackInfo?UserId=${account.userId}&IsPlayback=true&AutoOpenLiveStream=true"
            client.newCall(request(url, "POST", body.toString().toRequestBody("application/json".toMediaType())))
                .execute().use { response ->
                    require(response.isSuccessful) { "PlaybackInfo HTTP ${response.code}" }
                    val result = JSONObject(response.body?.string().orEmpty())
                    val source = result.optJSONArray("MediaSources")?.optJSONObject(0) ?: return@use null
                    val relative = when {
                        preferTranscoding -> source.optString("TranscodingUrl")
                        source.optBoolean("SupportsDirectPlay") ->
                            "/Videos/$itemId/stream?static=true&MediaSourceId=${source.optString("Id")}"
                        source.optString("DirectStreamUrl").isNotBlank() -> source.optString("DirectStreamUrl")
                        allowTranscoding -> source.optString("TranscodingUrl")
                        else -> ""
                    }
                    if (relative.isBlank()) null else absolutePlaybackUrl(relative)
                }
        }.getOrNull()
    }

    private fun absolutePlaybackUrl(url: String): String {
        val absolute = if (url.startsWith("http://") || url.startsWith("https://")) url
        else baseUrl + "/" + url.trimStart('/')
        return absolute + when {
            absolute.contains("api_key=") -> ""
            absolute.contains('?') -> "&api_key=${account.token}"
            else -> "?api_key=${account.token}"
        }
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
            val seriesName = obj.optString("SeriesName")
            val seriesId = obj.optString("SeriesId").ifBlank { if (type == "Series") id else null }
            val seasonId = obj.optString("SeasonId").ifBlank { null }
            val seasonNumber = obj.optInt("ParentIndexNumber", 1)
            val episodeNumber = obj.optInt("IndexNumber", 1)

            val detail = when (type) {
                "Episode" -> {
                    val sName = if (seriesName.isNotBlank()) seriesName else name
                    "$sName S${seasonNumber}E$episodeNumber"
                }
                "Series" -> if (year.isNotBlank()) "$year · 电视剧" else "电视剧"
                "Movie" -> if (year.isNotBlank()) "$year · 电影" else "电影"
                else -> type
            }

            val userData = obj.optJSONObject("UserData")
            val playedTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
            val totalTicks = obj.optLong("RunTimeTicks", 0L)
            val progress = if (totalTicks > 0) (playedTicks.toFloat() / totalTicks.toFloat()).coerceIn(0f, 1f) else 0f
            val isFavorite = userData?.optBoolean("IsFavorite", false) ?: false
            val lastPlayedAtMs = userData?.optString("LastPlayedDate")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) }
                ?: 0L

            // Tagline & Overview
            val taglines = obj.optJSONArray("Taglines")
            var tagline = if (taglines != null && taglines.length() > 0) taglines.optString(0, "") else ""
            val overview = obj.optString("Overview")
            if (tagline.isBlank() && overview.isNotBlank()) {
                val firstSentence = overview.split('。', '！', '!', '.', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: overview
                tagline = if (firstSentence.length > 35) firstSentence.take(35) + "..." else firstSentence
            }

            // ClearLogo URL
            val imageTags = obj.optJSONObject("ImageTags")
            val hasLogo = imageTags?.has("Logo") == true
            val parentLogoItemId = obj.optString("ParentLogoItemId").ifBlank { null }
            val logoItemId = when {
                hasLogo -> id
                !parentLogoItemId.isNullOrBlank() -> parentLogoItemId
                !seriesId.isNullOrBlank() -> seriesId
                else -> null
            }
            val logoUrl = if (logoItemId != null) {
                "$baseUrl/Items/$logoItemId/Images/Logo?quality=90&maxWidth=800&api_key=${account.token}"
            } else ""

            // Backdrop URL
            val backdropImageTags = obj.optJSONArray("BackdropImageTags")
            val hasBackdrop = (backdropImageTags != null && backdropImageTags.length() > 0) || imageTags?.has("Backdrop") == true
            val parentBackdropItemId = obj.optString("ParentBackdropItemId").ifBlank { null }
            val backdropItemId = when {
                hasBackdrop -> id
                !parentBackdropItemId.isNullOrBlank() -> parentBackdropItemId
                !seriesId.isNullOrBlank() -> seriesId
                else -> id
            }
            val backdropUrl = "$baseUrl/Items/$backdropItemId/Images/Backdrop?quality=85&maxWidth=1920&api_key=${account.token}"

            // Genres & CollectionType
            val genres = obj.optJSONArray("Genres")
            val genreList = mutableListOf<String>()
            if (genres != null) {
                for (g in 0 until genres.length()) {
                    genreList.add(genres.optString(g))
                }
            }
            val genreStr = genreList.joinToString(" / ")
            val isAnime = genreList.any { it.contains("动画") || it.contains("动漫") || it.equals("Anime", ignoreCase = true) }
            val collectionType = when {
                isAnime -> "anime"
                type == "Series" || type == "Episode" -> "tvshows"
                type == "Movie" -> "movies"
                else -> "movies"
            }

            result.add(
                MediaItemInfo(
                    id = id,
                    accountId = account.id,
                    serverType = account.type,
                    title = name,
                    detail = detail,
                    overview = overview,
                    tagline = tagline,
                    logoUrl = logoUrl,
                    posterUrl = "$baseUrl/Items/$id/Images/Primary?quality=85&maxWidth=600&api_key=${account.token}",
                    backdropUrl = backdropUrl,
                    progress = progress,
                    playbackPositionMs = playedTicks / 10_000,
                    totalDurationMs = totalTicks / 10_000,
                    lastPlayedAtMs = lastPlayedAtMs,
                    mediaType = type,
                    collectionType = collectionType,
                    year = year,
                    rating = obj.optString("CommunityRating", ""),
                    genre = genreStr,
                    isFavorite = isFavorite,
                    seriesId = seriesId,
                    seasonId = seasonId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    streamUrl = "$baseUrl/Videos/$id/stream.mp4?static=${AccountManager.preferDirectPlay.value}&api_key=${account.token}"
                )
            )
        }
        return result
    }
}
