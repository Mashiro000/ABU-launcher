package com.limi.tvdesktop

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect

internal fun controlCenterOpenMotion(): AnimationSpec<Float> =
    if (RenderPerformance.motionMode == RenderPerformance.MotionMode.FULL) keyframes {
        durationMillis = 360
        0f at 0 using CubicBezierEasing(.16f, 1f, .3f, 1f)
        1.015f at 240 using CubicBezierEasing(.4f, 0f, .2f, 1f)
        1f at 360
    } else tween(RenderPerformance.duration(360), easing = LinearEasing)

internal const val FOLDER_CREATION_MS = 600
internal fun folderCreationMotion(mode: RenderPerformance.MotionMode): AnimationSpec<Float> =
    if (mode == RenderPerformance.MotionMode.FULL) keyframes {
        durationMillis = FOLDER_CREATION_MS
        // Keep the shrink leg visible; the control-center's steep opening curve
        // had already completed most of this much larger size change in 100ms.
        0f at 0 using CubicBezierEasing(.4f, 0f, .2f, 1f)
        .72f at 300 using CubicBezierEasing(.2f, 0f, .2f, 1f)
        1f at FOLDER_CREATION_MS
    } else tween(RenderPerformance.durationFor(mode, FOLDER_CREATION_MS), easing = LinearEasing)

/** Destination matches FolderTile's 24dp inset and 7dp three-by-three gaps. */
internal fun folderCreationRect(source: Rect, card: Rect, progress: Float, density: Float): Rect {
    val scaleX = source.width / card.width.coerceAtLeast(1f)
    val scaleY = source.height / card.height.coerceAtLeast(1f)
    val insetX = 24f * density * scaleX
    val insetY = 24f * density * scaleY
    val target = Rect(source.left + insetX, source.top + insetY,
        source.left + insetX + (source.width - 2 * insetX - 14f * density * scaleX) / 3f,
        source.top + insetY + (source.height - 2 * insetY - 14f * density * scaleY) / 3f)
    fun mix(a: Float, b: Float) = a + (b - a) * progress
    return Rect(mix(source.left, target.left), mix(source.top, target.top),
        mix(source.right, target.right), mix(source.bottom, target.bottom))
}
