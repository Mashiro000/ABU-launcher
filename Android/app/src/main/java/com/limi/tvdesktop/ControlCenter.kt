package com.limi.tvdesktop

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*
import kotlinx.coroutines.launch

/** Compact avatar menu: actual app actions and supported device settings. */
@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun ControlCenter(
    haze: HazeState,
    staticBlur: ImageBitmap?,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    onSwitchMode: () -> Unit = {},
    onSignalSources: () -> Unit = {},
    onHiddenApps: () -> Unit = {},
    onApps: () -> Unit = {},
    onSearch: () -> Unit = {},
    onAnimationSettings: () -> Unit = {},
    deviceMode: DesktopPreferences.DeviceMode = DesktopPreferences.DeviceMode.TV,
    origin: Offset? = null,
) {
    val context = LocalContext.current
    val canvasHeight = LocalCanvasHeight.current
    val scope = rememberCoroutineScope()
    val reveal = remember { Animatable(0f) }
    val first = remember { FocusRequester() }
    var closing by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    val cachedMenuBlur = staticBlur != null && !RenderPerformance.reducedEffects &&
        RenderPerformance.blurMode != RenderPerformance.BlurMode.NONE
    val wifi = remember {
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "已连接"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网连接"
                else -> "网络设置"
            }
        }.getOrDefault("网络设置")
    }
    val bluetooth = remember {
        runCatching {
            if (context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true) "已开启" else "蓝牙设置"
        }.getOrDefault("蓝牙设置")
    }

    fun dismiss(action: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            reveal.animateTo(0f, tween(RenderPerformance.duration(240), easing = CubicBezierEasing(.4f, 0f, 1f, 1f)))
            onClose()
            action?.invoke()
        }
    }
    fun openSystem(primary: String, fallback: String? = null) {
        dismiss {
            val opened = runCatching { context.startActivity(Intent(primary).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess ||
                (fallback != null && runCatching { context.startActivity(Intent(fallback).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess)
            if (!opened) Toast.makeText(context, "此设备未提供对应设置入口", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler { dismiss() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
        if (RenderPerformance.motionMode == RenderPerformance.MotionMode.FULL) {
            reveal.animateTo(1f, keyframes {
                durationMillis = 460
                0f at 0 using CubicBezierEasing(.16f, 1f, .3f, 1f)
                1.025f at 300 using CubicBezierEasing(.4f, 0f, .2f, 1f)
                1f at 460
            })
        } else reveal.animateTo(1f, tween(RenderPerformance.duration(160)))
    }

    Box(Modifier.fillMaxSize().zIndex(300f)
        .onPreviewKeyEvent {
            if (it.key == Key.Escape || it.key == Key.Back) {
                if (it.type == KeyEventType.KeyUp) dismiss()
                true
            } else false
        }
        .focusProperties { onExit = { cancelFocusChange() } }.focusGroup()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .08f * reveal.value.coerceIn(0f, 1f)))
            .pointerInput(Unit) { detectTapGestures { dismiss() } })
        val shape = ContinuousCornerShape(28.dp)
        Box(Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 48.dp).width(420.dp)
            .onGloballyPositioned { panelBounds = it.boundsInRoot() }) {
        Column(Modifier.fillMaxWidth()
            .graphicsLayer {
                // Both values use the same root-pixel coordinates, including density and display insets.
                transformOrigin = if (origin != null && panelBounds.width > 0f && panelBounds.height > 0f)
                    TransformOrigin((origin.x - panelBounds.left) / panelBounds.width, (origin.y - panelBounds.top) / panelBounds.height)
                    else TransformOrigin(1f, 0f)
                val scale = if (RenderPerformance.motionMode == RenderPerformance.MotionMode.REDUCED) .9f + .1f * reveal.value else reveal.value
                scaleX = scale.coerceAtLeast(.001f)
                scaleY = scale.coerceAtLeast(.001f)
                alpha = reveal.value.coerceIn(0f, 1f)
            }
            .clip(shape)
            .then(if (cachedMenuBlur) Modifier.drawBehind {
                drawCoverWallpaper(staticBlur!!, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()),
                    IntOffset(-1204.dp.roundToPx(), -104.dp.roundToPx()))
                drawRect(Color(0xCC141414))
            } else if (RenderPerformance.blur31) Modifier.hazeEffect(haze) {
                blurRadius = 28.dp
                backgroundColor = Color.Transparent
                tints = listOf(HazeTint(Color(0xCC141414)))
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(.3f)
            } else Modifier.drawBehind {
                if (RenderPerformance.staticBlur && staticBlur != null) {
                    drawCoverWallpaper(staticBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()),
                        IntOffset(-1204.dp.roundToPx(), -104.dp.roundToPx()))
                }
                drawRect(Color(0xCC141414))
            })
            .padding(19.dp)
            .semantics { contentDescription = "快捷菜单" }) {
            Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(QuickIcon.MODE, "切换模式", deviceMode.label,
                    Modifier.weight(1f).fillMaxHeight().focusRequester(first), large = true) { dismiss(onSwitchMode) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile(QuickIcon.WIFI, "Wi-Fi", wifi, Modifier.fillMaxWidth().weight(1f)) {
                        openSystem(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
                    }
                    QuickTile(QuickIcon.BLUETOOTH, "蓝牙", bluetooth, Modifier.fillMaxWidth().weight(1f)) {
                        openSystem(Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(QuickIcon.INPUT, "信号源", null, Modifier.weight(1f).fillMaxHeight()) { dismiss(onSignalSources) }
                QuickTile(QuickIcon.HIDDEN, "隐藏应用", null, Modifier.weight(1f).fillMaxHeight()) { dismiss(onHiddenApps) }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile(QuickIcon.APPS, "应用列表", null, Modifier.weight(1f).fillMaxHeight(), small = true) { dismiss(onApps) }
                QuickTile(QuickIcon.SEARCH, "搜索", null, Modifier.weight(1f).fillMaxHeight(), small = true) { dismiss(onSearch) }
                QuickTile(QuickIcon.ANIMATION, "动画", null, Modifier.weight(1f).fillMaxHeight(), small = true) { dismiss(onAnimationSettings) }
                QuickTile(QuickIcon.SETTINGS, "设置", null, Modifier.weight(1f).fillMaxHeight(), small = true) { dismiss(onSettings) }
            }
        }
        }
    }

}

private enum class QuickIcon { MODE, WIFI, BLUETOOTH, INPUT, HIDDEN, APPS, SEARCH, ANIMATION, SETTINGS }

@Composable
private fun QuickTile(icon: QuickIcon, title: String, subtitle: String?, modifier: Modifier,
    large: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val emphasis by animateFloatAsState(if (active) 1f else 0f, tween(RenderPerformance.duration(180)), label = "quick-focus")
    val foreground = lerp(Color(0xFFEDEDE6), Color(0xFF151713), emphasis)
    val background = lerp(Color(0x80050505), Color(0xFFF2F3EB), emphasis)
    Box(modifier.graphicsLayer {
        val s = 1f + .025f * emphasis
        scaleX = s; scaleY = s
    }.clip(ContinuousCornerShape(if (large) 22.dp else 17.dp)).background(background)
        .focusProperties { canFocus = true }
        .hoverable(interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .padding(if (large) 16.dp else 10.dp), contentAlignment = Alignment.Center) {
        if (large || small) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QuickIconView(icon, if (large) 76.dp else 28.dp, foreground)
            Spacer(Modifier.height(if (large) 12.dp else 6.dp))
            Text(title, color = foreground, fontSize = if (large) 22.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = foreground.copy(alpha = .65f), fontSize = 13.sp, maxLines = 1)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickIconView(icon, 30.dp, foreground)
            Column {
                Text(title, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (subtitle != null) Text(subtitle, color = foreground.copy(alpha = .65f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private object QuickPaths {
    val paths = mapOf(
        QuickIcon.MODE to "M3 5 H16 V2 L22 8 L16 14 V11 H3 Z M21 19 H8 V22 L2 16 L8 10 V13 H21 Z",
        QuickIcon.WIFI to "M2 7 Q12 -1 22 7 L19 10 Q12 4 5 10 Z M6 12 Q12 7 18 12 L15 15 Q12 12 9 15 Z M12 16 A3 3 0 1 1 12 22 A3 3 0 1 1 12 16 Z",
        QuickIcon.BLUETOOTH to "M10 1 L20 8 L14 12 L20 16 L10 23 V15 L4 19 L2 16 L8 12 L2 8 L4 5 L10 9 Z M13 6 V10 L16 8 Z M13 14 V18 L16 16 Z",
        QuickIcon.INPUT to "M3 3 H21 V21 H3 Z M6 6 V18 H18 V6 Z M1 10 H10 V7 L15 12 L10 17 V14 H1 Z",
        QuickIcon.HIDDEN to "M1 12 Q12 -2 23 12 Q12 26 1 12 Z M12 7 A5 5 0 1 1 12 17 A5 5 0 1 1 12 7 Z M3 1 L23 21 L21 23 L1 3 Z",
        QuickIcon.SEARCH to "M10 2 A8 8 0 1 1 10 18 A8 8 0 1 1 10 2 Z M10 5 A5 5 0 1 0 10 15 A5 5 0 1 0 10 5 Z M15 13 L23 21 L21 23 L13 15 Z",
        QuickIcon.ANIMATION to "M8 3 L23 12 L8 21 Z M1 5 H4 V8 H1 Z M1 10 H6 V14 H1 Z M1 16 H4 V19 H1 Z",
    ).mapValues { (_, data) -> PathParser().parsePathString(data).toPath().apply { fillType = PathFillType.EvenOdd } }
}

@Composable
private fun QuickIconView(icon: QuickIcon, size: Dp, color: Color) {
    when (icon) {
        QuickIcon.APPS -> SettingsModuleIcon(SettingsCategory.APPLICATIONS, size, color)
        QuickIcon.SETTINGS -> SettingsModuleIcon(SettingsCategory.GENERAL, size, color)
        else -> Canvas(Modifier.size(size)) {
            scale(this.size.width / 24f, pivot = Offset.Zero) { drawPath(QuickPaths.paths.getValue(icon), color) }
        }
    }
}
