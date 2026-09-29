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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

internal enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: String,
    val gradientColors: List<Color>
) {
    DESKTOP(
        "桌面与外观",
        "壁纸、布局、动画与个性化",
        "▣",
        listOf(Color(0xFF3875F6), Color(0xFF634AF0))
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
        "通用与系统",
        "桌面默认、网络、存储与关于",
        "⚙",
        listOf(Color(0xFF64748B), Color(0xFF475569))
    );

    companion object {
        val all = SettingsCategory.entries
    }
}

private fun itemsFor(category: SettingsCategory, context: Context): List<Pair<String, String?>> = when (category) {
    SettingsCategory.DESKTOP -> listOf(
        "低性能模式" to if (RenderPerformance.lowPerformance) "已开启 · 精简动画与合成，优先流畅" else if (RenderPerformance.reducedEffects) "安卓 9 自动兼容 · 精简动画" else "已关闭 · 点击开启，优先流畅",
        "静态模糊" to if (RenderPerformance.staticBlurEnabled) "已开启（推荐）· 预生成一次，移动时不重复模糊" else "已关闭 · 使用实时模糊（性能要求较高）",
        "玻璃与模糊" to "导航条、Dock 与应用列表的模糊度和不透明度",
        "壁纸" to "自定义壁纸来源与预览",
        "启动动画" to LaunchAnim.current(context).label,
        "图标大小" to DesktopPreferences.IconScale.current(context).label,
        "时钟样式" to DesktopPreferences.ClockStyle.current(context).label,
        "Dock 布局" to DesktopPreferences.DockStyle.current(context).label,
        "网格密度" to DesktopPreferences.GridDensity.current(context).label,
        "屏幕保护" to DesktopPreferences.ScreenSaverTimeout.current(context).label,
        "开机自启动" to if (DesktopPreferences.AutoStart.isEnabled(context)) "已开启" else "已关闭"
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
        "强制横屏" to if (DesktopPreferences.ForceLandscape.isEnabled(context)) "已开启 · 手机安装时始终保持横屏" else "已关闭 · 跟随系统方向",
        "设置默认桌面" to "阿布桌面",
        "网络连接状态" to "Wi-Fi 已连接",
        "系统语言" to "简体中文",
        "设备存储空间" to "可用 48.6 GB / 64 GB",
        "软件版本号" to "0.1.0-tvOS",
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
@OptIn(ExperimentalHazeApi::class)
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
    var focusInRightSide by remember { mutableStateOf(false) }

    fun notifyDesktopChanged() {
        refreshTick++
        onWallpaperChanged()
    }

    // Multilevel back-press handler for TV remote
    BackHandler(enabled = detail == null) {
        when {
            focusInRightSide -> {
                leftRequesters[category]?.requestFocus()
            }
            else -> onClose()
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
                            else -> onClose()
                        }
                    }
                    true
                } else false
            }
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
    ) {
        if (RenderPerformance.staticBlur && staticBlur != null) {
            Image(staticBlur, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color(0xD90E1116)))
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 48.dp)
        ) {
            // Left sidebar: Apple TV tvOS category list
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
                    .padding(start = 56.dp)
                    .onFocusChanged { focusInRightSide = it.hasFocus }
            ) {
                when (detail) {
                    "wallpaper" -> WallpaperSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onWallpaperChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "glass_tuning" -> GlassTuningSettings(
                        onBack = { detail = null; leftRequesters[category]?.requestFocus() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "developer" -> DeveloperSettings(
                        onBack = { detail = null; leftRequesters[category]?.requestFocus() },
                        returnRequester = leftRequesters[category],
                        onTestDesktop = onClose
                    )
                    "animation" -> AnimationSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        returnRequester = leftRequesters[category]
                    )
                    "icon_scale" -> IconScaleSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "clock_style" -> ClockStyleSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "dock_style" -> DockStyleSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "grid_density" -> GridDensitySettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "screensaver" -> ScreenSaverSettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "media_library" -> MediaLibrarySettings(
                        onBack = {
                            detail = null
                            leftRequesters[category]?.requestFocus()
                        },
                        returnRequester = leftRequesters[category]
                    )
                    "playback_engine" -> PlaybackEngineSettings(
                        onBack = { detail = null; leftRequesters[category]?.requestFocus() },
                        onChanged = ::notifyDesktopChanged,
                        returnRequester = leftRequesters[category]
                    )
                    "plugins" -> PluginSettingsPage(
                        onBack = { detail = null; leftRequesters[category]?.requestFocus() },
                        returnRequester = leftRequesters[category]
                    )
                    else -> CategoryItems(
                        category = category,
                        refreshKey = refreshTick,
                        firstRequester = rightFirstRequester,
                        leftReturnRequester = leftRequesters[category],
                        onWallpaper = { detail = "wallpaper" },
                        onGlassTuning = { detail = "glass_tuning" },
                        onAnimation = { detail = "animation" },
                        onDeveloper = { detail = "developer" },
                        onIconScale = { detail = "icon_scale" },
                        onClockStyle = { detail = "clock_style" },
                        onDockStyle = { detail = "dock_style" },
                        onGridDensity = { detail = "grid_density" },
                        onScreenSaver = { detail = "screensaver" },
                        onMediaLibrary = { detail = "media_library" },
                        onPlaybackEngine = { detail = "playback_engine" },
                        onPlugins = { detail = "plugins" },
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
                        onDefaultHome = { openDefaultHomeSettings(context) }
                    )
                }
            }
        }
    }
}

/** tvOS left sidebar with vibrant icon badges and high-contrast focus capsules */
@Composable
private fun TvSettingsSidebar(
    selected: SettingsCategory,
    requesters: Map<SettingsCategory, FocusRequester>,
    rightFirstRequester: FocusRequester,
    onSelect: (SettingsCategory) -> Unit
) {
    LaunchedEffect(Unit) {
        requesters[selected]?.requestFocus()
    }

    Column(
        Modifier
            .width(420.dp)
            .fillMaxHeight()
            .padding(top = 12.dp)
    ) {
        Text(
            "设置",
            color = White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, bottom = 32.dp)
        )

        SettingsCategory.all.forEach { cat ->
            TvCategoryNavItem(
                category = cat,
                active = cat == selected,
                focusRequester = requesters.getValue(cat),
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
    rightFirstRequester: FocusRequester,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val raised = focused || hovered

    val scale by animateFloatAsState(if (raised) 1.03f else 1f, tween(180), label = "nav-scale")
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
    onWallpaper: () -> Unit,
    onGlassTuning: () -> Unit,
    onAnimation: () -> Unit,
    onDeveloper: () -> Unit,
    onIconScale: () -> Unit,
    onClockStyle: () -> Unit,
    onDockStyle: () -> Unit,
    onGridDensity: () -> Unit,
    onScreenSaver: () -> Unit,
    onMediaLibrary: () -> Unit,
    onPlaybackEngine: () -> Unit,
    onPlugins: () -> Unit,
    onToggleLowPerformance: () -> Unit,
    onToggleStaticBlur: () -> Unit,
    onToggleAutoStart: () -> Unit,
    onToggleForceLandscape: () -> Unit,
    onDefaultHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val items = remember(category, refreshKey, RenderPerformance.lowPerformance, RenderPerformance.staticBlurEnabled) { itemsFor(category, context) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp)
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
                label == "壁纸" -> onWallpaper
                label == "启动动画" -> onAnimation
                label == "开发者选项" -> onDeveloper
                label == "图标大小" -> onIconScale
                label == "时钟样式" -> onClockStyle
                label == "Dock 布局" -> onDockStyle
                label == "网格密度" -> onGridDensity
                label == "屏幕保护" -> onScreenSaver
                label == "默认播放器" -> onPlaybackEngine
                category == SettingsCategory.PLUGINS -> onPlugins
                category == SettingsCategory.LIBRARY -> onMediaLibrary
                label == "开机自启动" -> onToggleAutoStart
                label == "强制横屏" -> onToggleForceLandscape
                label == "设置默认桌面" -> onDefaultHome
                else -> ({})
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.hasFocus) {
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
                                if (delta != 0f) listState.animateScrollBy(delta)
                            }
                        }
                    }
            ) {
                SettingsRowItem(
                    label = label,
                    value = subtitle,
                    focusRequester = if (isFirst) firstRequester else null,
                    leftReturnRequester = leftReturnRequester,
                    onClick = onClick
                )
            }
            Spacer(Modifier.height(10.dp))
        }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 120.dp)) {
        SettingsRowItem("← 返回桌面设置", null, back, returnRequester, onBack)
        Spacer(Modifier.height(22.dp))
        Text("玻璃与模糊", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("触屏拖动；遥控器、键盘和手柄使用左右键微调", color = Color(0xFFA5ACB8), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

        SettingsSectionTitle("顶部导航条")
        GlassSliderRow("模糊度", navBlur, 0f..60f, "${navBlur.toInt()} dp", onValue = { navBlur = it }, onCommit = { save("glassNavBlur", it) }, onReset = { navBlur = DesktopPreferences.GlassTuning.NAV_BLUR_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassNavBlur"); onChanged() })
        GlassSliderRow("不透明度", navOpacity, 0f..0.9f, "${(navOpacity * 100).toInt()}%", step = .05f, onValue = { navOpacity = it }, onCommit = { save("glassNavOpacity", it) }, onReset = { navOpacity = DesktopPreferences.GlassTuning.NAV_OPACITY_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassNavOpacity"); onChanged() })

        SettingsSectionTitle("Dock 栏")
        GlassSliderRow("模糊度", dockBlur, 0f..60f, "${dockBlur.toInt()} dp", onValue = { dockBlur = it }, onCommit = { save("glassDockBlur", it) }, onReset = { dockBlur = DesktopPreferences.GlassTuning.DOCK_BLUR_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassDockBlur"); onChanged() })
        GlassSliderRow("不透明度", dockOpacity, 0f..0.9f, "${(dockOpacity * 100).toInt()}%", step = .05f, onValue = { dockOpacity = it }, onCommit = { save("glassDockOpacity", it) }, onReset = { dockOpacity = DesktopPreferences.GlassTuning.DOCK_OPACITY_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassDockOpacity"); onChanged() })

        SettingsSectionTitle("进入应用列表后的背景")
        GlassSliderRow("背景模糊度", appBlur, 0f..80f, "${appBlur.toInt()} dp", onValue = { appBlur = it }, onCommit = { save("glassAppListBlur", it) }, onReset = { appBlur = DesktopPreferences.GlassTuning.APP_LIST_BLUR_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassAppListBlur"); onChanged() })
        GlassSliderRow("背景不透明度", appOpacity, 0f..0.9f, "${(appOpacity * 100).toInt()}%", step = .05f, onValue = { appOpacity = it }, onCommit = { save("glassAppListOpacity", it) }, onReset = { appOpacity = DesktopPreferences.GlassTuning.APP_LIST_OPACITY_DEFAULT; DesktopPreferences.GlassTuning.reset(context, "glassAppListOpacity"); onChanged() })
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
    onReset: () -> Unit
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    Row(
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
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).clip(ContinuousCornerShape(16.dp)).background(Color(0x16FFFFFF)).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(valueText, color = Color(0xFF78A7FF), fontSize = 17.sp)
            }
            Slider(
                value = value,
                onValueChange = onValue,
                onValueChangeFinished = { onCommit(value) },
                valueRange = range,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF4A89FF), inactiveTrackColor = Color(0x405E6775)),
                modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> return@onPreviewKeyEvent false }
                    val next = (value + delta).coerceIn(range.start, range.endInclusive)
                    onValue(next); onCommit(next); true
                }
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(170.dp)) { SettingsRowItem("恢复默认", null, onClick = onReset) }
    }
}

/** tvOS Inset Grouped card item with focus zoom, pure white background on focus, and robust remote D-pad click */
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

    val scale by animateFloatAsState(if (raised) 1.025f else 1f, tween(180), label = "row-scale")
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

    val scale by animateFloatAsState(if (raised) 1.025f else 1f, tween(180), label = "radio-scale")
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 48.dp)
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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
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
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
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
