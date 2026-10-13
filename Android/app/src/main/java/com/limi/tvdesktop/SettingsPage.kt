package com.limi.tvdesktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

internal enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: String,
    val gradientColors: List<Color>
) {
    DESKTOP(
        "桌面与外观",
        "壁纸、布局、图标与个性化",
        "▣",
        listOf(Color(0xFF3875F6), Color(0xFF634AF0))
    ),
    STARTUP(
        "启动与行为",
        "默认页、屏保与开机行为",
        "↗",
        listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
    ),
    APPLICATIONS("应用与文件夹", "排列、隐藏、网格与文件夹行为", "▦",
        listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))),
    PERFORMANCE("性能与动画", "低性能模式、启动动画与模糊效果", "ϟ",
        listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
    PLAYER(
        "播放器设置",
        "解码器、字幕、音轨与倍速",
        "▶",
        listOf(Color(0xFFF7653A), Color(0xFFE53456))
    ),
    LIBRARY(
        "媒体库设置",
        "数据源、扫描与内容分类",
        "❖",
        listOf(Color(0xFF22C55E), Color(0xFF15803D))
    ),
    PLUGINS(
        "插件",
        "内核、界面扩展与能力权限",
        "⬡",
        listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
    ),
    GENERAL(
        "系统与关于",
        "默认桌面、更新、版本与诊断",
        "⚙",
        listOf(Color(0xFF64748B), Color(0xFF475569))
    );

    companion object {
        val all = SettingsCategory.entries.filter { it != PLUGINS }
    }
}

private fun itemsFor(category: SettingsCategory, context: Context): List<Pair<String, String?>> = when (category) {
    SettingsCategory.DESKTOP -> listOf(
        "玻璃与模糊" to "导航条、Dock 与应用列表的模糊度和不透明度",
        "壁纸" to "自定义壁纸来源与预览",
        "图标大小" to DesktopPreferences.IconScale.current(context).label,
        "时钟样式" to DesktopPreferences.ClockStyle.current(context).label,
        "时间格式" to DesktopPreferences.TimeFormat.current(context).label,
        "日期显示" to DesktopPreferences.DateDisplay.current(context).label,
        "时间显示秒数" to if (DesktopPreferences.ClockDisplay.showSeconds(context)) "已开启" else "已关闭",
        "Dock 布局" to DesktopPreferences.DockStyle.current(context).label,
        "应用圆角" to DesktopPreferences.AppCornerStyle.current(context).label,
    )
    SettingsCategory.APPLICATIONS -> listOf(
        "应用列表背景模糊" to if (DesktopPreferences.GlassTuning.appListBlurEnabled(context)) "已开启" else "已关闭",
        "网格密度" to DesktopPreferences.GridDensity.current(context).label,
        "文件夹页码样式" to DesktopPreferences.FolderPageIndicator.current(context).label,
        "启动文件夹内应用后" to DesktopPreferences.FolderLaunchBehavior.current(context).label,
        "应用排列" to DesktopPreferences.AppSort.current(context).label,
        "隐藏应用管理" to "恢复从桌面隐藏的应用"
    )
    SettingsCategory.STARTUP -> listOf(
        "启动默认页" to DesktopPreferences.StartupPage.current(context).label,
        "屏幕保护" to DesktopPreferences.ScreenSaverTimeout.current(context).label,
        "开机自启动" to if (DesktopPreferences.AutoStart.isEnabled(context)) "已开启" else "已关闭",
        "强制横屏" to if (DesktopPreferences.ForceLandscape.isEnabled(context)) "已开启 · 手机安装时始终保持横屏" else "已关闭 · 跟随系统方向",
    )
    SettingsCategory.PLAYER -> listOf(
        "默认播放器" to DesktopPreferences.PlaybackEngine.current(context).label,
        "硬件加速解码" to "优先开启",
        "字幕默认样式" to "白色无阴影",
        "音轨优先" to "国语 / 原声",
        "默认播放倍速" to "1.0x"
    )
    SettingsCategory.LIBRARY -> listOf(
        "媒体数据源与账号管理" to "${AccountManager.accounts.size} 个服务器已配置",
        "媒体库展现模式" to AccountManager.displayMode.value.displayName,
        "海报画质与加载" to AccountManager.posterQuality.value.displayName,
        "未绑定时体验演示" to if (AccountManager.showDemoWhenEmpty.value) "已开启" else "已隐藏"
    )
    SettingsCategory.PLUGINS -> listOf(
        "插件管理" to "安装、启停、更新与插件专属设置",
        "插件仓库" to "官方仓库与第三方仓库",
        "安全与权限" to "敏感能力授权、签名与安全模式"
    )
    SettingsCategory.PERFORMANCE -> listOf(
        "低性能模式" to RenderPerformance.motionMode.label,
        "启动动画" to LaunchAnim.current(context).label,
        "模糊效果" to RenderPerformance.blurMode.label,
    )
    SettingsCategory.GENERAL -> listOf(
        "设置默认桌面" to "阿布桌面",
        "软件版本号" to (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"),
        "检查更新" to when (val update = AppUpdateManager.state) {
            UpdateCheckState.Idle -> "基于 GitHub Releases 检查"
            UpdateCheckState.Checking -> "正在连接 GitHub…"
            is UpdateCheckState.Downloading -> "正在下载 ${update.release.version}"
            is UpdateCheckState.Ready -> "已下载 ${update.release.version} · 点击检查新版后安装"
            is UpdateCheckState.UpToDate -> if (update.version.startsWith("已忽略")) update.version else "已是最新版本 · ${update.version}"
            is UpdateCheckState.Available -> "发现新版本 ${update.release.version}"
            is UpdateCheckState.Failed -> "检查失败 · ${update.message}"
        },
        "开发者选项" to "性能测试、遥控器与设备诊断"
    )
}

private fun openDefaultHomeSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Shared with MainActivity so analog gamepad axes can enter the same TV focus path as D-pad keys. */
internal object SettingsInputState {
    @Volatile var isOpen: Boolean = false
}

@Composable
@OptIn(ExperimentalHazeApi::class, ExperimentalFoundationApi::class)
internal fun SettingsPage(
    haze: HazeState? = null,
    staticBlur: ImageBitmap? = null,
    onClose: () -> Unit,
    onWallpaperChanged: () -> Unit,
    initialDetail: String? = null,
    initialCategory: SettingsCategory = SettingsCategory.DESKTOP,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        SettingsInputState.isOpen = true
        onDispose { SettingsInputState.isOpen = false }
    }
    var categoryName by rememberSaveable { mutableStateOf(initialCategory.name) }
    var pluginTab by rememberSaveable { mutableStateOf(false) }
    var moduleOpen by rememberSaveable { mutableStateOf(initialDetail != null || initialCategory != SettingsCategory.DESKTOP) }
    var selectedPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by rememberSaveable { mutableStateOf(initialDetail) }
    var refreshTick by remember { mutableIntStateOf(0) }
    // 切换左侧分类时自增：右侧列表据此播放与选项行同一条 500ms 焦点曲线的过渡，
    // 从二级页返回时不变，所以返回不会重播入场。
    var categorySwitchTick by remember { mutableIntStateOf(0) }
    val category = SettingsCategory.valueOf(categoryName)
    val drawerReveal = remember { Animatable(0f) }
    val moduleShrink = remember { Animatable(0f) }
    var openingModule by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawerReveal.animateTo(1f, pageMotion()) }

    val leftRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val pluginManager = remember { com.limi.tvdesktop.plugins.PluginManager.get(context) }
    val installedPlugins by produceState(emptyList<com.limi.tvdesktop.plugins.InstalledPlugin>(), refreshTick, moduleOpen) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pluginManager.installed() }
    }
    val modules = remember(pluginTab, installedPlugins) {
        if (pluginTab) listOf(SettingsModule(SettingsCategory.PLUGINS.name, "插件管理", "已安装插件、启停与权限", "⬡", SettingsCategory.PLUGINS),
            SettingsModule("plugin:__store__", "插件商店", "发现与安装插件", "⬡", SettingsCategory.PLUGINS, "__store__")) +
            installedPlugins.map { SettingsModule("plugin:${it.id}", it.name, "v${it.version} · ${if (it.enabled) "已启用" else "已停用"}", it.name.take(1).uppercase(), SettingsCategory.PLUGINS, it.id) }
        else SettingsCategory.all.map { SettingsModule(it.name, it.label, it.subtitle, it.icon, it) }
    }
    val moduleRequesters = remember(modules) { modules.associate { it.id to (leftRequesters[it.category].takeIf { _ -> it.pluginId == null } ?: FocusRequester()) } }
    val selectedModule = selectedPluginId?.let { "plugin:$it" } ?: categoryName
    val selectedModuleRequester = moduleRequesters[selectedModule] ?: leftRequesters[category]
    val rightFirstRequester = remember { FocusRequester() }
    val continuousSettingsState = rememberLazyListState()
    val focusedRowRequester = remember { FocusRequester() }
    // 常驻挂在"最后聚焦的一级列表行"上：返回二级页时第一帧即可直接聚焦目标行，避免焦点先闪到侧边栏
    var focusInRightSide by remember { mutableStateOf(false) }
    var focusInSidebar by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }
    var focusedSidebarCategory by rememberSaveable { mutableStateOf(SettingsCategory.DESKTOP.name) }
    var lastFocusWasRight by rememberSaveable { mutableStateOf(false) }

    // 焦点还原：记录右侧列表最后聚焦的选项行，从二级页面返回时回到该行而不是左侧分类
    var lastFocusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReturnRow by remember { mutableStateOf<String?>(null) }

    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun selectModule(module: SettingsModule) {
        if (openingModule) return
        categoryName = module.category.name
        selectedPluginId = module.pluginId
        categorySwitchTick++
        lastFocusedRow = null
        pendingReturnRow = null
        openingModule = true
        scope.launch {
            if (!moduleOpen) moduleShrink.animateTo(1f, tween(RenderPerformance.duration(220)))
            moduleOpen = true
            detail = if (pluginTab) "plugins" else null
            moduleShrink.snapTo(0f)
            openingModule = false
            withFrameNanos { }
            runCatching { rightFirstRequester.requestFocus() }
        }
    }
    LaunchedEffect(pluginTab, moduleOpen) {
        if (!moduleOpen) {
            withFrameNanos { }
            runCatching { moduleRequesters[selectedModule]?.requestFocus() ?: moduleRequesters.values.firstOrNull()?.requestFocus() }
        }
    }
    val updateState = AppUpdateManager.state
    @Suppress("DEPRECATION")
    val settingsBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec = tween<Float>(RenderPerformance.duration(PAGE_TRANSITION_MS), easing = PageTransitionEasing)
        }
    }

    fun notifyDesktopChanged() {
        refreshTick++
        onWallpaperChanged()
    }

    fun backToList() {
        pendingReturnRow = lastFocusedRow
        detail = null
        refreshTick++
    }

    fun closePage() {
        if (closing) return
        closing = true
        scope.launch {
            drawerReveal.animateTo(0f, tween(RenderPerformance.duration(220)))
            onClose()
        }
    }

    // 焦点锁定：设置页打开期间，任何来源（初始抢焦点竞态、activity 恢复、外部 requestFocus）
    // 把焦点移出面板后，都立即拉回设置页内部，焦点永不落到桌面层。
    LaunchedEffect(category, detail) {
        snapshotFlow { Triple(closing, rootHasFocus, pendingReturnRow != null) }
            .collect { (closed, inside, returning) ->
                if (!closed && !inside && !returning) {
                    // 让瞬时焦点抖动（如窗口焦点恢复）先落定
                    delay(64)
                    if (!closing && !rootHasFocus && pendingReturnRow == null) {
                        val pulled = if (detail == null && lastFocusWasRight && lastFocusedRow != null) {
                            // 回到用户最后操作的选项行；该行滚出视口未组合时聚焦会失败
                            runCatching { focusedRowRequester.requestFocus() }.getOrDefault(false)
                        } else false
                        if (!pulled) {
                            val fallback = when {
                                detail != null -> rightFirstRequester
                                lastFocusWasRight -> rightFirstRequester
                                else -> leftRequesters[SettingsCategory.valueOf(focusedSidebarCategory)]
                            }
                            runCatching { fallback?.requestFocus() }
                        }
                    }
                }
            }
    }

    LaunchedEffect(category) {
        focusedSidebarCategory = category.name
    }

    // Request the first real settings node after it is attached. This is deliberately retried:
    // TV focus can still belong to the page being covered during the first composition frame.
    LaunchedEffect(Unit) {
        repeat(12) {
            withFrameNanos { }
            val focused = runCatching { leftRequesters[category]?.requestFocus() == true }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
    }

    // Multilevel back-press handler for TV remote
    BackHandler(enabled = detail == null) {
        when {
            focusInRightSide -> {
                leftRequesters[category]?.requestFocus()
            }
            moduleOpen -> { moduleOpen = false; selectedPluginId = null }
            else -> closePage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(200f)) {
    val overviewWidth = maxWidth * .62f
    val fixedChromeWidth = 92.dp + 36.dp + 36.dp
    val drawerWidth = if (moduleOpen)
        fixedChromeWidth + (overviewWidth - fixedChromeWidth).coerceAtLeast(0.dp) * .60f
        else overviewWidth
    Box(Modifier.align(Alignment.CenterEnd).width((maxWidth - drawerWidth).coerceAtLeast(0.dp)).fillMaxHeight()
        .pointerInput(Unit) { detectTapGestures { closePage() } })
    Box(
        Modifier
            .fillMaxHeight().width(drawerWidth)
            .graphicsLayer { translationX = -size.width * (1f - drawerReveal.value) }
            .zIndex(200f)
            .onPreviewKeyEvent {
                if (it.key == Key.Escape || it.key == Key.Back) {
                    if (detail != null) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyDown) {
                        when {
                            focusInRightSide -> {
                                leftRequesters[category]?.requestFocus()
                            }
                            moduleOpen -> { moduleOpen = false; selectedPluginId = null }
                            else -> closePage()
                        }
                    }
                    true
                } else if (it.type == KeyEventType.KeyDown && moduleOpen && !pluginTab && detail == null && (focusInRightSide || focusInSidebar)) {
                    when (it.key) {
                        Key.DirectionUp, Key.DirectionDown -> {
                            if (focusInRightSide) {
                                false // The continuous list navigates across category boundaries.
                            } else {
                                val categories = if (pluginTab) listOf(SettingsCategory.PLUGINS) else SettingsCategory.all
                                val current = categories.indexOfFirst { it.name == focusedSidebarCategory }
                                val next = current + if (it.key == Key.DirectionDown) 1 else -1
                                if (next in categories.indices) {
                                    leftRequesters[categories[next]]?.requestFocus()
                                    true
                                } else false
                            }
                        }
                        Key.DirectionLeft -> {
                            if (focusInRightSide) {
                                leftRequesters[category]?.requestFocus()
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (!focusInRightSide) {
                                if (categoryName != focusedSidebarCategory) {
                                    categoryName = focusedSidebarCategory
                                    categorySwitchTick++
                                    lastFocusedRow = null
                                    pendingReturnRow = null
                                    scope.launch { withFrameNanos { }; rightFirstRequester.requestFocus() }
                                    return@onPreviewKeyEvent true
                                }
                                if (lastFocusedRow != null) {
                                    pendingReturnRow = lastFocusedRow
                                } else {
                                    categorySwitchTick++
                                }
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusProperties {
                onExit = { cancelFocusChange() }
            }
            .focusGroup()
    ) {
        SettingsBackdrop(haze, staticBlur, Modifier.matchParentSize())
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Color.White, onPrimary = Color.Black,
            secondary = Color.White, onSecondary = Color.Black)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides settingsBringIntoViewSpec) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("应用设置", "插件设置").forEachIndexed { index, title ->
                TextButton(onClick = {
                    pluginTab = index == 1
                    moduleOpen = false
                    selectedPluginId = null
                    categoryName = if (pluginTab) SettingsCategory.PLUGINS.name else SettingsCategory.DESKTOP.name
                    detail = null
                    lastFocusWasRight = false
                    lastFocusedRow = null
                    pendingReturnRow = null
                }) {
                    Text(title, color = if (pluginTab == (index == 1)) White else Muted,
                        fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        if (!moduleOpen) {
            SettingsModuleGrid(modules, moduleRequesters, ::selectModule,
                Modifier.graphicsLayer {
                    val p = moduleShrink.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    scaleX = 1f - .82f * p
                    scaleY = 1f - .82f * p
                    alpha = 1f - p
                }, initialFocusKey = selectedModule)
        } else {
        Row(Modifier.fillMaxSize()) {
            // Left sidebar: settings categories
            SettingsIconRail(
                modules = modules,
                selected = selectedModule,
                requesters = moduleRequesters,
                onFocusChanged = { focusInSidebar = it },
                onModuleFocused = { focusedSidebarCategory = it.category.name; lastFocusWasRight = false },
                onSelect = ::selectModule,
            )

            // Right content panel
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // 水平留白由内容区内边距承担；垂直方向交给 LazyColumn 的 contentPadding，
                    // 滚动时行可以完整进入留白区显示，不会被容器边界裁切
                    .padding(horizontal = 36.dp)
                    .onFocusChanged {
                        focusInRightSide = it.hasFocus
                        if (it.hasFocus) lastFocusWasRight = true
                    }
                    .focusGroup()
            ) {
                AnimatedContent(targetState = detail, modifier = Modifier.fillMaxSize().clipToBounds(), transitionSpec = {
                    val duration = RenderPerformance.duration(240)
                    if (targetState == null) slideInHorizontally(tween(duration)) { -it } togetherWith slideOutHorizontally(tween(duration)) { it }
                    else slideInHorizontally(tween(duration)) { it } togetherWith slideOutHorizontally(tween(duration)) { -it }
                }, label = "settings-subpage") { displayedDetail ->
                when (displayedDetail) {
                    "updates" -> SettingsUpdateDetail(onBack = { backToList() })
                    "time_format", "date_display", "motion", "blur", "corners", "autostart" -> SettingsChoiceDetail(
                        displayedDetail, onBack = { backToList() }, onChanged = ::notifyDesktopChanged)

                    "wallpaper" -> WallpaperSettings(
                        onBack = {
                            backToList()
                        },
                        onWallpaperChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "glass_tuning" -> GlassTuningSettings(
                        onBack = { backToList() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "hidden_apps" -> HiddenAppsSettings(
                        onBack = { backToList() },
                        onChanged = { notifyDesktopChanged() },
                        returnRequester = rightFirstRequester
                    )
                    "developer" -> DeveloperSettings(
                        onBack = { backToList() },
                        returnRequester = leftRequesters[category],
                        onTestDesktop = ::closePage
                    )
                    "animation" -> AnimationSettings(
                        onBack = {
                            backToList()
                        },
                        returnRequester = leftRequesters[category]
                    )
                    "icon_scale" -> IconScaleSettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "clock_style" -> ClockStyleSettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "dock_style" -> DockStyleSettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "grid_density" -> GridDensitySettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "folder_indicator" -> FolderIndicatorSettings(
                        onBack = { backToList() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "folder_launch" -> FolderLaunchSettings(
                        onBack = { backToList() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "app_sort" -> AppSortSettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "screensaver" -> ScreenSaverSettings(
                        onBack = {
                            backToList()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "media_library" -> MediaLibrarySettings(
                        onBack = {
                            backToList()
                        },
                        returnRequester = leftRequesters[category]
                    )
                    "playback_engine" -> PlaybackEngineSettings(
                        onBack = { backToList() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "startup_page" -> StartupPageSettings(
                        onBack = { backToList() },
                        returnRequester = leftRequesters[category]
                    )
                    "plugins" -> PluginSettingsPage(
                        onBack = { moduleOpen = false; detail = null; selectedPluginId = null; refreshTick++ },
                        returnRequester = selectedModuleRequester,
                        initialPluginId = selectedPluginId,
                        embedded = true,
                        storeOnly = selectedPluginId == "__store__",
                    )
                    else -> CategoryItems(
                        category = category,
                        listState = continuousSettingsState,
                        onCategoryVisible = { categoryName = it.name },
                        switchTick = categorySwitchTick,
                        refreshKey = refreshTick + updateState.hashCode(),
                        firstRequester = rightFirstRequester,
                        leftReturnRequester = leftRequesters[category],
                        focusedRow = lastFocusedRow,
                        focusedRowRequester = focusedRowRequester,
                        pendingFocusRow = pendingReturnRow,
                        onRowFocused = { lastFocusedRow = it },
                        onReturnConsumed = { pendingReturnRow = null },
                        onNavigateRow = { label ->
                            lastFocusedRow = label
                            pendingReturnRow = label
                        },
                        onWallpaper = { detail = "wallpaper" },
                        onGlassTuning = { detail = "glass_tuning" },
                        onAnimation = { detail = "animation" },
                        onDeveloper = { detail = "developer" },
                        onIconScale = { detail = "icon_scale" },
                        onClockStyle = { detail = "clock_style" },
                        onTimeFormat = { detail = "time_format" },
                        onDateDisplay = { detail = "date_display" },
                        onToggleSeconds = {
                            DesktopPreferences.ClockDisplay.setShowSeconds(context, !DesktopPreferences.ClockDisplay.showSeconds(context))
                            notifyDesktopChanged()
                        },
                        onToggleAppListBlur = {
                            DesktopPreferences.GlassTuning.setAppListBlurEnabled(context, !DesktopPreferences.GlassTuning.appListBlurEnabled(context))
                            notifyDesktopChanged()
                        },
                        onDockStyle = { detail = "dock_style" },
                        onGridDensity = { detail = "grid_density" },
                        onAppCorners = { detail = "corners" },
                        onFolderIndicator = { detail = "folder_indicator" },
                        onFolderLaunch = { detail = "folder_launch" },
                        onAppSort = { detail = "app_sort" },
                        onScreenSaver = { detail = "screensaver" },
                        onMediaLibrary = { detail = "media_library" },
                        onPlaybackEngine = { detail = "playback_engine" },
                        onStartupPage = { detail = "startup_page" },
                        onPlugins = { detail = "plugins" },
                        onHiddenApps = { detail = "hidden_apps" },
                        onToggleLowPerformance = {
                            detail = "motion"
                        },
                        onChooseBlur = { detail = "blur" },
                        onToggleAutoStart = { detail = "autostart" },
                        onToggleForceLandscape = {
                            val enabled = !DesktopPreferences.ForceLandscape.isEnabled(context)
                            DesktopPreferences.ForceLandscape.setEnabled(context, enabled)
                            (context as? android.app.Activity)?.requestedOrientation = if (enabled) {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                            refreshTick++
                        },
                        onDefaultHome = { openDefaultHomeSettings(context) },
                        onCheckUpdate = {
                            detail = "updates"
                            AppUpdateManager.checkFromSettings(context)
                        }
                    )
                }
                }
            }
        }
        }
        }
        }
        }
    }
    }
    }
}

/** 设置页左右两个列表共用的切换颜色插值：同一条 500ms 焦点曲线，避免两边各用一套动效。 */
private fun mix(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction)

private data class SettingsListEntry(val category: SettingsCategory, val label: String?, val value: String?)
@Composable
private fun CategoryItems(
    category: SettingsCategory,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCategoryVisible: (SettingsCategory) -> Unit,
    switchTick: Int,
    refreshKey: Int,
    firstRequester: FocusRequester,
    leftReturnRequester: FocusRequester?,
    focusedRow: String?,
    focusedRowRequester: FocusRequester,
    pendingFocusRow: String?,
    onRowFocused: (String) -> Unit,
    onReturnConsumed: () -> Unit,
    onNavigateRow: (String) -> Unit,
    onWallpaper: () -> Unit,
    onGlassTuning: () -> Unit,
    onAnimation: () -> Unit,
    onDeveloper: () -> Unit,
    onIconScale: () -> Unit,
    onClockStyle: () -> Unit,
    onDockStyle: () -> Unit,
    onGridDensity: () -> Unit,
    onAppCorners: () -> Unit,
    onFolderIndicator: () -> Unit,
    onFolderLaunch: () -> Unit,
    onAppSort: () -> Unit,
    onScreenSaver: () -> Unit,
    onMediaLibrary: () -> Unit,
    onPlaybackEngine: () -> Unit,
    onStartupPage: () -> Unit,
    onPlugins: () -> Unit,
    onHiddenApps: () -> Unit,
    onToggleLowPerformance: () -> Unit,
    onChooseBlur: () -> Unit,
    onTimeFormat: () -> Unit,
    onDateDisplay: () -> Unit,
    onToggleSeconds: () -> Unit,
    onToggleAppListBlur: () -> Unit,
    onToggleAutoStart: () -> Unit,
    onToggleForceLandscape: () -> Unit,
    onDefaultHome: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val sections = remember(refreshKey, RenderPerformance.motionMode, RenderPerformance.blurMode) {
        SettingsCategory.all.associateWith { itemsFor(it, context) }
    }
    val entries = remember(sections) {
        sections.flatMap { (section, rows) ->
            listOf(SettingsListEntry(section, null, null)) + rows.map { (label, value) -> SettingsListEntry(section, label, value) }
        }
    }
    val settingRows = remember(entries) { entries.filter { it.label != null } }
    val rowRefs = remember(settingRows.map { it.label }) { settingRows.associate { it.label!! to FocusRequester() } }
    val firstLabel = sections[category]?.firstOrNull()?.first
    val currentCategoryVisible by rememberUpdatedState(onCategoryVisible)
    val currentPendingFocus by rememberUpdatedState(pendingFocusRow)
    var jumping by remember { mutableStateOf(false) }

    // Explicit selection scrolls the same list; scroll-spy never changes switchTick.
    LaunchedEffect(switchTick) {
        if (pendingFocusRow != null) return@LaunchedEffect
        val heading = entries.indexOfFirst { it.category == category && it.label == null }
        if (heading >= 0) {
            jumping = true
            try {
                listState.scrollToItem(heading)
                withFrameNanos { }
                runCatching { firstRequester.requestFocus() }
            } finally { jumping = false }
        }
    }
    LaunchedEffect(listState, entries) {
        snapshotFlow {
            val anchor = with(density) { 48.dp.toPx() }
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > anchor }
            entries.getOrNull(visible?.index ?: listState.firstVisibleItemIndex)?.category
        }.collect { section ->
            if (!jumping && currentPendingFocus == null && section != null) currentCategoryVisible(section)
        }
    }
    LaunchedEffect(pendingFocusRow) {
        val label = pendingFocusRow ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.label == label }
        if (index >= 0) {
            val section = entries[index].category
            currentCategoryVisible(section)
            val margin = with(density) { 24.dp.roundToPx() }
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (visible == null) listState.animateScrollToItemSpring(index, -margin)
            else {
                val bottom = listState.layoutInfo.viewportSize.height - margin
                val delta = when {
                    visible.offset < margin -> visible.offset - margin
                    visible.offset + visible.size > bottom -> visible.offset + visible.size - bottom
                    else -> 0
                }
                if (delta != 0) listState.animateScrollBy(delta.toFloat(), pageMotion())
            }
            repeat(12) {
                withFrameNanos { }
                val requester = if (label == sections[section]?.firstOrNull()?.first) firstRequester else focusedRowRequester
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) {
                    onReturnConsumed()
                    return@LaunchedEffect
                }
            }
        }
        onReturnConsumed()
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("continuous-settings-list").onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (ev.key != Key.DirectionDown && ev.key != Key.DirectionUp) return@onPreviewKeyEvent false
            val visibleLabels = listState.layoutInfo.visibleItemsInfo.mapNotNull { entries.getOrNull(it.index)?.label }
            if (pendingFocusRow != null) return@onPreviewKeyEvent true
            val anchor = focusedRow ?: visibleLabels.firstOrNull()
            val current = settingRows.indexOfFirst { it.label == anchor }
            val next = current + if (ev.key == Key.DirectionDown) 1 else -1
            if (current < 0) runCatching { firstRequester.requestFocus() }
            else if (next in settingRows.indices) onNavigateRow(settingRows[next].label!!)
            true
        },
        contentPadding = PaddingValues(top = 36.dp, bottom = 160.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.label ?: "heading-${entry.category.name}" }) { _, entry ->
            val section = entry.category
            val label = entry.label
            if (label == null) {
                Column(Modifier.padding(top = 24.dp, bottom = 24.dp)) {
                    Text(section.label, color = White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text(section.subtitle, color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                }
                return@itemsIndexed
            }
            val subtitle = entry.value
            val isFirst = label == firstLabel
            val onClick: () -> Unit = when {
                label == "低性能模式" -> onToggleLowPerformance
                label == "模糊效果" -> onChooseBlur
                label == "玻璃与模糊" -> onGlassTuning
                label == "隐藏应用管理" -> onHiddenApps
                label == "壁纸" -> onWallpaper
                label == "启动动画" -> onAnimation
                label == "开发者选项" -> onDeveloper
                label == "图标大小" -> onIconScale
                label == "时钟样式" -> onClockStyle
                label == "时间格式" -> onTimeFormat
                label == "日期显示" -> onDateDisplay
                label == "时间显示秒数" -> onToggleSeconds
                label == "应用列表背景模糊" -> onToggleAppListBlur
                label == "Dock 布局" -> onDockStyle
                label == "网格密度" -> onGridDensity
                label == "应用圆角" -> onAppCorners
                label == "文件夹页码样式" -> onFolderIndicator
                label == "启动文件夹内应用后" -> onFolderLaunch
                label == "应用排列" -> onAppSort
                label == "屏幕保护" -> onScreenSaver
                label == "默认播放器" -> onPlaybackEngine
                label == "启动默认页" -> onStartupPage
                section == SettingsCategory.PLUGINS -> onPlugins
                section == SettingsCategory.LIBRARY -> onMediaLibrary
                label == "开机自启动" -> onToggleAutoStart
                label == "强制横屏" -> onToggleForceLandscape
                label == "设置默认桌面" -> onDefaultHome
                label == "检查更新" -> onCheckUpdate
                else -> ({})
            }

            val groupFirst = label == sections[section]?.firstOrNull()?.first
            val groupLast = label == sections[section]?.lastOrNull()?.first
            Box(Modifier.fillMaxWidth()
                .background(Color(0xFF202020).copy(alpha = 0.30f), RoundedCornerShape(
                    topStart = if (groupFirst) 16.dp else 0.dp, topEnd = if (groupFirst) 16.dp else 0.dp,
                    bottomStart = if (groupLast) 16.dp else 0.dp, bottomEnd = if (groupLast) 16.dp else 0.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp).onFocusChanged {
                if (it.hasFocus) {
                    onRowFocused(label)
                    currentCategoryVisible(section)
                }
            }) {
                SettingsRowItem(
                    label = label, value = subtitle,
                    grouped = true,
                    focusRequester = when {
                        isFirst -> firstRequester
                        label == (pendingFocusRow ?: focusedRow) -> focusedRowRequester
                        else -> rowRefs.getValue(label)
                    },
                    leftReturnRequester = leftReturnRequester,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun HiddenAppsSettings(onBack: () -> Unit, onChanged: () -> Unit, returnRequester: FocusRequester) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val hiddenApps = remember(refresh) {
        val hidden = AppHomeFeatures.hidden(context)
        val pm = context.packageManager
        listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
            .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
            .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
            .filter { "${it.activityInfo.packageName}/${it.activityInfo.name}" in hidden }
            .map { it.activityInfo.packageName to (runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.activityInfo.packageName)) }
            .distinctBy { it.first }
    }
    BackHandler { onBack() }
    LaunchedEffect(Unit) { runCatching { returnRequester.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text("隐藏应用", color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("恢复后会重新出现在桌面应用列表", color = Color(0xFFA5ACB8), fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
        if (hiddenApps.isEmpty()) {
            Text("没有隐藏的应用", color = Color(0xFFD0D4DC), fontSize = 20.sp, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(hiddenApps, key = { _, item -> item.first }) { _, (packageName, label) ->
                    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            Text(packageName, color = Color(0xFFA5ACB8), fontSize = 14.sp)
                        }
                        TextButton(onClick = {
                            val pm = context.packageManager
                            val match = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
                                .flatMap { category -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                                .filter { it.activityInfo.packageName == packageName }
                            match.forEach { info -> AppHomeFeatures.setHidden(context, "${info.activityInfo.packageName}/${info.activityInfo.name}", false) }
                            refresh++; onChanged()
                        }) { Text("恢复显示") }
                    }
                }
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start).focusRequester(returnRequester)) { Text("← 返回桌面设置") }
    }
}

@Composable
internal fun SettingsSectionTitle(text: String) {
    Text(
        text,
        color = Color(0xFF8B929E),
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp, bottom = 14.dp, start = 6.dp)
    )
}

/** Keeps every focused settings control fully inside its nearest scrollable viewport. */
internal fun Modifier.settingsAutoScroll(): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    bringIntoViewRequester(requester)
        .onSizeChanged { size = it }
        .onFocusChanged { state ->
            if (state.isFocused) scope.launch {
                // Wait until Compose focus search and lazy/scroll layout have settled, then enforce
                // a 20dp visual safety margin for the scaled focus treatment.
                delay(48)
                with(density) {
                    requester.bringIntoView(
                        Rect(0f, -20.dp.toPx(), size.width.toFloat(), size.height + 20.dp.toPx())
                    )
                }
            }
        }
}

@Composable
internal fun Modifier.settingsVerticalScroll(): Modifier {
    return this.verticalScroll(rememberScrollState())
}

@Composable
private fun GlassTuningSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester?
) {
    val context = LocalContext.current
    val back = remember { FocusRequester() }
    var navBlur by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.navBlur(context)) }
    var navOpacity by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.navOpacity(context)) }
    var dockBlur by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.dockBlur(context)) }
    var dockOpacity by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.dockOpacity(context)) }
    var appBlur by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.appListBlur(context)) }
    var appOpacity by remember { mutableFloatStateOf(DesktopPreferences.GlassTuning.appListOpacity(context)) }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { back.requestFocus() }

    fun save(key: String, value: Float) {
        DesktopPreferences.GlassTuning.save(context, key, value)
        onChanged()
    }

    fun resetAll() {
        navBlur = DesktopPreferences.GlassTuning.NAV_BLUR_DEFAULT
        navOpacity = DesktopPreferences.GlassTuning.NAV_OPACITY_DEFAULT
        dockBlur = DesktopPreferences.GlassTuning.DOCK_BLUR_DEFAULT
        dockOpacity = DesktopPreferences.GlassTuning.DOCK_OPACITY_DEFAULT
        appBlur = DesktopPreferences.GlassTuning.APP_LIST_BLUR_DEFAULT
        appOpacity = DesktopPreferences.GlassTuning.APP_LIST_OPACITY_DEFAULT
        listOf(
            "glassNavBlur", "glassNavOpacity", "glassDockBlur",
            "glassDockOpacity", "glassAppListBlur", "glassAppListOpacity",
        ).forEach { DesktopPreferences.GlassTuning.reset(context, it) }
        onChanged()
    }

    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 40.dp, bottom = 140.dp)) {
        SettingsRowItem("← 返回桌面设置", null, back, returnRequester, onClick = onBack)
        Spacer(Modifier.height(22.dp))
        Text("玻璃与模糊", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("触屏拖动；遥控器、键盘和手柄使用左右键微调", color = Color(0xFFA5ACB8), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

        SettingsSectionTitle("顶部导航条")
        GlassSliderRow("模糊度", navBlur, 0f..60f, "${navBlur.toInt()} dp", onValue = { navBlur = it }, onCommit = { save("glassNavBlur", it) })
        GlassSliderRow("不透明度", navOpacity, 0f..0.9f, "${(navOpacity * 100).toInt()}%", step = .05f, onValue = { navOpacity = it }, onCommit = { save("glassNavOpacity", it) })

        SettingsSectionTitle("Dock 栏")
        GlassSliderRow("模糊度", dockBlur, 0f..60f, "${dockBlur.toInt()} dp", onValue = { dockBlur = it }, onCommit = { save("glassDockBlur", it) })
        GlassSliderRow("不透明度", dockOpacity, 0f..0.9f, "${(dockOpacity * 100).toInt()}%", step = .05f, onValue = { dockOpacity = it }, onCommit = { save("glassDockOpacity", it) })

        SettingsSectionTitle("进入应用列表后的背景")
        GlassSliderRow("背景模糊度", appBlur, 0f..80f, "${appBlur.toInt()} dp", onValue = { appBlur = it }, onCommit = { save("glassAppListBlur", it) })
        GlassSliderRow("背景不透明度", appOpacity, 0f..0.9f, "${(appOpacity * 100).toInt()}%", step = .05f, onValue = { appOpacity = it }, onCommit = { save("glassAppListOpacity", it) })

        Spacer(Modifier.height(20.dp))
        SettingsSectionTitle("重置")
        SettingsRowItem("全部恢复默认", "恢复六项玻璃参数", onClick = ::resetAll)
    }
}

@Composable
private fun GlassSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    step: Float = 2f,
    onValue: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val shape = ContinuousCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { rowSize = it }
            .onFocusChanged {
                if (it.hasFocus) scope.launch {
                    delay(40)
                    with(density) {
                        bringIntoView.bringIntoView(
                            Rect(0f, -12.dp.toPx(), rowSize.width.toFloat(), rowSize.height + 12.dp.toPx())
                        )
                    }
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -step
                    Key.DirectionRight -> step
                    else -> return@onPreviewKeyEvent false
                }
                val next = (((value + delta) / step).roundToInt() * step)
                    .coerceIn(range.start, range.endInclusive)
                if (next != value) {
                    onValue(next)
                    onCommit(next)
                }
                true
            }
            .focusable(interactionSource = interaction)
            .hoverable(interaction)
            .clip(shape)
            .background(if (active) Color.White else Color(0x16FFFFFF))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = if (active) Color(0xFF14161B) else White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = if (active) Color(0xFF245FD2) else Color(0xFF78A7FF), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = { onCommit(value) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = if (active) Color(0xFF245FD2) else Color.White,
                activeTrackColor = Color(0xFF4A89FF),
                inactiveTrackColor = if (active) Color(0xFFD5DAE2) else Color(0x405E6775),
            ),
            // The row owns TV focus and key handling. Disabling Slider focus prevents a second,
            // invisible focus stop while preserving touch/mouse dragging.
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
        )
    }
}

/** Inset grouped card item with focus zoom, a white focus background, and remote D-pad click. */
@Composable
internal fun SettingsRowItem(
    label: String,
    value: String?,
    focusRequester: FocusRequester? = null,
    leftReturnRequester: FocusRequester? = null,
    grouped: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var itemSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val raised = focused || hovered

    // 与左侧分类列表共用同一套切换动效：缩放 1.025 + 500ms 焦点曲线 + 底色 / 描边 / 文字过渡。
    val scale by animateFloatAsState(if (raised && !grouped) 1.025f else 1f, focusMotion(), label = "row-scale")
    val reveal by animateFloatAsState(if (raised) 1f else 0f, focusMotion(), label = "row-reveal")
    val shape = RoundedCornerShape(10.dp)
    val labelColor = mix(White, Color(0xFF14161B), reveal)
    val valueColor = mix(Color(0xFF9AA0AD), Color(0xFF4A4E58), reveal)
    val chevronColor = mix(Color(0xFF7E8492), Color(0xFF14161B), reveal)

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .bringIntoViewRequester(bringIntoView)
            .onSizeChanged { itemSize = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .settingsAutoScroll()
            .focusProperties {
                if (leftReturnRequester != null) left = leftReturnRequester
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    scope.launch {
                        delay(50)
                        with(density) {
                            bringIntoView.bringIntoView(
                                Rect(0f, -12.dp.toPx(), itemSize.width.toFloat(), itemSize.height + 12.dp.toPx())
                            )
                        }
                    }
                }
            }
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyDown) {
                        onClick()
                    }
                    true
                } else false
            }
            .focusProperties { canFocus = true }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(mix(if (grouped) Color.Transparent else Color(0xF2292929), Color.White, reveal), mix(if (grouped) Color.Transparent else Color(0xF2242424), Color.White, reveal))
                )
            )
            .focusSweep(raised, shape)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = labelColor,
                fontSize = 23.sp,
                fontWeight = if (raised) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(.4f).padding(end = 12.dp),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            Row(Modifier.weight(.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (value != null) {
                    Text(
                        value,
                        color = valueColor,
                        fontSize = 19.sp,
                        modifier = Modifier.weight(1f, fill = false).padding(end = 10.dp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "›",
                    color = chevronColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
internal fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    leftReturnRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val raised = focused || hovered

    val scale by animateFloatAsState(if (raised) 1.025f else 1f, focusMotion(), label = "radio-scale")
    val shape = RoundedCornerShape(10.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .settingsAutoScroll()
            .focusProperties {
                if (leftReturnRequester != null) left = leftReturnRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyDown) {
                        onClick()
                    }
                    true
                } else false
            }
            .focusProperties { canFocus = true }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
            .background(
                when {
                    raised -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F2)))
                    selected -> Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
                    else -> Brush.linearGradient(listOf(Color(0x20FFFFFF), Color(0x14FFFFFF)))
                }
            )
            .focusSweep(raised, shape)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = if (raised) Color(0xFF14161B) else White,
                fontSize = 23.sp,
                fontWeight = if (raised || selected) FontWeight.SemiBold else FontWeight.Normal
            )

            Box(
                Modifier
                    .size(26.dp)
                    .clip(ContinuousCornerShape(50.dp))
                    .border(
                        2.dp,
                        if (raised) (if (selected) Color(0xFF14161B) else Color(0xFF888888))
                        else (if (selected) White else Color(0x66FFFFFF)),
                        ContinuousCornerShape(50.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(ContinuousCornerShape(50.dp))
                            .background(if (raised) Color(0xFF14161B) else Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackEngineSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(DesktopPreferences.PlaybackEngine.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { backRequester.requestFocus() }
    Column(
        Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onClick = onBack)
        Spacer(Modifier.height(24.dp))
        Text("默认播放器", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "选择媒体库点击播放时使用的内核；自动模式会在解码失败时切换到 mpv",
            color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp)
        )
        SettingsSectionTitle("播放策略")
        DesktopPreferences.PlaybackEngine.entries.forEach { engine ->
            SettingsRadioRow(
                label = "${engine.label}  ·  ${engine.desc}",
                selected = selected == engine,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.PlaybackEngine.save(context, engine)
                selected = engine
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：启动默认页
// ==========================================
@Composable
private fun StartupPageSettings(
    onBack: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(DesktopPreferences.StartupPage.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { backRequester.requestFocus() }
    }

    Column(
        Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "启动默认页",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "选择设备开机或回到桌面时默认打开的页面",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("默认打开")

        DesktopPreferences.StartupPage.entries.forEach { page ->
            SettingsRadioRow(
                label = "${page.label} · ${page.desc}",
                selected = current == page,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.StartupPage.save(context, page)
                current = page
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：时钟样式选择器 (带实时效果预览)
// ==========================================
@Composable
internal fun ClockStyleSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentStyle by remember { mutableStateOf(DesktopPreferences.ClockStyle.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "时钟样式",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "选择桌面中央的时间、日期与农历排版风格",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 实时时钟效果预览卡片
        ClockStylePreview(currentStyle)

        Spacer(Modifier.height(28.dp))
        SettingsSectionTitle("选择显示样式")

        DesktopPreferences.ClockStyle.entries.forEach { style ->
            SettingsRadioRow(
                label = "${style.label} · ${style.desc}",
                selected = currentStyle == style,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.ClockStyle.save(context, style)
                currentStyle = style
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ClockStylePreview(style: DesktopPreferences.ClockStyle) {
    val context = LocalContext.current
    val clockVersion = DesktopPreferences.version
    val timeFormat = remember(clockVersion) { DesktopPreferences.TimeFormat.current(context) }
    val showSeconds = remember(clockVersion, style) { DesktopPreferences.ClockDisplay.showSeconds(context) }
    val dateDisplay = remember(clockVersion) { DesktopPreferences.DateDisplay.current(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showSeconds) {
        val interval = if (showSeconds) 1000L else 60_000L
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(interval - now % interval)
        }
    }
    val date = Date(now)
    val formatter = remember(timeFormat, showSeconds) {
        SimpleDateFormat(DesktopPreferences.ClockDisplay.pattern(timeFormat, showSeconds), Locale.CHINA)
    }
    val timeWithSeconds = formatter.format(date)
    val timeMinimal = timeWithSeconds
    val dateLines = remember(now / 60_000, dateDisplay) { DesktopPreferences.ClockDisplay.dateLines(now, dateDisplay) }
    val dateText = listOf(dateLines.first, dateLines.second).filter { it.isNotBlank() }.joinToString("  ")

    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(ContinuousCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF21252D), Color(0xFF131519)))),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            DesktopPreferences.ClockStyle.STANDARD -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeWithSeconds, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = White, fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    if (dateText.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(dateText, color = Color(0xD9FFFFFF), fontSize = 18.sp)
                    }
                }
            }
            DesktopPreferences.ClockStyle.MINIMAL -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeMinimal, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = White, fontSize = 76.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    if (dateText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(dateText, color = Color(0xD9FFFFFF), fontSize = 18.sp)
                    }
                }
            }
            DesktopPreferences.ClockStyle.SPLIT -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(timeMinimal, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    if (dateText.isNotBlank()) {
                        Box(Modifier.width(2.dp).height(50.dp).background(Color(0x60FFFFFF)))
                        Column {
                            if (dateLines.first.isNotBlank()) Text(dateLines.first, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            if (dateLines.second.isNotBlank()) Text(dateLines.second, color = Color(0xB3FFFFFF), fontSize = 17.sp)
                        }
                    }
                }
            }
            DesktopPreferences.ClockStyle.OFF -> {
                Text("全景壁纸模式 · 时钟已隐藏", color = Color(0x88FFFFFF), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ==========================================
// 二级设置页面：图标大小选择器
// ==========================================
@Composable
internal fun IconScaleSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentScale by remember { mutableStateOf(DesktopPreferences.IconScale.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "图标大小",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "调整应用卡片与底部 Dock 栏的缩放比例",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("尺寸预设")

        DesktopPreferences.IconScale.entries.forEach { scale ->
            SettingsRadioRow(
                label = "${scale.label} · ${scale.desc}",
                selected = currentScale == scale,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.IconScale.save(context, scale)
                currentScale = scale
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：Dock 布局风格选择器
// ==========================================
@Composable
internal fun DockStyleSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentStyle by remember { mutableStateOf(DesktopPreferences.DockStyle.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Dock 布局风格",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "定制桌面底部快捷应用栏的底座外观与视觉呈现",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("底座质感")

        DesktopPreferences.DockStyle.entries.forEach { style ->
            SettingsRadioRow(
                label = "${style.label} · ${style.desc}",
                selected = currentStyle == style,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.DockStyle.save(context, style)
                currentStyle = style
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：网格密度选择器
// ==========================================
@Composable
internal fun GridDensitySettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentDensity by remember { mutableStateOf(DesktopPreferences.GridDensity.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "网格密度",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "定制展开全部应用时每一行的卡片列数",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("每行列数")

        DesktopPreferences.GridDensity.entries.forEach { density ->
            SettingsRadioRow(
                label = "${density.label} · ${density.desc}",
                selected = currentDensity == density,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.GridDensity.save(context, density)
                currentDensity = density
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：应用排列方式
// ==========================================
@Composable
internal fun AppSortSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentSort by remember { mutableStateOf(DesktopPreferences.AppSort.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { backRequester.requestFocus() }

    Column(
        Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )
        Spacer(Modifier.height(24.dp))
        Text("应用排列", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            "只排列 Dock 之外的应用；手动移动后自动保存为自定义排列",
            color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(bottom = 24.dp)
        )
        SettingsSectionTitle("排列方式")
        DesktopPreferences.AppSort.entries.forEach { sort ->
            SettingsRadioRow(
                label = "${sort.label} · ${sort.desc}",
                selected = currentSort == sort,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.AppSort.save(context, sort)
                currentSort = sort
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun FolderIndicatorSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(DesktopPreferences.FolderPageIndicator.current(context)) }
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { backRequester.requestFocus() }
    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)) {
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onClick = onBack)
        Spacer(Modifier.height(24.dp))
        Text("文件夹页码样式", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        Text("统一设置文件夹底部的分页位置指示器", color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(bottom = 24.dp))
        SettingsSectionTitle("指示器样式")
        DesktopPreferences.FolderPageIndicator.entries.forEach { style ->
            SettingsRadioRow("${style.label} · ${style.desc}", current == style, leftReturnRequester = returnRequester) {
                DesktopPreferences.FolderPageIndicator.save(context, style)
                current = style
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun FolderLaunchSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(DesktopPreferences.FolderLaunchBehavior.current(context)) }
    val backRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { backRequester.requestFocus() }
    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)) {
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onClick = onBack)
        Spacer(Modifier.height(24.dp))
        Text("启动文件夹内应用后", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp))
        Text("选择返回桌面时文件夹的状态", color = Color(0xFFA5ACB8), fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp))
        SettingsSectionTitle("文件夹状态")
        DesktopPreferences.FolderLaunchBehavior.entries.forEach { behavior ->
            SettingsRadioRow("${behavior.label} · ${behavior.desc}", current == behavior,
                leftReturnRequester = returnRequester) {
                DesktopPreferences.FolderLaunchBehavior.save(context, behavior)
                current = behavior
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==========================================
// 二级设置页面：屏幕保护超时选择器
// ==========================================
@Composable
internal fun ScreenSaverSettings(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var currentTimeout by remember { mutableStateOf(DesktopPreferences.ScreenSaverTimeout.current(context)) }
    val backRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "屏幕保护",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "在桌面闲置无遥控器按键操作时，自动开启全景超清画廊屏保",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("空闲启动时间")

        DesktopPreferences.ScreenSaverTimeout.entries.forEach { timeout ->
            SettingsRadioRow(
                label = timeout.label,
                selected = currentTimeout == timeout,
                leftReturnRequester = returnRequester
            ) {
                DesktopPreferences.ScreenSaverTimeout.save(context, timeout)
                currentTimeout = timeout
                onChanged()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
