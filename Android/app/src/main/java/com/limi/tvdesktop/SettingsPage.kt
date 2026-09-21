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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.*
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
    SettingsCategory.GENERAL -> listOf(
        "设置默认桌面" to "阿布桌面",
        "网络连接状态" to "Wi-Fi 已连接",
        "系统语言" to "简体中文",
        "设备存储空间" to "可用 48.6 GB / 64 GB",
        "软件版本号" to "0.1.0-tvOS",
        "开发者选项" to "已就绪"
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
    BackHandler {
        when {
            detail != null -> {
                detail = null
                leftRequesters[category]?.requestFocus()
            }
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
                if (Build.VERSION.SDK_INT >= 31 && haze != null) {
                    Modifier.hazeEffect(haze) {
                        blurRadius = 64.dp
                        backgroundColor = Color.Transparent
                        tints = listOf(HazeTint(Color(0xD90E1116)))
                        noiseFactor = 0f
                        inputScale = HazeInputScale.Fixed(0.5f)
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
                    if (it.type == KeyEventType.KeyDown) {
                        when {
                            detail != null -> {
                                detail = null
                                leftRequesters[category]?.requestFocus()
                            }
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
                    else -> CategoryItems(
                        category = category,
                        refreshKey = refreshTick,
                        firstRequester = rightFirstRequester,
                        leftReturnRequester = leftRequesters[category],
                        onWallpaper = { detail = "wallpaper" },
                        onAnimation = { detail = "animation" },
                        onIconScale = { detail = "icon_scale" },
                        onClockStyle = { detail = "clock_style" },
                        onDockStyle = { detail = "dock_style" },
                        onGridDensity = { detail = "grid_density" },
                        onScreenSaver = { detail = "screensaver" },
                        onMediaLibrary = { detail = "media_library" },
                        onToggleAutoStart = {
                            val cur = DesktopPreferences.AutoStart.isEnabled(context)
                            DesktopPreferences.AutoStart.setEnabled(context, !cur)
                            notifyDesktopChanged()
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
    onAnimation: () -> Unit,
    onIconScale: () -> Unit,
    onClockStyle: () -> Unit,
    onDockStyle: () -> Unit,
    onGridDensity: () -> Unit,
    onScreenSaver: () -> Unit,
    onMediaLibrary: () -> Unit,
    onToggleAutoStart: () -> Unit,
    onDefaultHome: () -> Unit
) {
    val context = LocalContext.current
    val items = remember(category, refreshKey) { itemsFor(category, context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 40.dp)
    ) {
        Text(
            category.label,
            color = White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            category.subtitle,
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 28.dp)
        )

        SettingsSectionTitle("偏好选项")

        items.forEachIndexed { idx, (label, subtitle) ->
            val isFirst = idx == 0
            val onClick: () -> Unit = when {
                label == "壁纸" -> onWallpaper
                label == "启动动画" -> onAnimation
                label == "图标大小" -> onIconScale
                label == "时钟样式" -> onClockStyle
                label == "Dock 布局" -> onDockStyle
                label == "网格密度" -> onGridDensity
                label == "屏幕保护" -> onScreenSaver
                category == SettingsCategory.LIBRARY -> onMediaLibrary
                label == "开机自启动" -> onToggleAutoStart
                label == "设置默认桌面" -> onDefaultHome
                else -> ({})
            }

            SettingsRowItem(
                label = label,
                value = subtitle,
                focusRequester = if (isFirst) firstRequester else null,
                leftReturnRequester = leftReturnRequester,
                onClick = onClick
            )
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
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val raised = focused || hovered

    val scale by animateFloatAsState(if (raised) 1.025f else 1f, tween(180), label = "row-scale")
    val shape = ContinuousCornerShape(18.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
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
