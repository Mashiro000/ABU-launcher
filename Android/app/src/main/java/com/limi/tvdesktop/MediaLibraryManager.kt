package com.limi.tvdesktop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MediaLibraryManager {

    private val providerCache = mutableMapOf<String, MediaSourceProvider>()
    private val scope = CoroutineScope(Dispatchers.Main)

    val resumeWatching = mutableStateListOf<MediaItemInfo>()
    val latestItems = mutableStateListOf<MediaItemInfo>()
    val categories = mutableStateListOf<MediaCategoryInfo>()
    val currentBannerItem = mutableStateOf<MediaItemInfo?>(null)
    val isRefreshing = mutableStateOf(false)

    val hasRealAccounts: Boolean
        get() = AccountManager.accounts.any { it.enabled }

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
                categories.clear()
                currentBannerItem.value = null
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
        val allCategories = mutableListOf<MediaCategoryInfo>()

        for (acc in targetAccounts) {
            val provider = getProvider(acc)
            val resume = runCatching { provider.getResumeWatching() }.getOrDefault(emptyList())
            val latest = runCatching { provider.getLatest() }.getOrDefault(emptyList())
            val cats = runCatching { provider.getCategories() }.getOrDefault(emptyList())

            allResume.addAll(resume)
            allLatest.addAll(latest)
            allCategories.addAll(cats)
        }

        withContext(Dispatchers.Main) {
            resumeWatching.clear()
            resumeWatching.addAll(allResume.distinctBy { "${it.accountId}_${it.id}" })

            latestItems.clear()
            latestItems.addAll(allLatest.distinctBy { "${it.accountId}_${it.id}" })

            categories.clear()
            categories.addAll(allCategories)

            currentBannerItem.value = resumeWatching.firstOrNull() ?: latestItems.firstOrNull()
        }
    }

    fun reportPlayback(item: MediaItemInfo, positionMs: Long, isPlaying: Boolean) {
        scope.launch(Dispatchers.IO) {
            val provider = getProviderByAccountId(item.accountId)
            provider?.reportPlaybackProgress(item.id, positionMs, isPlaying)
        }
    }
}
