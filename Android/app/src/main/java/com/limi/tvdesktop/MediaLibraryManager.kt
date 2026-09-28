package com.limi.tvdesktop

import androidx.compose.runtime.mutableStateListOf
import com.limi.tvdesktop.player.TvEpisodeItem
import com.limi.tvdesktop.player.TvPlaybackInfo
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaLibraryManager {

    private const val CACHE_VERSION = 2

    private val providerCache = mutableMapOf<String, MediaSourceProvider>()
    private val scope = CoroutineScope(Dispatchers.Main)

    val resumeWatching = mutableStateListOf<MediaItemInfo>()
    val latestItems = mutableStateListOf<MediaItemInfo>()
    val favorites = mutableStateListOf<MediaItemInfo>()
    val categories = mutableStateListOf<MediaCategoryInfo>()
    val currentBannerItem = mutableStateOf<MediaItemInfo?>(null)
    val isRefreshing = mutableStateOf(false)

    private var cacheFile: java.io.File? = null

    val hasRealAccounts: Boolean
        get() = AccountManager.accounts.any { it.enabled }

    val hasData: Boolean
        get() = resumeWatching.isNotEmpty() || latestItems.isNotEmpty() || categories.isNotEmpty()

    fun init(context: android.content.Context) {
        if (cacheFile == null) {
            cacheFile = java.io.File(context.filesDir, "media_library_cache.json")
            loadFromCache()
        }
    }

    private fun loadFromCache() {
        val file = cacheFile ?: return
        if (!file.exists() || file.length() == 0L) return
        runCatching {
            val content = file.readText()
            val root = org.json.JSONObject(content)

            val resumeArr = if (root.optInt("version", 1) >= CACHE_VERSION) root.optJSONArray("resume") else null
            if (resumeArr != null && resumeArr.length() > 0) {
                resumeWatching.clear()
                for (i in 0 until resumeArr.length()) {
                    resumeWatching.add(MediaItemInfo.fromJson(resumeArr.getJSONObject(i)))
                }
            }

            val latestArr = root.optJSONArray("latest")
            if (latestArr != null && latestArr.length() > 0) {
                latestItems.clear()
                for (i in 0 until latestArr.length()) {
                    latestItems.add(MediaItemInfo.fromJson(latestArr.getJSONObject(i)))
                }
            }

            val favArr = root.optJSONArray("favorites")
            if (favArr != null && favArr.length() > 0) {
                favorites.clear()
                for (i in 0 until favArr.length()) {
                    favorites.add(MediaItemInfo.fromJson(favArr.getJSONObject(i)))
                }
            }

            val catArr = root.optJSONArray("categories")
            if (catArr != null && catArr.length() > 0) {
                categories.clear()
                for (i in 0 until catArr.length()) {
                    categories.add(MediaCategoryInfo.fromJson(catArr.getJSONObject(i)))
                }
            }

            currentBannerItem.value = resumeWatching.firstOrNull() ?: latestItems.firstOrNull()
        }
    }

    private fun saveToCache() {
        val file = cacheFile ?: return
        runCatching {
            val root = org.json.JSONObject()
            root.put("version", CACHE_VERSION)

            val resumeArr = org.json.JSONArray()
            resumeWatching.forEach { resumeArr.put(it.toJson()) }
            root.put("resume", resumeArr)

            val latestArr = org.json.JSONArray()
            latestItems.forEach { latestArr.put(it.toJson()) }
            root.put("latest", latestArr)

            val favArr = org.json.JSONArray()
            favorites.forEach { favArr.put(it.toJson()) }
            root.put("favorites", favArr)

            val catArr = org.json.JSONArray()
            categories.forEach { catArr.put(it.toJson()) }
            root.put("categories", catArr)

            file.writeText(root.toString())
        }
    }

    fun getProvider(account: MediaAccount): MediaSourceProvider {
        return providerCache.getOrPut(account.id) {
            when (account.type) {
                ServerType.EMBY, ServerType.JELLYFIN -> EmbyProvider(account)
                ServerType.PLEX -> PlexProvider(account)
                ServerType.WEBDAV_ALIST -> WebDavAlistProvider(account)
            }
        }
    }

    fun getProviderByAccountId(accountId: String): MediaSourceProvider? {
        val account = AccountManager.accounts.find { it.id == accountId } ?: return null
        return getProvider(account)
    }

    fun refresh(onComplete: (() -> Unit)? = null) {
        scope.launch {
            isRefreshing.value = true
            withContext(Dispatchers.IO) {
                runCatching {
                    fetchData()
                }
            }
            isRefreshing.value = false
            onComplete?.invoke()
        }
    }

    private suspend fun fetchData() {
        val accounts = AccountManager.accounts.filter { it.enabled }
        if (accounts.isEmpty()) {
            withContext(Dispatchers.Main) {
                resumeWatching.clear()
                latestItems.clear()
                favorites.clear()
                categories.clear()
                currentBannerItem.value = null
                saveToCache()
            }
            return
        }

        val mode = AccountManager.displayMode.value
        val targetAccounts = if (mode == LibraryDisplayMode.ISOLATED) {
            val selected = AccountManager.selectedAccountId.value
            accounts.filter { it.id == selected }.ifEmpty { accounts.take(1) }
        } else {
            accounts.filter { it.includeInAggregate }
        }

        val allResume = mutableListOf<MediaItemInfo>()
        val allLatest = mutableListOf<MediaItemInfo>()
        val allFavorites = mutableListOf<MediaItemInfo>()
        val allCategories = mutableListOf<MediaCategoryInfo>()

        for (acc in targetAccounts) {
            val provider = getProvider(acc)
            val resume = runCatching { provider.getResumeWatching() }.getOrDefault(emptyList())
            val latest = runCatching { provider.getLatest() }.getOrDefault(emptyList())
            val favs = runCatching { provider.getFavorites() }.getOrDefault(emptyList())
            val cats = runCatching { provider.getCategories() }.getOrDefault(emptyList())

            allResume.addAll(resume)
            allLatest.addAll(latest)
            allFavorites.addAll(favs)
            allCategories.addAll(cats)
        }

        withContext(Dispatchers.Main) {
            resumeWatching.clear()
            if (AccountManager.showNextUp.value) {
                resumeWatching.addAll(
                    allResume.distinctBy { "${it.accountId}_${it.id}" }
                        .sortedByDescending { it.lastPlayedAtMs }
                )
            }

            latestItems.clear()
            latestItems.addAll(
                allLatest.distinctBy { "${it.accountId}_${it.id}" }
                    .filterNot { AccountManager.hideWatched.value && it.totalDurationMs > 0L && it.playbackPositionMs >= it.totalDurationMs * 0.9 }
            )

            favorites.clear()
            favorites.addAll(allFavorites.distinctBy { "${it.accountId}_${it.id}" })

            categories.clear()
            categories.addAll(allCategories)

            currentBannerItem.value = resumeWatching.firstOrNull() ?: latestItems.firstOrNull()
            saveToCache()
        }
    }

    /**
     * A Series has no stream of its own (Emby answers 500 for /Videos/{seriesId}/stream.mp4),
     * so resolve it to its first playable episode before handing it to the player.
     */
    suspend fun playableItem(item: MediaItemInfo): MediaItemInfo {
        if (item.mediaType != "Series") return item
        val provider = getProviderByAccountId(item.accountId) ?: return item
        val seriesId = item.seriesId ?: item.id
        val episodes = runCatching { provider.getEpisodes(seriesId) }.getOrDefault(emptyList())
        val episode = episodes.firstOrNull { it.streamUrl.isNotBlank() } ?: return item
        return MediaItemInfo(
            id = episode.id,
            accountId = item.accountId,
            serverType = item.serverType,
            title = episode.title,
            detail = episode.durationText,
            overview = episode.overview,
            posterUrl = episode.thumbUrl,
            backdropUrl = item.backdropUrl,
            mediaType = "Episode",
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            streamUrl = episode.streamUrl,
            playbackPositionMs = episode.playbackPositionMs,
            totalDurationMs = episode.durationMs,
            seriesId = item.seriesId
        )
    }

    /**
     * Real player package for [item]: real stream URL, real episode playlist, real codecs /
     * quality / metadata. Nothing demo or mock is injected.
     */
    suspend fun buildPlaybackInfo(item: MediaItemInfo, title: String, startMs: Long): TvPlaybackInfo {
        val provider = getProviderByAccountId(item.accountId)
        val detail = runCatching { provider?.getItemDetail(item.id) }.getOrNull()
        val episodes = if (item.mediaType == "Series" || item.mediaType == "Episode") {
            runCatching { provider?.getEpisodes(item.seriesId ?: item.id).orEmpty() }.getOrDefault(emptyList())
        } else emptyList()

        val playlist = episodes.map { ep ->
            TvEpisodeItem(
                id = ep.id,
                index = ep.episodeNumber,
                title = "第 " + ep.episodeNumber + " 集",
                subTitle = ep.title,
                thumbUrl = ep.thumbUrl,
                streamUrl = ep.streamUrl,
                durationMs = ep.durationMs,
                startPositionMs = ep.playbackPositionMs
            )
        }
        val currentIndex = playlist.indexOfFirst { it.id == item.id }.takeIf { it >= 0 } ?: 0

        val video = detail?.mediaSource?.video
        val quality = when {
            video == null -> ""
            video.height >= 2000 -> "4K " + video.codec.uppercase()
            video.height >= 1000 -> "1080p " + video.codec.uppercase()
            video.height > 0 -> video.height.toString() + "p " + video.codec.uppercase()
            else -> video.codec.uppercase()
        }
        val meta = buildString {
            val year = detail?.productionYear.orEmpty().ifBlank { item.year }
            if (year.isNotBlank()) append(year)
            val genres = detail?.genres.orEmpty().ifEmpty { item.genre.split(" / ").filter { it.isNotBlank() } }
            if (genres.isNotEmpty()) { if (isNotEmpty()) append(" ｜ "); append(genres.joinToString(" ")) }
            val count = if (episodes.isNotEmpty()) episodes.size else detail?.recursiveItemCount ?: 0
            if (count > 0) { if (isNotEmpty()) append(" ｜ "); append("共 " + count + " 集") }
        }
        val seText = if (item.mediaType == "Episode" || item.mediaType == "Series") {
            "S%02d E%02d".format(item.seasonNumber.coerceAtLeast(1), item.episodeNumber.coerceAtLeast(1))
        } else ""

        return TvPlaybackInfo(
            mediaId = item.id,
            title = title.ifBlank { item.title },
            seasonEpisodeText = seText,
            metaSubtitle = meta,
            qualityTag = quality,
            streamUrl = item.streamUrl,
            backdropUrl = item.backdropUrl,
            startPositionMs = startMs,
            totalDurationMs = if (item.totalDurationMs > 0) item.totalDurationMs else detail?.runtimeMs ?: 0L,
            overview = item.overview.ifBlank { detail?.overview.orEmpty() },
            playlist = playlist,
            currentEpisodeIndex = currentIndex,
            danmakus = emptyList(),
            externalSubtitleUrls = detail?.mediaSource?.subtitles.orEmpty()
                .filter { it.isExternal && it.deliveryUrl.isNotBlank() }
                .map { it.deliveryUrl }
        )
    }

    fun reportPlayback(item: MediaItemInfo, positionMs: Long, durationMs: Long = item.totalDurationMs, isPlaying: Boolean) {
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.takeIf { it > 0L } ?: item.totalDurationMs
        val progress = if (safeDuration > 0L) {
            (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        } else item.progress

        // Reflect a playback stop locally at once. Emby/Jellyfin may apply a server-side
        // minimum-resume threshold or take time to update /Items/Resume; neither should make a
        // movie just watched in this app disappear from the home screen.
        if (!isPlaying && safePosition > 0L) {
            val keyMatches: (MediaItemInfo) -> Boolean = { it.id == item.id && it.accountId == item.accountId }
            resumeWatching.removeAll(keyMatches)
            if (safeDuration <= 0L || safePosition < safeDuration * 0.95) {
                resumeWatching.add(
                    0,
                    item.copy(
                        playbackPositionMs = safePosition,
                        totalDurationMs = safeDuration,
                        progress = progress,
                        lastPlayedAtMs = System.currentTimeMillis()
                    )
                )
                currentBannerItem.value = resumeWatching.first()
            }
            saveToCache()
        }

        scope.launch(Dispatchers.IO) {
            val provider = getProviderByAccountId(item.accountId)
            provider?.reportPlaybackProgress(item.id, positionMs, isPlaying)
        }
    }

    fun toggleFavorite(item: MediaItemInfo, isFav: Boolean) {
        if (isFav) {
            if (favorites.none { it.id == item.id && it.accountId == item.accountId }) {
                favorites.add(0, item.copy(isFavorite = true))
            }
        } else {
            favorites.removeAll { it.id == item.id && it.accountId == item.accountId }
        }
        saveToCache()
        scope.launch(Dispatchers.IO) {
            val provider = getProviderByAccountId(item.accountId)
            provider?.setFavorite(item.id, isFav)
        }
    }
}
