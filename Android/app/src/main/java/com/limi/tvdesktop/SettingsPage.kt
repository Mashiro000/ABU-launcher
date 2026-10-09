package com.limi.tvdesktop

import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
        "默认页、应用动画、屏保与开机行为",
        "↗",
        listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
    ),
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
        "性能兼容、默认桌面、版本与诊断",
        "⚙",
        listOf(Color(0xFF64748B), Color(0xFF475569))
    );

    companion object {
        val all = SettingsCategory.entries
    }
}

private fun itemsFor(category: SettingsCategory, context: Context): List<Pair<String, String?>> = when (category) {
    SettingsCategory.DESKTOP -> listOf(
        "玻璃与模糊" to "导航条、Dock 与应用列表的模糊度和不透明度",
        "壁纸" to "自定义壁纸来源与预览",
        "图标大小" to DesktopPreferences.IconScale.current(context).label,
        "时钟样式" to DesktopPreferences.ClockStyle.current(context).label,
        "Dock 布局" to DesktopPreferences.DockStyle.current(context).label,
        "网格密度" to DesktopPreferences.GridDensity.current(context).label,
        "文件夹页码样式" to DesktopPreferences.FolderPageIndicator.current(context).label,
        "启动文件夹内应用后" to DesktopPreferences.FolderLaunchBehavior.current(context).label,
        "应用排列" to DesktopPreferences.AppSort.current(context).label,
        "隐藏应用管理" to "恢复从桌面隐藏的应用"
    )
    SettingsCategory.STARTUP -> listOf(
        "启动默认页" to DesktopPreferences.StartupPage.current(context).label,
        "启动动画" to LaunchAnim.current(context).label,
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
    SettingsCategory.GENERAL -> listOf(
        "低性能模式" to if (RenderPerformance.lowPerformance) "已开启 · 精简动画与合成，优先流畅" else if (RenderPerformance.reducedEffects) "安卓 9 自动兼容 · 精简动画" else "已关闭 · 点击开启，优先流畅",
        "静态模糊" to if (RenderPerformance.staticBlurEnabled) "已开启（推荐）· 减少重复模糊计算" else "已关闭 · 使用实时模糊（性能要求较高）",
        "设置默认桌面" to "阿布桌面",
        "软件版本号" to (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"),
        "检查更新" to when (val update = AppUpdateManager.state) {
            UpdateCheckState.Idle -> "基于 GitHub Releases 检查"
            UpdateCheckState.Checking -> "正在连接 GitHub…"
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

@Composable
@OptIn(ExperimentalHazeApi::class, ExperimentalFoundationApi::class)
internal fun SettingsPage(
    haze: HazeState? = null,
    staticBlur: ImageBitmap? = null,
    onClose: () -> Unit,
    onWallpaperChanged: () -> Unit
) {
    val context = LocalContext.current
    var categoryName by rememberSaveable { mutableStateOf(SettingsCategory.DESKTOP.name) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val category = SettingsCategory.valueOf(categoryName)

    val leftRequesters = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val rightFirstRequester = remember { FocusRequester() }
    // 常驻挂在"最后聚焦的一级列表行"上：返回二级页时第一帧即可直接聚焦目标行，避免焦点先闪到侧边栏
    val focusedRowRequester = remember { FocusRequester() }
    var focusInRightSide by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }

    // 焦点还原：记录右侧列表最后聚焦的选项行，从二级页面返回时回到该行而不是左侧分类
    var lastFocusedRow by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingReturnRow by remember { mutableStateOf<String?>(null) }

    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val updateState = AppUpdateManager.state
    @Suppress("DEPRECATION")
    val settingsBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec = tween<Float>(PAGE_TRANSITION_MS, easing = PageTransitionEasing)
        }
    }

    fun notifyDesktopChanged() {
        refreshTick++
        onWallpaperChanged()
    }

    fun backToList() {
        pendingReturnRow = lastFocusedRow
        detail = null
    }

    fun closePage() {
        closing = true
        onClose()
    }

    // 焦点锁定：设置页打开期间，任何来源（初始抢焦点竞态、activity 恢复、外部 requestFocus）
    // 把焦点移出面板后，都立即拉回设置页内部，焦点永不落到桌面层。
    LaunchedEffect(category) {
        snapshotFlow { Triple(closing, rootHasFocus, pendingReturnRow != null) }
            .collect { (closed, inside, returning) ->
                if (!closed && !inside && !returning) {
                    // 让瞬时焦点抖动（如窗口焦点恢复）先落定
                    delay(64)
                    if (!closing && !rootHasFocus && pendingReturnRow == null) {
                        val pulled = if (detail == null && lastFocusedRow != null) {
                            // 回到用户最后操作的选项行；该行滚出视口未组合时聚焦会失败
                            runCatching { focusedRowRequester.requestFocus() }.getOrDefault(false)
                        } else false
                        if (!pulled) {
                            val fallback = if (detail == null) leftRequesters[category] else rightFirstRequester
                            runCatching { fallback?.requestFocus() }
                        }
                    }
                }
            }
    }

    LaunchedEffect(category) { lastFocusedRow = null }

    // Multilevel back-press handler for TV remote
    BackHandler(enabled = detail == null) {
        when {
            focusInRightSide -> {
                leftRequesters[category]?.requestFocus()
            }
            else -> closePage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(200f)
            .then(
                if (RenderPerformance.blur31 && haze != null) {
                    Modifier.hazeEffect(haze) {
                        blurRadius = 64.dp
                        backgroundColor = Color.Transparent
                        tints = listOf(HazeTint(Color(0xD90E1116)))
                        noiseFactor = 0f
                        inputScale = HazeInputScale.Fixed(0.35f)
                    }
                } else {
                    // Fallback static high-blur styling for lower Android versions
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(Color(0xF5111318), Color(0xF80B0D11), Color(0xFB060709))
                        )
                    )
                }
            )
            .onPreviewKeyEvent {
                if (it.key == Key.Escape || it.key == Key.Back) {
                    if (detail != null) return@onPreviewKeyEvent false
                    if (it.type == KeyEventType.KeyDown) {
                        when {
                            focusInRightSide -> {
                                leftRequesters[category]?.requestFocus()
                            }
                            else -> closePage()
                        }
                    }
                    true
                } else false
            }
            .onFocusChanged { rootHasFocus = it.hasFocus }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
    ) {
        if (RenderPerformance.staticBlur && staticBlur != null) {
            Image(staticBlur, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color(0xD90E1116)))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides settingsBringIntoViewSpec) {
        Row(Modifier.fillMaxSize()) {
            // Left sidebar: settings categories
            TvSettingsSidebar(
                selected = category,
                requesters = leftRequesters,
                rightFirstRequester = rightFirstRequester,
                onSelect = {
                    categoryName = it.name
                    detail = null
                }
            )

            // Right content panel
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // 水平留白由内容区内边距承担；垂直方向交给 LazyColumn 的 contentPadding，
                    // 滚动时行可以完整进入留白区显示，不会被容器边界裁切
                    .padding(start = 56.dp, end = 36.dp)
                    .onFocusChanged { focusInRightSide = it.hasFocus }
            ) {
                when (detail) {
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
                        onBack = { backToList() },
                        returnRequester = leftRequesters[category]
                    )
                    else -> CategoryItems(
                        category = category,
                        refreshKey = refreshTick + updateState.hashCode(),
                        firstRequester = rightFirstRequester,
                        leftReturnRequester = leftRequesters[category],
                        focusedRow = lastFocusedRow,
                        focusedRowRequester = focusedRowRequester,
                        pendingFocusRow = pendingReturnRow,
                        onRowFocused = { lastFocusedRow = it },
                        onReturnConsumed = { pendingReturnRow = null },
                        onNavigateRow = { label ->
                            // 行间上下导航：更新聚焦记录并复用还原机制完成滚动+聚焦
                            lastFocusedRow = label
                            pendingReturnRow = label
                        },
                        onWallpaper = { detail = "wallpaper" },
                        onGlassTuning = { detail = "glass_tuning" },
                        onAnimation = { detail = "animation" },
                        onDeveloper = { detail = "developer" },
                        onIconScale = { detail = "icon_scale" },
                        onClockStyle = { detail = "clock_style" },
                        onDockStyle = { detail = "dock_style" },
                        onGridDensity = { detail = "grid_density" },
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
                            if (DeveloperDiagnostics.remaining > 0) DeveloperDiagnostics.finish("渲染模式改变，测试提前结束")
                            RenderPerformance.setEnabled(context, !RenderPerformance.lowPerformance)
                            refreshTick++
                        },
                        onToggleStaticBlur = {
                            RenderPerformance.setStaticBlur(context, !RenderPerformance.staticBlurEnabled)
                            notifyDesktopChanged()
                        },
                        onToggleAutoStart = {
                            val cur = DesktopPreferences.AutoStart.isEnabled(context)
                            DesktopPreferences.AutoStart.setEnabled(context, !cur)
                            notifyDesktopChanged()
                        },
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
                        onCheckUpdate = { scope.launch { AppUpdateManager.check(context) } }
                    )
                }
            }
        }
        }
    }
}

/** Settings sidebar with icon badges and high-contrast focus capsules. */
@Composable
private fun TvSettingsSidebar(
    selected: SettingsCategory,
    requesters: Map<SettingsCategory, FocusRequester>,
    rightFirstRequester: FocusRequester,
    onSelect: (SettingsCategory) -> Unit
) {
    LaunchedEffect(Unit) {
        // 组合初期节点可能尚未挂载，直接 requestFocus 会抛异常；失败由页面级焦点看门狗兜底
        runCatching { requesters[selected]?.requestFocus() }
    }

    Column(
        Modifier
            .width(420.dp)
            .fillMaxHeight()
            // 外框不留白，留白由侧边栏自身内边距承担
            .padding(start = 36.dp, end = 12.dp, top = 24.dp, bottom = 24.dp)
    ) {
        Text(
            "设置",
            color = White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 32.dp)
        )

        SettingsCategory.all.forEachIndexed { index, cat ->
            TvCategoryNavItem(
                category = cat,
                active = cat == selected,
                focusRequester = requesters.getValue(cat),
                upRequester = if (index > 0) requesters[SettingsCategory.all[index - 1]] else null,
                downRequester = if (index < SettingsCategory.all.lastIndex) requesters[SettingsCategory.all[index + 1]] else null,
                rightFirstRequester = rightFirstRequester,
                onSelect = { onSelect(cat) }
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TvCategoryNavItem(
    category: SettingsCategory,
    active: Boolean,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    rightFirstRequester: FocusRequester,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val raised = focused || hovered

    val scale by animateFloatAsState(if (raised) 1.03f else 1f, focusMotion(), label = "nav-scale")
    val shape = ContinuousCornerShape(20.dp)

    LaunchedEffect(focused) {
        if (focused) onSelect()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(74.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .focusRequester(focusRequester)
            .focusProperties {
                right = rightFirstRequester
                // 显式上下链 + 边界阻断：侧边栏内不依赖几何查找，防止焦点查出设置页外
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyDown) {
                        onSelect()
                        rightFirstRequester.requestFocus()
                    }
                    true
                } else false
            }
            .focusable(interactionSource = interaction)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .clip(shape)
            .background(
                when {
                    raised -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEEEEEE)))
                    active -> Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                width = if (raised) 2.dp else if (active) 1.dp else 0.dp,
                color = if (raised) Color.White else if (active) Color(0x40FFFFFF) else Color.Transparent,
                shape = shape
            )
            .focusSweep(raised, shape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(ContinuousCornerShape(12.dp))
                    .background(Brush.linearGradient(category.gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    category.icon,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(18.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    category.label,
                    color = if (raised) Color(0xFF14161B) else White,
                    fontSize = 23.sp,
                    fontWeight = if (active || raised) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    category.subtitle,
                    color = if (raised) Color(0xFF5A606D) else Color(0xFF9EA3AE),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            if (active && !raised) {
                Text(
                    "›",
                    color = Color(0x99FFFFFF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryItems(
    category: SettingsCategory,
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
    onToggleStaticBlur: () -> Unit,
    onToggleAutoStart: () -> Unit,
    onToggleForceLandscape: () -> Unit,
    onDefaultHome: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val items = remember(category, refreshKey, RenderPerformance.lowPerformance, RenderPerformance.staticBlurEnabled) { itemsFor(category, context) }

    // 从二级页面返回 / 行间上下导航：把焦点还原到目标行（focusedRowRequester 常驻挂在该行上）；
    // 先滚动到该行完全可见，再聚焦；找不到时退回第一行。
    LaunchedEffect(pendingFocusRow) {
        if (pendingFocusRow == null) return@LaunchedEffect
        val targetIndex = items.indexOfFirst { it.first == pendingFocusRow }
        if (targetIndex < 0 || targetIndex == 0) {
            runCatching { firstRequester.requestFocus() }
        } else {
            val itemIndex = targetIndex + 1 // LazyColumn 里第 0 个 item 是标题
            val margin = with(density) { 24.dp.toPx() }
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
            if (info == null) {
                val direction = if (targetIndex > items.indexOfFirst { it.first == focusedRow }) 1f else -1f
                listState.animateScrollBy(with(density) { 96.dp.toPx() } * direction, pageMotion())
            } else {
                val viewportH = listState.layoutInfo.viewportSize.height
                val delta = when {
                    info.offset < margin -> info.offset - margin
                    info.offset + info.size > viewportH - margin -> info.offset + info.size - (viewportH - margin)
                    else -> 0f
                }
                if (delta != 0f) listState.animateScrollBy(delta, pageMotion())
            }
            var grabbed = false
            var attempts = 0
            while (attempts < 30 && !grabbed) {
                grabbed = runCatching { focusedRowRequester.requestFocus() }.getOrDefault(false)
                if (grabbed) break
                withFrameNanos { }
                attempts++
            }
        }
        onReturnConsumed()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                // 行间上下导航显式驱动，不依赖几何焦点查找：
                // 列表行与被覆盖的桌面节点同处焦点树，几何查找会把焦点选到屏幕外的桌面层。
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionDown, Key.DirectionUp -> {
                        val cur = items.indexOfFirst { it.first == focusedRow }
                        val next = if (ev.key == Key.DirectionDown) cur + 1 else cur - 1
                        if (cur < 0) false
                        else if (next !in items.indices) true // 到边界：吃掉事件，防焦点逃出设置页
                        else {
                            onNavigateRow(items[next].first)
                            true
                        }
                    }
                    else -> false
                }
            },
        contentPadding = PaddingValues(top = 60.dp, bottom = 68.dp)
    ) {
        item(key = "heading-${category.name}") {
            Text(category.label, color = White, fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Text(category.subtitle, color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(bottom = 28.dp))
            SettingsSectionTitle("偏好选项")
        }

        itemsIndexed(items, key = { _, item -> item.first }) { idx, (label, subtitle) ->
            val isFirst = idx == 0
            val onClick: () -> Unit = when {
                label == "低性能模式" -> onToggleLowPerformance
                label == "静态模糊" -> onToggleStaticBlur
                label == "玻璃与模糊" -> onGlassTuning
                label == "隐藏应用管理" -> onHiddenApps
                label == "壁纸" -> onWallpaper
                label == "启动动画" -> onAnimation
                label == "开发者选项" -> onDeveloper
                label == "图标大小" -> onIconScale
                label == "时钟样式" -> onClockStyle
                label == "Dock 布局" -> onDockStyle
                label == "网格密度" -> onGridDensity
                label == "文件夹页码样式" -> onFolderIndicator
                label == "启动文件夹内应用后" -> onFolderLaunch
                label == "应用排列" -> onAppSort
                label == "屏幕保护" -> onScreenSaver
                label == "默认播放器" -> onPlaybackEngine
                label == "启动默认页" -> onStartupPage
                category == SettingsCategory.PLUGINS -> onPlugins
                category == SettingsCategory.LIBRARY -> onMediaLibrary
                label == "开机自启动" -> onToggleAutoStart
                label == "强制横屏" -> onToggleForceLandscape
                label == "设置默认桌面" -> onDefaultHome
                label == "检查更新" -> onCheckUpdate
                else -> ({})
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
                            onRowFocused(label)
                            scope.launch {
                                // LazyColumn performs its own focus relocation first. Correct its
                                // final position afterwards so the scaled card never rests against
                                // (or behind) the clipped top/bottom edge of the viewport.
                                delay(90)
                                val info = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == idx + 1 } ?: return@launch
                                val margin = with(density) { 18.dp.toPx() }
                                val safeTop = margin
                                val safeBottom = listState.layoutInfo.viewportSize.height - margin
                                val delta = when {
                                    info.offset < safeTop -> info.offset - safeTop
                                    info.offset + info.size > safeBottom -> info.offset + info.size - safeBottom
                                    else -> 0f
                                }
                                if (delta != 0f) listState.animateScrollBy(delta, pageMotion())
                            }
                        }
                    }
            ) {
                SettingsRowItem(
                    label = label,
                    value = subtitle,
                    focusRequester = when {
                        isFirst -> firstRequester
                        label == focusedRow -> focusedRowRequester
                        else -> null
                    },
                    leftReturnRequester = leftReturnRequester,
                    onClick = onClick
                )
            }
            Spacer(Modifier.height(10.dp))
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
        SettingsRowItem("← 返回桌面设置", null, back, returnRequester, onBack)
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
            .border(1.5.dp, if (active) Color.White else Color(0x20FFFFFF), shape)
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

    val scale by animateFloatAsState(if (raised) 1.025f else 1f, focusMotion(), label = "row-scale")
    val shape = ContinuousCornerShape(18.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
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
            .focusable(interactionSource = interaction)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
            .background(
                if (raised) Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F2)))
                else Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x18FFFFFF)))
            )
            .border(
                width = if (raised) 2.dp else 1.dp,
                color = if (raised) Color.White else Color(0x1AFFFFFF),
                shape = shape
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
                fontWeight = if (raised) FontWeight.SemiBold else FontWeight.Normal
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        value,
                        color = if (raised) Color(0xFF4A4E58) else Color(0xFF9AA0AD),
                        fontSize = 19.sp,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Text(
                    "›",
                    color = if (raised) Color(0xFF14161B) else Color(0xFF7E8492),
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
    val shape = ContinuousCornerShape(18.dp)

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
            .focusable(interactionSource = interaction)
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
            .border(
                width = if (raised) 2.dp else if (selected) 1.5.dp else 1.dp,
                color = if (raised) Color.White else if (selected) Color(0x50FFFFFF) else Color(0x14FFFFFF),
                shape = shape
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
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onBack)
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val date = Date(now)
    val timeWithSeconds = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(date)
    val timeMinimal = SimpleDateFormat("HH:mm", Locale.CHINA).format(date)
    val dateText = SimpleDateFormat("M月d日  EEEE", Locale.CHINA).format(date)

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
                    Text(timeWithSeconds, color = White, fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Spacer(Modifier.height(6.dp))
                    Text(dateText, color = Color(0xD9FFFFFF), fontSize = 18.sp)
                }
            }
            DesktopPreferences.ClockStyle.MINIMAL -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeMinimal, color = White, fontSize = 76.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
                    Spacer(Modifier.height(4.dp))
                    Text(dateText, color = Color(0xD9FFFFFF), fontSize = 18.sp)
                }
            }
            DesktopPreferences.ClockStyle.SPLIT -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(timeMinimal, color = White, fontSize = 72.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.width(2.dp).height(50.dp).background(Color(0x60FFFFFF)))
                    Column {
                        Text(SimpleDateFormat("M月d日", Locale.CHINA).format(date), color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(SimpleDateFormat("EEEE", Locale.CHINA).format(date), color = Color(0xB3FFFFFF), fontSize = 17.sp)
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
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onBack)
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
        SettingsRowItem("← 返回分类", null, backRequester, returnRequester, onBack)
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
