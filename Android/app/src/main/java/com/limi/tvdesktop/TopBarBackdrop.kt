package com.limi.tvdesktop

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*

/** Fixed top-bar backdrop, behind controls; unsupported renderers keep only the black gradient. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun TopBarBackdrop(source: HazeState, reveal: () -> Float) {
    val view = LocalView.current
    var hardwareAccelerated by remember(view) { mutableStateOf(view.isHardwareAccelerated) }
    Box(Modifier.fillMaxWidth().height(112.dp).onGloballyPositioned {
        hardwareAccelerated = view.isHardwareAccelerated
    }) {
        if (Build.VERSION.SDK_INT >= 31 && hardwareAccelerated) {
            Box(Modifier.fillMaxSize().hazeEffect(source) {
                blurEnabled = reveal() > 0f
                blurRadius = (20f * reveal().coerceIn(0f, 1f)).coerceAtLeast(.01f).dp
                progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                backgroundColor = Color.Transparent
                tints = emptyList()
                fallbackTint = HazeTint(Color.Transparent)
                noiseFactor = 0f
                inputScale = HazeInputScale.Fixed(.5f)
            })
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal().coerceIn(0f, 1f) }.background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .6f), Color.Transparent))))
    }
}
