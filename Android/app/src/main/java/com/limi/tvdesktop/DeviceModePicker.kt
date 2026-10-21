package com.limi.tvdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.*

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun DeviceModePicker(
    haze: HazeState, staticBlur: ImageBitmap?, current: DesktopPreferences.DeviceMode,
    onSelect: (DesktopPreferences.DeviceMode) -> Unit, onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val initial = remember { current }
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    CenteredSelectionPanel(haze, staticBlur, "设备模式选择", onDismiss) {
        DesktopPreferences.DeviceMode.entries.forEach { mode ->
            key(mode) {
                SelectionTile(mode.label, if (mode == current) "当前模式" else "点击切换", mode == current,
                    Modifier.weight(1f).fillMaxHeight().then(if (mode == initial) Modifier.focusRequester(first) else Modifier),
                    icon = { DeviceModeIcon(mode, it) }) { onSelect(mode) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun CenteredSelectionPanel(
    haze: HazeState, staticBlur: ImageBitmap?, description: String, onDismiss: () -> Unit,
    panelHeight: androidx.compose.ui.unit.Dp = 228.dp,
    onBack: () -> Unit = onDismiss,
    content: @Composable RowScope.() -> Unit,
) {
    val canvasHeight = LocalCanvasHeight.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(RenderPerformance.duration(120))) }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().zIndex(310f).onPreviewKeyEvent {
        if (it.key == Key.Back || it.key == Key.Escape) {
            if (it.type == KeyEventType.KeyUp) onBack()
            true
        } else false
    }.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .12f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } })
        Row(Modifier.align(Alignment.Center).width(900.dp).height(panelHeight)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                alpha = reveal.value
                scaleX = if (RenderPerformance.reducedEffects) 1f else .96f + .04f * reveal.value
                scaleY = scaleX
            }.clip(ContinuousCornerShape(30.dp))
            .then(if (staticBlur != null && RenderPerformance.blurMode != RenderPerformance.BlurMode.NONE)
                Modifier.drawBehind {
                    drawCoverWallpaper(staticBlur, IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()),
                        IntOffset(-bounds.left.toInt(), -bounds.top.toInt()))
                    drawRect(Color(0xCC141414))
                }
            else if (RenderPerformance.blur31) Modifier.hazeEffect(haze) {
                blurRadius = 40.dp
                backgroundColor = Color.Transparent
                tints = listOf(HazeTint(Color(0xCC141414)))
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(.3f)
            } else Modifier.background(Color(0xCC141414)))
            .padding(20.dp).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
internal fun SelectionTile(title: String, subtitle: String, chosen: Boolean, modifier: Modifier,
    icon: @Composable (Color) -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val foreground = if (chosen) Color(0xFF151713) else Color.White
    Column(modifier.focusProperties { canFocus = true }.clip(ContinuousCornerShape(22.dp))
        .background(if (chosen) Color(0xF2F2F3EB) else Color.White.copy(alpha = if (focused) .24f else .06f))
        .semantics { selected = chosen }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        icon(foreground)
        Spacer(Modifier.height(14.dp))
        Text(title, color = foreground, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = foreground.copy(alpha = .65f), fontSize = 15.sp, maxLines = 2)
    }
}

private val modePaths = mapOf(
    DesktopPreferences.DeviceMode.CONSOLE to "M7 5 H17 Q20 5 21 9 L23 18 Q23 21 20 21 L15 17 H9 L4 21 Q1 21 1 18 L3 9 Q4 5 7 5 Z M6 9 V11 H4 V13 H6 V15 H8 V13 H10 V11 H8 V9 Z M16 10 A1.5 1.5 0 1 0 16 13 A1.5 1.5 0 1 0 16 10 Z M19 13 A1.5 1.5 0 1 0 19 16 A1.5 1.5 0 1 0 19 13 Z",
    DesktopPreferences.DeviceMode.HANDHELD to "M3 5 H21 Q24 5 24 8 V17 Q24 20 21 20 H3 Q0 20 0 17 V8 Q0 5 3 5 Z M6 8 V17 H18 V8 Z M2 10 V12 H1 V14 H2 V16 H4 V14 H5 V12 H4 V10 Z M21 9 A1 1 0 1 0 21 11 A1 1 0 1 0 21 9 Z M22 13 A1 1 0 1 0 22 15 A1 1 0 1 0 22 13 Z",
    DesktopPreferences.DeviceMode.TV to "M2 3 H22 Q24 3 24 5 V17 Q24 19 22 19 H14 V21 H19 V23 H5 V21 H10 V19 H2 Q0 19 0 17 V5 Q0 3 2 3 Z M3 6 V16 H21 V6 Z",
).mapValues { (_, data) -> PathParser().parsePathString(data).toPath().apply { fillType = PathFillType.EvenOdd } }

@Composable
internal fun DeviceModeIcon(mode: DesktopPreferences.DeviceMode, color: Color) {
    Canvas(Modifier.size(56.dp)) {
        scale(size.width / 24f, pivot = Offset.Zero) { drawPath(modePaths.getValue(mode), color) }
    }
}
