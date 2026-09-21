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
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val progress: Float = 0f,
    val playbackPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val mediaType: String = "Movie", // "Movie", "Series", "Episode", "Music", "Folder"
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val seriesId: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val streamUrl: String = ""
)

data class MediaCategoryInfo(
    val id: String,
    val title: String,
    val countText: String = "",
    val collectionType: String = "movies", // "movies", "tvshows", "music", "folders"
    val accountId: String = ""
)

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
    val durationMs: Long = 0L
)
