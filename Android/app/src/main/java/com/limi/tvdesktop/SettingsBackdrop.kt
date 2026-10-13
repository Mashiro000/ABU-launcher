package com.limi.tvdesktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*

/** Settings alone sample the desktop; controls stay sharp above this single blur surface. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun SettingsBackdrop(haze: HazeState?, staticBlur: ImageBitmap?, modifier: Modifier) {
    val view = LocalView.current
    var accelerated by remember(view) { mutableStateOf(view.isHardwareAccelerated) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val canvasHeight = LocalCanvasHeight.current
    val viewport = with(density) { IntSize(1672.dp.roundToPx(), canvasHeight.roundToPx()) }
    val realtime = haze != null && accelerated && RenderPerformance.blur31
    Box(modifier.clipToBounds().onSizeChanged { widthPx = it.width.toFloat() }
        .onGloballyPositioned { accelerated = view.isHardwareAccelerated }) {
        if (realtime) {
            Box(Modifier.fillMaxSize().hazeEffect(haze!!) {
                blurRadius = 40.dp
                // The left 75% stays at 40; only the right edge tapers down to zero.
                if (RenderPerformance.blur33 && widthPx > 0f) {
                    progressive = HazeProgressive.horizontalGradient(
                        startX = widthPx * .75f, endX = widthPx,
                        startIntensity = 1f, endIntensity = 0f,
                    )
                }
                backgroundColor = Color.Transparent
                tints = emptyList()
                fallbackTint = HazeTint(Color.Transparent)
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(.3f)
            })
        } else if (RenderPerformance.staticBlur && staticBlur != null) {
            Canvas(Modifier.fillMaxSize()) { drawCoverWallpaper(staticBlur, viewport, IntOffset.Zero) }
        }
        // A single 50% black layer also serves unsupported renderers and disabled effects.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)))
    }
}
