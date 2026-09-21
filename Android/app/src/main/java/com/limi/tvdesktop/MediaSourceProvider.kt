package com.limi.tvdesktop

interface MediaSourceProvider {
    val account: MediaAccount

    suspend fun testConnection(): Result<String>

    suspend fun getResumeWatching(): List<MediaItemInfo>

    suspend fun getLatest(): List<MediaItemInfo>

    suspend fun getCategories(): List<MediaCategoryInfo>

    suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo>

    suspend fun getEpisodes(seriesId: String): List<EpisodeInfo>

    suspend fun getStreamUrl(itemId: String): String

    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPlaying: Boolean)
}
