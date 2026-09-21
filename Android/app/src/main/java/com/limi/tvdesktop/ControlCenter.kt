package com.limi.tvdesktop

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.material3.Text
import dev.chrisbanes.haze.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// One soft spring drives both the pop and the retract, so the scale ratio is identical in and out.
private val PopSpring = spring<Float>(dampingRatio = .7f, stiffness = 90f)

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun ControlCenter(
    haze: HazeState,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    // 0f = collapsed, 1f = fully open. Played in reverse to close.
    val pop = remember { Animatable(0f) }
    val blurProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val blurExit = launch { blurProgress.animateTo(0f, tween(380)) }
            pop.animateTo(0f, PopSpring)
            blurExit.join()
            onClose()
        }
    }

    BackHandler(onBack = { close() })
    val first = remember { FocusRequester() }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.CHINA).format(now)
            date = SimpleDateFormat("M月d日  EEEE", Locale.CHINA).format(now)
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    LaunchedEffect(Unit) {
        launch { blurProgress.animateTo(1f, tween(380)) }
        pop.animateTo(1f, PopSpring)
    }

    Box(Modifier.fillMaxSize().zIndex(300f)
        .onPreviewKeyEvent {
            if (it.key == Key.Escape || it.key == Key.Back) {
                if (it.type == KeyEventType.KeyDown) close()
                true
            } else false
        }
        .focusProperties { onExit = { cancelFocusChange() } }
        .focusGroup()) {
        // Blur the full desktop as one stationary layer. It must not share the panel's scale,
        // otherwise Haze appears to sweep horizontally while the panel opens.
        Box(Modifier.fillMaxSize()
            .then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeEffect(haze) {
                blurRadius = (28f * blurProgress.value).coerceAtLeast(.01f).dp
                backgroundColor = Color.Transparent
                tints = listOf(HazeTint(Color.Black.copy(alpha = .15f * blurProgress.value)))
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(.5f)
            } else Modifier.background(Color.Black.copy(alpha = .4f * blurProgress.value)))
            .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        })
        val panelShape = ContinuousCornerShape(30.dp)
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 34.dp).width(820.dp).wrapContentHeight()) {
        // Keep sampling coordinates fixed; reveal the sampled surface with a moving clip.
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            // Reserve real render-target space around the panel for spring overshoot.
            // Expanding only the clip path cannot expand Haze's underlying texture.
            val bleed = 96.dp.roundToPx()
            val child = measurable.measure(Constraints.fixed(
                constraints.maxWidth + bleed * 2, constraints.maxHeight + bleed * 2))
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(-bleed, -bleed) }
        }.graphicsLayer {
            clip = true
            val s = pop.value.coerceAtLeast(.0001f)
            shape = object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                    val bleed = with(density) { 96.dp.toPx() }
                    val panelWidth = size.width - bleed * 2
                    val panelHeight = size.height - bleed * 2
                    val path = Path().apply {
                        addOutline(panelShape.createOutline(Size(panelWidth * s, panelHeight * s), layoutDirection, density))
                        translate(Offset(bleed + panelWidth * (1f - s), bleed))
                    }
                    return Outline.Generic(path)
                }
            }
        }.then(if (Build.VERSION.SDK_INT >= 31) Modifier.hazeEffect(haze) {
            blurRadius = 102.dp
            backgroundColor = Color.Transparent
            tints = listOf(HazeTint(Color(0x99151515)))
            noiseFactor = 0f
            inputScale = HazeInputScale.Fixed(.5f)
        } else Modifier.background(Color(0xF0181818))))
        Column(Modifier.fillMaxWidth()
            .graphicsLayer {
                // The avatar is at the upper-right: open toward the lower-left and retract to the same origin.
                val s = pop.value.coerceAtLeast(0f)
                transformOrigin = TransformOrigin(1f, 0f)
                scaleX = s
                scaleY = s
            }
            .clip(panelShape)
            .padding(32.dp)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("快捷控制中心", color = White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(time, color = Color(0xFFE3E5E8), fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    Text(date, color = Color(0xFFA8ADB4), fontSize = 17.sp)
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().height(184.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlTile("▣", "电视模式", "沉浸桌面，大屏体验", Modifier.weight(1f).focusRequester(first), selected = true) {}
                ControlTile("●", "主机模式", "减少干扰，专注内容", Modifier.weight(1f)) {}
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlTile("⌁", "Wi-Fi", "已连接", Modifier.weight(1f).fillMaxHeight(), compact = true) {}
                ControlTile("ᛒ", "蓝牙", "已开启", Modifier.weight(1f).fillMaxHeight(), compact = true) {}
                ControlTile("⌘", "手柄", "未连接", Modifier.weight(1f).fillMaxHeight(), compact = true) {}
                ControlTile("◕", "音量", "68%", Modifier.weight(1f).fillMaxHeight(), compact = true) {}
                ControlTile("☼", "亮度", "76%", Modifier.weight(1f).fillMaxHeight(), compact = true) {}
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlTile("◉", "音频输出", "电视扬声器", Modifier.weight(1f), horizontal = true) {}
                ControlTile("◐", "显示模式", "影院", Modifier.weight(1f), horizontal = true) {}
                ControlTile("⚙", "设置", "打开设置", Modifier.weight(1f), horizontal = true, onClick = onSettings)
                ControlTile("⏻", "关闭", "返回桌面", Modifier.weight(1f), horizontal = true, onClick = { close() })
            }
            Spacer(Modifier.height(8.dp))

            ControlTile("▶", "当前桌面", "阿布桌面 · 正在运行", Modifier.fillMaxWidth().height(124.dp), horizontal = true, selected = false) { close() }
        }
        }
    }
}

@Composable
private fun ControlTile(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
    compact: Boolean = false,
    horizontal: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered || selected
    val background by animateColorAsState(if (selected) Color(0xE6F2F2F2) else Color(0x4D2D3239), tween(180), label = "control-background")
    val foreground by animateColorAsState(if (selected) Color(0xFF111317) else White, tween(180), label = "control-foreground")
    val shape = ContinuousCornerShape(if (compact || horizontal) 15.dp else 21.dp)
    val scale by androidx.compose.animation.core.animateFloatAsState(if (focused) 1.03f else 1f, tween(180), label = "control-scale")
    Box(modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape).background(background)
        .drawWithContent {
            drawContent()
            if (active) {
                val stroke = 2.dp.toPx()
                inset(stroke / 2f) {
                    drawOutline(shape.createOutline(size, layoutDirection, this),
                        Color.White, style = Stroke(stroke))
                }
            }
        }
        .focusSweep(focused || hovered, shape)
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
        .padding(if (compact || horizontal) 16.dp else 20.dp),
        contentAlignment = if (horizontal) Alignment.CenterStart else Alignment.BottomStart) {
        if (horizontal) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(icon, color = foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Column {
                    Text(title, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(subtitle, color = foreground.copy(alpha = .65f), fontSize = 13.sp, maxLines = 1)
                }
            }
        } else {
            Column {
                Text(icon, color = foreground, fontSize = if (compact) 29.sp else 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(if (compact) 13.dp else 22.dp))
                Text(title, color = foreground, fontSize = if (compact) 17.sp else 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = foreground.copy(alpha = .65f), fontSize = if (compact) 13.sp else 17.sp, maxLines = 1)
            }
        }
    }
}
