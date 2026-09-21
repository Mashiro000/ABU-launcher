package com.limi.tvdesktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Keep the horizontal reference proportions, but let the viewport show as much height as available. */
internal val LocalCanvasHeight = staticCompositionLocalOf { 941.dp }
internal val LocalKeyboardControl = staticCompositionLocalOf { false }

/** Match ContentScale.Crop when a small cached wallpaper is sampled behind a glass control. */
internal fun DrawScope.drawCoverWallpaper(image: ImageBitmap, viewport: IntSize, offset: IntOffset = IntOffset.Zero) {
    val scale = max(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val width = (image.width * scale).roundToInt()
    val height = (image.height * scale).roundToInt()
    drawImage(image, dstSize = IntSize(width, height), dstOffset = IntOffset(
        offset.x + (viewport.width - width) / 2,
        offset.y + (viewport.height - height) / 2))
}
