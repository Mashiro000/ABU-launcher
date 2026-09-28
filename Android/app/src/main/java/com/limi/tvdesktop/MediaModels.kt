package com.limi.tvdesktop

import org.json.JSONObject
import java.util.UUID

enum class ServerType(val displayName: String, val defaultPort: Int) {
    EMBY("Emby", 8096),
    JELLYFIN("Jellyfin", 8096),
    PLEX("Plex", 32400),
    WEBDAV_ALIST("WebDAV / Alist", 5244)
}

enum class LibraryDisplayMode(val displayName: String, val description: String) {
    AGGREGATED("聚合海报墙", "汇总全部已启用服务器的继续观看与最新内容"),
    ISOLATED("独立源切换", "顶部快速切换单一媒体源，各个服务器内容独立分明")
}

data class MediaAccount(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ServerType,
    val serverUrl: String,
    val username: String = "",
    val token: String = "",
    val userId: String = "",
    val enabled: Boolean = true,
    val includeInAggregate: Boolean = true,
    val ignoreSslErrors: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("serverUrl", serverUrl)
        put("username", username)
        put("token", token)
        put("userId", userId)
        put("enabled", enabled)
        put("includeInAggregate", includeInAggregate)
        put("ignoreSslErrors", ignoreSslErrors)
    }

    companion object {
        fun fromJson(json: JSONObject): MediaAccount = MediaAccount(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "未命名媒体源"),
            type = ServerType.entries.find { it.name == json.optString("type") } ?: ServerType.EMBY,
            serverUrl = json.optString("serverUrl", "http://127.0.0.1:8096"),
            username = json.optString("username", ""),
            token = json.optString("token", ""),
            userId = json.optString("userId", ""),
            enabled = json.optBoolean("enabled", true),
            includeInAggregate = json.optBoolean("includeInAggregate", true),
            ignoreSslErrors = json.optBoolean("ignoreSslErrors", true)
        )
    }
}

data class MediaItemInfo(
    val id: String,
    val accountId: String,
    val serverType: ServerType,
    val title: String,
    val detail: String = "",
    val overview: String = "",
    val tagline: String = "",
    val logoUrl: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val progress: Float = 0f,
    val playbackPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    /** Last playback time in Unix milliseconds; used to merge/sort resume rows across servers. */
    val lastPlayedAtMs: Long = 0L,
    val mediaType: String = "Movie", // "Movie", "Series", "Episode", "Music", "Folder"
    val collectionType: String = "movies", // "movies", "tvshows", "anime", "music"
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val isFavorite: Boolean = false,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val streamUrl: String = "",
    /** Server-declared poster ratio (Emby PrimaryImageAspectRatio); 0 = unknown. */
    val primaryImageAspectRatio: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("accountId", accountId)
        put("serverType", serverType.name)
        put("title", title)
        put("detail", detail)
        put("overview", overview)
        put("tagline", tagline)
        put("logoUrl", logoUrl)
        put("posterUrl", posterUrl)
        put("backdropUrl", backdropUrl)
        put("progress", progress.toDouble())
        put("playbackPositionMs", playbackPositionMs)
        put("totalDurationMs", totalDurationMs)
        put("lastPlayedAtMs", lastPlayedAtMs)
        put("mediaType", mediaType)
        put("collectionType", collectionType)
        put("year", year)
        put("rating", rating)
        put("genre", genre)
        put("isFavorite", isFavorite)
        put("seriesId", seriesId ?: "")
        put("seasonId", seasonId ?: "")
        put("seasonNumber", seasonNumber)
        put("episodeNumber", episodeNumber)
        put("streamUrl", streamUrl)
        put("primaryImageAspectRatio", primaryImageAspectRatio.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): MediaItemInfo = MediaItemInfo(
            id = json.optString("id"),
            accountId = json.optString("accountId"),
            serverType = ServerType.entries.find { it.name == json.optString("serverType") } ?: ServerType.EMBY,
            title = json.optString("title"),
            detail = json.optString("detail"),
            overview = json.optString("overview"),
            tagline = json.optString("tagline"),
            logoUrl = json.optString("logoUrl"),
            posterUrl = json.optString("posterUrl"),
            backdropUrl = json.optString("backdropUrl"),
            progress = json.optDouble("progress", 0.0).toFloat(),
            playbackPositionMs = json.optLong("playbackPositionMs", 0L),
            totalDurationMs = json.optLong("totalDurationMs", 0L),
            lastPlayedAtMs = json.optLong("lastPlayedAtMs", 0L),
            mediaType = json.optString("mediaType", "Movie"),
            collectionType = json.optString("collectionType", "movies"),
            year = json.optString("year"),
            rating = json.optString("rating"),
            genre = json.optString("genre"),
            isFavorite = json.optBoolean("isFavorite", false),
            seriesId = json.optString("seriesId").ifBlank { null },
            seasonId = json.optString("seasonId").ifBlank { null },
            seasonNumber = json.optInt("seasonNumber", 1),
            episodeNumber = json.optInt("episodeNumber", 1),
            streamUrl = json.optString("streamUrl"),
            primaryImageAspectRatio = json.optDouble("primaryImageAspectRatio", 0.0).toFloat()
        )
    }
}

data class MediaCategoryInfo(
    val id: String,
    val title: String,
    val countText: String = "",
    val collectionType: String = "movies", // "movies", "tvshows", "music", "folders"
    val thumbUrl: String = "",
    val accountId: String = "",
    /** Server-declared library image ratio; 0 = unknown, fall back to heuristics. */
    val primaryImageAspectRatio: Float = 0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("countText", countText)
        put("collectionType", collectionType)
        put("thumbUrl", thumbUrl)
        put("accountId", accountId)
        put("primaryImageAspectRatio", primaryImageAspectRatio.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): MediaCategoryInfo = MediaCategoryInfo(
            id = json.optString("id"),
            title = json.optString("title"),
            countText = json.optString("countText"),
            collectionType = json.optString("collectionType", "movies"),
            thumbUrl = json.optString("thumbUrl"),
            accountId = json.optString("accountId"),
            primaryImageAspectRatio = json.optDouble("primaryImageAspectRatio", 0.0).toFloat()
        )
    }
}

data class EpisodeInfo(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val durationText: String,
    val overview: String,
    val thumbUrl: String,
    val streamUrl: String,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val mediaSource: MediaSourceInfo? = null
)

/** One cast/crew entry from the media server. */
data class PersonInfo(
    val id: String,
    val name: String,
    val role: String = "",
    val type: String = "",        // Actor, Director, Writer, GuestStar, Producer...
    val imageUrl: String = ""
)

/** One video/audio/subtitle track. */
data class MediaStreamInfo(
    val type: String,             // Video, Audio, Subtitle
    val displayTitle: String = "",
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val channels: Int = 0,
    val language: String = "",
    val index: Int = -1,
    val isExternal: Boolean = false,
    val deliveryUrl: String = ""
)

/** Physical media file behind one item/episode. */
data class MediaSourceInfo(
    val container: String = "",
    val name: String = "",
    val sizeBytes: Long = 0L,
    val runTimeMs: Long = 0L,
    val video: MediaStreamInfo? = null,
    val audios: List<MediaStreamInfo> = emptyList(),
    val subtitles: List<MediaStreamInfo> = emptyList()
)

/** Real item metadata fetched for the detail page (null when unavailable). */
data class MediaDetailInfo(
    val originalTitle: String = "",
    val overview: String = "",
    val premiereDate: String = "",
    val officialRating: String = "",
    val communityRating: String = "",
    val productionYear: String = "",
    val runtimeMs: Long = 0L,
    val genres: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    val status: String = "",
    val recursiveItemCount: Int = 0,
    val people: List<PersonInfo> = emptyList(),
    val mediaSource: MediaSourceInfo? = null
)

/** One page of a category listing for the poster wall's incremental loading. */
data class MediaPage(
    val items: List<MediaItemInfo>,
    val totalCount: Int = 0
)
