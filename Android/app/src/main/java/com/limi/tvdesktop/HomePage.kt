package com.limi.tvdesktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.icu.util.ChineseCalendar
import android.icu.util.Calendar
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

private data class BannerResult(val bitmap: Bitmap, val backgroundColor: Int)

private data class DockApp(
    val name: String,
    val banner: ImageBitmap?,
    val icon: ProcessedAppIcon?,
    val launch: Intent,
    val bannerBgColor: Int = android.graphics.Color.rgb(38, 42, 48)
)
private data class DockAppSource(val name: String, val packageName: String, val banner: Drawable?, val drawable: Drawable, val launch: Intent)
private object DockAppCache {
    @Volatile var apps: List<DockApp> = emptyList()
}

private fun isValidBanner(drawable: Drawable?): Boolean {
    if (drawable == null) return false
    // Adaptive icons are square 1:1 masked icons, not landscape TV banners
    if (Build.VERSION.SDK_INT >= 26 && drawable is AdaptiveIconDrawable) {
        return false
    }
    val w = drawable.intrinsicWidth
    val h = drawable.intrinsicHeight
    if (w > 0 && h > 0) {
        val aspect = w.toFloat() / h
        if (aspect < 1.3f) return false
    }
    return true
}

private fun renderBanner(drawable: Drawable): BannerResult? {
    return runCatching {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 320
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 180
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val old = drawable.bounds
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        drawable.bounds = old

        val corners = intArrayOf(
            bitmap.getPixel(0, 0),
            bitmap.getPixel(width - 1, 0),
            bitmap.getPixel(0, height - 1),
            bitmap.getPixel(width - 1, height - 1)
        )
        val validCorners = corners.filter { android.graphics.Color.alpha(it) > 128 }
        val bgColor = if (validCorners.isNotEmpty()) {
            val r = validCorners.sumOf { android.graphics.Color.red(it) } / validCorners.size
            val g = validCorners.sumOf { android.graphics.Color.green(it) } / validCorners.size
            val b = validCorners.sumOf { android.graphics.Color.blue(it) } / validCorners.size
            android.graphics.Color.rgb(r, g, b)
        } else {
            android.graphics.Color.rgb(38, 42, 48)
        }

        BannerResult(bitmap, bgColor)
    }.getOrNull()
}

@Composable internal fun HomePage(
    wallpaperHaze: HazeState,
    artwork: DemoArtwork,
    first: FocusRequester,
    navigation: FocusRequester,
    onExpandProgress: (Float) -> Unit = {},
    registerReturnToTop: ((() -> Unit)?) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val shape = ContinuousCornerShape(43.25.dp)
    val dockTint = Color.Black.copy(alpha = .2f)
    val canvasHeight = LocalCanvasHeight.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(800, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "home-expand-progress"
    )

    LaunchedEffect(progress) {
        onExpandProgress(progress)
    }

    val apps by produceState(DockAppCache.apps, context) {
        val sources = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val entries = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                .distinctBy { it.activityInfo.packageName }.filter { it.activityInfo.packageName != context.packageName }
                .sortedWith(compareBy({ !it.activityInfo.packageName.contains("youtube") }, { it.loadLabel(pm).toString() }))
            entries.mapNotNull { entry ->
                runCatching {
                    val banner = runCatching {
                        val b = entry.activityInfo.loadBanner(pm) ?: entry.activityInfo.applicationInfo.loadBanner(pm)
                        if (isValidBanner(b)) b else null
                    }.getOrNull()
                    val icon = runCatching { entry.loadIcon(pm) }.getOrNull() ?: return@mapNotNull null
                    val label = runCatching { entry.loadLabel(pm).toString() }.getOrNull() ?: entry.activityInfo.packageName
                    DockAppSource(
                        name = label,
                        packageName = entry.activityInfo.packageName,
                        banner = banner,
                        drawable = icon,
                        launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(entry.activityInfo.packageName, entry.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.getOrNull()
            }
        }

        // Fast-path: process the first 6 dock apps immediately so they appear without waiting for all 50+ apps
        val dockSources = sources.take(6)
        val initialProcessed = withContext(Dispatchers.IO) {
            dockSources.mapNotNull { source ->
                runCatching {
                    val bannerRes = source.banner?.let { renderBanner(it) }
                    val bannerBitmap = bannerRes?.bitmap?.asImageBitmap()
                    val bannerBg = bannerRes?.backgroundColor ?: android.graphics.Color.rgb(38, 42, 48)
                    val icon = if (bannerBitmap == null) {
                        AppIconProcessor.process(context, source.packageName, source.drawable)
                    } else null
                    DockApp(source.name, bannerBitmap, icon, source.launch, bannerBg)
                }.getOrNull()
            }
        }
        if (DockAppCache.apps.isEmpty()) {
            DockAppCache.apps = initialProcessed
            value = initialProcessed
        }

        // Then process the remaining apps in background
        val remainingSources = sources.drop(6)
        val remainingProcessed = withContext(Dispatchers.IO) {
            remainingSources.mapNotNull { source ->
                runCatching {
                    val bannerRes = source.banner?.let { renderBanner(it) }
                    val bannerBitmap = bannerRes?.bitmap?.asImageBitmap()
                    val bannerBg = bannerRes?.backgroundColor ?: android.graphics.Color.rgb(38, 42, 48)
                    val icon = if (bannerBitmap == null) {
                        AppIconProcessor.process(context, source.packageName, source.drawable)
                    } else null
                    DockApp(source.name, bannerBitmap, icon, source.launch, bannerBg)
                }.getOrNull()
            }
        }
        val all = initialProcessed + remainingProcessed
        DockAppCache.apps = all
        value = all
    }

    val prefVersion = DesktopPreferences.version
    val clockStyle = remember(prefVersion) { DesktopPreferences.ClockStyle.current(context) }
    val iconScale = remember(prefVersion) { DesktopPreferences.IconScale.current(context) }
    val dockStyle = remember(prefVersion) { DesktopPreferences.DockStyle.current(context) }
    val gridDensity = remember(prefVersion) { DesktopPreferences.GridDensity.current(context) }

    val columns = gridDensity.columns
    val scale = iconScale.scale
    val cardHeight = 132.dp * scale
    val dockHeight = 200.dp * scale
    val cardPaddingV = ((dockHeight - cardHeight) / 2).coerceAtLeast(14.dp)
    val artworkSize = 80.96.dp * scale

    val dockApps = remember(apps) { apps.take(6) }
    val gridApps = remember(apps) { apps.drop(6) }
    val gridRows = remember(gridApps, columns) { gridApps.chunked(columns) }

    val dockRefs = remember(first) { List(6) { if (it == 0) first else FocusRequester() } }
    val gridRefs = remember(gridRows) {
        gridRows.map { row -> List(row.size) { FocusRequester() } }
    }
    val gridListState = rememberLazyListState()
    val navJobHolder = remember { object { var job: Job? = null } }

    DisposableEffect(Unit) {
        registerReturnToTop {
            expanded = false
        }
        onDispose { registerReturnToTop(null) }
    }

    BackHandler(enabled = expanded) {
        expanded = false
        dockRefs[0].requestFocus()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val date = Date(now)
    val timeTextStandard = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(date)
    val timeTextMinimal = SimpleDateFormat("HH:mm", Locale.CHINA).format(date)
    val gregorianDateText = SimpleDateFormat("EEEE  M月d日", Locale.CHINA).format(date)
    val lunarDateText = remember(now / 60_000) {
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
            "农历" + (if (lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 1) "闰" else "") + months[monthIdx] + "月" + lunarDay
        }.getOrDefault("")
    }
    val fullDateText = if (lunarDateText.isNotBlank()) "$gregorianDateText  $lunarDateText" else gregorianDateText
    var dockPosition by remember { mutableStateOf(Offset.Zero) }

    val dockTargetY = 40.dp
    val dockInitialY = (canvasHeight - dockHeight - 40.dp).coerceAtLeast(40.dp)
    val dockY = dockInitialY - (dockInitialY - dockTargetY) * progress

    Box(Modifier.fillMaxSize()) {

        // Hero Clock Area: slides up and fades out
        Box(
            Modifier.fillMaxWidth().height((canvasHeight - 280.dp).coerceAtLeast(0.dp)).padding(top = 112.dp)
                .graphicsLayer {
                    alpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
                    translationY = -140.dp.toPx() * progress
                },
            contentAlignment = Alignment.Center
        ) {
            when (clockStyle) {
                DesktopPreferences.ClockStyle.STANDARD -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            timeTextStandard,
                            Modifier.staggeredEntrance(0),
                            color = Color.White,
                            fontSize = 136.sp,
                            lineHeight = 148.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-3).sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            fullDateText,
                            Modifier.staggeredEntrance(1),
                            color = Color(0xE6FFFFFF),
                            fontSize = 25.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                DesktopPreferences.ClockStyle.MINIMAL -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            timeTextMinimal,
                            Modifier.staggeredEntrance(0),
                            color = Color.White,
                            fontSize = 152.sp,
                            lineHeight = 156.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-2).sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            fullDateText,
                            Modifier.staggeredEntrance(1),
                            color = Color(0xD0FFFFFF),
                            fontSize = 24.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
                DesktopPreferences.ClockStyle.SPLIT -> {
                    Row(
                        modifier = Modifier.staggeredEntrance(0),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            timeTextMinimal,
                            color = Color.White,
                            fontSize = 120.sp,
                            lineHeight = 126.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        )
                        Box(
                            Modifier
                                .padding(horizontal = 28.dp)
                                .width(2.dp)
                                .height(76.dp)
                                .background(Color(0x4DFFFFFF))
                        )
                        Column {
                            Text(
                                gregorianDateText,
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            if (lunarDateText.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    lunarDateText,
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 22.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
                DesktopPreferences.ClockStyle.OFF -> {
                    // Hidden
                }
            }
        }

        // Dock Row (Top Shelf): moves smoothly from bottom to top
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = dockY)
                .width(1584.dp)
                .height(dockHeight)
        ) {
            val currentDockTint = Color.Black.copy(alpha = 0.2f + 0.22f * progress)
            Box(Modifier.matchParentSize().staggeredEntrance(2).onGloballyPositioned { dockPosition = it.boundsInRoot().topLeft }
                .clip(shape)
            ) {
                when (dockStyle) {
                    DesktopPreferences.DockStyle.GLASS -> {
                        if (Build.VERSION.SDK_INT >= 31) {
                            Box(
                                Modifier.fillMaxSize()
                                    .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
                                    .hazeEffect(wallpaperHaze) {
                                        blurRadius = 28.dp; noiseFactor = 0f; backgroundColor = Color.Transparent
                                        tints = emptyList(); inputScale = HazeInputScale.Fixed(.5f)
                                    }
                            )
                        }
                        Box(Modifier.fillMaxSize().background(currentDockTint))
                    }
                    DesktopPreferences.DockStyle.FLOATING -> {
                        // Floating dock: completely transparent background
                    }
                    DesktopPreferences.DockStyle.TRANSLUCENT -> {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.48f + 0.2f * progress))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = cardPaddingV),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { index ->
                    val app = dockApps.getOrNull(index)
                    if (app != null) {
                        FocusCard(
                            Modifier.weight(1f).height(cardHeight).staggeredEntrance(3 + index)
                                .focusRequester(dockRefs[index])
                                .focusProperties {
                                    up = if (!expanded) navigation else FocusRequester.Default
                                    left = if (index > 0) dockRefs[index - 1] else FocusRequester.Cancel
                                    right = if (index < 5 && index < dockApps.lastIndex) dockRefs[index + 1] else FocusRequester.Cancel
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        if (event.key == Key.DirectionUp) {
                                            if (expanded) {
                                                expanded = false
                                                true
                                            } else {
                                                navigation.requestFocus()
                                                true
                                            }
                                        } else if (event.key == Key.DirectionDown) {
                                            expanded = true
                                            if (gridRows.isNotEmpty()) {
                                                val targetCol = (index * columns / 6).coerceIn(0, gridRows[0].lastIndex)
                                                navJobHolder.job?.cancel()
                                                navJobHolder.job = scope.launch {
                                                    val ref = gridRefs.getOrNull(0)?.getOrNull(targetCol)
                                                    if (ref == null || !runCatching { ref.requestFocus() }.isSuccess) {
                                                        var attempts = 0
                                                        while (attempts < 20) {
                                                            withFrameNanos { }
                                                            val r = gridRefs.getOrNull(0)?.getOrNull(targetCol)
                                                            if (r != null && runCatching { r.requestFocus() }.isSuccess) break
                                                            attempts++
                                                        }
                                                    }
                                                }
                                                true
                                            } else {
                                                // Even if no grid apps, expanded moves to top
                                                true
                                            }
                                        } else false
                                    } else false
                                },
                            radius = (22.dp * scale).coerceAtLeast(16.dp),
                            showBorder = false,
                            uniformExpansionDp = 10.dp,
                            onClick = {
                                launchAppWithClipReveal(context, view, null, app.launch)
                                DetailOrigin.retainedFocus = null
                            }
                        ) {
                            if (app.banner != null) {
                                Box(Modifier.fillMaxSize().background(Color(app.bannerBgColor)))
                                Image(app.banner, app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (app.icon != null) {
                                val color = Color(app.icon.backgroundColor)
                                Box(Modifier.fillMaxSize().background(createBrandGradient(color)))
                                DockAppArtwork(app.icon, app.name, Modifier.align(Alignment.Center).size(artworkSize))
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // App Grid: positioned directly below the Dock, slides up together with the Dock
        val gridTop = dockY + dockHeight + 4.dp
        if ((expanded || progress > 0.001f) && gridRows.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .offset(y = gridTop)
                    .width(1584.dp)
                    .height((canvasHeight - gridTop).coerceAtLeast(0.dp))
                    .graphicsLayer { alpha = progress }
            ) {
                LazyColumn(
                    state = gridListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(gridRows, key = { index, _ -> "grid-row-$index" }) { r, rowApps ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (columns == 7) 20.dp else 34.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            repeat(columns) { col ->
                                val app = rowApps.getOrNull(col)
                                if (app != null) {
                                    GridAppItem(
                                        app = app,
                                        iconScale = scale,
                                        modifier = Modifier.weight(1f),
                                        cardModifier = Modifier
                                            .focusRequester(gridRefs.getOrNull(r)?.getOrNull(col) ?: FocusRequester())
                                            .focusProperties {
                                                left = if (col > 0) gridRefs.getOrNull(r)?.getOrNull(col - 1) ?: FocusRequester.Cancel else FocusRequester.Cancel
                                                right = if (col < rowApps.lastIndex) gridRefs.getOrNull(r)?.getOrNull(col + 1) ?: FocusRequester.Cancel else FocusRequester.Cancel
                                                up = FocusRequester.Cancel
                                                down = FocusRequester.Cancel
                                            }
                                            .onPreviewKeyEvent { event ->
                                                if (event.type == KeyEventType.KeyDown) {
                                                    val isRepeat = event.nativeKeyEvent.repeatCount > 0
                                                    if (event.key == Key.DirectionDown) {
                                                        if (r < gridRows.lastIndex) {
                                                            val nextRow = r + 1
                                                            val targetCol = col.coerceIn(0, gridRows[nextRow].lastIndex)
                                                            navJobHolder.job?.cancel()
                                                            navJobHolder.job = scope.launch {
                                                                runCatching {
                                                                    val layoutInfo = gridListState.layoutInfo
                                                                    val visible = layoutInfo.visibleItemsInfo.find { it.index == nextRow }
                                                                    val isCompletelyVisible = visible != null && (visible.offset + visible.size <= layoutInfo.viewportEndOffset - with(density) { 24.dp.toPx() })
                                                                    if (!isCompletelyVisible) {
                                                                        val scrollDistance = with(density) { (cardHeight + 78.dp).toPx() }
                                                                        if (isRepeat) {
                                                                            gridListState.scrollBy(scrollDistance)
                                                                        } else {
                                                                            gridListState.animateScrollBy(scrollDistance, tween<Float>(120))
                                                                        }
                                                                    }
                                                                    val targetRef = gridRefs.getOrNull(nextRow)?.getOrNull(targetCol)
                                                                    if (targetRef != null) {
                                                                        if (!runCatching { targetRef.requestFocus() }.isSuccess) {
                                                                            var attempts = 0
                                                                            while (attempts < 10) {
                                                                                withFrameNanos { }
                                                                                if (runCatching { targetRef.requestFocus() }.isSuccess) break
                                                                                attempts++
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            return@onPreviewKeyEvent true
                                                        } else {
                                                            return@onPreviewKeyEvent true
                                                        }
                                                    } else if (event.key == Key.DirectionUp) {
                                                        val isRepeat = event.nativeKeyEvent.repeatCount > 0
                                                        if (r > 0) {
                                                            val prevRow = r - 1
                                                            val targetCol = col.coerceIn(0, gridRows[prevRow].lastIndex)
                                                            navJobHolder.job?.cancel()
                                                            navJobHolder.job = scope.launch {
                                                                runCatching {
                                                                    val layoutInfo = gridListState.layoutInfo
                                                                    val visible = layoutInfo.visibleItemsInfo.find { it.index == prevRow }
                                                                    val isCompletelyVisible = visible != null && (visible.offset >= layoutInfo.viewportStartOffset + with(density) { 16.dp.toPx() })
                                                                    if (!isCompletelyVisible) {
                                                                        val scrollDistance = -with(density) { (cardHeight + 78.dp).toPx() }
                                                                        if (isRepeat) {
                                                                            gridListState.scrollBy(scrollDistance)
                                                                        } else {
                                                                            gridListState.animateScrollBy(scrollDistance, tween<Float>(120))
                                                                        }
                                                                    }
                                                                    val targetRef = gridRefs.getOrNull(prevRow)?.getOrNull(targetCol)
                                                                    if (targetRef != null) {
                                                                        if (!runCatching { targetRef.requestFocus() }.isSuccess) {
                                                                            var attempts = 0
                                                                            while (attempts < 10) {
                                                                                withFrameNanos { }
                                                                                if (runCatching { targetRef.requestFocus() }.isSuccess) break
                                                                                attempts++
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            return@onPreviewKeyEvent true
                                                        } else {
                                                            navJobHolder.job?.cancel()
                                                            navJobHolder.job = scope.launch {
                                                                if (isRepeat) {
                                                                    gridListState.scrollToItem(0)
                                                                } else {
                                                                    gridListState.animateScrollToItem(0)
                                                                }
                                                                withFrameNanos { }
                                                                val targetCol = (col * 6 / columns).coerceIn(0, dockApps.lastIndex)
                                                                dockRefs.getOrNull(targetCol)?.requestFocus()
                                                            }
                                                            return@onPreviewKeyEvent true
                                                        }
                                                    }
                                                }
                                                false
                                            },
                                        onClick = {
                                            launchAppWithClipReveal(context, view, null, app.launch)
                                            DetailOrigin.retainedFocus = null
                                        }
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    item(key = "grid-bottom-space") {
                        Spacer(Modifier.height(60.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridAppItem(
    app: DockApp,
    iconScale: Float = 1f,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "grid-app-label"
    )
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
    }
    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoView).padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FocusCard(
            modifier = cardModifier.fillMaxWidth().height(132.dp * iconScale),
            radius = (22.dp * iconScale).coerceAtLeast(16.dp),
            showBorder = false,
            uniformExpansionDp = 10.dp,
            onHighlightChanged = { isFocused = it },
            onClick = onClick
        ) {
            if (app.banner != null) {
                Box(Modifier.fillMaxSize().background(Color(app.bannerBgColor)))
                Image(app.banner, app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (app.icon != null) {
                val color = Color(app.icon.backgroundColor)
                Box(Modifier.fillMaxSize().background(createBrandGradient(color)))
                DockAppArtwork(app.icon, app.name, Modifier.align(Alignment.Center).size(80.96.dp * iconScale))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(top = 14.dp)
                .graphicsLayer {
                    alpha = labelAlpha
                    translationY = (1f - labelAlpha) * (-6.dp.toPx())
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        offset = Offset(0f, 2f),
                        blurRadius = 4f
                    )
                )
            )
        }
    }
}

/** tvOS-style brand gradient: preserve HSL hue/saturation and vary lightness by 5%. */
private fun createBrandGradient(baseColor: Color): Brush {
    val baseHsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), baseHsl)

    fun withLightness(lightness: Float): Color {
        val adjusted = floatArrayOf(baseHsl[0], baseHsl[1], lightness.coerceIn(0f, 1f))
        return Color(androidx.core.graphics.ColorUtils.HSLToColor(adjusted))
    }

    val topColor = withLightness(baseHsl[2] + .05f)
    val centerColor = baseColor.copy(alpha = 1f)
    val bottomColor = withLightness(baseHsl[2] - .05f)
    return Brush.verticalGradient(colors = listOf(topColor, centerColor, bottomColor))
}

@Composable private fun DockAppArtwork(icon: ProcessedAppIcon, name: String, modifier: Modifier) {
    var current by remember { mutableStateOf(icon) }
    var previous by remember { mutableStateOf<ProcessedAppIcon?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(icon) {
        if (icon !== current) {
            previous = current
            current = icon
            fade.snapTo(0f)
            fade.animateTo(1f, tween(180))
            previous = null
        }
    }
    Box(modifier) {
        previous?.let { Image(it.foreground.asImageBitmap(), name, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fade.value }, contentScale = ContentScale.Fit) }
        Image(current.foreground.asImageBitmap(), name, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentScale = ContentScale.Fit)
    }
}
