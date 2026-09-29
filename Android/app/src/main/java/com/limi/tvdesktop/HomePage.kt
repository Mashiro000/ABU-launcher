package com.limi.tvdesktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.ComponentName
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
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private data class BannerResult(val bitmap: Bitmap, val backgroundColor: Int)

private data class DockApp(
    val name: String,
    val banner: ImageBitmap?,
    val icon: ProcessedAppIcon?,
    val launch: Intent,
    val bannerBgColor: Int = android.graphics.Color.rgb(38, 42, 48),
    val packageName: String,
    val activityName: String
)
private data class DockAppSource(
    val name: String,
    val packageName: String,
    val activityName: String,
    val banner: Drawable?,
    val drawable: Drawable,
    val launch: Intent
)
private object DockAppCache {
    @Volatile var apps: List<DockApp> = emptyList()
}

/** Small LRU for decoded visuals. Metadata for every app is cheap; only recently visible card
 * bitmaps remain resident. */
private object DockVisualMemoryCache {
    private const val MAX_ENTRIES = 36
    private val entries = object : LinkedHashMap<String, DockApp>(MAX_ENTRIES, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DockApp>?) = size > MAX_ENTRIES
    }
    private fun key(packageName: String, activityName: String) = "$packageName/$activityName"
    @Synchronized fun get(app: DockApp): DockApp? = entries[key(app.packageName, app.activityName)]
    @Synchronized fun put(app: DockApp) { entries[key(app.packageName, app.activityName)] = app }
}

/** Fixed-size persistent TV-banner cache. Avoid decoding and uploading full-resolution banners
 * every time the launcher process starts. */
private object AppBannerCache {
    private const val WIDTH = 512
    private const val HEIGHT = 288
    private const val VERSION = 1

    private fun baseFile(context: Context, packageName: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(packageName.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "app-banners/v$VERSION-$digest")
    }

    fun read(context: Context, packageName: String, sourceUpdatedAt: Long): BannerResult? = runCatching {
        val base = baseFile(context, packageName)
        val meta = File(base.path + ".meta").readText().split(',')
        if (meta[0].toLong() != sourceUpdatedAt) return null
        val bitmap = android.graphics.BitmapFactory.decodeFile(base.path + ".png") ?: return null
        BannerResult(bitmap, meta[1].toInt())
    }.getOrNull()

    fun write(context: Context, packageName: String, sourceUpdatedAt: Long, result: BannerResult) = runCatching {
        val base = baseFile(context, packageName)
        base.parentFile?.mkdirs()
        File(base.path + ".png").outputStream().use {
            result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(base.path + ".meta").writeText("$sourceUpdatedAt,${result.backgroundColor}")
    }

    fun render(context: Context, source: DockAppSource): BannerResult? {
        // Package lastUpdateTime is only queried for banner apps and makes stale cache
        // invalidation deterministic.
        val packageUpdatedAt = runCatching {
            context.packageManager.getPackageInfo(source.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        read(context, source.packageName, packageUpdatedAt)?.let { return it }
        val drawable = source.banner ?: return null
        val result = renderBanner(drawable, WIDTH, HEIGHT) ?: return null
        write(context, source.packageName, packageUpdatedAt, result)
        return result
    }
}

private object InstalledAppSnapshot {
    private const val PREFS = "installed_app_snapshot"
    private const val KEY_APPS = "apps_v1"

    data class Entry(val name: String, val packageName: String, val activityName: String)

    fun read(context: Context): List<Entry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APPS, null)
            ?: return emptyList()
        val array = JSONArray(raw)
        buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(Entry(item.getString("name"), item.getString("package"), item.getString("activity")))
            }
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, sources: List<DockAppSource>) {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(JSONObject().put("name", source.name).put("package", source.packageName).put("activity", source.activityName))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_APPS, array.toString()).apply()
    }
}

private fun sourceFromSnapshot(context: Context, entry: InstalledAppSnapshot.Entry): DockAppSource? = runCatching {
    val pm = context.packageManager
    val activity = pm.getActivityInfo(ComponentName(entry.packageName, entry.activityName), 0)
    val icon = activity.loadIcon(pm)
    val banner = runCatching {
        val candidate = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        if (isValidBanner(candidate)) candidate else null
    }.getOrNull()
    DockAppSource(
        name = entry.name,
        packageName = entry.packageName,
        activityName = entry.activityName,
        banner = banner,
        drawable = icon,
        launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(entry.packageName, entry.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}.getOrNull()

private fun processSource(context: Context, source: DockAppSource): DockApp? = runCatching {
    val bannerRes = if (source.banner != null) AppBannerCache.render(context, source) else null
    val bannerBitmap = bannerRes?.bitmap?.asImageBitmap()
    DockApp(
        source.name,
        bannerBitmap,
        if (bannerBitmap == null) AppIconProcessor.process(context, source.packageName, source.drawable) else null,
        source.launch,
        bannerRes?.backgroundColor ?: android.graphics.Color.rgb(38, 42, 48),
        source.packageName,
        source.activityName
    ).also(DockVisualMemoryCache::put)
}.getOrNull()

private fun snapshotApp(entry: InstalledAppSnapshot.Entry) = DockApp(
    name = entry.name,
    banner = null,
    icon = null,
    launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(entry.packageName, entry.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    packageName = entry.packageName,
    activityName = entry.activityName
)

private fun sourceApp(source: DockAppSource) = DockApp(
    name = source.name,
    banner = null,
    icon = null,
    launch = source.launch,
    packageName = source.packageName,
    activityName = source.activityName
)

@Composable
private fun rememberVisualApp(app: DockApp): DockApp {
    val context = LocalContext.current
    return produceState(initialValue = DockVisualMemoryCache.get(app) ?: app, app.packageName, app.activityName) {
        if (value.banner != null || value.icon != null) return@produceState
        value = withContext(Dispatchers.IO) {
            DockVisualMemoryCache.get(app) ?: sourceFromSnapshot(
                context,
                InstalledAppSnapshot.Entry(app.name, app.packageName, app.activityName)
            )?.let { processSource(context, it) } ?: app
        }
    }.value
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

private fun renderBanner(drawable: Drawable, targetWidth: Int = 512, targetHeight: Int = 288): BannerResult? {
    return runCatching {
        // Cards are roughly 230x132 at the reference resolution. 512x288 retains sharpness at
        // focus zoom while preventing multi-megapixel launcher banners from exhausting GPU RAM.
        val width = targetWidth
        val height = targetHeight
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
    staticBlur: ImageBitmap?,
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
        animationSpec = tween(if (RenderPerformance.reducedEffects) 160 else 480, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "home-expand-progress"
    )

    LaunchedEffect(progress) {
        onExpandProgress(progress)
    }

    val apps by produceState(DockAppCache.apps, context) {
        // Restore the last known ordering first. Icon processing has its own disk cache, so a
        // cold process can paint the launcher without waiting for a package-manager scan.
        val savedEntries = withContext(Dispatchers.IO) { InstalledAppSnapshot.read(context) }
        if (DockAppCache.apps.isEmpty() && savedEntries.isNotEmpty()) {
            // Restore the visible Dock first instead of making it wait behind the whole grid.
            val restoredDock = withContext(Dispatchers.IO) {
                savedEntries.take(6).mapNotNull { entry ->
                    sourceFromSnapshot(context, entry)?.let { processSource(context, it) }
                }
            }
            if (restoredDock.isNotEmpty()) {
                DockAppCache.apps = restoredDock
                value = restoredDock
            }

            // The grid is metadata-only. LazyColumn loads visuals only for visible rows.
            val restored = restoredDock + savedEntries.drop(6).map(::snapshotApp)
            DockAppCache.apps = restored
            value = restored
        }

        // Refresh in the background. Only publish a new list when packages/activities or labels
        // actually changed, avoiding the visible reload on every launch.
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
                        activityName = entry.activityInfo.name,
                        banner = banner,
                        drawable = icon,
                        launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(entry.activityInfo.packageName, entry.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.getOrNull()
            }
        }

        val savedSignature = savedEntries.map { Triple(it.packageName, it.activityName, it.name) }
        val freshSignature = sources.map { Triple(it.packageName, it.activityName, it.name) }
        if (value.isNotEmpty() && savedSignature == freshSignature) return@produceState

        withContext(Dispatchers.IO) { InstalledAppSnapshot.write(context, sources) }

        // Fast-path: process the first 6 dock apps immediately so they appear without waiting for all 50+ apps
        val dockSources = sources.take(6)
        val initialProcessed = withContext(Dispatchers.IO) {
            dockSources.mapNotNull { processSource(context, it) }
        }
        if (DockAppCache.apps.isEmpty()) {
            DockAppCache.apps = initialProcessed
            value = initialProcessed
        }

        // Then process the remaining apps in background
        val all = initialProcessed + sources.drop(6).map(::sourceApp)
        DockAppCache.apps = all
        value = all
    }

    val prefVersion = DesktopPreferences.version
    val clockStyle = remember(prefVersion) { DesktopPreferences.ClockStyle.current(context) }
    val iconScale = remember(prefVersion) { DesktopPreferences.IconScale.current(context) }
    val dockStyle = remember(prefVersion) { DesktopPreferences.DockStyle.current(context) }
    val gridDensity = remember(prefVersion) { DesktopPreferences.GridDensity.current(context) }
    val dockBlur = remember(prefVersion) { DesktopPreferences.GlassTuning.dockBlur(context) }
    val dockOpacity = remember(prefVersion) { DesktopPreferences.GlassTuning.dockOpacity(context) }

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
            HomeClock(clockStyle)
        }

        // Dock Row (Top Shelf): moves smoothly from bottom to top
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = dockY)
                .width(1584.dp)
                .height(dockHeight)
        ) {
            val currentDockTint = Color.Black.copy(alpha = dockOpacity.coerceIn(0f, .9f))
            Box(Modifier.matchParentSize().staggeredEntrance(2).onGloballyPositioned { dockPosition = it.boundsInRoot().topLeft }
                .clip(shape)
            ) {
                when (dockStyle) {
                    DesktopPreferences.DockStyle.GLASS -> {
                        if (staticBlur == null && RenderPerformance.blur31) {
                            Box(
                                Modifier.fillMaxSize()
                                    .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
                                    .hazeEffect(wallpaperHaze) {
                                        blurRadius = dockBlur.dp; noiseFactor = 0f; backgroundColor = Color.Transparent
                                        tints = emptyList(); inputScale = HazeInputScale.Fixed(.3f)
                                    }
                            )
                        }
                        if (staticBlur != null && dockBlur > 0f) {
                            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = (dockBlur / 28f).coerceIn(0f, 1f) }) {
                                drawCoverWallpaper(staticBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()), IntOffset(-dockPosition.x.toInt(), -dockPosition.y.toInt()))
                            }
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
                        val visualApp = rememberVisualApp(app)
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
                            if (visualApp.banner != null) {
                                Box(Modifier.fillMaxSize().background(Color(visualApp.bannerBgColor)))
                                Image(visualApp.banner, visualApp.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (visualApp.icon != null) {
                                val color = Color(visualApp.icon.backgroundColor)
                                val gradient = remember(visualApp.icon.backgroundColor) { createBrandGradient(color) }
                                Box(Modifier.fillMaxSize().background(gradient))
                                DockAppArtwork(visualApp.icon, visualApp.name, Modifier.align(Alignment.Center).size(artworkSize))
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
    val visualApp = rememberVisualApp(app)
    var isFocused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(220, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        label = "grid-app-label"
    )
    Column(
        // D-pad navigation already scrolls the exact target row. A second BringIntoView request
        // used to fight that animation and caused visible hitching on every vertical move.
        modifier = modifier.padding(top = 12.dp),
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
            if (visualApp.banner != null) {
                Box(Modifier.fillMaxSize().background(Color(visualApp.bannerBgColor)))
                Image(visualApp.banner, visualApp.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (visualApp.icon != null) {
                val color = Color(visualApp.icon.backgroundColor)
                val gradient = remember(visualApp.icon.backgroundColor) { createBrandGradient(color) }
                Box(Modifier.fillMaxSize().background(gradient))
                DockAppArtwork(visualApp.icon, visualApp.name, Modifier.align(Alignment.Center).size(80.96.dp * iconScale))
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
        previous?.let {
            val previousBitmap = remember(it.foreground) { it.foreground.asImageBitmap() }
            Image(previousBitmap, name, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fade.value }, contentScale = ContentScale.Fit)
        }
        val currentBitmap = remember(current.foreground) { current.foreground.asImageBitmap() }
        Image(currentBitmap, name, Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }, contentScale = ContentScale.Fit)
    }
}


/** Keep clock ticks outside the application grid composition. */
@Composable
private fun HomeClock(clockStyle: DesktopPreferences.ClockStyle) {
    if (clockStyle == DesktopPreferences.ClockStyle.OFF) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(clockStyle) {
        val interval = if (clockStyle == DesktopPreferences.ClockStyle.STANDARD) 1000L else 60_000L
        while (true) { now = System.currentTimeMillis(); delay(interval - now % interval) }
    }
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
