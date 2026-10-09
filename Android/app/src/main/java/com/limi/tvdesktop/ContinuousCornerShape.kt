package com.limi.tvdesktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.RoundedCornerShape

/** Continuous G2 corners: tangent and curvature agree with the straight edges.
 * This is our cubic approximation, not Apple's private corner implementation.
 */
internal class ContinuousCornerShape(
    private val radius: Dp? = null,
    private val verticalRadius: Dp? = null
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val requested = radius?.let { with(density) { it.toPx() } }
        // Preserve the original full stadium shape for pills and circular buttons.
        // A squircle is not a replacement for a half-height capsule radius.
        if (requested == null || (verticalRadius == null && requested >= minOf(w, h) / 2f)) {
            return RoundedCornerShape(50).createOutline(size, layoutDirection, density)
        }
        val rx: Float
        val ry: Float
        if (verticalRadius == null) {
            val r = (requested * 1.6f).coerceIn(0f, minOf(w, h) / 2f)
            rx = r
            ry = r
        } else {
            rx = (requested * 1.6f).coerceIn(0f, w / 2f)
            ry = (with(density) { verticalRadius.toPx() } * 1.6f).coerceIn(0f, h / 2f)
        }
        if (rx == 0f || ry == 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        return Outline.Generic(Path().apply {
            moveTo(rx, 0f); lineTo(w-rx, 0f)
            cubicTo(w-.75f*rx, 0f, w-.5f*rx, 0f, w-.25f*rx, .25f*ry)
            cubicTo(w, .5f*ry, w, .75f*ry, w, ry)
            lineTo(w, h-ry)
            cubicTo(w, h-.75f*ry, w, h-.5f*ry, w-.25f*rx, h-.25f*ry)
            cubicTo(w-.5f*rx, h, w-.75f*rx, h, w-rx, h)
            lineTo(rx, h)
            cubicTo(.75f*rx, h, .5f*rx, h, .25f*rx, h-.25f*ry)
            cubicTo(0f, h-.5f*ry, 0f, h-.75f*ry, 0f, h-ry)
            lineTo(0f, ry)
            cubicTo(0f, .75f*ry, 0f, .5f*ry, .25f*rx, .25f*ry)
            cubicTo(.5f*rx, 0f, .75f*rx, 0f, rx, 0f)
            close()
        })
    }
}
