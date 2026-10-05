package com.limi.tvdesktop.player

import com.limi.tvdesktop.SELECTION_TRANSITION_MS

import com.limi.tvdesktop.RenderPerformance

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limi.tvdesktop.ContinuousCornerShape
import dev.chrisbanes.haze.*

// 调色板：深色透明玻璃与柔和微光
val GlassCardBackground = Color(0x66131622) // 40% 半透明深灰黑，保证透过看见后方模糊画面
val GlassCardBorder = Color(0x35FFFFFF)
val GlassCardBorderFocused = Color(0x807399D6)
val GlassCardHighlight = Color(0x22FFFFFF)
val AccentGlowBlue = Color(0xFF4A89FF)
val AccentGlowLight = Color(0xFF70A8FF)

/**
 * 悬浮深色透明玻璃卡片容器 (支持真正的实时背景模糊 Backdrop Blur，无渐变，无边框)
 */
@Composable
fun TvGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    hazeState: HazeState? = null,
    backgroundColor: Color = GlassCardBackground,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = ContinuousCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .clip(ContinuousCornerShape(cornerRadius))
            .then(
                if (hazeState != null && RenderPerformance.blur31) {
                    Modifier.hazeEffect(hazeState) {
                        blurRadius = 36.dp
                        tints = listOf(HazeTint(backgroundColor))
                        noiseFactor = 0f
                    inputScale = HazeInputScale.Fixed(.4f)
                    }
                } else {
                    Modifier.background(backgroundColor)
                }
            )
    ) {
        content()
    }
}

/**
 * 遥控器圆形图标按钮 (纯净磨砂玻璃，40% 不透明度，无边框，低版本仅不透明度降级)
 */
@Composable
fun TvCircleIconButton(
    iconType: TvIconType,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    iconSize: Dp = 28.dp,
    isPrimary: Boolean = false,
    tooltip: String? = null,
    hazeState: HazeState? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val actualSize = if (isPrimary) 80.dp else size
    val actualIconSize = if (isPrimary) 38.dp else iconSize

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = SELECTION_TRANSITION_MS),
        label = "button_scale"
    )

    // 40% 不透明度基础色，聚焦时高亮提亮
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> Color(0xF04C82E6)
            isFocused -> Color(0xCC5A8DEE)
            isPrimary -> Color(0x663D75D9) // 40% 不透明
            else -> Color(0x66202436)      // 40% 不透明
        },
        animationSpec = tween(durationMillis = SELECTION_TRANSITION_MS),
        label = "button_bg"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isPrimary -> Color(0xFFE8EFFF)
            else -> Color(0xEEFFFFFF)
        },
        animationSpec = tween(durationMillis = SELECTION_TRANSITION_MS),
        label = "button_icon"
    )

    val canBlur = hazeState != null && RenderPerformance.blur31

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .scale(scale)
                .size(actualSize)
                .clip(CircleShape)
                .then(
                    if (hazeState != null && RenderPerformance.blur31) {
                        Modifier.hazeEffect(hazeState) {
                            blurRadius = 24.dp
                            tints = listOf(HazeTint(bgColor))
                            noiseFactor = 0f
                        inputScale = HazeInputScale.Fixed(.4f)
                        }
                    } else {
                        Modifier.background(bgColor)
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter)
                    ) {
                        onClick()
                        true
                    } else false
                }
        ) {
            Canvas(modifier = Modifier.size(actualIconSize)) {
                drawTvIcon(iconType, iconColor)
            }
        }

        // 聚焦时若有提示文字，显示在下方
        if (tooltip != null && isFocused) {
            Text(
                text = tooltip,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * TV 定制开关控件 (加大幅度适配电视)
 */
@Composable
fun TvSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "switch_scale"
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1.0f else 0.0f,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "thumb_offset"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            checked && isFocused -> Color(0xFF4C85FF)
            checked -> Color(0xFF3872E8)
            isFocused -> Color(0x4DFFFFFF)
            else -> Color(0x28FFFFFF)
        },
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "track_color"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .width(68.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(trackColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0x99A2C5FF) else Color(0x28FFFFFF),
                shape = RoundedCornerShape(19.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCheckedChange(!checked)
            }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onCheckedChange(!checked)
                    true
                } else false
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = 4.dp.toPx()
            val thumbRadius = (size.height - padding * 2) / 2f
            val startX = padding + thumbRadius
            val endX = size.width - padding - thumbRadius
            val currentX = startX + (endX - startX) * thumbOffset

            // 绘制白色大圆点
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(currentX, size.height / 2f)
            )
        }
    }
}

/**
 * TV 定制滑块 (加宽轨道，大字号，获得焦点后按遥控器左右键调节数值)
 */
@Composable
fun TvSlider(
    value: Float, // 0.0f ~ 1.0f
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0.1f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "slider_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0x38426CA3) else Color.Transparent)
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) Color(0x8076A9FA) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val newVal = (value - step).coerceIn(0.0f, 1.0f)
                            onValueChange(newVal)
                            true
                        }
                        Key.DirectionRight -> {
                            val newVal = (value + step).coerceIn(0.0f, 1.0f)
                            onValueChange(newVal)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 进度条轨道
        Box(
            modifier = Modifier
                .width(135.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x38FFFFFF))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val activeWidth = size.width * value.coerceIn(0.0f, 1.0f)
                // 激活蓝色轨道
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF4A89FF), Color(0xFF70A8FF))
                    ),
                    size = Size(activeWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )

                // 游标圆点
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx(),
                    center = Offset(activeWidth.coerceIn(8.dp.toPx(), size.width - 8.dp.toPx()), size.height / 2f)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // 当前数值文本
        Text(
            text = valueText,
            color = if (isFocused) Color.White else Color(0xEEFFFFFF),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp)
        )
    }
}

/**
 * TV 胶囊选择器 (无边框，40% 不透明度磨砂模糊，支持遥控器确认键切换)
 */
@Composable
fun TvCapsuleSelector(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "capsule_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xCC4E74B0) else Color(0x66202436),
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "capsule_bg"
    )

    val canBlur = hazeState != null && RenderPerformance.blur31

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (hazeState != null && RenderPerformance.blur31) {
                    Modifier.hazeEffect(hazeState) {
                        blurRadius = 24.dp
                        tints = listOf(HazeTint(bgColor))
                        noiseFactor = 0f
                    inputScale = HazeInputScale.Fixed(.4f)
                    }
                } else {
                    Modifier.background(bgColor)
                }
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.DirectionRight)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else Color(0xEEFFFFFF),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 进度条与时间轴组件 (大幅度加粗明显显示，TV 远距离清晰可见)
 */
@Composable
fun TvTimelineScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedProgress = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 8.dp,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "track_height"
    )

    val thumbRadius by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 9.dp,
        animationSpec = tween(SELECTION_TRANSITION_MS),
        label = "thumb_radius"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val seekStep = 10_000L
                            val target = (currentPositionMs - seekStep).coerceAtLeast(0L)
                            onSeek(target)
                            true
                        }
                        Key.DirectionRight -> {
                            val seekStep = 10_000L
                            val target = (currentPositionMs + seekStep).coerceAtMost(durationMs)
                            onSeek(target)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 当前播放时间 (大字体 18sp)
        Text(
            text = formatTvTime(currentPositionMs),
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(20.dp))

        // 轨道主体
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
            ) {
                val h = size.height
                val totalW = size.width
                val activeW = totalW * progress
                val bufferedW = totalW * bufferedProgress

                // 1. 底层背景轨道 (未下载部分)
                drawRoundRect(
                    color = Color(0x33FFFFFF),
                    size = Size(totalW, h),
                    cornerRadius = CornerRadius(h / 2f)
                )

                // 2. 缓冲进度轨道 (已下载/已缓存部分)：必须与底色明显区分，
                //    且永远不短于已播进度，否则会被蓝色盖住看不见。
                val visibleBuffered = bufferedW.coerceIn(activeW, totalW)
                if (visibleBuffered > 0f) {
                    drawRoundRect(
                        color = Color(0xB3FFFFFF),
                        size = Size(visibleBuffered, h),
                        cornerRadius = CornerRadius(h / 2f)
                    )
                }

                // 3. 已播高亮轨道 (浅蓝高光)
                if (activeW > 0) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF4A89FF), Color(0xFF70A8FF))
                        ),
                        size = Size(activeW, h),
                        cornerRadius = CornerRadius(h / 2f)
                    )
                }

                // 4. 游标白点 (带微光外圈)
                val thumbCenter = Offset(
                    activeW.coerceIn(thumbRadius.toPx(), totalW - thumbRadius.toPx()),
                    h / 2f
                )

                if (isFocused) {
                    drawCircle(
                        color = Color(0x4D4A89FF),
                        radius = thumbRadius.toPx() + 6.dp.toPx(),
                        center = thumbCenter
                    )
                }

                drawCircle(
                    color = Color.White,
                    radius = thumbRadius.toPx(),
                    center = thumbCenter
                )
            }
        }

        Spacer(Modifier.width(20.dp))

        // 总时长
        Text(
            text = if (durationMs > 0) formatTvTime(durationMs) else "--:--",
            color = Color(0xAAFFFFFF),
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )

        // 已缓存百分比 (只在确有领先于播放位置的缓存时显示)
        if (durationMs > 0 && bufferedProgress > progress + 0.005f && bufferedProgress < 1f) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = "已缓存 " + (bufferedProgress * 100).toInt() + "%",
                color = Color(0x99FFFFFF),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 时间格式化助手函数
 */
fun formatTvTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d:%02d", 0, minutes, seconds)
    }
}

/**
 * 统一的矢量图标类型枚举
 */
enum class TvIconType {
    MORE,
    INFO,
    PLAYLIST,
    REWIND_10,
    PREVIOUS_EPISODE,
    PLAY,
    PAUSE,
    NEXT_EPISODE,
    FORWARD_10,
    DANMAKU,
    AUDIO_TRACK,
    PIP,
    ASPECT_RATIO,
    SEND_DANMAKU
}

/**
 * 绘制统一风格的简洁线性/面性 TV 矢量图标
 */
fun DrawScope.drawTvIcon(type: TvIconType, color: Color) {
    val w = size.width
    val h = size.height
    val strokeWidth = 2.4.dp.toPx()

    when (type) {
        TvIconType.MORE -> {
            val r = 2.6.dp.toPx()
            drawCircle(color, r, Offset(w * 0.25f, h * 0.5f))
            drawCircle(color, r, Offset(w * 0.50f, h * 0.5f))
            drawCircle(color, r, Offset(w * 0.75f, h * 0.5f))
        }
        TvIconType.INFO -> {
            drawCircle(
                color = color,
                radius = w * 0.44f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = strokeWidth)
            )
            drawCircle(color, 2.2.dp.toPx(), Offset(w * 0.5f, h * 0.33f))
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.46f),
                end = Offset(w * 0.5f, h * 0.69f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        TvIconType.PLAYLIST -> {
            val inset = w * 0.20f
            val lineLen = w * 0.60f
            for (i in 0..2) {
                val y = h * (0.30f + i * 0.20f)
                drawLine(
                    color = color,
                    start = Offset(inset, y),
                    end = Offset(inset + lineLen, y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        TvIconType.REWIND_10 -> {
            drawArc(
                color = color,
                startAngle = 60f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(w * 0.16f, h * 0.16f),
                size = Size(w * 0.68f, h * 0.68f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val arrowPath = Path().apply {
                moveTo(w * 0.40f, h * 0.08f)
                lineTo(w * 0.22f, h * 0.18f)
                lineTo(w * 0.38f, h * 0.30f)
            }
            drawPath(arrowPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawLine(
                color = color,
                start = Offset(w * 0.42f, h * 0.54f),
                end = Offset(w * 0.58f, h * 0.54f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        TvIconType.PREVIOUS_EPISODE -> {
            drawLine(
                color = color,
                start = Offset(w * 0.24f, h * 0.24f),
                end = Offset(w * 0.24f, h * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val path = Path().apply {
                moveTo(w * 0.74f, h * 0.24f)
                lineTo(w * 0.35f, h * 0.50f)
                lineTo(w * 0.74f, h * 0.76f)
                close()
            }
            drawPath(path, color)
        }
        TvIconType.PLAY -> {
            val path = Path().apply {
                moveTo(w * 0.35f, h * 0.22f)
                lineTo(w * 0.76f, h * 0.50f)
                lineTo(w * 0.35f, h * 0.78f)
                close()
            }
            drawPath(path, color)
        }
        TvIconType.PAUSE -> {
            val barW = w * 0.13f
            val barH = h * 0.54f
            val y = h * 0.23f
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.32f, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.55f, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
        }
        TvIconType.NEXT_EPISODE -> {
            val path = Path().apply {
                moveTo(w * 0.26f, h * 0.24f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.26f, h * 0.76f)
                close()
            }
            drawPath(path, color)
            drawLine(
                color = color,
                start = Offset(w * 0.76f, h * 0.24f),
                end = Offset(w * 0.76f, h * 0.76f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        TvIconType.FORWARD_10 -> {
            drawArc(
                color = color,
                startAngle = 120f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(w * 0.16f, h * 0.16f),
                size = Size(w * 0.68f, h * 0.68f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val arrowPath = Path().apply {
                moveTo(w * 0.60f, h * 0.08f)
                lineTo(w * 0.78f, h * 0.18f)
                lineTo(w * 0.62f, h * 0.30f)
            }
            drawPath(arrowPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            drawLine(
                color = color,
                start = Offset(w * 0.42f, h * 0.54f),
                end = Offset(w * 0.58f, h * 0.54f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        TvIconType.DANMAKU -> {
            val rectPath = Path().apply {
                moveTo(w * 0.18f, h * 0.28f)
                lineTo(w * 0.82f, h * 0.28f)
                quadraticTo(w * 0.88f, h * 0.28f, w * 0.88f, h * 0.34f)
                lineTo(w * 0.88f, h * 0.66f)
                quadraticTo(w * 0.88f, h * 0.72f, w * 0.82f, h * 0.72f)
                lineTo(w * 0.45f, h * 0.72f)
                lineTo(w * 0.30f, h * 0.85f)
                lineTo(w * 0.30f, h * 0.72f)
                lineTo(w * 0.18f, h * 0.72f)
                quadraticTo(w * 0.12f, h * 0.72f, w * 0.12f, h * 0.66f)
                lineTo(w * 0.12f, h * 0.34f)
                quadraticTo(w * 0.12f, h * 0.28f, w * 0.18f, h * 0.28f)
                close()
            }
            drawPath(rectPath, color, style = Stroke(width = strokeWidth))
            drawLine(color, Offset(w * 0.28f, h * 0.46f), Offset(w * 0.72f, h * 0.46f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.55f, h * 0.56f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
        TvIconType.AUDIO_TRACK -> {
            val barW = 2.6.dp.toPx()
            val bars = listOf(0.40f, 0.75f, 0.55f, 0.90f, 0.45f)
            val stepX = w / (bars.size + 1)
            bars.forEachIndexed { i, factor ->
                val x = stepX * (i + 1)
                val barLen = h * 0.62f * factor
                val topY = h * 0.5f - barLen / 2f
                val botY = h * 0.5f + barLen / 2f
                drawLine(
                    color = color,
                    start = Offset(x, topY),
                    end = Offset(x, botY),
                    strokeWidth = barW,
                    cap = StrokeCap.Round
                )
            }
        }
        TvIconType.PIP -> {
            val bigW = w * 0.70f
            val bigH = h * 0.54f
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.15f, h * 0.23f),
                size = Size(bigW, bigH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.48f, h * 0.44f),
                size = Size(w * 0.30f, h * 0.26f),
                cornerRadius = CornerRadius(2.5.dp.toPx())
            )
        }
        TvIconType.ASPECT_RATIO -> {
            val len = w * 0.22f
            drawLine(color, Offset(w * 0.20f, h * 0.22f + len), Offset(w * 0.20f, h * 0.22f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.20f, h * 0.22f), Offset(w * 0.20f + len, h * 0.22f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.80f - len, h * 0.22f), Offset(w * 0.80f, h * 0.22f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.80f, h * 0.22f), Offset(w * 0.80f, h * 0.22f + len), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.20f, h * 0.78f - len), Offset(w * 0.20f, h * 0.78f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.20f, h * 0.78f), Offset(w * 0.20f + len, h * 0.78f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.80f - len, h * 0.78f), Offset(w * 0.80f, h * 0.78f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.80f, h * 0.78f), Offset(w * 0.80f, h * 0.78f - len), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        TvIconType.SEND_DANMAKU -> {
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.50f)
                lineTo(w * 0.84f, h * 0.20f)
                lineTo(w * 0.56f, h * 0.82f)
                lineTo(w * 0.44f, h * 0.58f)
                close()
            }
            drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(color, Offset(w * 0.44f, h * 0.58f), Offset(w * 0.84f, h * 0.20f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}
