package com.limi.tvdesktop

import android.app.ActivityOptions
import android.content.Context
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.net.Uri
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.icu.util.ChineseCalendar
import android.icu.util.Calendar
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*
import kotlinx.coroutines.*
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
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
    val activityName: String,
    val folder: HomeFolder? = null,
    val customIcon: ImageBitmap? = null
)
private data class DockAppSource(
    val name: String,
    val packageName: String,
    val activityName: String,
    val banner: Drawable?,
    val drawable: Drawable,
    val launch: Intent
)
private data class PendingWidgetBind(val id: Int, val provider: AppWidgetProviderInfo, val width: Int, val height: Int, val configuring: Boolean = false)
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
        return File(context.filesDir, "launcher-banners/v$VERSION-$digest")
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
    private const val KEY_SCANNED_AT = "scanned_at"
    private const val REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L

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

    fun isFresh(context: Context): Boolean {
        val scannedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SCANNED_AT, 0L)
        return scannedAt > 0L && System.currentTimeMillis() - scannedAt < REFRESH_INTERVAL_MS
    }

    fun markFresh(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SCANNED_AT, System.currentTimeMillis()).commit()
    }

    fun write(context: Context, sources: List<DockAppSource>) {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(JSONObject().put("name", source.name).put("package", source.packageName).put("activity", source.activityName))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APPS, array.toString())
            .putLong(KEY_SCANNED_AT, System.currentTimeMillis())
            .commit()
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
    val visual = produceState(initialValue = DockVisualMemoryCache.get(app) ?: app, app.packageName, app.activityName) {
        if (value.banner != null || value.icon != null) return@produceState
        value = withContext(Dispatchers.IO) {
            DockVisualMemoryCache.get(app) ?: sourceFromSnapshot(
                context,
                InstalledAppSnapshot.Entry(app.name, app.packageName, app.activityName)
            )?.let { processSource(context, it) } ?: app
        }
    }.value
    return visual.copy(name = app.name, customIcon = app.customIcon)
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
    onDesktopEdit: () -> Unit = {},
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
    var featureRevision by remember { mutableIntStateOf(0) }
    var activeMenu by remember { mutableStateOf<DockApp?>(null) }
    var activeMenuBounds by remember { mutableStateOf<Rect?>(null) }
    // 菜单是否正在关闭：关闭开始后 forceHighlight 立即退场，改由真实焦点维持放大，
    // 避免关闭动画（700ms）期间用户移焦后旧图标仍被卡在放大态
    var menuClosing by remember { mutableStateOf(false) }
    // 待恢复焦点的应用 key：长按菜单关闭后把焦点还给打开菜单的图标
    var menuReturnKey by remember { mutableStateOf<String?>(null) }

    // 需要保持放大高亮的应用：菜单打开未关闭时是长按的应用；兜底恢复期间是 menuReturnKey。
    // 关闭开始后由真实焦点接管，不再用 forceHighlight 维持，移焦不会留下卡住的放大态。
    val forceHighlightKey: String? = when {
        menuReturnKey != null -> menuReturnKey
        activeMenu != null && !menuClosing -> activeMenu?.let(::appKey)
        else -> null
    }
    var lastConfirmKeyDownAt by remember { mutableLongStateOf(0L) }
    var suppressMenuOpeningRelease by remember { mutableStateOf(false) }
    var layoutRevision by remember { mutableIntStateOf(0) }
    var editOrderKeys by remember { mutableStateOf<List<String>?>(null) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var dockPlacementKey by remember { mutableStateOf<String?>(null) }
    var iconEditApp by remember { mutableStateOf<DockApp?>(null) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val app = iconEditApp
        if (uri != null && app != null && AppHomeFeatures.saveCustomIcon(context, appKey(app), uri)) featureRevision++
    }
    var folderToOpen by remember { mutableStateOf<HomeFolder?>(null) }
    var folderPicker by remember { mutableStateOf<HomeFolder?>(null) }
    var pendingPinApp by remember { mutableStateOf<DockApp?>(null) }
    var pinMode by remember { mutableStateOf<String?>(null) }
    var pinFirstEntry by remember { mutableStateOf("") }
    var folderAnimation by remember { mutableStateOf<DockApp?>(null) }
    var widgetOwner by remember { mutableStateOf<DockApp?>(null) }
    var chosenWidgetProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }
    var widgets by remember { mutableStateOf(AppHomeFeatures.widgets(context)) }
    var pendingWidgetBind by remember { mutableStateOf<PendingWidgetBind?>(null) }
    val widgetHost = remember(context) { AppWidgetHost(context, 0xAB17) }
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    DisposableEffect(widgetHost) {
        runCatching { widgetHost.startListening() }
        onDispose { runCatching { widgetHost.stopListening() } }
    }
    fun persistWidget(pending: PendingWidgetBind) {
        widgets = widgets.filterNot { it.id == pending.id } + HomeWidget(pending.id, pending.provider.provider.flattenToString(), pending.width, pending.height)
        AppHomeFeatures.saveWidgets(context, widgets)
    }
    val widgetConfigLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidgetBind
        if (pending != null && result.resultCode == android.app.Activity.RESULT_OK) persistWidget(pending)
        else if (pending != null) widgetHost.deleteAppWidgetId(pending.id)
        pendingWidgetBind = null
    }
    val widgetBindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidgetBind
        if (pending != null && result.resultCode == android.app.Activity.RESULT_OK) {
            if (pending.provider.configure != null) {
                pendingWidgetBind = pending.copy(configuring = true)
                widgetConfigLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(pending.provider.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, pending.provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, android.os.Bundle()))
            } else persistWidget(pending)
            if (pending.provider.configure == null) pendingWidgetBind = null
        } else if (pending != null) widgetHost.deleteAppWidgetId(pending.id)
        if (pending == null || result.resultCode != android.app.Activity.RESULT_OK) pendingWidgetBind = null
    }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = pageMotion(),
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

        // PackageManager enumeration and label/icon loading is one of the most expensive parts
        // of launcher startup. A persisted snapshot is authoritative for a short window; a later
        // background refresh picks up installs/removals without reloading everything on each entry.
        if (savedEntries.isNotEmpty() && InstalledAppSnapshot.isFresh(context)) return@produceState

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
        if (value.isNotEmpty() && savedSignature == freshSignature) {
            withContext(Dispatchers.IO) { InstalledAppSnapshot.markFresh(context) }
            return@produceState
        }

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

    val folders = remember(featureRevision) { AppHomeFeatures.folders(context) }
    val hiddenKeys = remember(featureRevision) { AppHomeFeatures.hidden(context) }
    val baseHomeEntries = remember(apps, folders, hiddenKeys, featureRevision) {
        val byKey = apps.associateBy { "${it.packageName}/${it.activityName}" }
        fun customized(app: DockApp): DockApp {
            val key = appKey(app)
            return app.copy(
                name = AppHomeFeatures.customLabel(context, key) ?: app.name,
                customIcon = AppHomeFeatures.customIcon(context, key)
            )
        }
        val owners = folders.flatMap { folder -> folder.members.map { it to folder } }.toMap()
        val insertedFolders = mutableSetOf<String>()
        buildList {
            apps.forEach { app ->
                val key = "${app.packageName}/${app.activityName}"
                if (key in hiddenKeys) return@forEach
                val owner = owners[key]
                if (owner != null) {
                    if (insertedFolders.add(owner.id)) {
                        val first = owner.members.firstNotNullOfOrNull(byKey::get) ?: app
                        add(first.copy(name = owner.name, folder = owner, packageName = "folder.${owner.id}", activityName = owner.id))
                    }
                } else add(customized(app))
            }
            folders.filter { insertedFolders.add(it.id) }.forEach { folder ->
                val first = folder.members.firstNotNullOfOrNull(byKey::get) ?: return@forEach
                add(customized(first).copy(name = folder.name, folder = folder, packageName = "folder.${folder.id}", activityName = folder.id))
            }
        }
    }
    val appSort = remember(prefVersion) { DesktopPreferences.AppSort.current(context) }
    val homeEntries = remember(baseHomeEntries, appSort, layoutRevision, editOrderKeys) {
        val byKey = baseHomeEntries.associateBy(::appKey)
        val saved = AppHomeFeatures.homeOrder(context)
        val canonical = buildList {
            saved.forEach { key -> byKey[key]?.let { add(it) } }
            baseHomeEntries.forEach { app -> if (none { appKey(it) == appKey(app) }) add(app) }
        }
        val editing = editOrderKeys
        if (editing != null) {
            buildList {
                editing.forEach { key -> byKey[key]?.let { add(it) } }
                canonical.forEach { app -> if (none { appKey(it) == appKey(app) }) add(app) }
            }
        } else {
            val dock = canonical.take(6)
            val grid = canonical.drop(6)
            val sortedGrid = when (appSort) {
                DesktopPreferences.AppSort.CUSTOM -> grid
                DesktopPreferences.AppSort.ALPHABETICAL -> {
                    val collator = Collator.getInstance(Locale.CHINA)
                    grid.sortedWith { left, right -> collator.compare(left.name, right.name) }
                }
                DesktopPreferences.AppSort.INSTALL_NEWEST,
                DesktopPreferences.AppSort.INSTALL_OLDEST -> {
                    val times = grid.associateWith { app -> runCatching {
                        context.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
                    }.getOrDefault(0L) }
                    if (appSort == DesktopPreferences.AppSort.INSTALL_NEWEST) grid.sortedByDescending { times[it] ?: 0L }
                    else grid.sortedBy { times[it] ?: Long.MAX_VALUE }
                }
                DesktopPreferences.AppSort.SMART -> {
                    val now = System.currentTimeMillis()
                    grid.sortedByDescending { app ->
                        val installedAt = runCatching { context.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime }.getOrDefault(0L)
                        val ageDays = ((now - installedAt).coerceAtLeast(0L) / 86_400_000.0)
                        val newBoost = ((14.0 - ageDays).coerceAtLeast(0.0) / 14.0) * 1_000_000.0
                        val usage = AppHomeFeatures.launchUsage(context, appKey(app))
                        val recentDays = if (usage.lastUsedAt > 0L) (now - usage.lastUsedAt).coerceAtLeast(0L) / 86_400_000.0 else 365.0
                        newBoost + usage.count * 10_000.0 + (30.0 - recentDays).coerceAtLeast(0.0) * 1_000.0
                    }
                }
            }
            dock + sortedGrid
        }
    }
    LaunchedEffect(baseHomeEntries) {
        if (baseHomeEntries.isNotEmpty() && AppHomeFeatures.homeOrder(context).isEmpty()) {
            AppHomeFeatures.saveHomeOrder(context, baseHomeEntries.map(::appKey))
            layoutRevision++
        }
    }
    val dockApps = remember(homeEntries) { homeEntries.take(6) }
    val gridApps = remember(homeEntries) { homeEntries.drop(6) }
    val gridRows = remember(gridApps, columns) { gridApps.chunked(columns) }

    val dockRefs = remember(first) { List(6) { if (it == 0) first else FocusRequester() } }
    val gridRefs = remember(gridRows) {
        gridRows.map { row -> List(row.size) { FocusRequester() } }
    }
    val gridListState = rememberLazyListState()
    val navJobHolder = remember { object { var job: Job? = null } }

    fun saveLayout(keys: List<String>) {
        AppHomeFeatures.saveHomeOrder(context, keys)
        DesktopPreferences.AppSort.save(context, DesktopPreferences.AppSort.CUSTOM)
        layoutRevision++
    }

    fun swapEditingApp(targetIndex: Int) {
        val keys = editOrderKeys ?: return
        val key = editingKey ?: return
        val from = keys.indexOf(key)
        if (from !in keys.indices || targetIndex !in keys.indices || from == targetIndex) return
        editOrderKeys = keys.toMutableList().apply {
            val displaced = this[targetIndex]
            this[targetIndex] = key
            this[from] = displaced
        }
    }

    fun editingTarget(index: Int, key: Key): Int? {
        val total = editOrderKeys?.size ?: return null
        if (index !in 0 until total) return null
        if (index < 6) return when (key) {
            Key.DirectionLeft -> (index - 1).takeIf { it >= 0 }
            Key.DirectionRight -> (index + 1).takeIf { it < minOf(6, total) }
            Key.DirectionDown -> {
                val firstGridSize = minOf(columns, total - 6)
                if (firstGridSize <= 0) null else 6 + (index * firstGridSize / minOf(6, total)).coerceIn(0, firstGridSize - 1)
            }
            else -> null
        }
        val relative = index - 6
        val row = relative / columns
        val col = relative % columns
        return when (key) {
            Key.DirectionLeft -> (index - 1).takeIf { col > 0 }
            Key.DirectionRight -> (index + 1).takeIf { col < columns - 1 && it < total }
            Key.DirectionUp -> if (row == 0) (col * minOf(6, total) / columns).coerceIn(0, minOf(6, total) - 1) else index - columns
            Key.DirectionDown -> (index + columns).takeIf { it < total }
            else -> null
        }
    }

    fun handleEditingKey(event: androidx.compose.ui.input.key.KeyEvent, index: Int): Boolean {
        if (editOrderKeys == null) return false
        val confirm = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.Spacebar
        if (confirm) {
            if (event.type == KeyEventType.KeyDown) {
                editOrderKeys?.let(::saveLayout)
                editOrderKeys = null
                editingKey = null
            }
            return true
        }
        if (event.type == KeyEventType.KeyDown) {
            editingTarget(index, event.key)?.let(::swapEditingApp)
            if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                event.key == Key.DirectionUp || event.key == Key.DirectionDown) return true
        }
        return false
    }

    fun placeInDock(targetIndex: Int) {
        val key = dockPlacementKey ?: return
        val keys = homeEntries.map(::appKey).toMutableList()
        val source = keys.indexOf(key)
        if (source !in keys.indices || targetIndex !in 0 until minOf(6, keys.size)) return
        val displaced = keys[targetIndex]
        keys[targetIndex] = key
        keys[source] = displaced
        saveLayout(keys)
        dockPlacementKey = null
    }

    DisposableEffect(Unit) {
        registerReturnToTop {
            expanded = false
        }
        onDispose { registerReturnToTop(null) }
    }

    BackHandler(enabled = expanded || editOrderKeys != null || dockPlacementKey != null || activeMenu != null) {
        when {
            activeMenu != null -> {
                // 返回只关闭长按菜单（焦点恢复由 menuReturnKey 机制在菜单销毁后完成）
                val app = activeMenu
                activeMenu = null
                activeMenuBounds = null
                menuClosing = false
                if (app != null) menuReturnKey = appKey(app)
            }
            editOrderKeys != null -> {
                editOrderKeys = null
                editingKey = null
                dockRefs[0].requestFocus()
            }
            dockPlacementKey != null -> {
                dockPlacementKey = null
                dockRefs[0].requestFocus()
            }
            else -> {
                expanded = false
                dockRefs[0].requestFocus()
            }
        }
    }

    // 长按菜单关闭后，把焦点还原到打开菜单的那个应用图标。菜单销毁会打断关闭瞬间的
    // requestFocus，所以等菜单真正离开组合后再聚焦，并带重试兜底。
    LaunchedEffect(menuReturnKey) {
        val key = menuReturnKey ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val idx = homeEntries.indexOfFirst { appKey(it) == key }
        var restored = false
        if (idx in 0..5) {
            restored = runCatching { dockRefs.getOrNull(idx)?.requestFocus() }.getOrNull() ?: false
        } else if (idx >= 6) {
            restored = runCatching {
                gridRefs.getOrNull((idx - 6) / columns)?.getOrNull((idx - 6) % columns)?.requestFocus()
            }.getOrNull() ?: false
        }
        if (!restored) runCatching { dockRefs.getOrNull(0)?.requestFocus() }
        menuReturnKey = null
    }

    LaunchedEffect(editOrderKeys, editingKey, gridRefs) {
        val key = editingKey ?: return@LaunchedEffect
        val index = editOrderKeys?.indexOf(key) ?: return@LaunchedEffect
        withFrameNanos { }
        if (index < 6) dockRefs.getOrNull(index)?.requestFocus()
        else gridRefs.getOrNull((index - 6) / columns)?.getOrNull((index - 6) % columns)?.requestFocus()
    }

    LaunchedEffect(dockPlacementKey) {
        if (dockPlacementKey != null) {
            withFrameNanos { }
            dockRefs.firstOrNull()?.requestFocus()
        }
    }

    var dockPosition by remember { mutableStateOf(Offset.Zero) }

    var pendingLaunchBounds by remember { mutableStateOf<Rect?>(null) }

    fun openEntry(app: DockApp, bounds: Rect? = null) {
        val folder = app.folder
        if (folder != null) {
            folderToOpen = folder
        } else if (appKey(app) in AppHomeFeatures.locked(context)) {
            pendingPinApp = app
            pendingLaunchBounds = bounds
            pinMode = if (AppHomeFeatures.hasPin(context)) "verify_launch" else "setup_launch"
        } else {
            AppHomeFeatures.recordLaunch(context, appKey(app))
            launchAppWithClipReveal(context, view, bounds, app.launch)
            DetailOrigin.retainedFocus = null
        }
    }

    fun addWidget(provider: AppWidgetProviderInfo, width: Int, height: Int) {
        val id = widgetHost.allocateAppWidgetId()
        val options = android.os.Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
        }
        if (widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider, options)) {
            val pending = PendingWidgetBind(id, provider, width, height)
            if (provider.configure != null) {
                pendingWidgetBind = pending.copy(configuring = true)
                widgetConfigLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(provider.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options))
            } else persistWidget(pending)
        } else {
            pendingWidgetBind = PendingWidgetBind(id, provider, width, height)
            widgetBindLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options))
        }
    }

    val dockTargetY = 40.dp
    val dockInitialY = (canvasHeight - dockHeight - 40.dp).coerceAtLeast(40.dp)
    val dockY = dockInitialY - (dockInitialY - dockTargetY) * progress

    Box(Modifier.fillMaxSize()) {

        if (editOrderKeys != null || dockPlacementKey != null) {
            val message = if (editOrderKeys != null) "桌面编辑 · 方向键移动 · OK 保存 · 返回取消"
                else "选择 Dock 位置 · OK 互换 · 返回取消"
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 112.dp).zIndex(900f)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xD9202227))
                    .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(message, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }

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
                                    if (handleEditingKey(event, index)) return@onPreviewKeyEvent true
                                    if (dockPlacementKey != null) {
                                        val confirm = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                                        if (confirm) {
                                            if (event.type == KeyEventType.KeyDown) placeInDock(index)
                                            return@onPreviewKeyEvent true
                                        }
                                        if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) return@onPreviewKeyEvent true
                                    }
                                    if (event.type == KeyEventType.KeyDown) {
                                        val code = event.nativeKeyEvent.keyCode
                                        if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER ||
                                            code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || code == android.view.KeyEvent.KEYCODE_SPACE) {
                                            lastConfirmKeyDownAt = SystemClock.elapsedRealtime()
                                        }
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
                            uniformExpansionDp = if (editingKey == appKey(app)) 18.dp else 10.dp,
                            forceHighlight = forceHighlightKey != null && forceHighlightKey == appKey(app),
                            onClick = {},
                            onClickWithBounds = { bounds ->
                                when {
                                    dockPlacementKey != null -> placeInDock(index)
                                    editOrderKeys != null -> {
                                        editOrderKeys?.let(::saveLayout)
                                        editOrderKeys = null
                                        editingKey = null
                                    }
                                    else -> openEntry(app, bounds)
                                }
                            },
                            onLongClick = { bounds -> if (app.folder == null && editOrderKeys == null && dockPlacementKey == null) {
                                suppressMenuOpeningRelease = SystemClock.elapsedRealtime() - lastConfirmKeyDownAt < 1500L
                                menuClosing = false
                                activeMenu = app
                                activeMenuBounds = bounds
                            } }
                        ) {
                            if (visualApp.folder != null) {
                                FolderTile(visualApp.folder.members.size, Modifier.fillMaxSize())
                            } else if (visualApp.customIcon != null) {
                                Image(visualApp.customIcon, visualApp.name, Modifier.align(Alignment.Center).size(artworkSize), contentScale = ContentScale.Fit)
                            } else if (visualApp.banner != null) {
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
                                        editing = editingKey == appKey(app),
                                        forceHighlight = forceHighlightKey != null && forceHighlightKey == appKey(app),
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
                                                val globalIndex = 6 + r * columns + col
                                                if (handleEditingKey(event, globalIndex)) return@onPreviewKeyEvent true
                                                if (dockPlacementKey != null) return@onPreviewKeyEvent true
                                                if (event.type == KeyEventType.KeyDown) {
                                                    val code = event.nativeKeyEvent.keyCode
                                                    if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER ||
                                                        code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || code == android.view.KeyEvent.KEYCODE_SPACE) {
                                                        lastConfirmKeyDownAt = SystemClock.elapsedRealtime()
                                                    }
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
                                        onClick = { bounds ->
                                            if (editOrderKeys != null) {
                                                editOrderKeys?.let(::saveLayout)
                                                editOrderKeys = null
                                                editingKey = null
                                            } else if (dockPlacementKey == null) openEntry(app, bounds)
                                        },
                                        onLongClick = { bounds -> if (app.folder == null && editOrderKeys == null && dockPlacementKey == null) {
                                                suppressMenuOpeningRelease = SystemClock.elapsedRealtime() - lastConfirmKeyDownAt < 1500L
                                                menuClosing = false
                                                activeMenu = app
                                                activeMenuBounds = bounds
                                        } }
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

        widgets.forEach { widget ->
            val info = remember(widget.provider) {
                val component = ComponentName.unflattenFromString(widget.provider)
                widgetManager.installedProviders.firstOrNull { it.provider == component }
            } ?: return@forEach
            val widgetOffset = remember(widget.id) { mutableStateOf(Offset(widget.xDp.toFloat(), widget.yDp.toFloat())) }
            AndroidView(
                factory = { hostContext ->
                    widgetHost.createView(hostContext, widget.id, info).apply {
                        setAppWidget(widget.id, info)
                        updateAppWidgetSize(android.os.Bundle(), widget.widthDp, widget.heightDp, widget.widthDp, widget.heightDp)
                    }
                },
                modifier = Modifier.offset { IntOffset(with(density) { widgetOffset.value.x.dp.roundToPx() }, with(density) { widgetOffset.value.y.dp.roundToPx() }) }
                    .size(widget.widthDp.dp, widget.heightDp.dp)
                    .pointerInput(widget.id) {
                        detectDragGestures(onDragEnd = {
                            val moved = widget.copy(xDp = widgetOffset.value.x.roundToInt(), yDp = widgetOffset.value.y.roundToInt())
                            widgets = widgets.map { if (it.id == moved.id) moved else it }
                            AppHomeFeatures.saveWidgets(context, widgets)
                        }) { change, drag ->
                            change.consume()
                            widgetOffset.value = widgetOffset.value + Offset(drag.x / density.density, drag.y / density.density)
                        }
                    }
            )
        }

        activeMenu?.let { app ->
            AppContextMenu(
                app = app,
                anchor = activeMenuBounds ?: Rect.Zero,
                isInDock = dockApps.any { appKey(it) == appKey(app) },
                suppressOpeningConfirmRelease = suppressMenuOpeningRelease,
                wallpaperHaze = wallpaperHaze,
                staticBlur = staticBlur,
                onCloseStarted = {
                    // 关闭动画一开始就把焦点交还原图标：方向键立刻可用。
                    // （菜单 onExit 守卫在 closing 时放行，这里才收得回来。）
                    menuClosing = true
                    val idx = homeEntries.indexOfFirst { appKey(it) == appKey(app) }
                    val restored = when {
                        idx in 0..5 -> runCatching { dockRefs.getOrNull(idx)?.requestFocus() }.getOrNull() ?: false
                        idx >= 6 -> runCatching {
                            gridRefs.getOrNull((idx - 6) / columns)?.getOrNull((idx - 6) % columns)?.requestFocus()
                        }.getOrNull() ?: false
                        else -> false
                    }
                    // 立即恢复失败才走菜单销毁后的兜底恢复，避免用户在动画期间已移焦被抢回
                    if (!restored) menuReturnKey = appKey(app)
                },
                onDismiss = { activeMenu = null; activeMenuBounds = null; menuClosing = false },
                onAppInfo = {
                    activeMenu = null
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                },
                onCreateFolder = {
                    activeMenu = null
                    folderAnimation = app
                },
                onToggleLock = {
                    val isLocked = appKey(app) in AppHomeFeatures.locked(context)
                    if (isLocked && AppHomeFeatures.hasPin(context)) {
                        pendingPinApp = app; pinMode = "verify_unlock"
                    } else if (isLocked) {
                        AppHomeFeatures.setLocked(context, appKey(app), false); featureRevision++; activeMenu = null
                    } else if (!AppHomeFeatures.hasPin(context)) {
                        pendingPinApp = app; pinMode = "setup_lock"; activeMenu = null
                    } else {
                        AppHomeFeatures.setLocked(context, appKey(app), true); featureRevision++; activeMenu = null
                    }
                },
                onUninstall = {
                    activeMenu = null
                    val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
                        .putExtra(Intent.EXTRA_RETURN_RESULT, false)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(uninstallIntent) }
                        .recoverCatching {
                            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                },
                onWidgets = { activeMenu = null; widgetOwner = app },
                onHide = {
                    activeMenu = null
                    AppHomeFeatures.setHidden(context, appKey(app), true)
                    featureRevision++
                },
                onDesktopEdit = {
                    val keys = homeEntries.map(::appKey)
                    editOrderKeys = keys
                    editingKey = appKey(app)
                    activeMenu = null
                    expanded = true
                },
                onDockAction = {
                    val keys = homeEntries.map(::appKey)
                    val index = keys.indexOf(appKey(app))
                    if (index in 0..5) {
                        if (keys.size > 6) {
                            val moved = keys.toMutableList()
                            val firstGrid = moved[6]
                            moved[6] = moved[index]
                            moved[index] = firstGrid
                            saveLayout(moved)
                        }
                    } else if (index >= 6) {
                        dockPlacementKey = appKey(app)
                        expanded = true
                    }
                    activeMenu = null
                },
                onIconEdit = { activeMenu = null; iconEditApp = app },
                onShortcut = { shortcut ->
                    activeMenu = null
                    runCatching {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(app.packageName, shortcut.id, null, null, Process.myUserHandle())
                    }
                }
            )
        }

        iconEditApp?.let { app ->
            IconEditorDialog(
                app = app,
                currentName = AppHomeFeatures.customLabel(context, appKey(app)) ?: app.name,
                currentIcon = AppHomeFeatures.customIcon(context, appKey(app)),
                onDismiss = { iconEditApp = null },
                onChooseImage = { iconPicker.launch("image/*") },
                onSaveName = { name ->
                    AppHomeFeatures.saveCustomLabel(context, appKey(app), name.takeIf { it != app.name })
                    featureRevision++
                    iconEditApp = null
                },
                onReset = {
                    AppHomeFeatures.saveCustomLabel(context, appKey(app), null)
                    AppHomeFeatures.clearCustomIcon(context, appKey(app))
                    featureRevision++
                    iconEditApp = null
                }
            )
        }

        folderAnimation?.let { app ->
            FolderCreateAnimation(app, onFinished = {
                val folder = HomeFolder("folder-${System.currentTimeMillis()}", "新建文件夹", listOf(appKey(app)))
                AppHomeFeatures.saveFolder(context, folder)
                featureRevision++
                folderToOpen = folder
                folderAnimation = null
            }, onDismiss = { folderAnimation = null })
        }

        folderToOpen?.let { folder ->
            FolderContentsDialog(
                folder = folder,
                apps = apps,
                onDismiss = { folderToOpen = null },
                onAdd = { folderPicker = folder },
                onLaunch = { selected ->
                    if (appKey(selected) in AppHomeFeatures.locked(context)) {
                        pendingPinApp = selected; pendingLaunchBounds = null; pinMode = "verify_launch"
                    } else {
                        folderToOpen = null
                        AppHomeFeatures.recordLaunch(context, appKey(selected))
                        launchAppWithClipReveal(context, view, null, selected.launch)
                    }
                }
            )
        }
        folderPicker?.let { folder ->
            FolderAppPicker(
                folder = folder,
                apps = apps.filter { app -> appKey(app) !in folder.members && appKey(app) !in hiddenKeys },
                onDismiss = { folderPicker = null },
                onConfirm = { picked ->
                    val updated = folder.copy(members = (folder.members + picked).distinct())
                    AppHomeFeatures.saveFolder(context, updated)
                    folderToOpen = updated
                    folderPicker = null
                    featureRevision++
                }
            )
        }
        pinMode?.let { mode ->
            PinEntryDialog(mode = mode, onDismiss = { pinMode = null; pendingPinApp = null; pendingLaunchBounds = null }, onSubmit = { value ->
                val app = pendingPinApp
                when {
                    mode.startsWith("setup") -> {
                        if (mode.contains("confirm")) {
                            if (value == pinFirstEntry) {
                                AppHomeFeatures.setPin(context, value)
                                if (mode.contains("lock")) app?.let { AppHomeFeatures.setLocked(context, appKey(it), true) }
                                featureRevision++
                                pinMode = null
                                if (mode.contains("launch")) app?.let {
                                    AppHomeFeatures.recordLaunch(context, appKey(it))
                                    launchAppWithClipReveal(context, view, pendingLaunchBounds, it.launch)
                                }
                                pendingPinApp = null
                                pendingLaunchBounds = null
                            } else pinMode = "${mode}_mismatch"
                        } else {
                            pinFirstEntry = value
                            pinMode = if (mode.contains("lock")) "setup_lock_confirm" else "setup_launch_confirm"
                        }
                    }
                    mode.startsWith("verify_launch") && AppHomeFeatures.matchesPin(context, value) -> {
                        pinMode = null; pendingPinApp = null; app?.let {
                            AppHomeFeatures.recordLaunch(context, appKey(it))
                            launchAppWithClipReveal(context, view, pendingLaunchBounds, it.launch)
                        }
                    }
                    mode.startsWith("verify_unlock") && AppHomeFeatures.matchesPin(context, value) -> {
                        app?.let { AppHomeFeatures.setLocked(context, appKey(it), false) }
                        featureRevision++; pinMode = null; pendingPinApp = null; pendingLaunchBounds = null; activeMenu = null
                    }
                    else -> pinMode = if (mode.startsWith("verify_unlock")) "verify_unlock_error" else "verify_launch_error"
                }
            }) }
        widgetOwner?.let { owner ->
            val providers = remember(owner.packageName) { widgetManager.installedProviders.filter { it.provider.packageName == owner.packageName } }
            WidgetProviderDialog(owner.name, providers, onDismiss = { widgetOwner = null }, onChoose = { chosen ->
                chosenWidgetProvider = chosen
                widgetOwner = null
            })
        }
        chosenWidgetProvider?.let { provider ->
            WidgetSizeDialog(provider, onDismiss = { chosenWidgetProvider = null }, onChoose = { width, height ->
                addWidget(provider, width, height)
                chosenWidgetProvider = null
            })
        }
    }
}

@Composable
private fun GridAppItem(
    app: DockApp,
    iconScale: Float = 1f,
    editing: Boolean = false,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    forceHighlight: Boolean = false,
    onClick: (Rect) -> Unit,
    onLongClick: ((Rect) -> Unit)? = null
) {
    val visualApp = rememberVisualApp(app)
    var isFocused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = focusMotion(),
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
            uniformExpansionDp = if (editing) 18.dp else 10.dp,
            onHighlightChanged = { isFocused = it },
            forceHighlight = forceHighlight,
            onClick = {},
            onClickWithBounds = onClick,
            onLongClick = onLongClick
        ) {
            if (visualApp.folder != null) {
                FolderTile(visualApp.folder.members.size, Modifier.fillMaxSize())
            } else if (visualApp.banner != null) {
                Box(Modifier.fillMaxSize().background(Color(visualApp.bannerBgColor)))
                Image(visualApp.banner, visualApp.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (visualApp.customIcon != null) {
                Image(visualApp.customIcon, visualApp.name, Modifier.align(Alignment.Center).size(80.96.dp * iconScale), contentScale = ContentScale.Fit)
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

private fun appKey(app: DockApp) = "${app.packageName}/${app.activityName}"

@Composable
private fun FolderTile(count: Int, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        LineIcon("folder", Modifier.size(52.dp), Color.White)
        Text(count.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).background(Color(0xAA30343A), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun LineIcon(name: String, modifier: Modifier = Modifier.size(26.dp), tint: Color = Color.White) {
    Canvas(modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * sx, y1 * sy), Offset(x2 * sx, y2 * sy), 2f * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 2f) = drawRoundRect(tint, Offset(l * sx, t * sy), androidx.compose.ui.geometry.Size((r-l)*sx, (b-t)*sy), androidx.compose.ui.geometry.CornerRadius(radius*sx), style = Stroke(1.8f*sx))
        fun circle(x: Float, y: Float, radius: Float, stroke: Boolean = true) = drawCircle(tint, radius*sx, Offset(x*sx,y*sy), style = if (stroke) Stroke(1.8f*sx) else androidx.compose.ui.graphics.drawscope.Fill)
        when (name) {
            "info" -> { circle(12f, 12f, 9f); line(12f, 10f, 12f, 17f); circle(12f, 7f, 1f, false) }
            "folder" -> { drawPath(Path().apply { moveTo(3*sx,7*sy); lineTo(3*sx,5*sy); quadraticTo(3*sx,4*sy,4*sx,4*sy); lineTo(9*sx,4*sy); lineTo(11*sx,6*sy); lineTo(20*sx,6*sy); quadraticTo(21*sx,6*sy,21*sx,7*sy); lineTo(21*sx,19*sy); quadraticTo(21*sx,20*sy,20*sx,20*sy); lineTo(4*sx,20*sy); quadraticTo(3*sx,20*sy,3*sx,19*sy); close() }, tint, style = Stroke(1.8f*sx)) }
            "lock" -> { rect(5f,10f,19f,21f); drawArc(tint, 180f, 180f, false, Offset(7*sx,2*sx), androidx.compose.ui.geometry.Size(10*sx,14*sy), style=Stroke(1.8f*sx)); line(12f,14f,12f,17f) }
            "unlock" -> { rect(5f,10f,19f,21f); drawArc(tint, 200f, 150f, false, Offset(8*sx,2*sx), androidx.compose.ui.geometry.Size(10*sx,14*sy), style=Stroke(1.8f*sx)); line(12f,14f,12f,17f) }
            "trash" -> { line(4f,7f,20f,7f); rect(6f,8f,18f,21f); line(9f,4f,15f,4f); line(10f,11f,10f,18f); line(14f,11f,14f,18f) }
            "widgets" -> { rect(3f,3f,10f,10f,1f); rect(14f,3f,21f,10f,1f); rect(3f,14f,10f,21f,1f); rect(14f,14f,21f,21f,1f) }
            "share" -> { circle(18f,5f,2f,false); circle(6f,12f,2f,false); circle(18f,19f,2f,false); line(8f,11f,16f,6f); line(8f,13f,16f,18f) }
            "hide" -> { drawPath(Path().apply { moveTo(2*sx,12*sy); quadraticTo(12*sx,1*sy,22*sx,12*sy); quadraticTo(12*sx,23*sy,2*sx,12*sy); close() }, tint, style=Stroke(1.8f*sx)); circle(12f,12f,3f); line(4f,20f,20f,4f) }
            "plus" -> { line(12f,4f,12f,20f); line(4f,12f,20f,12f) }
            "search" -> { circle(10f,10f,6f); line(14.5f,14.5f,21f,21f) }
            "pin" -> { circle(12f,12f,8f); line(12f,8f,12f,13f); line(12f,16f,12f,16.1f) }
            "edit" -> { line(4f,20f,8f,19f); line(4f,20f,5f,16f); line(5f,16f,16f,5f); line(14f,7f,17f,10f); line(18f,4f,20f,6f) }
            "image" -> { rect(3f,4f,21f,20f,2f); circle(8f,9f,2f); line(5f,17f,10f,12f); line(10f,12f,14f,16f); line(14f,16f,17f,13f); line(17f,13f,20f,17f) }
            "dock" -> { rect(3f,7f,21f,17f,3f); line(8f,10f,8f,14f); line(12f,10f,12f,14f); line(16f,10f,16f,14f) }
            "grid" -> { (0..2).forEach { row -> (0..2).forEach { col -> rect(3f+col*6f,3f+row*6f,7f+col*6f,7f+row*6f,1f) } } }
            else -> { circle(12f,12f,8f); line(8f,12f,16f,12f) }
        }
    }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun AppContextMenu(
    app: DockApp,
    anchor: Rect,
    isInDock: Boolean,
    suppressOpeningConfirmRelease: Boolean,
    wallpaperHaze: HazeState,
    staticBlur: ImageBitmap?,
    onCloseStarted: () -> Unit,
    onDismiss: () -> Unit,
    onAppInfo: () -> Unit,
    onCreateFolder: () -> Unit,
    onToggleLock: () -> Unit,
    onUninstall: () -> Unit,
    onWidgets: () -> Unit,
    onHide: () -> Unit,
    onDesktopEdit: () -> Unit,
    onDockAction: () -> Unit,
    onIconEdit: () -> Unit,
    onShortcut: (ShortcutInfo) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var openingReleaseConsumed by remember { mutableStateOf(!suppressOpeningConfirmRelease) }
    val firstMenuAction = remember { FocusRequester() }
    // Give D-pad/keyboard focus to the menu immediately. The opening long-press release is
    // consumed by the menu's key handler below, so it cannot activate this first item.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        firstMenuAction.requestFocus()
    }
    val shortcuts by produceState(emptyList<ShortcutInfo>(), app.packageName) {
        value = if (Build.VERSION.SDK_INT >= 25) runCatching {
            val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().setPackage(app.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            launcher.getShortcuts(query, Process.myUserHandle()).orEmpty().distinctBy { it.id }
        }.getOrDefault(emptyList()) else emptyList()
    }
    val providers = remember(app.packageName) {
        AppWidgetManager.getInstance(context).installedProviders.filter { it.provider.packageName == app.packageName }
    }
    val isLocked = appKey(app) in AppHomeFeatures.locked(context)
    var scaleTarget by remember { mutableFloatStateOf(.18f) }
    var closing by remember { mutableStateOf(false) }
    val menuMotion: AnimationSpec<Float> = overlayMotion()
    val scale by animateFloatAsState(scaleTarget, menuMotion, label = "app-menu-scale")
    val alpha by animateFloatAsState(if (scaleTarget > .7f) 1f else 0f, menuMotion, label = "app-menu-alpha")
    LaunchedEffect(Unit) {
        // Let the menu report its real bounds before starting so the external pivot below
        // stays fixed at the pressed icon throughout the entire animation.
        withFrameNanos { }
        scaleTarget = 1f
    }
    val requestDismiss = {
        if (!closing) {
            closing = true
            scaleTarget = .18f
            // 淡出动画开始时立刻把焦点还给原应用图标：菜单项销毁时焦点已就位，
            // 否则焦点会 fallback 到桌面其他节点，看起来像整个页面跳了一下
            onCloseStarted()
        }
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(OVERLAY_TRANSITION_MS.toLong())
            onDismiss()
        }
    }
    // One focus pill shared by every entry in the menu. Items only report their layout
    // bounds; the pill below glides between them instead of each item fading in place.
    var focusedItemKey by remember { mutableStateOf<String?>(null) }
    var itemBounds by remember { mutableStateOf(emptyMap<String, Rect>()) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    val targetRect = focusedItemKey
        ?.let { key -> itemBounds[key]?.translate(-contentOrigin.x, -contentOrigin.y) }
        ?.takeIf { it.width > 0f }
    val previousTarget = remember { mutableStateOf<Rect?>(null) }
    // First appearance snaps under the already-focused item; later moves glide in 500ms.
    val slideSpec: AnimationSpec<Rect> = if (previousTarget.value == null) snap()
    else tween(SELECTION_TRANSITION_MS, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))
    val highlightRect by animateValueAsState(
        targetValue = targetRect ?: Rect.Zero,
        typeConverter = Rect.VectorConverter,
        animationSpec = slideSpec,
        label = "menu-focus-slide",
    )
    SideEffect { previousTarget.value = targetRect }
    val onMenuFocus = { key: String -> focusedItemKey = key }
    val registerBounds = { key: String, rect: Rect -> itemBounds = itemBounds + (key to rect) }
    val positionProvider = remember(anchor, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val margin = with(density) { 12.dp.roundToPx() }
                val gap = with(density) { 12.dp.roundToPx() }
                val left = anchor.left.roundToInt()
                val top = anchor.top.roundToInt()
                val right = anchor.right.roundToInt()
                val bottom = anchor.bottom.roundToInt()
                val centeredX = (left + right - popupContentSize.width) / 2
                val x = centeredX.coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val below = bottom + gap
                val above = top - popupContentSize.height - gap
                val y = when {
                    below + popupContentSize.height <= windowSize.height - margin -> below
                    above >= margin -> above
                    else -> (top + (bottom - top - popupContentSize.height) / 2).coerceIn(margin, (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
                }
                return IntOffset(x, y)
            }
        }
    }
    BackHandler(onBack = requestDismiss)
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(1000f)) {
        val menuWidth = minOf(296.dp, (maxWidth - 24.dp).coerceAtLeast(248.dp))
        var measuredMenuSize by remember { mutableStateOf(IntSize.Zero) }
        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 12.dp.roundToPx() }
        val widthPx = with(density) { menuWidth.roundToPx() }
        val menuHeightPx = measuredMenuSize.height.takeIf { it > 0 } ?: with(density) { 500.dp.roundToPx() }
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight
        val x = ((anchor.left + anchor.right - widthPx) / 2f).roundToInt()
            .coerceIn(margin, (maxWidthPx - widthPx - margin).coerceAtLeast(margin))
        val below = anchor.bottom.roundToInt() + gap
        val above = anchor.top.roundToInt() - menuHeightPx - gap
        val y = when {
            below + menuHeightPx <= maxHeightPx - margin -> below
            above >= margin -> above
            else -> (anchor.center.y.roundToInt() - menuHeightPx / 2).coerceIn(margin, (maxHeightPx - menuHeightPx - margin).coerceAtLeast(margin))
        }
        // Fractions intentionally may sit outside 0..1: when the menu is placed above or
        // below its app, the scale pivot is the app icon itself, not an edge of the menu.
        val iconPivotX = (anchor.center.x - x) / widthPx.toFloat().coerceAtLeast(1f)
        val iconPivotY = (anchor.center.y - y) / menuHeightPx.toFloat().coerceAtLeast(1f)
        // Sample the wallpaper instead of assuming every backdrop has the same brightness.
        // Bright images receive a stronger dark scrim; already-dark images retain more glass.
        val backdropLuminance = remember(staticBlur) {
            staticBlur?.asAndroidBitmap()?.let(::sampledBitmapLuminance) ?: .55f
        }
        val adaptiveScrimAlpha = remember(backdropLuminance) {
            when {
                backdropLuminance >= .65f -> .76f
                backdropLuminance >= .42f -> .66f
                backdropLuminance >= .22f -> .54f
                else -> .42f
            }
        }
        Box(Modifier.fillMaxSize().focusProperties { canFocus = false }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = requestDismiss))
        Column(
            Modifier.offset { IntOffset(x, y) }.width(menuWidth).heightIn(max = 620.dp)
                .onSizeChanged { measuredMenuSize = it }
                // A focusGroup improves traversal order but does not trap focus by itself.
                // Keep directional navigation inside the modal menu until it is dismissed;
                // once closing starts, release the guard so the focus handoff back to the
                // launching icon succeeds right away instead of waiting for removal.
                .focusProperties { onExit = { if (!closing) cancelFocusChange() } }
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    val keyCode = event.nativeKeyEvent.keyCode
                    val isConfirmRelease = event.type == KeyEventType.KeyUp &&
                        (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                            keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == android.view.KeyEvent.KEYCODE_SPACE)
                    val isConfirm = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                        keyCode == android.view.KeyEvent.KEYCODE_SPACE
                    if (!openingReleaseConsumed && isConfirm) {
                        // The long-press key is still physically held when this menu gains
                        // focus. Swallow its repeats and its first release so the focused
                        // item cannot be activated by the gesture that merely opened us.
                        if (isConfirmRelease) openingReleaseConsumed = true
                        true
                    } else false
                }
                .graphicsLayer {
                scaleX = scale; scaleY = scale; this.alpha = alpha
                transformOrigin = TransformOrigin(iconPivotX, iconPivotY)
            }
                .clip(RoundedCornerShape(32.dp))
                .drawBehind {
                    if (Build.VERSION.SDK_INT >= 31 && staticBlur != null) {
                        // Reuse the already blurred wallpaper at its real screen position,
                        // clipped to this compact card. This works even when Haze's source
                        // was skipped by the launcher's low-cost cached-wallpaper mode.
                        drawCoverWallpaper(staticBlur, IntSize(maxWidthPx, maxHeightPx), IntOffset(-x, -y))
                        drawRect(Color.Black.copy(alpha = adaptiveScrimAlpha))
                    } else {
                        drawRect(Color.Black.copy(alpha = .82f))
                    }
                }
                .then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeEffect(wallpaperHaze) {
                    blurRadius = 30.dp
                    noiseFactor = 0f
                    backgroundColor = Color.Transparent
                    // Haze can reintroduce bright pixels after drawBehind, so keep an adaptive
                    // dark tint in the final glass pass as a contrast guarantee.
                    tints = listOf(HazeTint(Color.Black.copy(alpha = .24f + backdropLuminance * .30f)))
                    inputScale = HazeInputScale.Fixed(.35f)
                } else Modifier)
                .border(1.dp, Color.White.copy(alpha = if (backdropLuminance > .55f) .44f else .30f), RoundedCornerShape(32.dp))
                // Compact, equal inset on all four sides.
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
                .onGloballyPositioned { contentOrigin = it.positionInRoot() }
                .drawBehind {
                    // The sliding focus pill, drawn under the items in scroll-content
                    // coordinates so it moves with the list while it glides between entries.
                    if (targetRect != null) drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = .92f), Color(0xFFD0D0D0).copy(alpha = .92f)),
                            start = Offset(highlightRect.left, highlightRect.top),
                            end = Offset(highlightRect.right, highlightRect.bottom),
                        ),
                        topLeft = Offset(highlightRect.left, highlightRect.top),
                        size = highlightRect.size,
                        cornerRadius = CornerRadius(19.dp.toPx()),
                    )
                },
            // Keep the three shortcut controls at their existing size; tighten only the
            // action list below so the popup stays compact on TV-sized layouts.
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
                Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                    MenuTopButton("应用信息", "info", onAppInfo, Modifier.weight(1f), firstMenuAction, "top-info", onMenuFocus, registerBounds)
                    MenuTopButton("创建文件夹", "folder", onCreateFolder, Modifier.weight(1f), menuKey = "top-folder", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                    MenuTopButton(if (isLocked) "取消访问锁" else "访问锁", if (isLocked) "unlock" else "lock", onToggleLock, Modifier.weight(1f), menuKey = "top-lock", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color.White.copy(alpha = .20f)))
                shortcuts.take(4).forEach { shortcut ->
                    val drawable = remember(shortcut.id) {
                        runCatching { (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi) }.getOrNull()
                    }
                    MenuRow(shortcut.shortLabel?.toString()?.ifBlank { null } ?: shortcut.longLabel?.toString()?.ifBlank { null } ?: shortcut.id,
                        onClick = { onShortcut(shortcut) }, iconContent = { if (drawable != null) DrawableIcon(drawable) else LineIcon("grid") },
                        menuKey = "shortcut-${shortcut.id}", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                }
                MenuRow("卸载应用", onUninstall, "trash", menuKey = "uninstall", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                if (providers.isNotEmpty()) MenuRow("小组件", onWidgets, "widgets", menuKey = "widgets-row", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                MenuRow("桌面编辑", onDesktopEdit, "edit", menuKey = "desktop-edit", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                MenuRow(if (isInDock) "移出 Dock" else "添加到 Dock", onDockAction, "dock", menuKey = "dock-toggle", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                MenuRow("图标编辑", onIconEdit, "image", menuKey = "icon-edit", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
                MenuRow("隐藏应用", onHide, "hide", menuKey = "hide", onMenuFocus = onMenuFocus, registerBounds = registerBounds)
        }
    }
}

/** Cheap 8x8 luminance sample; recomputed only when the cached wallpaper bitmap changes. */
private fun sampledBitmapLuminance(bitmap: Bitmap): Float {
    if (bitmap.width <= 0 || bitmap.height <= 0) return .5f
    var total = 0.0
    var count = 0
    for (row in 0 until 8) {
        val y = ((row + .5f) * bitmap.height / 8f).toInt().coerceIn(0, bitmap.height - 1)
        for (column in 0 until 8) {
            val x = ((column + .5f) * bitmap.width / 8f).toInt().coerceIn(0, bitmap.width - 1)
            val color = bitmap.getPixel(x, y)
            fun linear(channel: Int): Double {
                val value = channel / 255.0
                return if (value <= .04045) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
            }
            total += .2126 * linear(android.graphics.Color.red(color)) +
                .7152 * linear(android.graphics.Color.green(color)) +
                .0722 * linear(android.graphics.Color.blue(color))
            count++
        }
    }
    return (total / count.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}

@Composable
private fun MenuTopButton(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, focusRequester: FocusRequester? = null, menuKey: String, onMenuFocus: (String) -> Unit, registerBounds: (String, Rect) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(17.dp)
    // The focus background itself is the shared sliding pill drawn by the menu container;
    // items only animate their content color as the pill arrives and leaves.
    val focusReveal by animateFloatAsState(if (focused || hovered) 1f else 0f,
        focusMotion(),
        label = "menu-top-focus-$label")
    val foreground = lerp(Color.White, Color(0xFF17191D), focusReveal)
    Column(modifier.padding(2.dp).fillMaxHeight().clip(shape)
        .onGloballyPositioned { coords -> registerBounds(menuKey, coords.boundsInRoot()) }
        .semantics { contentDescription = label }
        .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) onMenuFocus(menuKey)
        }
        .hoverable(interaction)
        .focusable()
        .activateMenuItem(onClick)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LineIcon(icon, Modifier.size(27.dp), foreground)
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, icon: String? = null, iconContent: (@Composable () -> Unit)? = null, menuKey: String? = null, onMenuFocus: ((String) -> Unit)? = null, registerBounds: ((String, Rect) -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(19.dp)
    val sliding = menuKey != null
    // Sliding mode: the shared pill carries the highlight, items only flip content color.
    // Legacy mode (widget dialogs): keep the original in-place fade + scale.
    val focusSpec: AnimationSpec<Float> = focusMotion()
    val focusReveal by animateFloatAsState(if (focused || hovered) 1f else 0f, focusSpec, label = "menu-row-focus-$label")
    val foreground = lerp(Color.White, Color(0xFF17191D), focusReveal)
    Row(Modifier.fillMaxWidth().padding(2.dp).heightIn(min = 50.dp)
        .clip(shape)
        .then(if (sliding) Modifier.onGloballyPositioned { coords -> registerBounds?.invoke(menuKey!!, coords.boundsInRoot()) } else Modifier)
        .then(if (sliding) Modifier else Modifier.background(Brush.linearGradient(listOf(
            Color.White.copy(alpha = .92f * focusReveal),
            Color(0xFFD0D0D0).copy(alpha = .92f * focusReveal)
        ))))
        .then(if (sliding) Modifier else Modifier.graphicsLayer {
            scaleX = .96f + .04f * focusReveal
            scaleY = .96f + .04f * focusReveal
        })
        .onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) menuKey?.let { key -> onMenuFocus?.invoke(key) }
        }.hoverable(interaction).focusable().activateMenuItem(onClick)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = foreground, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (iconContent != null) iconContent() else LineIcon(icon ?: "grid", Modifier.size(25.dp), foreground)
    }
}

private fun Modifier.activateMenuItem(onClick: () -> Unit) = onPreviewKeyEvent { event ->
    val code = event.nativeKeyEvent.keyCode
    val confirm = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER ||
        code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || code == android.view.KeyEvent.KEYCODE_SPACE
    if (event.type == KeyEventType.KeyUp && confirm) {
        onClick()
        true
    } else false
}

private val DialogPanel = Color(0xF51B2028)
private val DialogCard = Color(0xFF292F39)
private val DialogAccent = Color(0xFF8ED7FF)

@Composable
private fun HomeDialogSurface(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier.widthIn(min = 420.dp, max = 600.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.attributes = window?.attributes?.also { it.dimAmount = .38f }
            if (Build.VERSION.SDK_INT >= 31) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window?.setBackgroundBlurRadius(42)
                window?.attributes = window?.attributes?.also { it.blurBehindRadius = 42 }
            }
            onDispose {
                if (Build.VERSION.SDK_INT >= 31) window?.setBackgroundBlurRadius(0)
            }
        }
        Column(
            modifier.clip(RoundedCornerShape(24.dp))
                .background(DialogPanel)
                .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = .68f), fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun HomeDialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = enabled && (hovered || focused)
    val reveal by animateFloatAsState(if (active) 1f else 0f, focusMotion(), label = "dialog-$label")
    val bg = if (primary) lerp(Color(0xFF2A6687), DialogAccent, reveal) else lerp(DialogCard, Color.White, reveal)
    val fg = if (reveal > .52f) Color(0xFF10151B) else Color.White.copy(alpha = if (enabled) 1f else .38f)
    Row(
        modifier.height(if (compact) 38.dp else 46.dp)
            .clip(RoundedCornerShape(if (compact) 13.dp else 15.dp))
            .background(bg.copy(alpha = if (enabled) 1f else .45f))
            .border(1.dp, Color.White.copy(alpha = .12f + reveal * .30f), RoundedCornerShape(if (compact) 13.dp else 15.dp))
            .hoverable(interaction, enabled)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) LineIcon(icon, Modifier.size(if (compact) 18.dp else 20.dp), fg)
        Text(label, color = fg, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconEditorDialog(
    app: DockApp,
    currentName: String,
    currentIcon: ImageBitmap?,
    onDismiss: () -> Unit,
    onChooseImage: () -> Unit,
    onSaveName: (String) -> Unit,
    onReset: () -> Unit
) {
    var name by remember(app.packageName, app.activityName) { mutableStateOf(currentName) }
    HomeDialogSurface("图标与名称", "自定义桌面显示效果", onDismiss, Modifier.width(560.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(DialogCard)
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    when {
                        currentIcon != null -> Image(currentIcon, app.name, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
                        app.icon != null -> DockAppArtwork(app.icon, app.name, Modifier.size(60.dp))
                        else -> LineIcon("image", Modifier.size(36.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(app.name, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HomeDialogAction("从相册选择图标", onClick = onChooseImage, icon = "image")
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("桌面显示名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HomeDialogAction("恢复默认", onClick = onReset)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeDialogAction("取消", onClick = onDismiss)
                    HomeDialogAction("保存", enabled = name.isNotBlank(), primary = true, onClick = { if (name.isNotBlank()) onSaveName(name.trim()) })
                }
            }
    }
}

@Composable
private fun DrawableIcon(drawable: Drawable, modifier: Modifier = Modifier.size(27.dp)) {
    val bitmap = remember(drawable) {
        runCatching {
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { b ->
                val canvas = android.graphics.Canvas(b)
                drawable.setBounds(0, 0, 96, 96); drawable.draw(canvas)
            }.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, null, modifier) else LineIcon("grid", modifier)
}

@Composable
private fun FolderCreateAnimation(app: DockApp, onFinished: () -> Unit, onDismiss: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, overlayMotion()); onFinished() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.attributes = window?.attributes?.also { it.dimAmount = .38f }
            if (Build.VERSION.SDK_INT >= 31) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window?.setBackgroundBlurRadius(42)
                window?.attributes = window?.attributes?.also { it.blurBehindRadius = 42 }
            }
            onDispose { if (Build.VERSION.SDK_INT >= 31) window?.setBackgroundBlurRadius(0) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .32f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(330.dp).graphicsLayer { scaleX = .78f + progress.value * .22f; scaleY = .78f + progress.value * .22f }
                .clip(RoundedCornerShape(42.dp)).background(Color.White.copy(alpha = .14f)).border(2.dp, Color.White.copy(alpha = progress.value), RoundedCornerShape(42.dp)), contentAlignment = Alignment.Center) {
                LineIcon("folder", Modifier.size(126.dp), Color.White.copy(alpha = progress.value))
                app.icon?.let { DockAppArtwork(it, app.name, Modifier.size(68.dp).graphicsLayer { scaleX = 1f - .40f*progress.value; scaleY = 1f - .40f*progress.value }) }
            }
        }
    }
}

@Composable
private fun PinEntryDialog(mode: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val context = LocalContext.current
    var value by remember(mode) { mutableStateOf("") }
    var submitted by remember(mode) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val sequenceScroll = rememberScrollState()
    val isSetup = mode.startsWith("setup")
    val expectedLength = if (isSetup) 0 else AppHomeFeatures.pinLength(context)
    fun append(direction: String) {
        if (isSetup || expectedLength <= 0 || value.length < expectedLength) value += direction
    }
    fun submit() { if (value.length >= 4) onSubmit(value) }
    val title = when {
        mode.contains("mismatch") -> "两次方向密码不一致"
        mode.startsWith("setup") && mode.contains("confirm") -> "再次输入方向密码"
        mode.startsWith("setup") -> "设置方向密码"
        mode.startsWith("verify_unlock") -> if (mode.contains("error")) "方向密码不正确，请重试" else "输入方向密码解除访问锁"
        mode.contains("error") -> "方向密码不正确，请重试"
        else -> "输入方向密码后继续"
    }
    BackHandler {
        if (value.isNotEmpty()) value = value.dropLast(1) else onDismiss()
    }
    LaunchedEffect(Unit) { withFrameNanos { }; focus.requestFocus() }
    LaunchedEffect(value.length) { sequenceScroll.animateScrollTo(sequenceScroll.maxValue) }
    LaunchedEffect(value, expectedLength, isSetup) {
        if (!isSetup && !submitted && expectedLength >= 4 && value.length == expectedLength) {
            submitted = true
            onSubmit(value)
        }
    }
    HomeDialogSurface(
        title = title,
        subtitle = if (isSetup) "依次按方向键，至少 4 步；按 OK 保存" else "依次输入方向密码，正确后自动解锁",
        onDismiss = onDismiss,
        modifier = Modifier.width(470.dp).focusRequester(focus).focusable().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionUp -> { append("U"); true }
                Key.DirectionDown -> { append("D"); true }
                Key.DirectionLeft -> { append("L"); true }
                Key.DirectionRight -> { append("R"); true }
                Key.Enter -> { if (isSetup) submit(); true }
                else -> false
            }
        }
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).horizontalScroll(sequenceScroll), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (value.isEmpty()) Text("等待输入…", color = Color.White.copy(alpha = .38f), fontSize = 17.sp)
            value.forEach { _ ->
                Box(Modifier.padding(horizontal = 4.dp).size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(DialogAccent.copy(alpha = .15f)).border(1.dp, DialogAccent.copy(alpha = .65f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("●", color = DialogAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("已输入 ${value.length} 步", color = Color.White.copy(alpha = .58f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            HomeDialogAction("清空", onClick = { value = "" }, enabled = value.isNotEmpty())
            HomeDialogAction("删除一步", onClick = { value = value.dropLast(1) }, enabled = value.isNotEmpty())
            if (isSetup) HomeDialogAction("保存", onClick = ::submit, primary = true, enabled = value.length >= 4)
        }
    }
}

@Composable
private fun FolderContentsDialog(folder: HomeFolder, apps: List<DockApp>, onDismiss: () -> Unit, onAdd: () -> Unit, onLaunch: (DockApp) -> Unit) {
    val members = remember(folder, apps) { folder.members.mapNotNull { key -> apps.firstOrNull { appKey(it) == key } } }
    HomeDialogSurface(folder.name, "${members.size} 个应用", onDismiss, Modifier.fillMaxWidth(.72f).fillMaxHeight(.72f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HomeDialogAction("添加应用", onClick = onAdd, icon = "plus", primary = true)
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(170.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(members.size) { index ->
                    val app = rememberVisualApp(members[index])
                    val interaction = remember { MutableInteractionSource() }
                    val hovered by interaction.collectIsHoveredAsState()
                    var focused by remember { mutableStateOf(false) }
                    val reveal by animateFloatAsState(if (hovered || focused) 1f else 0f, focusMotion(), label = "folder-app")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(lerp(DialogCard, Color.White, reveal).copy(alpha = .18f + reveal * .72f))
                        .border(1.dp, Color.White.copy(alpha = .10f + reveal * .25f), RoundedCornerShape(24.dp))
                        .hoverable(interaction).onFocusChanged { focused = it.isFocused }.focusable()
                        .clickable(interactionSource = interaction, indication = null) { onLaunch(app) }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                            app.icon?.let { DockAppArtwork(it, app.name, Modifier.size(64.dp)) } ?: LineIcon("grid", Modifier.size(42.dp))
                        }
                        Text(app.name, color = if (reveal > .55f) Color(0xFF11151A) else Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
    }
}

@Composable
private fun FolderAppPicker(folder: HomeFolder, apps: List<DockApp>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val staged = remember(folder.id) { mutableStateListOf<String>() }
    HomeDialogSurface("添加到文件夹", "选择一个或多个应用", onDismiss, Modifier.fillMaxWidth(.82f).fillMaxHeight(.78f)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Column(Modifier.weight(1.35f).fillMaxHeight()) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(apps, key = { appKey(it) }) { app ->
                        val selected = appKey(app) in staged
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) DialogAccent.copy(alpha = .22f) else if (hovered) Color.White.copy(alpha = .14f) else Color.Transparent)
                            .hoverable(interaction).clickable(interactionSource = interaction, indication = null) { val key = appKey(app); if (key in staged) staged.remove(key) else staged.add(key) }
                            .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                            app.icon?.let { DockAppArtwork(it, app.name, Modifier.size(44.dp)) } ?: LineIcon("grid", Modifier.size(44.dp))
                            Text(app.name, color = Color.White, fontSize = 19.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            if (selected) LineIcon("pin", Modifier.size(22.dp))
                        }
                    }
                }
            }
            Column(Modifier.width(260.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已选择 · ${staged.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    staged.takeLast(4).forEachIndexed { index, key ->
                        val app = apps.firstOrNull { appKey(it) == key }
                        Box(Modifier.offset(x = (index * 10).dp, y = (-index * 12).dp).size(82.dp).clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF343943)).border(1.dp, Color.White.copy(alpha = .32f), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                            app?.icon?.let { DockAppArtwork(it, app.name, Modifier.size(54.dp)) } ?: LineIcon("grid", Modifier.size(40.dp))
                        }
                    }
                    if (staged.isEmpty()) Text("点击左侧应用暂存", color = Color.White.copy(alpha = .65f), fontSize = 17.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeDialogAction("取消", onClick = onDismiss)
                    HomeDialogAction("确认添加", onClick = { onConfirm(staged.toList()) }, primary = true, enabled = staged.isNotEmpty())
                }
            }
        }
    }
}

@Composable
private fun WidgetProviderDialog(title: String, providers: List<AppWidgetProviderInfo>, onDismiss: () -> Unit, onChoose: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    HomeDialogSurface("选择小组件", title, onDismiss, Modifier.width(540.dp).heightIn(max = 560.dp)) {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(providers, key = { it.provider.flattenToString() }) { provider ->
                    val label = runCatching { provider.loadLabel(context.packageManager) }.getOrDefault(provider.provider.className)
                    MenuRow(label, { onChoose(provider) }, "widgets")
                }
            }
    }
}

@Composable
private fun WidgetSizeDialog(provider: AppWidgetProviderInfo, onDismiss: () -> Unit, onChoose: (Int, Int) -> Unit) {
    HomeDialogSurface("选择尺寸", "稍后仍可在桌面编辑中调整", onDismiss, Modifier.width(500.dp)) {
            listOf(180 to 120, 300 to 180, 420 to 260).forEachIndexed { index, size ->
                MenuRow(listOf("小 · 1 × 1", "中 · 2 × 1", "大 · 2 × 2")[index], { onChoose(size.first.coerceAtLeast(provider.minWidth), size.second.coerceAtLeast(provider.minHeight)) }, "widgets")
            }
    }
}

/** Brand gradient: preserve HSL hue/saturation and vary lightness by 5%. */
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
            fade.animateTo(1f, tween(SELECTION_TRANSITION_MS))
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
