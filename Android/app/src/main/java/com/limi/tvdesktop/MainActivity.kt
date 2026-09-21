package com.limi.tvdesktop

import android.os.Build
import android.os.Bundle
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccountManager.init(this)
        PosterCacheManager.init(this)
        MediaLibraryManager.refresh()
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
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (ScreenSaverState.isActive) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                ScreenSaverState.dismiss()
            }
            return true
        }
        ScreenSaverState.notifyInteraction()
        return super.dispatchKeyEvent(event)
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
    var page by rememberSaveable { mutableStateOf("首页") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf(false) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<DemoMedia?>(null) }
    var playingMedia by remember { mutableStateOf<MediaItemInfo?>(null) }
    var bannerIndex by rememberSaveable { mutableIntStateOf(0) }

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
    val hasAccounts = MediaLibraryManager.hasRealAccounts
    val showDemo = AccountManager.showDemoWhenEmpty.value

    val watchingList = remember(hasAccounts, realResumeWatching.size, realLatestItems.size, showDemo) {
        if (hasAccounts) {
            if (realResumeWatching.isNotEmpty()) {
                realResumeWatching.map { it.toDemoMedia() }
            } else if (realLatestItems.isNotEmpty()) {
                realLatestItems.take(6).map { it.toDemoMedia() }
            } else if (showDemo) {
                DemoLibrary.watching
            } else {
                emptyList()
            }
        } else if (showDemo) {
            DemoLibrary.watching
        } else {
            emptyList()
        }
    }
    val recentList = remember(hasAccounts, realLatestItems.size, showDemo) {
        if (hasAccounts && realLatestItems.isNotEmpty()) {
            realLatestItems.map { it.toDemoMedia() }
        } else if (showDemo) {
            DemoLibrary.recent
        } else {
            emptyList()
        }
    }
    val safeBannerIndex = bannerIndex.coerceIn(0, (watchingList.size - 1).coerceAtLeast(0))
    val banner = watchingList.getOrNull(safeBannerIndex) ?: DemoLibrary.watching.first()

    var detailClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(selected) {
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
    val controlHaze = remember { HazeState() }
    val wallpaperHaze = remember { HazeState() }
    var legacyBlur by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artwork, banner) {
        if (Build.VERSION.SDK_INT < 33) legacyBlur = withContext(Dispatchers.Default) { artwork.blurredWallpaper(banner) }
    }
    var homeExpandProgress by remember { mutableFloatStateOf(0f) }
    var homeReturnToTop by remember { mutableStateOf<(() -> Unit)?>(null) }
    val list = rememberLazyListState()
    val watching = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    val scope = rememberCoroutineScope()
    val watchingFocus = remember(watchingList.size) { List(watchingList.size.coerceAtLeast(1)) { FocusRequester() } }
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
            search -> search = false
            page == "首页" && homeExpandProgress > 0.05f -> homeReturnToTop?.invoke()
            page != "媒体库" -> page = "媒体库"
            scrolled -> returnToTop()
            else -> nav.requestFocus()
        }
    }
    LaunchedEffect(page) {
        if (page == "媒体库") {
            list.scrollToItem(0)
        } else if (page == "首页") {
            homeReturnToTop?.invoke()
        }
    }
    LaunchedEffect(Unit) {
        if (page == "首页" && !settings && !search) homeDock.requestFocus()
        else if (page == "媒体库" && !settings && !search && selected == null) first.requestFocus()
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
            FocusRowsHost(list, LibraryFocusRows, page == "媒体库" && selected == null && !settings && !search, onDirection = { keyboardControl = true }) {
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
                    val handledHere = !settings && !settingsPage
                    if (it.type == KeyEventType.KeyDown && handledHere) closeOrReturn()
                    handledHere
                } else false
            }) {
                Box(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeSource(controlHaze) else Modifier)) {
                Box(Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeSource(haze) else Modifier)) {
                    val wallpaperMode = remember(wallpaperVersion) { WallpaperMode.current(context) }
                    val fixedWallpaper = remember(wallpaperVersion, wallpaperMode) {
                        when (wallpaperMode) {
                            WallpaperMode.BING -> BingWallpaper.currentBitmap(context)
                            WallpaperMode.LOCAL -> LocalWallpapers.bitmap(context)
                            else -> null
                        }
                    }
                    val videoUri = remember(wallpaperVersion, wallpaperMode) {
                        if (wallpaperMode == WallpaperMode.VIDEO) VideoWallpaperPrefs.current(context) else null
                    }
                    val currentBlurProgress = if (page == "媒体库") progress() else if (page == "首页") homeExpandProgress else 0f

                    if (page == "媒体库") {
                        FollowContentWallpaper(banner, artwork, wallpaperHaze)
                    } else {
                        when {
                            wallpaperMode == WallpaperMode.VIDEO && videoUri != null ->
                                VideoWallpaperSurface(videoUri, Modifier.fillMaxSize())
                            wallpaperMode != WallpaperMode.FOLLOW_CONTENT && fixedWallpaper != null ->
                                Image(fixedWallpaper, null, Modifier.fillMaxSize()
                                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeSource(wallpaperHaze) else Modifier),
                                    contentScale = ContentScale.Crop)
                            wallpaperMode == WallpaperMode.FOLLOW_CONTENT ->
                                FollowContentWallpaper(banner, artwork, wallpaperHaze)
                            else ->
                                Image(artwork.background.asImageBitmap(), null, Modifier.fillMaxSize()
                                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeSource(wallpaperHaze) else Modifier),
                                    contentScale = ContentScale.Crop)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Box(Modifier.fillMaxSize().hazeEffect(wallpaperHaze) {
                            blurEnabled = currentBlurProgress > 0f
                            blurRadius = (50f * currentBlurProgress).coerceAtLeast(.01f).dp
                            backgroundColor = Color.Transparent
                            tints = if (page == "首页") listOf(HazeTint(Color.Black.copy(alpha = .35f * currentBlurProgress))) else emptyList()
                            noiseFactor = 0f
                            inputScale = HazeInputScale.Fixed(.5f)
                        })
                    } else if (legacyBlur != null) {
                        Image(legacyBlur!!, null, Modifier.fillMaxSize().graphicsLayer { alpha = currentBlurProgress }, contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x08202020), Color(0x0A101010), Color(0xA6101010)))))
                    Canvas(Modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = .72f * currentBlurProgress)) }
                    if (page == "媒体库") {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            item(key = "first-screen") {
                                Box(Modifier.fillMaxWidth().height(941.dp)) {
                                    Text(if (banner.title == "海岸线之外") "更大的世界\n在海岸线之外" else "", Modifier.offset(1440.dp, 181.dp).graphicsLayer { rotationZ = -9f },
                                        color = Color(0xFF808080), fontSize = 26.sp, lineHeight = 38.sp,
                                        fontFamily = FontFamily(Font(R.font.ma_shan_zheng)), letterSpacing = 3.sp)
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
                                        Crossfade(banner, Modifier.staggeredEntrance(0).height(120.dp), tween(280), label = "banner-title") { media ->
                                            Text(media.title, color = White, fontSize = 90.sp, lineHeight = 120.sp, fontFamily = FontFamily(Font(R.font.ma_shan_zheng)), letterSpacing = 3.sp)
                                        }
                                        Spacer(Modifier.height(29.dp))
                                        Crossfade(banner, Modifier.staggeredEntrance(1).height(32.dp), tween(280), label = "banner-intro") { media ->
                                            Text(DemoLibrary.bannerIntro(media), color = Muted, fontSize = 23.sp, letterSpacing = 2.sp)
                                        }
                                        Spacer(Modifier.height(36.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                            PlaybackButton("▶  继续播放", Modifier.staggeredEntrance(2).size(236.dp, 70.dp).rowFocusTarget(row = "play").focusRequester(playback)
                                                .focusProperties { up = nav; down = first }.onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                                        returnToTop(first); true
                                                    } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                                        nav.requestFocus(); true
                                                    } else false
                                                }, wallpaperHaze, legacyBlur) {
                                                    if (banner.realItem?.streamUrl?.isNotBlank() == true) {
                                                        playingMedia = banner.realItem
                                                    } else {
                                                        selected = banner
                                                    }
                                                }
                                            Column(Modifier.staggeredEntrance(3).width(286.dp)) {
                                                Progress(banner.progress, Modifier.fillMaxWidth().height(8.dp))
                                                Spacer(Modifier.height(12.dp))
                                                Text(if (banner.detail.contains("集")) "${banner.detail.substringBefore("/").trim()} · 已观看 ${(banner.progress * 45).toInt()} 分钟 / 45 分钟" else banner.detail, color = Muted, fontSize = 18.sp)
                                            }
                                        }
                                    }
                                    Column(Modifier.offset(y = 618.dp).fillMaxWidth()) {
                                        Text("继续观看", Modifier.staggeredEntrance(4).padding(start = LibraryDesign.pageInset), color = White, fontSize = LibraryDesign.heading, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
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
                                                        if (media.realItem?.streamUrl?.isNotBlank() == true) {
                                                            playingMedia = media.realItem
                                                        } else {
                                                            selected = media
                                                        }
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "categories") {
                                SectionSurface {
                                    SectionTitle("我的媒体", entranceIndex = 10)
                                    Row(horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
                                        DemoLibrary.categories.forEachIndexed { i, media ->
                                            CategoryCard(media, artwork, Modifier.staggeredEntrance(11 + i).rowFocusTarget(row = "categories").width(LibraryDesign.posterWidth).height(366.dp)
                                                .then(if (i == 0) Modifier.focusRequester(category) else Modifier)
                                                .focusProperties { up = watchingFocus[bannerIndex] }
                                                .onPreviewKeyEvent {
                                                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) { returnToTop(watchingFocus[bannerIndex]); true } else false
                                                }) { selected = media }
                                        }
                                    }
                                }
                            }
                            item(key = "favorites") { Favorites(artwork) { selected = it } }
                            item(key = "recent") { PosterSection("最近添加", DemoLibrary.recent, artwork, entranceIndex = 25) { selected = it } }
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
                    } else if (page == "首页") {
                        HomePage(wallpaperHaze, artwork, homeDock, homeNav,
                            onExpandProgress = { homeExpandProgress = it },
                            registerReturnToTop = { homeReturnToTop = it })
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
                if (settings) ControlCenter(controlHaze, onSettings = {
                    settings = false
                    settingsPage = true
                }, onClose = { settings = false })
                if (settingsPage) SettingsPage(haze = controlHaze, onClose = { settingsPage = false }, onWallpaperChanged = { wallpaperVersion++ })
                if (playingMedia != null) {
                    VideoPlayerScreen(
                        title = playingMedia!!.title,
                        streamUrl = playingMedia!!.streamUrl,
                        startPositionMs = playingMedia!!.playbackPositionMs,
                        onProgressUpdate = { pos, _ ->
                            MediaLibraryManager.reportPlayback(playingMedia!!, pos, false)
                        },
                        onOpenExternal = {
                            launchExternalPlayer(context, playingMedia!!.streamUrl, playingMedia!!.title)
                        },
                        onClose = {
                            playingMedia = null
                        }
                    )
                }
                if (ScreenSaverState.isActive) {
                    ScreenSaverOverlay(onDismiss = { ScreenSaverState.dismiss() })
                }
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

        // Apple TV style Clock & Date in bottom-left corner
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

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun FollowContentWallpaper(banner: DemoMedia, artwork: DemoArtwork, wallpaperHaze: HazeState) {
    Crossfade(banner, Modifier.fillMaxSize()
        .then(if (Build.VERSION.SDK_INT >= 33) Modifier.hazeSource(wallpaperHaze) else Modifier),
        animationSpec = tween(380), label = "banner-background") { media ->
        Image(if (media.title == "海岸线之外") artwork.background.asImageBitmap() else artwork.image(media),
            null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun Header(page: String, animeko: Boolean, haze: HazeState, legacyBlur: ImageBitmap?, nav: FocusRequester, homeNav: FocusRequester, artwork: DemoArtwork, backdropReveal: () -> Float, onPage: (String) -> Unit, onSettings: () -> Unit, onSearch: () -> Unit, onContent: () -> Unit) {
    val canvasHeight = LocalCanvasHeight.current
    TopBarBackdrop(haze, backdropReveal)
    val tabs = listOf("首页", "媒体库", "直播", "应用", "游戏") + if (animeko) listOf("Animeko") else emptyList()
    val width = if (animeko) 744.dp else 620.dp
    var hoveredTab by remember { mutableStateOf<String?>(null) }
    var keyboardNavigation by remember { mutableStateOf(false) }
    val capsuleWidth = (width - 10.dp) / tabs.size - 18.dp
    Box(Modifier.offset(x = (1672.dp - width) / 2, y = 26.dp).size(width, 64.dp).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown) keyboardNavigation = true
        false
    }) {
        Box(Modifier.fillMaxSize().clip(Glass)
        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeEffect(haze) {
            blurRadius = 24.dp; noiseFactor = .025f
            backgroundColor = Color(0xFF333333)
            tints = listOf(HazeTint(Color(0x40404040)))
            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = .65f)
        } else Modifier)) {
        if (Build.VERSION.SDK_INT < 31 && legacyBlur != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawCoverWallpaper(legacyBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()), IntOffset(-(((1672f - width.value) / 2) * density).toInt(), -(26 * density).toInt()))
                drawRect(Color(0x40404040))
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .20f)))
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
                    // five-tab bar reads 115dp and the Animeko bar scales the same way (115.4dp).
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
    val context = LocalContext.current
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
private val LocalCardCoverAlpha = compositionLocalOf { 1f }

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
            if (showBorder) inset(stroke / 2f) {
                drawOutline(shape.createOutline(size, layoutDirection, this),
                    if (borderOnlyWhenHighlighted) White.copy(alpha = borderEmphasis) else lerp(Color(0x60808080), White, borderEmphasis),
                    style = Stroke(stroke), blendMode = borderBlendMode)
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
        val realUrl = media.realItem?.posterUrl.orEmpty()
        val networkBitmap = if (realUrl.isNotBlank()) rememberPosterImage(realUrl) else null
        Image(networkBitmap ?: artwork.image(media), null, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }, contentScale = ContentScale.Crop)
        // Gradient slides up from below; caption fades in — synced with the return reveal.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - reveal) * size.height }.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0D0D0D)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp).graphicsLayer { alpha = reveal }) {
            Text(media.title, color = White, fontSize = LibraryDesign.title, lineHeight = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Progress(media.progress, Modifier.weight(1f).height(8.dp))
                Text(media.detail, color = Color(0xFFDDDDDD), fontSize = 16.sp)
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
            .then(if (Build.VERSION.SDK_INT >= 33) Modifier.hazeEffect(wallpaper) {
                blurRadius = 24.dp; noiseFactor = 0f
                backgroundColor = Color(0xFF333333)
                tints = listOf(HazeTint(Color(0x40333333)))
            } else Modifier.background(Color(0x66333333)))) {
            if (Build.VERSION.SDK_INT < 33 && legacyBlur != null) Canvas(Modifier.fillMaxSize()) {
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

@Composable
private fun CategoryCard(media: DemoMedia, artwork: DemoArtwork, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, 12.dp, onClick) {
        val coverAlpha = LocalCardCoverAlpha.current
        Image(artwork.image(media), null, Modifier.fillMaxSize().graphicsLayer { alpha = coverAlpha }, contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF00D0D0D)))))
        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            Text(media.title, color = White, fontSize = LibraryDesign.title, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            Text(media.detail, color = Muted, fontSize = LibraryDesign.metadata, lineHeight = 28.sp)
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
private fun Favorites(artwork: DemoArtwork, onClick: (DemoMedia) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("全部影视") }
    SectionSurface {
        SectionTitle("我的收藏", entranceIndex = 17, onAll = { onClick(DemoLibrary.favorites[0]) })
        Row(Modifier.staggeredEntrance(18).background(Color(0x50404040), Glass).border(1.dp, Color(0x60808080), Glass).padding(horizontal = 10.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("全部影视", "电影", "剧集", "动漫", "纪录片", "音乐").forEach { label ->
                Text(label, Modifier.rowFocusTarget(row = "favoriteFilters").optionZoom().clip(Glass).background(if (filter == label) Color(0xFFDDDDDD) else Color.Transparent)
                    .clickable { filter = label }.padding(horizontal = 28.dp, vertical = 9.dp), color = if (filter == label) Color(0xFF202020) else Muted, fontSize = 21.sp)
            }
        }
        Spacer(Modifier.height(LibraryDesign.titleGap))
        val visible = DemoLibrary.favorites.filter { filter == "全部影视" || it.detail.contains(if (filter == "剧集") "电视剧" else filter) }
        Row(horizontalArrangement = Arrangement.spacedBy(LibraryDesign.cardGap)) {
            visible.forEachIndexed { i, media -> PosterCard(media, artwork, focusRow = "favorites", entranceIndex = 19 + i) { onClick(media) } }
        }
        if (visible.isEmpty()) Text("暂无收藏", color = Muted, fontSize = 24.sp, modifier = Modifier.staggeredEntrance(19).height(342.dp).padding(top = 90.dp))
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
