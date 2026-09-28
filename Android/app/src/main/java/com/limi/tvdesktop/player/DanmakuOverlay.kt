package com.limi.tvdesktop.player

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * 屏幕上正在飞过的动态弹幕渲染单元
 */
private class ActiveDanmaku(
    val item: TvDanmakuItem,
    val lane: Int,
    val startTimeMs: Long,
    val durationMs: Long,
    val textWidth: Float
) {
    fun getX(currentMs: Long, screenWidth: Float): Float {
        val elapsed = currentMs - startTimeMs
        val progress = elapsed.toFloat() / durationMs.toFloat()
        val totalDistance = screenWidth + textWidth
        return screenWidth - (totalDistance * progress)
    }

    fun isFinished(currentMs: Long): Boolean {
        return currentMs >= startTimeMs + durationMs
    }
}

/**
 * 弹幕渲染层 (毫秒时间轴严格同步)
 */
@Composable
fun DanmakuOverlay(
    currentPositionMs: Long,
    isPlaying: Boolean,
    danmakuList: List<TvDanmakuItem>,
    settings: TvDanmakuSettings,
    modifier: Modifier = Modifier
) {
    if (!settings.enabled || danmakuList.isEmpty()) return

    val density = LocalDensity.current
    val baseFontSize = with(density) { (20.sp * (settings.fontSizePercent / 100f)).toPx() }

    // 原生绘制 Paint：文字发光与描边（保证在任何明暗视频背景下均清晰可读）
    val strokePaint = remember(settings.fontSizePercent) {
        Paint().apply {
            isAntiAlias = true
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = android.graphics.Color.argb(180, 0, 0, 0)
        }
    }

    val textPaint = remember(settings.fontSizePercent) {
        Paint().apply {
            isAntiAlias = true
            textSize = baseFontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            style = Paint.Style.FILL
        }
    }

    // 活动弹幕集合
    val activeDanmakus = remember { mutableStateListOf<ActiveDanmaku>() }
    var lastProcessedPosition by remember { mutableLongStateOf(-1L) }

    // 轨道数量（全屏通常 12-14 轨）
    val laneCount = 12
    val laneNextAvailableTime = remember { LongArray(laneCount) { 0L } }

    // 当用户 Seek 大于 1.5 秒时，清空当前活跃弹幕重新根据时间轴加载
    LaunchedEffect(currentPositionMs) {
        if (lastProcessedPosition >= 0 && abs(currentPositionMs - lastProcessedPosition) > 1500L) {
            activeDanmakus.clear()
            for (i in 0 until laneCount) {
                laneNextAvailableTime[i] = 0L
            }
        }
        lastProcessedPosition = currentPositionMs

        // 挑选应该登场的弹幕 (时间在 [currentPositionMs - 100ms, currentPositionMs + 300ms])
        val windowStart = currentPositionMs - 150L
        val windowEnd = currentPositionMs + 250L

        val incoming = danmakuList.filter { item ->
            item.timeMs in windowStart..windowEnd &&
            activeDanmakus.none { it.item.id == item.id }
        }

        // 过滤规则应用
        val filtered = incoming.filter { item ->
            when (settings.filterType) {
                DanmakuFilter.NONE -> true
                DanmakuFilter.COLOR -> item.color == Color.White
                DanmakuFilter.TOP_BOTTOM -> !item.isTop && !item.isBottom
                DanmakuFilter.REPEAT -> true
            }
        }

        val duration = settings.speed.durationMs

        // 弹幕分配轨道
        for (item in filtered) {
            val textW = textPaint.measureText(item.text)
            // 根据设置的 displayArea 限制可用轨道
            val maxLane = when (settings.displayArea) {
                DanmakuArea.FULL -> laneCount
                DanmakuArea.HALF -> (laneCount * 0.5f).toInt()
                DanmakuArea.TOP -> (laneCount * 0.35f).toInt().coerceAtLeast(3)
                DanmakuArea.BOTTOM -> laneCount
            }
            val minLane = when (settings.displayArea) {
                DanmakuArea.BOTTOM -> (laneCount * 0.65f).toInt()
                else -> 0
            }

            // 寻找最空闲的可用轨道
            var selectedLane = minLane
            var earliestTime = Long.MAX_VALUE
            for (lane in minLane until maxLane) {
                if (laneNextAvailableTime[lane] <= currentPositionMs) {
                    selectedLane = lane
                    earliestTime = 0L
                    break
                } else if (laneNextAvailableTime[lane] < earliestTime) {
                    earliestTime = laneNextAvailableTime[lane]
                    selectedLane = lane
                }
            }

            // 预计该弹幕完全进入屏幕的时间 (约 1.2s)
            val cooldown = 1200L
            laneNextAvailableTime[selectedLane] = currentPositionMs + cooldown

            activeDanmakus.add(
                ActiveDanmaku(
                    item = item,
                    lane = selectedLane,
                    startTimeMs = item.timeMs,
                    durationMs = duration,
                    textWidth = textW
                )
            )
        }

        // 清理离开屏幕的过期弹幕
        activeDanmakus.removeAll { it.isFinished(currentPositionMs) }
    }

    val alpha = (settings.opacity * 255).toInt().coerceIn(0, 255)

    Canvas(modifier = modifier.fillMaxSize()) {
        val screenW = size.width
        val screenH = size.height
        val laneHeight = (screenH * 0.85f) / laneCount
        val topPadding = 70.dp.toPx() // 避开顶部状态栏

        val nativeCanvas = drawContext.canvas.nativeCanvas

        for (active in activeDanmakus) {
            val x = active.getX(currentPositionMs, screenW)
            val y = topPadding + (active.lane * laneHeight) + baseFontSize

            // 越出屏幕左右边界时不绘制
            if (x + active.textWidth < 0 || x > screenW) continue

            val c = active.item.color
            textPaint.color = android.graphics.Color.argb(
                alpha,
                (c.red * 255).toInt(),
                (c.green * 255).toInt(),
                (c.blue * 255).toInt()
            )
            strokePaint.color = android.graphics.Color.argb((alpha * 0.7f).toInt(), 0, 0, 0)

            // 先画阴影描边，再画纯色文字
            nativeCanvas.drawText(active.item.text, x, y, strokePaint)
            nativeCanvas.drawText(active.item.text, x, y, textPaint)
        }
    }
}
