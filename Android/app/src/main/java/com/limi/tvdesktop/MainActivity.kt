package com.limi.tvdesktop

import android.os.Build
import android.os.Bundle
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.core.*
import androidx.compose.animation.Crossfade
import android.icu.util.ChineseCalendar
import android.icu.util.Calendar

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*
import com.limi.tvdesktop.player.TvPlaybackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import com.limi.tvdesktop.plugins.PluginCrashGuard
import com.limi.tvdesktop.plugins.PluginManager
import com.limi.tvdesktop.plugins.PluginKind
import com.limi.tvdesktop.plugins.runtime.PluginDataSourceBrowser
import com.limi.tvdesktop.plugins.runtime.PluginSubtitleResolver
import com.limi.tvdesktop.plugins.runtime.PluginConsentRequest
import com.limi.tvdesktop.plugins.runtime.PluginSurfaceHost
import com.limi.tvdesktop.plugins.runtime.PluginSurfaceRegistry
import com.limi.tvdesktop.plugins.runtime.PluginSlotHost
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var pluginSafeModeWindowUntil = 0L
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pluginCrashGuard = PluginCrashGuard(this)
        pluginCrashGuard.beginStartup()
        pluginSafeModeWindowUntil = android.os.SystemClock.uptimeMillis() + 15_000L
        RenderPerformance.init(this)
        AccountManager.init(this)
        PosterCacheManager.init(this)
        MediaLibraryManager.init(this)
        applyOrientationPreference()
        if (AccountManager.syncOnLaunch.value) MediaLibraryManager.refresh()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { AppEntranceHost { TvDesktop() } } }
        window.decorView.postDelayed({ pluginCrashGuard.markStable() }, 8_000)
    }

    override fun onStart() {
        super.onStart()
        applyOrientationPreference()
        DeveloperDiagnostics.attach(this)
    }

    private fun applyOrientationPreference() {
        requestedOrientation = if (DesktopPreferences.ForceLandscape.isEnabled(this)) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onStop() {
        DeveloperDiagnostics.detach()
        super.onStop()
    }

    // Activity key dispatch is required for TV remotes even when Compose has no focused node.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK &&
            event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount > 0 &&
            android.os.SystemClock.uptimeMillis() <= pluginSafeModeWindowUntil
        ) {
            PluginManager.get(this).installed()
                .filter { it.trust != com.limi.tvdesktop.plugins.PluginTrust.OFFICIAL && it.enabled }
                .forEach { PluginManager.get(this).setEnabled(it.id, false) }
            android.widget.Toast.makeText(this, "已进入安全模式：第三方插件已停用", android.widget.Toast.LENGTH_LONG).show()
            recreate()
            return true
        }
        DeveloperDiagnostics.recordKey(event)
        if (ScreenSaverState.isActive) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                ScreenSaverState.dismiss()
            }
            return true
        }
        ScreenSaverState.notifyInteraction()
        // 常见蓝牙/USB 手柄不会把 A/B 映射为电视确认/返回。统一归一化后，
        // Compose 的焦点系统、播放器桥接和系统返回逻辑都能复用同一套行为。
        fun remapKey(keyCode: Int) = android.view.KeyEvent(
            event.downTime, event.eventTime, event.action, keyCode, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source
        )
        val normalizedEvent = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> remapKey(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
            android.view.KeyEvent.KEYCODE_BUTTON_B -> remapKey(android.view.KeyEvent.KEYCODE_BACK)
            else -> event
        }
        // 播放器打开时优先把按键交给播放器（保证收起控件后任意键都能唤回）
        PlayerKeyBridge.onKey?.let { handler -> if (handler(normalizedEvent)) return true }
        return super.dispatchKeyEvent(normalizedEvent)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        ScreenSaverState.notifyInteraction()
    }
}

object ScreenSaverState {
    var isActive by mutableStateOf(false)
    var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())

    fun notifyInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }

    fun dismiss() {
        isActive = false
        lastInteractionTime = System.currentTimeMillis()
    }
}

internal val White = Color(0xFFF5F5F5)
internal val Muted = Color(0xFFBDBDBD)
internal val Glass = ContinuousCornerShape()

// Shared spacing and typography in the reference viewport.
internal object LibraryDesign {
    val pageInset = 64.dp
    val cardGap = 24.dp
    val titleGap = 38.dp
    val posterWidth = (1672.dp - pageInset * 2 - cardGap * 5) / 6
    val heading = 28.sp
    val title = 24.sp
    val metadata = 20.sp
}

@Composable
@OptIn(ExperimentalHazeApi::class)
fun TvDesktop() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desktop", Context.MODE_PRIVATE) }
    var animeko by remember { mutableStateOf(prefs.getBoolean("animeko", false)) }
    // 旧版本可能存过已移除的“应用”页，兜底回媒体库，避免停在无标签的空白页。
    var page by rememberSaveable { mutableStateOf(DesktopPreferences.LastTab.get(context).let { if (it == "应用") "媒体库" else it }) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(false) }
    var forceNativeSettings by rememberSaveable { mutableStateOf(false) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<DemoMedia?>(null) }
    var posterWallCategory by remember { mutableStateOf<MediaCategoryInfo?>(null) }
    var playingMedia by remember { mutableStateOf<MediaItemInfo?>(null) }
    var activeDataSourceId by remember { mutableStateOf<String?>(null) }
    val dataSourcePlugins = remember(settingsPage, page) {
        PluginManager.get(context).installed().filter { it.enabled && it.kind == PluginKind.DATA_SOURCE && it.entry != null }
    }
    var playingInfo by remember { mutableStateOf<TvPlaybackInfo?>(null) }
    var subtitleConsent by remember { mutableStateOf<Pair<PluginConsentRequest, CompletableDeferred<Boolean>>?>(null) }
    var mpvLaunchedMediaId by remember { mutableStateOf<String?>(null) }
    val mpvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val returnedPosition = result.data?.getIntExtra("position", -1)?.toLong() ?: -1L
        val returnedDuration = result.data?.getIntExtra("duration", -1)?.toLong() ?: -1L
        if (returnedPosition >= 0L) {
            playingMedia?.let { MediaLibraryManager.reportPlayback(it, returnedPosition, returnedDuration, false) }
            playingInfo = playingInfo?.copy(
                startPositionMs = returnedPosition,
                totalDurationMs = returnedDuration.takeIf { it > 0L } ?: playingInfo?.totalDurationMs ?: 0L
            )
        }
        playingMedia = null
        playingInfo = null
        mpvLaunchedMediaId = null
    }
    var bannerIndex by rememberSaveable { mutableIntStateOf(0) }

    // Build the real player package (stream URL, episode playlist, codecs) before opening the
    // player, so ExoPlayer is created with the correct media item instead of demo data.
    LaunchedEffect(playingMedia) {
        val m = playingMedia
        playingInfo = null
        if (m != null) {
            val info = withContext(Dispatchers.IO) {
                runCatching { MediaLibraryManager.buildPlaybackInfo(m, m.title, m.playbackPositionMs) }.getOrNull()
            }
            if (info != null) {
                val pluginSubtitles = try {
                    PluginSubtitleResolver.resolve(context, m) { request ->
                        val answer = CompletableDeferred<Boolean>()
                        subtitleConsent = request to answer
                        try { answer.await() } finally { subtitleConsent = null }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                playingInfo = info.copy(externalSubtitleUrls = (info.externalSubtitleUrls + pluginSubtitles).distinct())
            }
        } else {
            mpvLaunchedMediaId = null
        }
    }

    val activity = context as? android.app.Activity
    LaunchedEffect(Unit) {
        if (activity?.intent?.getBooleanExtra("open_player", false) == true) {
            val stream = activity.intent.getStringExtra("stream_url") ?: com.limi.tvdesktop.player.TvPlaybackInfo.DEFAULT_TEST_VIDEO
            val mediaTitle = activity.intent.getStringExtra("media_title") ?: "海岸线之外"
            playingMedia = MediaItemInfo(
                id = "demo_coastline",
                accountId = "",
                serverType = ServerType.EMBY,
                title = mediaTitle,
                streamUrl = stream,
                mediaType = "Episode",
                seasonNumber = 1,
                episodeNumber = 3,
                year = "2024",
                genre = "科幻"
            )
        }
    }

    val screensaverTimeout = remember(DesktopPreferences.version) {
        DesktopPreferences.ScreenSaverTimeout.current(context)
    }

    LaunchedEffect(screensaverTimeout) {
        if (screensaverTimeout.minutes > 0) {
            val timeoutMs = screensaverTimeout.minutes * 60 * 1000L
            while (true) {
                delay(3000)
                if (!ScreenSaverState.isActive && (System.currentTimeMillis() - ScreenSaverState.lastInteractionTime >= timeoutMs)) {
                    ScreenSaverState.isActive = true
                }
            }
        } else {
            ScreenSaverState.isActive = false
        }
    }

    val realResumeWatching = MediaLibraryManager.resumeWatching
    val realLatestItems = MediaLibraryManager.latestItems
    val realFavorites = MediaLibraryManager.favorites
    val realCategories = MediaLibraryManager.categories
    val hasAccounts = MediaLibraryManager.hasRealAccounts
    val showDemo = AccountManager.showDemoWhenEmpty.value && !hasAccounts
    val isRecommendationMode = hasAccounts && realResumeWatching.isEmpty()

    val watchingList = remember(hasAccounts, realResumeWatching.size, realLatestItems.size, showDemo) {
        if (realResumeWatching.isNotEmpty()) {
            realResumeWatching.map { it.toDemoMedia() }
        } else if (hasAccounts && realLatestItems.isNotEmpty()) {
            // This is intentionally presented as recommendations, never as watch history.
            realLatestItems.take(6).map { it.toDemoMedia() }
        } else if (showDemo) {
            DemoLibrary.watching
        } else {
            emptyList()
        }
    }
    val recentList = remember(hasAccounts, realLatestItems.size, showDemo) {
        if (realLatestItems.isNotEmpty()) {
            realLatestItems.map { it.toDemoMedia() }
        } else if (showDemo) {
            DemoLibrary.recent
        } else {
            emptyList()
        }
    }
    val categoryList = remember(hasAccounts, realCategories.size, showDemo) {
        if (realCategories.isNotEmpty()) {
            realCategories.map { it.toDemoMedia() }
        } else if (showDemo) {
            DemoLibrary.categories
        } else {
            emptyList()
        }
    }
    var localFavVersion by remember { mutableIntStateOf(0) }
    val favoriteList = remember(hasAccounts, realFavorites.size, realResumeWatching.size, realLatestItems.size, showDemo, localFavVersion) {
        val prefs = context.getSharedPreferences("favorites", 0)
        val pool = (realFavorites + realResumeWatching + realLatestItems).map { it.toDemoMedia() } +
            if (showDemo) (DemoLibrary.favorites + DemoLibrary.watching + DemoLibrary.recent) else emptyList()
        val localFavs = pool.filter { prefs.getBoolean(it.title, false) }
        val combined = (realFavorites.map { it.toDemoMedia() } + localFavs).distinctBy { it.title }
        if (combined.isNotEmpty()) {
            combined
        } else if (showDemo && !hasAccounts) {
            DemoLibrary.favorites
        } else {
            emptyList()
        }
    }
    val safeBannerIndex = bannerIndex.coerceIn(0, (watchingList.size - 1).coerceAtLeast(0))
    val banner = watchingList.getOrNull(safeBannerIndex) ?: DemoLibrary.watching.first()
    // Warm the media-library wallpaper before the user switches tabs, so the follow-content
    // backdrop is already decoded instead of flashing the demo fallback for a frame.
    LaunchedEffect(banner.realItem?.backdropUrl) {
        val url = banner.realItem?.backdropUrl
        if (!url.isNullOrBlank()) withContext(Dispatchers.Default) { PosterCacheManager.loadPoster(url) }
    }

    var detailClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(selected) {
        if (selected == null) {
            localFavVersion++
        }
        if (selected?.let { isDetailMedia(it) } != true) DetailOrigin.retainedFocus = null
    }
    var search by rememberSaveable { mutableStateOf(false) }
    var keyboardControl by remember { mutableStateOf(false) }
    val artwork = remember { DemoArtwork(context) }
    LaunchedEffect(artwork) {
        withContext(Dispatchers.Default) {
            (DemoLibrary.watching + DemoLibrary.categories + DemoLibrary.favorites + DemoLibrary.recent + DemoLibrary.music + DemoLibrary.memories).forEach { artwork.image(it) }
        }
    }
    val haze = remember { HazeState() }
    val wallpaperHaze = remember { HazeState() }
    val glassPrefVersion = DesktopPreferences.version
    val appListBlur = remember(glassPrefVersion) { DesktopPreferences.GlassTuning.appListBlur(context) }
    val appListOpacity = remember(glassPrefVersion) { DesktopPreferences.GlassTuning.appListOpacity(context) }
    val wallpaperMode = remember(wallpaperVersion) { WallpaperMode.current(context) }
    val fixedWallpaper = remember(wallpaperVersion, wallpaperMode) {
        when (wallpaperMode) {
            WallpaperMode.BING -> BingWallpaper.currentBitmap(context)
            WallpaperMode.LOCAL -> LocalWallpapers.bitmap(context)
            else -> null
        }
    }
    var legacyBlur by remember { mutableStateOf<ImageBitmap?>(null) }
    val cachedHomeBlur = page == "首页" && wallpaperMode != WallpaperMode.VIDEO
    LaunchedEffect(artwork, banner, page, wallpaperMode, fixedWallpaper, RenderPerformance.staticBlur, cachedHomeBlur) {
        legacyBlur = if (RenderPerformance.staticBlur || cachedHomeBlur) withContext(Dispatchers.Default) {
            when {
                page == "媒体库" -> {
                    val backdropUrl = banner.realItem?.backdropUrl.orEmpty()
                    val remote = if (backdropUrl.isNotBlank()) PosterCacheManager.loadPoster(backdropUrl) else null
                    if (remote != null) artwork.blurredBitmap(remote.asAndroidBitmap(), "media:$backdropUrl")
                    else if (banner.realItem != null) artwork.blurredBitmap(
                        android.graphics.Bitmap.createBitmap(16, 9, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF141414.toInt()) },
                        "media:neutral"
                    ) else artwork.blurredWallpaper(banner)
                }
                fixedWallpaper != null -> artwork.blurredBitmap(fixedWallpaper.asAndroidBitmap(), "wallpaper:${wallpaperMode.name}:$wallpaperVersion")
                else -> artwork.blurredWallpaper(null)
            }
        } else null
    }
    var homeExpandProgress by remember { mutableFloatStateOf(0f) }
    var homeReturnToTop by remember { mutableStateOf<(() -> Unit)?>(null) }
    val list = rememberLazyListState()
    val watching = rememberLazyListState()
    val categories = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val scope = rememberCoroutineScope()
    val watchingFocus = remember(watchingList.size) { List(watchingList.size.coerceAtLeast(1)) { FocusRequester() } }
    val categoryFocus = remember(categoryList.size) { List(categoryList.size.coerceAtLeast(1)) { FocusRequester() } }
    val first = watchingFocus.first()
    val playback = remember { FocusRequester() }
    val category = remember { FocusRequester() }
    val nav = remember { FocusRequester() }
    val homeNav = remember { FocusRequester() }
    val homeDock = remember { FocusRequester() }
    var verticalNavigation by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun moveToLibraryRow(index: Int, target: FocusRequester, headerInsetPx: Int) {
        verticalNavigation?.cancel()
        if (target.requestFocus()) return
        verticalNavigation = scope.launch {
            // Hand focus over the moment the row scrolls into view, so the focus
            // animation overlaps the reveal scroll (mirrors the horizontal shelf).
            launch {
                snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == index } }
                    .first { it }
                withFrameNanos { }
                target.requestFocus()
            }
            list.animateScrollToItemSpring(index, -headerInsetPx)
        }
    }
    fun returnToTop(target: FocusRequester = first) {
        val focused = target.requestFocus()
        if (focused) {
            if (target === first) scope.launch { watching.scrollToItem(0) }
            return
        }
        scope.launch {
            launch {
                snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == 0 } }
                    .first { it }
                withFrameNanos { }
                target.requestFocus()
            }
            list.animateScrollToItemSpring(0)
            if (target === first) watching.scrollToItem(0)
        }
    }
    fun closeOrReturn() {
        when {
            settings -> settings = false
            selected?.let { isDetailMedia(it) } == true -> detailClose?.invoke()
            selected != null -> selected = null
            posterWallCategory != null -> posterWallCategory = null
            search -> search = false
            page == "首页" && homeExpandProgress > 0.05f -> homeReturnToTop?.invoke()
            page != "媒体库" -> page = "媒体库"
            scrolled -> returnToTop()
            else -> nav.requestFocus()
        }
    }
    LaunchedEffect(page) {
        DesktopPreferences.LastTab.set(context, page)
        if (page == "媒体库") {
            list.scrollToItem(0)
        } else if (page == "首页") {
            homeReturnToTop?.invoke()
        }
    }
    LaunchedEffect(Unit) {
        if (page == "首页" && !settings && !search) homeDock.requestFocus()
        else if (page == "媒体库" && !settings && !search && selected == null && posterWallCategory == null) first.requestFocus()
    }
    BackHandler {
        closeOrReturn()
    }
    // Design coordinates remain invariant even if system density/font scale changes.
    // Scale by width; taller screens expose subsequent sections instead of letterboxing.
    PageEntranceScope("primary:$page", replayOnOpen = true) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        val physical = LocalDensity.current
        val scale = with(physical) { maxWidth.toPx() } / 1672f
        val canvasHeight = (with(physical) { maxHeight.toPx() } / scale).dp
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalCanvasHeight provides canvasHeight, LocalKeyboardControl provides keyboardControl) {
            // 我的媒体 现在是单行横向货架：行高取最高卡片，行中心 = 96 顶距 + 标题 36 + titleGap 38 + 货架上下 24 + 半高
            val categoryRowHeight = remember(categoryList, artwork) {
                categoryList.map { categoryCardSize(categoryCardRatio(it, artwork)).second.value }.maxOrNull() ?: 198f
            }
            val categoryRowCenterDp = 194f + categoryRowHeight / 2f
            val dynamicFocusRows = remember(categoryRowCenterDp, favoriteList.size, recentList.size, hasAccounts, showDemo) {
                val rows = mutableListOf<FocusRowSpec>()
                rows.add(FocusRowSpec("header", 0, false))
                rows.add(FocusRowSpec("play", 0, false))
                rows.add(FocusRowSpec("watching", 0, false))

                var lazyIdx = 1

                if (categoryList.isNotEmpty()) {
                    rows.add(FocusRowSpec("categories", lazyIdx, centerOffsetDp = categoryRowCenterDp))
                    lazyIdx++
                }

                // 我的收藏模块始终渲染；只有真正存在可聚焦内容时才登记它的焦点行。
                val favIdx = lazyIdx
                if (favoriteList.isNotEmpty()) {
                    rows.add(FocusRowSpec("我的收藏:actions", favIdx))
                    rows.add(FocusRowSpec("favoriteFilters", favIdx))
                    rows.add(FocusRowSpec("favorites", favIdx))
                }
                lazyIdx++

                if (recentList.isNotEmpty()) {
                    val recIdx = lazyIdx
                    rows.add(FocusRowSpec("最近添加:actions", recIdx))
                    rows.add(FocusRowSpec("最近添加:cards", recIdx))
                    lazyIdx++
                }

                if (!hasAccounts && showDemo) {
                    val musicIdx = lazyIdx
                    rows.add(FocusRowSpec("最近听过:actions", musicIdx))
                    rows.add(FocusRowSpec("最近听过:cards", musicIdx))
                    lazyIdx++

                    val memIdx = lazyIdx
                    rows.add(FocusRowSpec("最近的回忆:actions", memIdx))
                    rows.add(FocusRowSpec("memories", memIdx))
                    lazyIdx++
                }

                rows
            }
            FocusRowsHost(list, dynamicFocusRows, page == "媒体库" && selected == null && posterWallCategory == null && !settings && !search, onDirection = { keyboardControl = true }) {
            val progress = {
                if (page != "媒体库") 0f
                else if (list.firstVisibleItemIndex > 0) 1f
                else (list.firstVisibleItemScrollOffset / (941f * scale)).coerceIn(0f, 1f)
            }
            Box(Modifier.align(Alignment.TopCenter).requiredSize(1672.dp, canvasHeight).clipToBoundsCompat().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press ||
                            (event.type == PointerEventType.Move && event.changes.any { it.position != it.previousPosition })) keyboardControl = false
                    }
                }
            }.onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) keyboardControl = true
                if (it.key == Key.Escape || it.key == Key.Back) {
                    // When the control center is open, don't consume Back here: let its own
                    // BackHandler run the animated close, so Back matches tapping the 关闭 tile.
                    // 播放器打开时不拦截返回：交给播放器自己的 BackHandler（关抽屉→收控件→退出）
                    val handledHere = !settings && !settingsPage && playingMedia == null
                    if (it.type == KeyEventType.KeyDown && handledHere) closeOrReturn()
                    handledHere
                } else false
            }) {
                Box(Modifier.fillMaxSize().then(if (RenderPerformance.blur31) Modifier.hazeSource(haze) else Modifier)) {
                    val videoUri = remember(wallpaperVersion, wallpaperMode) {
                        if (wallpaperMode == WallpaperMode.VIDEO) VideoWallpaperPrefs.current(context) else null
                    }
                    val currentBlurProgress = { if (page == "媒体库") progress() else if (page == "首页") homeExpandProgress else 0f }

                    if (page == "媒体库") {
                        FollowContentWallpaper(banner, artwork, wallpaperHaze)
                    } else {
                        when {
                            wallpaperMode == WallpaperMode.VIDEO && videoUri != null && !RenderPerformance.lowPerformance ->
                                VideoWallpaperSurface(videoUri, Modifier.fillMaxSize())
                            wallpaperMode != WallpaperMode.FOLLOW_CONTENT && fixedWallpaper != null ->
                                Image(fixedWallpaper, null, Modifier.fillMaxSize()
                                    .then(if (RenderPerformance.blur31 && !cachedHomeBlur) Modifier.hazeSource(wallpaperHaze) else Modifier),
                                    contentScale = ContentScale.Crop)
                            wallpaperMode == WallpaperMode.FOLLOW_CONTENT ->
                                FollowContentWallpaper(banner, artwork, wallpaperHaze, enableHaze = !cachedHomeBlur)
                            else ->
                                Image(artwork.background.asImageBitmap(), null, Modifier.fillMaxSize()
                                    .then(if (RenderPerformance.blur31 && !cachedHomeBlur) Modifier.hazeSource(wallpaperHaze) else Modifier),
                                    contentScale = ContentScale.Crop)
                        }
                    }
                    if (RenderPerformance.blur31 && !cachedHomeBlur) {
                        Box(Modifier.fillMaxSize().hazeEffect(wallpaperHaze) {
                            blurEnabled = currentBlurProgress() > 0f
                            blurRadius = ((if (page == "首页") appListBlur else 50f) * currentBlurProgress()).coerceAtLeast(.01f).dp
                            backgroundColor = Color.Transparent
                            tints = emptyList()
                            noiseFactor = 0f
                            // Blur radius and motion remain unchanged; a smaller intermediate
                            // surface removes millions of texture samples per animation frame.
                            inputScale = HazeInputScale.Fixed(.35f)
                        })
                    } else if ((RenderPerformance.staticBlur || cachedHomeBlur) && legacyBlur != null) {
                        Image(legacyBlur!!, null, Modifier.fillMaxSize().graphicsLayer {
                            alpha = currentBlurProgress() * (if (page == "首页") (appListBlur / 50f).coerceIn(0f, 1f) else 1f)
                        }, contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x08202020), Color(0x0A101010), Color(0xA6101010)))))
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = (if (page == "首页") appListOpacity else .72f) * currentBlurProgress())) }
                    if (page == "媒体库") {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            item(key = "first-screen") {
                                Box(Modifier.fillMaxWidth().height(941.dp)) {
                                    Text(if (banner.title == "海岸线之外") "更大的世界\n在海岸线之外" else "", Modifier.offset(1440.dp, 181.dp).graphicsLayer { rotationZ = -9f },
                                        color = Color(0xFF808080), fontSize = 26.sp, lineHeight = 38.sp,
                                        fontFamily = FontFamily.Default, letterSpacing = 3.sp)
                                    Column(Modifier.offset(LibraryDesign.pageInset, 188.dp)) {
                                        if (AccountManager.displayMode.value == LibraryDisplayMode.ISOLATED && AccountManager.accounts.isNotEmpty()) {
                                            val curAcc = AccountManager.accounts.find { it.id == AccountManager.selectedAccountId.value } ?: AccountManager.accounts.first()
                                            Row(
                                                modifier = Modifier
                                                    .padding(bottom = 14.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x2E3875F6))
                                                    .clickable {
                                                        val idx = AccountManager.accounts.indexOfFirst { it.id == curAcc.id }
                                                        val next = AccountManager.accounts[(idx + 1) % AccountManager.accounts.size]
                                                        AccountManager.selectAccount(next.id)
                                                        MediaLibraryManager.refresh()
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("当前媒体源: ${curAcc.name} (${curAcc.type.displayName}) ⇄", color = Color(0xFF93C5FD), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Crossfade(banner, Modifier.staggeredEntrance(0).height(120.dp), tween(if (RenderPerformance.reducedEffects) 90 else 220), label = "banner-title") { media ->
                                            val logoUrl = media.realItem?.logoUrl.orEmpty()
                                            val logoBitmap = if (logoUrl.isNotBlank()) rememberPosterImage(logoUrl) else null
                                            if (logoBitmap != null) {
                                                Image(
                                                    bitmap = logoBitmap,
                                                    contentDescription = media.title,
                                                    modifier = Modifier.height(110.dp).wrapContentWidth(Alignment.Start),
                                                    contentScale = ContentScale.Fit,
                                                    alignment = Alignment.CenterStart
                                                )
                                            } else {
                                                val isDemoCalligraphy = media.title == "海岸线之外"
                                                Text(
                                                    text = media.title,
                                                    color = White,
                                                    fontSize = if (isDemoCalligraphy) 90.sp else 64.sp,
                                                    lineHeight = if (isDemoCalligraphy) 120.sp else 80.sp,
                                                    fontFamily = FontFamily.Default,
                                                    fontWeight = if (isDemoCalligraphy) FontWeight.Normal else FontWeight.Bold,
                                                    letterSpacing = if (isDemoCalligraphy) 3.sp else 1.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(29.dp))
                                        Crossfade(banner, Modifier.staggeredEntrance(1).height(36.dp), tween(if (RenderPerformance.reducedEffects) 90 else 220), label = "banner-intro") { media ->
                                            val introText = when {
                                                media.realItem?.tagline?.isNotBlank() == true -> media.realItem.tagline
                                                media.realItem?.overview?.isNotBlank() == true -> {
                                                    val ov = media.realItem.overview.trim()
                                                    val first = ov.split('。', '！', '!', '.', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: ov
                                                    if (first.length > 35) first.take(35) + "..." else first
                                                }
                                                else -> DemoLibrary.bannerIntro(media)
                                            }
                                            Text(introText, color = Muted, fontSize = 23.sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(36.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                            PlaybackButton(if (isRecommendationMode) "▶  播放" else "▶  继续播放", Modifier.staggeredEntrance(2).size(236.dp, 70.dp).rowFocusTarget(row = "play").focusRequester(playback)
                                                .focusProperties { up = nav; down = first }.onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                        returnToTop(first); true
                                                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                                        nav.requestFocus(); true
                                                    } else false
                                                }, wallpaperHaze, legacyBlur) {
                                                    val target = banner.realItem
                                                    if (target != null) {
                                                        scope.launch {
                                                            val playable = withContext(Dispatchers.IO) { MediaLibraryManager.playableItem(target) }
                                                            playingMedia = playable
                                                        }
                                                    } else {
                                                        selected = banner
                                                    }
                                                }
                                            if (dataSourcePlugins.isNotEmpty()) {
                                                GlassButton("插件媒体源", Modifier.width(200.dp).height(70.dp)) {
                                                    activeDataSourceId = dataSourcePlugins.first().id
                                                }
                                            }
                                            if (!isRecommendationMode) {
                                                Column(Modifier.staggeredEntrance(3).width(340.dp)) {
                                                    Progress(banner.progress, Modifier.fillMaxWidth().height(8.dp))
                                                    Spacer(Modifier.height(12.dp))
                                                    Text(formatMediaProgressText(banner), color = Muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                    Column(Modifier.offset(y = 618.dp).fillMaxWidth()) {
                                        Text(if (isRecommendationMode) "推荐" else "继续观看", Modifier.staggeredEntrance(4).padding(start = LibraryDesign.pageInset), color = White, fontSize = LibraryDesign.heading, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(14.dp))
                                        val watchingShelf = remember(watching, scope, scale) {
                                            ShelfScrollController(watching, scope, itemWidthPx = { 352f * scale }, gapPx = { LibraryDesign.cardGap.value * scale })
                                        }
                                        LazyRow(state = watching, modifier = Modifier.pointerInput(watching) {
                                            // Wheel over this shelf moves horizontally; elsewhere it scrolls the page.
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    if (event.type == PointerEventType.Scroll) {
                                                        val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                                        val amount = if (delta.x != 0f) delta.x else delta.y
                                                        if (amount != 0f) {
                                                            scope.launch { watching.animateScrollBy(amount * 72.dp.toPx()) }
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            }
                                        }, contentPadding = PaddingValues(horizontal = LibraryDesign.pageInset, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
                                            itemsIndexed(watchingList, key = { i, media -> "${media.title}_$i" }) { i, media ->
                                                WatchingCard(media, artwork, Modifier.staggeredEntrance(5 + i).rowFocusTarget(row = "watching").width(352.dp).aspectRatio(16f / 9f)
                                                    .onFocusChanged { if (it.isFocused) bannerIndex = i }
                                                    .focusRequester(watchingFocus.getOrElse(i) { watchingFocus.first() })
                                                    .focusProperties { up = playback; down = category }
                                                    .onPreviewKeyEvent { event ->
                                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                            moveToLibraryRow(1, category, (128f * scale).toInt()); true
                                                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                                            playback.requestFocus(); true
                                                        } else if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft)) {
                                                            val target = (i + if (event.key == Key.DirectionRight) 1 else -1).coerceIn(0, (watchingList.size - 1).coerceAtLeast(0))
                                                            // Focus change and shelf movement share one frame, one duration and one curve.
                                                            if (target != i && target in watchingFocus.indices) watchingShelf.select(target) { watchingFocus[target].requestFocus() }
                                                            true
                                                        } else false
                                                    }) {
                                                        selected = media
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                            if (categoryList.isNotEmpty()) {
                                item(key = "categories") {
                                    Column(Modifier.fillMaxWidth().padding(top = 96.dp, bottom = 32.dp)) {
                                        Box(Modifier.padding(horizontal = LibraryDesign.pageInset)) {
                                            SectionTitle("我的媒体", entranceIndex = 10)
                                        }
                                        val categoryShelf = remember(categories, scope, scale) {
                                            ShelfScrollController(categories, scope, itemWidthPx = { 352f * scale }, gapPx = { LibraryDesign.cardGap.value * scale })
                                        }
                                        LazyRow(
                                            state = categories,
                                            modifier = Modifier.pointerInput(categories) {
                                                // 鼠标滚轮在这条货架上是横向滚动。
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                                        if (event.type == PointerEventType.Scroll) {
                                                            val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                                            val amount = if (delta.x != 0f) delta.x else delta.y
                                                            if (amount != 0f) {
                                                                scope.launch { categories.animateScrollBy(amount * 72.dp.toPx()) }
                                                                event.changes.forEach { it.consume() }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = LibraryDesign.pageInset, vertical = 24.dp),
                                            horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)
                                        ) {
                                            itemsIndexed(categoryList, key = { i, media -> "${media.title}_$i" }) { i, media ->
                                                CategoryCard(
                                                    media = media,
                                                    artwork = artwork,
                                                    modifier = Modifier
                                                        .staggeredEntrance(11 + i)
                                                        .rowFocusTarget(row = "categories")
                                                        .focusRequester(categoryFocus.getOrElse(i) { categoryFocus.first() })
                                                        .onPreviewKeyEvent { event ->
                                                            if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft)) {
                                                                val target = (i + if (event.key == Key.DirectionRight) 1 else -1).coerceIn(0, (categoryList.size - 1).coerceAtLeast(0))
                                                                if (target != i && target in categoryFocus.indices) categoryShelf.select(target) { categoryFocus[target].requestFocus() }
                                                                true
                                                            } else false
                                                        }
                                                ) {
                                                    val catInfo = realCategories.find { it.id == media.realItem?.id }
                                                        ?: MediaCategoryInfo(
                                                            id = media.realItem?.id ?: media.title,
                                                            title = media.title,
                                                            countText = media.detail,
                                                            thumbUrl = media.realItem?.posterUrl.orEmpty(),
                                                            collectionType = media.realItem?.collectionType ?: "movies",
                                                            accountId = media.realItem?.accountId.orEmpty()
                                                        )
                                                    posterWallCategory = catInfo
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "favorites") { Favorites(artwork, favoriteList) { selected = it } }
                            if (recentList.isNotEmpty()) {
                                item(key = "recent") { PosterSection("最近添加", recentList, artwork, entranceIndex = 25) { selected = it } }
                            }
                            if (!hasAccounts && showDemo) {
                                item(key = "music") { PosterSection("最近听过", DemoLibrary.music, artwork, square = true, entranceIndex = 32) { selected = it } }
                                item(key = "memories") {
                                    SectionSurface {
                                        SectionTitle("最近的回忆", entranceIndex = 39, onAll = { selected = DemoLibrary.memories[0] })
                                        Row(horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
                                            DemoLibrary.memories.forEachIndexed { i, media -> PosterCard(media, artwork, (1672.dp - LibraryDesign.pageInset * 2 - LibraryDesign.cardGap * 3) / 4, 211.dp, focusRow = "memories", entranceIndex = 40 + i) { selected = media } }
                                        }
                                        Spacer(Modifier.height(76.dp))
                                    }
                                }
                            }
                        }
                    } else if (page == "首页") {
                        val homeOwner = PluginSurfaceRegistry.owner("home", PluginManager.get(context).installed())
                        if (homeOwner != null) {
                            PluginSurfaceHost(homeOwner, "home")
                        } else {
                            Box(Modifier.fillMaxSize()) {
                                HomePage(wallpaperHaze, artwork, legacyBlur, homeDock, homeNav,
                                    onExpandProgress = { homeExpandProgress = it },
                                    registerReturnToTop = { homeReturnToTop = it })
                                PluginSlotHost("home.quickActions", Modifier.align(Alignment.TopEnd).padding(top = 132.dp, end = 52.dp).width(420.dp).heightIn(max = 300.dp))
                            }
                        }
                    } else {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(page, Modifier.staggeredEntrance(0), color = White, fontSize = 54.sp, fontWeight = FontWeight.Light)
                            Spacer(Modifier.height(22.dp))
                            Text(if (page == "Animeko") "Animeko 内容将在这里呈现" else "内容即将呈现", Modifier.staggeredEntrance(1), color = Muted, fontSize = 24.sp)
                        }
                    }
                }
                // Every first-level page uses this single persistent header instance.
                Box(Modifier.graphicsLayer {
                    if (page == "首页") {
                        alpha = (1f - homeExpandProgress).coerceIn(0f, 1f)
                        translationY = -120.dp.toPx() * homeExpandProgress
                    }
                }) {
                    Header(page, animeko, haze, legacyBlur, nav, homeNav, artwork, backdropReveal = {
                        if (page != "媒体库") 0f
                        else if (list.firstVisibleItemIndex > 0) 1f
                        else (list.firstVisibleItemScrollOffset / ((618f - 112f) * scale)).coerceIn(0f, 1f)
                    }, onPage = { selected = null; page = it; if (it == "首页") homeReturnToTop?.invoke() }, onSettings = { settings = true }, onSearch = { search = true },
                        onContent = { if (page == "媒体库") returnToTop(playback) else if (page == "首页") homeDock.requestFocus() })
                }
                if (posterWallCategory != null) {
                    MediaPosterWallPage(
                        category = posterWallCategory!!,
                        artwork = artwork,
                        onDismiss = { posterWallCategory = null },
                        onSelectMedia = { selected = it }
                    )
                }
                if (selected != null && !isDetailMedia(selected!!)) Overlay(selected!!.title, onClose = { selected = null }) {
                    Text(selected!!.detail, color = Muted, fontSize = 24.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("展示内容，媒体数据与播放将在后续接入。", color = Muted, fontSize = 21.sp)
                }
                if (selected?.let { isDetailMedia(it) } == true) MediaDetailPage(selected!!, artwork,
                    registerClose = { detailClose = it }, onDismiss = { restore ->
                        // Transfer focus while both pages still exist. Otherwise disposing
                        // the detail briefly assigns focus to the first navigation item.
                        restore()
                        selected = null
                    }, onPlayItem = { item ->
                        playingMedia = item
                    })
                if (search) Overlay("搜索媒体", onClose = { search = false }) {
                    var query by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(query, { query = it }, singleLine = true, shape = ContinuousCornerShape(16.dp), placeholder = { Text("输入媒体名称") }, modifier = Modifier.width(620.dp).focusSweepOnFocus(ContinuousCornerShape(16.dp)))
                    Spacer(Modifier.height(20.dp))
                    val results = (DemoLibrary.favorites + DemoLibrary.recent + DemoLibrary.watching).distinctBy { it.title }.filter { query.isNotBlank() && it.title.contains(query, true) }
                    results.take(4).forEach { media ->
                        GlassButton(media.title, Modifier.width(620.dp).height(54.dp)) { search = false; selected = media }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                }
                if (settings) ControlCenter(haze, legacyBlur, onSettings = {
                    settings = false
                    settingsPage = true
                }, onClose = { settings = false })
                if (settingsPage) {
                    val settingsOwner = PluginSurfaceRegistry.owner("settings", PluginManager.get(context).installed())
                    if (settingsOwner != null && !forceNativeSettings) {
                        BackHandler { forceNativeSettings = true }
                        Box(Modifier.fillMaxSize().zIndex(200f).background(Color(0xFA090B10))) {
                            PluginSurfaceHost(settingsOwner, "settings")
                            Text(
                                "原生设置",
                                color = White,
                                fontSize = 17.sp,
                                modifier = Modifier.align(Alignment.TopEnd).padding(28.dp)
                                    .background(Color(0x663A3D45), RoundedCornerShape(12.dp))
                                    .clickable { forceNativeSettings = true }.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        SettingsPage(haze = haze, staticBlur = legacyBlur, onClose = {
                            settingsPage = false
                            forceNativeSettings = false
                        }, onWallpaperChanged = { wallpaperVersion++ })
                    }
                }
                activeDataSourceId?.let { id ->
                    dataSourcePlugins.firstOrNull { it.id == id }?.let { plugin ->
                        PluginDataSourceBrowser(
                            plugin = plugin,
                            sources = dataSourcePlugins,
                            onSelectSource = { activeDataSourceId = it },
                            onClose = { activeDataSourceId = null },
                            onPlay = { itemId, title, url ->
                                activeDataSourceId = null
                                playingMedia = MediaItemInfo(
                                    id = "plugin:${plugin.id}:$itemId", accountId = "", serverType = ServerType.EMBY,
                                    title = title, streamUrl = url, mediaType = "Movie",
                                )
                            },
                        )
                    }
                }
                val media = playingMedia
                val info = playingInfo
                subtitleConsent?.let { (request, answer) ->
                    AlertDialog(
                        onDismissRequest = { if (!answer.isCompleted) answer.complete(false) },
                        title = { Text("字幕插件请求权限") },
                        text = { Text("插件请求“${request.title}”。${if (request.sensitive) "这是敏感权限，请确认信任该插件。" else ""}") },
                        confirmButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(true) }) { Text("允许") } },
                        dismissButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(false) }) { Text("拒绝") } },
                    )
                }
                if (media != null && info != null) {
                    VideoPlayerScreen(
                        title = info.title,
                        streamUrl = info.streamUrl,
                        startPositionMs = info.startPositionMs,
                        playbackInfo = info,
                        onProgressUpdate = { pos, duration ->
                            MediaLibraryManager.reportPlayback(media, pos, duration, false)
                        },
                        onOpenExternal = { positionMs ->
                            val mpvIntent = createMpvIntent(info, positionMs)
                            if (mpvIntent.resolveActivity(context.packageManager) != null) {
                                mpvLauncher.launch(mpvIntent)
                            } else {
                                android.widget.Toast.makeText(context, "尚未安装 mpv-android", android.widget.Toast.LENGTH_LONG).show()
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/mpv-android/mpv-android/releases/latest")
                                    )
                                )
                            }
                        },
                        onClose = {
                            playingMedia = null
                        }
                    )
                    PluginSlotHost(
                        "player.overlay",
                        Modifier.fillMaxSize().zIndex(310f).padding(start = 48.dp, top = 48.dp, end = 48.dp, bottom = 140.dp),
                    )
                } else if (media != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF3875B6))
                }
                SideEffect {
                    DeveloperDiagnostics.scene = when {
                        ScreenSaverState.isActive -> "屏保"
                        playingMedia != null -> "播放器"
                        settingsPage -> "设置"
                        settings -> "控制中心"
                        selected != null -> "详情页"
                        posterWallCategory != null -> "海报墙"
                        search -> "搜索"
                        else -> page
                    }
                }
                if (ScreenSaverState.isActive) {
                    ScreenSaverOverlay(onDismiss = { ScreenSaverState.dismiss() })
                }
                PerformanceOverlay()
            }
        }
    }
}
}
}

@Composable
private fun ScreenSaverOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val date = Date(now)
    val timeText = SimpleDateFormat("HH:mm", Locale.CHINA).format(date)
    val dateText = remember(now / 60_000) {
        runCatching {
            val lunar = ChineseCalendar().apply { timeInMillis = now }
            val months = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
            val day = lunar.get(Calendar.DAY_OF_MONTH)
            val numbers = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
            val lunarDay = when (day) {
                10 -> "初十"; 20 -> "二十"; 30 -> "三十"
                else -> listOf("初", "十", "廿")[((day - 1) / 10).coerceIn(0, 2)] + numbers[((day - 1) % 10).coerceIn(0, 9)]
            }
            val monthIdx = lunar.get(Calendar.MONTH).coerceIn(0, 11)
            SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(now)) + " · 农历" +
                (if (lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 1) "闰" else "") + months[monthIdx] + "月" + lunarDay
        }.getOrElse {
            SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date(now))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "screensaver")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "screensaver-scale"
    )
    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hint-alpha"
    )

    val wallpaperMode = WallpaperMode.current(context)
    val fixedWallpaper = remember(wallpaperMode) {
        when (wallpaperMode) {
            WallpaperMode.BING -> BingWallpaper.currentBitmap(context)
            WallpaperMode.LOCAL -> LocalWallpapers.bitmap(context)
            else -> null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        // Ambient background with gentle slow pan/zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            if (fixedWallpaper != null) {
                Image(
                    bitmap = fixedWallpaper,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617)),
                                radius = 1200f
                            )
                        )
                )
            }
        }

        // Vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
        )

        // Clock and date in the bottom-left corner
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 72.dp, bottom = 64.dp)
        ) {
            Text(
                text = timeText,
                color = Color.White,
                fontSize = 108.sp,
                lineHeight = 114.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-2).sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 4f),
                        blurRadius = 12f
                    )
                )
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = dateText,
                color = Color(0xE6FFFFFF),
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.5.sp,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 2f),
                        blurRadius = 8f
                    )
                )
            )
        }

        // Breathing hint at bottom right
        Text(
            text = "按遥控器任意键唤醒",
            color = Color.White.copy(alpha = hintAlpha),
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 72.dp, bottom = 64.dp),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(0f, 2f),
                    blurRadius = 8f
                )
            )
        )
    }
}

private fun Modifier.clipToBoundsCompat() = clip(ContinuousCornerShape(0.dp))

internal fun formatMediaProgressText(media: DemoMedia): String {
    val real = media.realItem
    if (real != null) {
        val totalMs = real.totalDurationMs
        val posMs = real.playbackPositionMs
        val isEpisode = real.mediaType == "Episode" || real.seasonNumber > 0 || real.episodeNumber > 0

        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) {
                if (remMinutes > 0) "$hours 小时 $remMinutes 分钟" else "$hours 小时"
            } else {
                "${minutes.coerceAtLeast(1)} 分钟"
            }
        }

        val episodePart = if (isEpisode && real.episodeNumber > 0) {
            "第 ${real.episodeNumber} 集 · "
        } else if (real.detail.contains("集")) {
            "${real.detail.substringBefore("/").trim()} · "
        } else ""

        return if (totalMs > 0 && posMs > 0) {
            "$episodePart${formatDuration(posMs)} / ${formatDuration(totalMs)}"
        } else if (totalMs > 0) {
            val calcPosMs = (totalMs * real.progress).toLong()
            "$episodePart${formatDuration(calcPosMs)} / ${formatDuration(totalMs)}"
        } else if (real.progress > 0f) {
            val estTotal = 45 * 60 * 1000L
            val estPos = (estTotal * real.progress).toLong()
            "$episodePart${formatDuration(estPos)} / ${formatDuration(estTotal)}"
        } else {
            episodePart.removeSuffix(" · ").ifBlank { real.detail }
        }
    } else {
        val mins = (media.progress * 45).toInt().coerceAtLeast(1)
        return if (media.detail.contains("集")) {
            "${media.detail.substringBefore("/").trim()} · $mins 分钟 / 45 分钟"
        } else {
            "$mins 分钟 / 45 分钟"
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun FollowContentWallpaper(
    banner: DemoMedia,
    artwork: DemoArtwork,
    wallpaperHaze: HazeState,
    enableHaze: Boolean = true
) {
    Crossfade(banner, Modifier.fillMaxSize()
        .then(if (enableHaze && RenderPerformance.blur33) Modifier.hazeSource(wallpaperHaze) else Modifier),
        animationSpec = tween(if (RenderPerformance.reducedEffects) 100 else 260), label = "banner-background") { media ->
        val realBackdrop = media.realItem?.backdropUrl
        val remoteBmp = if (!realBackdrop.isNullOrBlank()) rememberPosterImage(realBackdrop) else null
        if (remoteBmp != null) {
            Image(remoteBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (media.realItem != null) {
            // Server backdrop not decoded yet: stay on a neutral frame rather than flashing an
            // unrelated bundled demo image.
            Box(Modifier.fillMaxSize().background(Color(0xFF141414)))
        } else {
            Image(if (media.title == "海岸线之外") artwork.background.asImageBitmap() else artwork.image(media),
                null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Header(page: String, animeko: Boolean, haze: HazeState, legacyBlur: ImageBitmap?, nav: FocusRequester, homeNav: FocusRequester, artwork: DemoArtwork, backdropReveal: () -> Float, onPage: (String) -> Unit, onSettings: () -> Unit, onSearch: () -> Unit, onContent: () -> Unit) {
    val context = LocalContext.current
    val tuningVersion = DesktopPreferences.version
    val navBlur = remember(tuningVersion) { DesktopPreferences.GlassTuning.navBlur(context) }
    val navOpacity = remember(tuningVersion) { DesktopPreferences.GlassTuning.navOpacity(context) }
    val canvasHeight = LocalCanvasHeight.current
    TopBarBackdrop(haze, backdropReveal)
    val tabs = listOf("首页", "媒体库", "直播", "游戏") + if (animeko) listOf("Animeko") else emptyList()
    // 每个标签槽位保持 124dp 设计宽度，标签数变化时导航条整体等比伸缩。
    val width = 124.dp * tabs.size
    var hoveredTab by remember { mutableStateOf<String?>(null) }
    var keyboardNavigation by remember { mutableStateOf(false) }
    val capsuleWidth = (width - 10.dp) / tabs.size - 18.dp
    Box(Modifier.offset(x = (1672.dp - width) / 2, y = 26.dp).size(width, 64.dp).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) keyboardNavigation = true
        false
    }) {
        Box(Modifier.fillMaxSize().clip(Glass)
        .then(if (RenderPerformance.blur31) Modifier.hazeEffect(haze) {
            blurRadius = navBlur.dp; noiseFactor = .025f
            backgroundColor = Color(0xFF333333)
            tints = listOf(HazeTint(Color(0x40404040)))
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = .65f)
        } else Modifier)) {
        if (RenderPerformance.staticBlur && legacyBlur != null && navBlur > 0f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (navBlur / 24f).coerceIn(0f, 1f) }) {
                drawCoverWallpaper(legacyBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()), IntOffset(-(((1672f - width.value) / 2) * density).toInt(), -(26 * density).toInt()))
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = navOpacity.coerceIn(0f, .9f))))
        }
        Row(Modifier.fillMaxSize().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { i, tab ->
                var focused by remember { mutableStateOf(false) }
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                val active = page == tab
                val tabFocus = remember { if (i == 0) homeNav else if (i == 1) nav else FocusRequester() }
                LaunchedEffect(hovered) {
                    if (hovered) { hoveredTab = tab; keyboardNavigation = false }
                    else if (hoveredTab == tab) hoveredTab = null
                }
                val raised = if (keyboardNavigation) focused else hoveredTab == tab || (focused && hoveredTab == null)
                val baseReveal by animateFloatAsState(if (active && !raised) 1f else 0f,
                    tween(300), label = "navigation-current-$tab")
                val raisedReveal by animateFloatAsState(if (raised) 1f else 0f,
                    tween(300), label = "navigation-highlight-$tab")
                LaunchedEffect(focused) {
                    if (focused) {
                        // Complete the focus transaction before replacing page content.
                        withFrameNanos { }
                        if (focused && page != tab) onPage(tab)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 9.dp, vertical = 5.dp)
                    .zIndex(if (raised) 2f else if (raisedReveal > .001f) 1f else 0f)
                    .focusRequester(tabFocus).rowFocusTarget(tabFocus, "header", preferredEntry = active)
                    .focusProperties { canFocus = true }.onFocusChanged {
                        focused = it.isFocused
                    }
                    .onPreviewKeyEvent {
                        when (it.key) {
                            Key.DirectionUp -> true
                            Key.DirectionDown -> { if (it.type == KeyEventType.KeyDown) onContent(); true }
                            else -> false
                        }
                    }
                    .pointerInput(tab) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Move && event.changes.any { it.position != it.previousPosition }) {
                                    hoveredTab = tab; keyboardNavigation = false
                                }
                            }
                        }
                    }.hoverable(interaction).clickable(interactionSource = interaction, indication = null) {
                        tabFocus.requestFocus(); onPage(tab)
                    }, contentAlignment = Alignment.Center) {
                    // Current-page capsule: width stays proportional to the tab slot, so the
                    // four-tab bar reads 114.6dp and the Animeko bar scales the same way (115dp).
                    val currentCapsuleWidth = ((width - 10.dp) / tabs.size + 10.dp) * .8715f
                    NavigationCapsule(currentCapsuleWidth, 51.08.dp, baseReveal)
                    NavigationCapsule(capsuleWidth + 30.dp, 74.dp, raisedReveal, raised)
                    Text(tab, color = lerp(Color(0xFFD4D4D4), Color(0xFF161616), maxOf(baseReveal, raisedReveal)),
                        fontSize = if (tab == "Animeko") 19.sp else 22.sp,
                        fontWeight = if (active || raised) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
    var time by remember { mutableStateOf("") }
    var wifi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            wifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            delay(1000)
        }
    }
    Row(Modifier.offset(1350.dp, 34.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
        Box(Modifier.size(36.dp).rowFocusTarget(row = "header").optionZoom().clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                drawCircle(Color(0xFFE0E0E0), radius = size.width * .31f, center = Offset(size.width * .4f, size.height * .4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawLine(Color(0xFFE0E0E0), Offset(size.width * .64f, size.height * .64f), Offset(size.width * .9f, size.height * .9f), 2.dp.toPx())
            }
        }
        Box(Modifier.width(1.dp).height(22.dp).background(Color(0x66999999)))
        Text(time, color = Color(0xFFD6D6D6), fontSize = 21.sp)
        Canvas(Modifier.size(32.dp)) {
            val color = if (wifi) Color(0xFFE6E6E6) else Color(0xFF909090)
            for (radius in listOf(26f, 18f, 10f)) drawArc(color, 225f, 90f, false, topLeft = Offset((size.width - radius.dp.toPx()) / 2, (size.height - radius.dp.toPx()) / 2 + 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(radius.dp.toPx(), radius.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
            drawCircle(color, 2.dp.toPx(), Offset(size.width / 2, size.height * .76f))
        }
        Image(artwork.background.asImageBitmap(), "打开快捷控制中心", Modifier.size(42.dp).rowFocusTarget(row = "header").optionZoom().clip(Glass).border(1.5.dp, Color(0xFFC4C4C4), Glass).clickable(onClick = onSettings), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun NavigationCapsule(width: Dp, height: Dp, reveal: Float, selected: Boolean = false) {
    if (reveal > 0f) Box(Modifier.requiredSize(width, height).graphicsLayer {
        scaleX = .8f + .2f * reveal
        scaleY = .8f + .2f * reveal
        alpha = reveal
    }.focusSweep(selected).clip(Glass).background(Brush.linearGradient(listOf(Color.White, Color(0xFFD0D0D0)))))
}

private val LocalCardReveal = compositionLocalOf { 1f }
internal val LocalCardCoverAlpha = compositionLocalOf { 1f }

@Composable
internal fun FocusCard(
    modifier: Modifier,
    radius: Dp = 12.dp,
    onClick: () -> Unit,
    zoomOnFocus: Boolean = true,
    onHighlightChanged: (Boolean) -> Unit = {},
    borderBlendMode: BlendMode = BlendMode.SrcOver,
    borderOnlyWhenHighlighted: Boolean = false,
    showBorder: Boolean = true,
    uniformExpansionDp: Dp? = null,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val ownFocus = remember { FocusRequester() }
    val hovered by interaction.collectIsHoveredAsState()
    val keyboardControl = LocalKeyboardControl.current
    LaunchedEffect(hovered, keyboardControl) { if (hovered && !keyboardControl) ownFocus.requestFocus() }
    val highlight = focused || (hovered && !keyboardControl) || DetailOrigin.retainedFocus === ownFocus
    // While the detail page collapses back, this card's border and content fade/slide in
    // with the reveal instead of popping; the zoom stays put to match the return bounds.
    val returning = DetailOrigin.retainedFocus === ownFocus && DetailOrigin.returning
    val reveal = if (returning) DetailOrigin.returnProgress else 1f
    val coverAlpha = if (returning) 0f else 1f
    LaunchedEffect(highlight) { onHighlightChanged(highlight) }
    val baseEmphasis by animateFloatAsState(if (highlight) 1f else 0f, focusMotion(), label = "card-emphasis")
    val emphasisAlpha by animateFloatAsState(if (returning) 0f else 1f, tween(300), label = "return-glow-fade")
    val glowEmphasis = baseEmphasis * emphasisAlpha
    val borderEmphasis = if (returning) 0f else baseEmphasis
    // The highlight zoom stays put through the whole detail-page cycle, so the return's single
    // spring settle is the only animation: no re-zoom / second bounce once it settles.
    val zoomProgress by animateFloatAsState(
        if (highlight && zoomOnFocus) 1f else 0f,
        focusScaleMotion(highlight && zoomOnFocus), label = "card-focus-scale"
    )
    var cardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val shape = ContinuousCornerShape(radius)

    Box(modifier.onGloballyPositioned { cardCoordinates = it }.zIndex(if (highlight) 2f else if (borderEmphasis > .001f) 1f else 0f).graphicsLayer {
        if (uniformExpansionDp != null && size.width > 0f && size.height > 0f) {
            val expansionPx = uniformExpansionDp.toPx()
            val maxScaleX = (size.width + 2f * expansionPx) / size.width
            val maxScaleY = (size.height + 2f * expansionPx) / size.height
            scaleX = 1f + (maxScaleX - 1f) * zoomProgress
            scaleY = 1f + (maxScaleY - 1f) * zoomProgress
        } else {
            val s = 1f + 0.1f * zoomProgress
            scaleX = s
            scaleY = s
        }
    }
        .whiteFocusGlow(if (showBorder) glowEmphasis else 0f, radius).focusSweep(highlight, shape)
        .shadow((if (showBorder) 18f * glowEmphasis else 0f).dp, shape, ambientColor = White.copy(alpha = .45f), spotColor = White.copy(alpha = .45f))
        .drawWithContent {
            drawContent()
            val stroke = (1f + 2f * borderEmphasis).dp.toPx()
            if (showBorder && size.width > stroke && size.height > stroke) {
                inset(stroke / 2f) {
                    drawOutline(shape.createOutline(size, layoutDirection, this),
                        if (borderOnlyWhenHighlighted) White.copy(alpha = borderEmphasis) else lerp(Color(0x60808080), White, borderEmphasis),
                        style = Stroke(stroke), blendMode = borderBlendMode)
                }
            }
        }
        .clip(shape).focusRequester(ownFocus).focusProperties { canFocus = true }.onFocusChanged { focused = it.isFocused }
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = {
            val cardBounds = cardCoordinates?.boundsInRoot() ?: Rect.Zero
            // Anchor at the card's highlighted bounds, the size it keeps for the whole
            // detail-page cycle, so the return lands exactly on the card with no size jump.
            // onGloballyPositioned is before .graphicsLayer, so it catches the 1.0x size.
            val targetScaleX = if (zoomOnFocus) {
                if (uniformExpansionDp != null && cardBounds.width > 0f) {
                    (cardBounds.width + 2f * with(density) { uniformExpansionDp.toPx() }) / cardBounds.width
                } else 1.1f
            } else 1f
            val targetScaleY = if (zoomOnFocus) {
                if (uniformExpansionDp != null && cardBounds.height > 0f) {
                    (cardBounds.height + 2f * with(density) { uniformExpansionDp.toPx() }) / cardBounds.height
                } else 1.1f
            } else 1f
            val halfWidth = cardBounds.width * targetScaleX / 2f
            val halfHeight = cardBounds.height * targetScaleY / 2f
            DetailOrigin.bounds = Rect((cardBounds.center.x - halfWidth) / density.density,
                (cardBounds.center.y - halfHeight) / density.density,
                (cardBounds.center.x + halfWidth) / density.density,
                (cardBounds.center.y + halfHeight) / density.density)
            DetailOrigin.restoreFocus = { ownFocus.requestFocus() }
            if (DetailOrigin.retainedFocus == null) DetailOrigin.retainedFocus = ownFocus
            onClick()
        })) {
            CompositionLocalProvider(LocalCardReveal provides reveal, LocalCardCoverAlpha provides coverAlpha) {
                content()
            }
        }
}

/** Layered translucent white strokes create a small glow outside the card bounds. */
internal fun Modifier.whiteFocusGlow(amount: Float, radius: Dp) = drawBehind {
    if (amount > .001f) for (ring in 12 downTo 1) {
        val spread = ring.dp.toPx()
        val fade = 1f - ring / 13f
        val outline = ContinuousCornerShape(radius + ring.dp).createOutline(
            Size(size.width + spread * 2, size.height + spread * 2), layoutDirection, this)
        withTransform({ translate(-spread, -spread) }) {
            drawOutline(outline, Color.White.copy(alpha = .065f * amount * fade * fade), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
internal fun Modifier.optionZoom(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val zoom by animateFloatAsState(if (focused || hovered) 1.2f else 1f, tween(160), label = "option-focus")
    return this.zIndex(if (focused || hovered) 2f else 0f).graphicsLayer { scaleX = zoom; scaleY = zoom }
        .focusSweep(focused || (hovered && !LocalKeyboardControl.current))
        .focusProperties { canFocus = true }.onFocusChanged { focused = it.isFocused }.hoverable(interaction)
}

@Composable
private fun WatchingCard(media: DemoMedia, artwork: DemoArtwork, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, onClick = onClick) {
        val reveal = LocalCardReveal.current
        val coverAlpha = LocalCardCoverAlpha.current
        val realUrl = media.realItem?.backdropUrl?.ifBlank { media.realItem?.posterUrl.orEmpty() } ?: media.realItem?.posterUrl.orEmpty()
        val networkBitmap = if (realUrl.isNotBlank()) rememberPosterImage(realUrl) else null
        Image(networkBitmap ?: artwork.image(media), null, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }, contentScale = ContentScale.Crop)
        // Gradient slides up from below; caption fades in — synced with the return reveal.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - reveal) * size.height }.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0D0D0D)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp).graphicsLayer { alpha = reveal }) {
            Text(media.title, color = White, fontSize = LibraryDesign.title, lineHeight = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Progress(media.progress, Modifier.weight(1f).height(8.dp))
                Text(formatMediaProgressText(media), color = Color(0xFFDDDDDD), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Progress(progress: Float, modifier: Modifier) {
    Box(modifier.clip(Glass).background(Color(0x60808080))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(Glass).background(Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF)))))
    }
}

@Composable
internal fun GlassButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, 50.dp, onClick) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD0D0D0)))))
        Text(label, Modifier.align(Alignment.Center), color = Color(0xFF161616), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaybackButton(label: String, modifier: Modifier, wallpaper: HazeState,
    legacyBlur: ImageBitmap?, onClick: () -> Unit) {
    val canvasHeight = LocalCanvasHeight.current
    var highlighted by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(if (highlighted) 1f else 0f, focusMotion(), label = "playback-selection")
    var position by remember { mutableStateOf(Offset.Zero) }
    FocusCard(modifier, 50.dp, onClick, zoomOnFocus = false, onHighlightChanged = { highlighted = it }) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { position = it.boundsInRoot().topLeft }
            .graphicsLayer { alpha = 1f - reveal }
            .then(if (RenderPerformance.blur33) Modifier.hazeEffect(wallpaper) {
                blurRadius = 24.dp; noiseFactor = 0f
                backgroundColor = Color(0xFF333333)
                tints = listOf(HazeTint(Color(0x40333333)))
            } else Modifier.background(Color(0x66333333)))) {
            if (RenderPerformance.staticBlur && legacyBlur != null) Canvas(Modifier.fillMaxSize()) {
                drawCoverWallpaper(legacyBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()),
                    IntOffset(-position.x.toInt(), -position.y.toInt()))
                drawRect(Color(0x40333333))
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal }
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFD0D0D0)))))
        Text(label, Modifier.align(Alignment.Center), color = lerp(White, Color(0xFF161616), reveal),
            fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = LibraryDesign.pageInset, end = LibraryDesign.pageInset, top = 96.dp, bottom = 32.dp), content = content)
}

@Composable
internal fun SectionTitle(title: String, entranceIndex: Int? = null, onAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(36.dp).then(if (entranceIndex != null) Modifier.staggeredEntrance(entranceIndex) else Modifier), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = LibraryDesign.heading, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
        if (onAll != null) CapsuleTextAction("查看全部  →", Modifier.rowFocusTarget(row = "${title}:actions"), onClick = onAll)
    }
    Spacer(Modifier.height(LibraryDesign.titleGap))
}

/** Poster ratio known before the bitmap loads, so the card layout and the focus rows always agree. */
internal fun categoryCardRatio(media: DemoMedia, artwork: DemoArtwork?): Float {
    val real = media.realItem
    if (real != null && real.primaryImageAspectRatio > 0f) return real.primaryImageAspectRatio
    if (real == null) {
        val bitmap = artwork?.image(media)
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            return bitmap.width.toFloat() / bitmap.height.toFloat()
        }
    }
    val type = real?.collectionType.orEmpty().lowercase()
    return when {
        type.contains("music") || type.contains("audio") -> 1.0f
        type.contains("tv") || type.contains("show") || type.contains("movie") || type.contains("film") -> 0.67f
        media.title.contains("电影") || media.title.contains("剧") || media.title.contains("漫") -> 0.67f
        else -> 1.77f
    }
}

internal fun categoryCardSize(ratio: Float): Pair<Dp, Dp> = when {
    ratio > 1.25f -> 352.dp to 198.dp // 16:9 横版
    ratio < 0.85f -> 224.dp to 336.dp // 2:3 竖版
    else -> 224.dp to 224.dp          // 1:1 方形
}

@Composable
private fun CategoryCard(media: DemoMedia, artwork: DemoArtwork, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val realUrl = media.realItem?.posterUrl.orEmpty()
    val networkBitmap = if (realUrl.isNotBlank()) rememberPosterImage(realUrl) else null
    val imgBitmap = networkBitmap ?: artwork.image(media)

    // Deterministic size: derived from the server ratio / local artwork / heuristic, never
    // from the async-loaded bitmap. Otherwise the card reflows once the poster lands and the
    // focus rows no longer match the real FlowRow wrapping (middle rows become unreachable).
    val (cardWidth, cardHeight) = categoryCardSize(categoryCardRatio(media, artwork))

    FocusCard(modifier.size(cardWidth, cardHeight), 12.dp, onClick) {
        val coverAlpha = LocalCardCoverAlpha.current
        if (imgBitmap != null) {
            Image(
                bitmap = imgBitmap,
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha },
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0x66000000), Color(0xF20D0D0D))
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(media.title, color = White, fontSize = LibraryDesign.title, lineHeight = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (media.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(media.detail, color = Muted, fontSize = LibraryDesign.metadata, lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun PosterCard(media: DemoMedia, artwork: DemoArtwork, width: Dp = LibraryDesign.posterWidth, height: Dp = 342.dp, focusRow: String? = null, entranceIndex: Int? = null, onClick: () -> Unit) {
    var highlighted by remember { mutableStateOf(false) }
    val visibility = remember { BringIntoViewRequester() }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(highlighted, itemSize) {
        if (highlighted && itemSize.height > 0) {
            // Child focus relocation sees only the cover. Relocate the complete item
            // after scaling, including captions, overscan and the persistent header.
            withFrameNanos { }
            val header = with(density) { 128.dp.toPx() }
            val bottom = with(density) { 24.dp.toPx() }
            visibility.bringIntoView(Rect(
                left = -itemSize.width * .05f,
                top = -itemSize.height * .05f - header,
                right = itemSize.width * 1.05f,
                bottom = itemSize.height * 1.05f + bottom
            ))
        }
    }
    val zoom by animateFloatAsState(if (highlighted) 1.1f else 1f, focusScaleMotion(highlighted), label = "poster-focus")
    // Elevate and scale the whole row item so the cover cannot obscure its caption.
    Column(Modifier.width(width).then(if (entranceIndex != null) Modifier.staggeredEntrance(entranceIndex) else Modifier).rowFocusTarget(row = focusRow).bringIntoViewRequester(visibility).onSizeChanged { itemSize = it }
        .zIndex(if (highlighted) 10f else if (zoom > 1.001f) 5f else 0f).graphicsLayer { scaleX = zoom; scaleY = zoom }) {
        FocusCard(Modifier.fillMaxWidth().height(height), 12.dp, onClick, zoomOnFocus = false, onHighlightChanged = { highlighted = it }) {
            val coverAlpha = LocalCardCoverAlpha.current
            val realUrl = media.realItem?.posterUrl.orEmpty()
            val networkBitmap = if (realUrl.isNotBlank()) rememberPosterImage(realUrl) else null
            Image(networkBitmap ?: artwork.image(media), null, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }, contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(12.dp))
        Text(media.title, color = White, fontSize = LibraryDesign.title, lineHeight = 32.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(media.detail, color = Muted, fontSize = LibraryDesign.metadata, lineHeight = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterSection(title: String, media: List<DemoMedia>, artwork: DemoArtwork, square: Boolean = false, entranceIndex: Int = 25, onClick: (DemoMedia) -> Unit) {
    SectionSurface {
        SectionTitle(title, entranceIndex = entranceIndex, onAll = { onClick(media[0]) })
        Row(horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
            media.forEachIndexed { i, media -> PosterCard(media, artwork, height = if (square) LibraryDesign.posterWidth else 342.dp, focusRow = "${title}:cards", entranceIndex = entranceIndex + 1 + i) { onClick(media) } }
        }
    }
}

@Composable
private fun Favorites(artwork: DemoArtwork, favorites: List<DemoMedia>, onClick: (DemoMedia) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部影视") }
    SectionSurface {
        SectionTitle("我的收藏", entranceIndex = 17, onAll = if (favorites.isNotEmpty()) ({ favorites.firstOrNull()?.let(onClick) }) else null)
        if (favorites.isEmpty()) {
            // 空收藏时仍保留模块与标题，只放一行紧凑提示，避免整块空白占位。
            Text("暂无收藏", color = Muted, fontSize = 22.sp, modifier = Modifier.staggeredEntrance(18))
        } else {
            Row(Modifier.staggeredEntrance(18).background(Color(0x50404040), Glass).border(1.dp, Color(0x60808080), Glass).padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("全部影视", "电影", "剧集", "动漫", "纪录片", "音乐").forEach { label ->
                    Text(label, Modifier.rowFocusTarget(row = "favoriteFilters").optionZoom().clip(Glass).background(if (filter == label) Color(0xFFDDDDDD) else Color.Transparent)
                        .clickable { filter = label }.padding(horizontal = 28.dp, vertical = 9.dp), color = if (filter == label) Color(0xFF202020) else Muted, fontSize = 21.sp)
                }
            }
            Spacer(Modifier.height(LibraryDesign.titleGap))
            val visible = remember(filter, favorites) {
                favorites.filter { item ->
                    if (filter == "全部影视") true
                    else {
                        val real = item.realItem
                        val cType = real?.collectionType.orEmpty().lowercase()
                        val mType = real?.mediaType.orEmpty().lowercase()
                        val genre = real?.genre.orEmpty()
                        val detail = item.detail
                        when (filter) {
                            "电影" -> cType == "movies" || mType == "movie" || detail.contains("电影")
                            "剧集" -> cType == "tvshows" || mType in listOf("series", "episode") || detail.contains("电视剧") || detail.contains("剧集")
                            "动漫" -> cType == "anime" || genre.contains("动画") || genre.contains("动漫") || detail.contains("动漫") || detail.contains("动画")
                            "纪录片" -> cType == "documentaries" || genre.contains("纪录") || detail.contains("纪录")
                            "音乐" -> cType == "music" || mType == "music" || detail.contains("音乐") || detail.contains("首")
                            else -> true
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
                visible.take(12).forEachIndexed { i, media -> PosterCard(media, artwork, focusRow = "favorites", entranceIndex = 19 + i) { onClick(media) } }
            }
            if (visible.isEmpty()) Text("暂无收藏", color = Muted, fontSize = 24.sp, modifier = Modifier.staggeredEntrance(19))
        }
    }
}

@Composable
internal fun Overlay(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) { close.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xDA101010)).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
        }
    }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(780.dp).focusProperties { onExit = { cancelFocusChange() } }.focusGroup().clip(ContinuousCornerShape(28.dp)).background(Color(0xFF292929)).border(1.dp, Color(0x66999999), ContinuousCornerShape(28.dp)).padding(55.dp)) {
            Text(title, color = White, fontSize = 38.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(30.dp))
            content()
            Spacer(Modifier.height(35.dp))
            GlassButton("返回", Modifier.width(200.dp).height(60.dp).focusRequester(close), onClose)
        }
    }
}
