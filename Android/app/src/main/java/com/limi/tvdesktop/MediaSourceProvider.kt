package com.limi.tvdesktop

interface MediaSourceProvider {
    val account: MediaAccount

    suspend fun testConnection(): Result<String>

    suspend fun getResumeWatching(): List<MediaItemInfo>

    suspend fun getLatest(): List<MediaItemInfo>

    suspend fun getFavorites(): List<MediaItemInfo>

    suspend fun getCategories(): List<MediaCategoryInfo>

    suspend fun getCategoryItems(categoryId: String): List<MediaItemInfo>

    /** Paged listing for the poster wall. Default ignores paging (single full page). */
    suspend fun getCategoryItemsPage(categoryId: String, startIndex: Int, limit: Int): MediaPage {
        if (startIndex > 0) return MediaPage(emptyList(), 0)
        val all = getCategoryItems(categoryId)
        return MediaPage(all, all.size)
    }

    suspend fun getEpisodes(seriesId: String): List<EpisodeInfo>

    suspend fun getStreamUrl(itemId: String): String

    suspend fun reportPlaybackProgress(itemId: String, positionMs: Long, isPlaying: Boolean)

    suspend fun setFavorite(itemId: String, isFavorite: Boolean): Boolean = false

    /** Rich metadata for the detail page; null when the provider cannot supply it. */
    suspend fun getItemDetail(itemId: String): MediaDetailInfo? = null

    /** Server-recommended similar titles (Emby /Items/{id}/Similar). */
    suspend fun getSimilar(itemId: String): List<MediaItemInfo> = emptyList()
}
