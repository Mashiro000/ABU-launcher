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
internal class ContinuousCornerShape(private val radius: Dp? = null) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val requested = radius?.let { with(density) { it.toPx() } }
        // Preserve the original full stadium shape for pills and circular buttons.
        // A squircle is not a replacement for a half-height capsule radius.
        if (requested == null || requested >= minOf(w, h) / 2f) {
            return RoundedCornerShape(50).createOutline(size, layoutDirection, density)
        }
        val r = (requested * 1.6f)
            .coerceIn(0f, minOf(w, h) / 2f)
        if (r == 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        return Outline.Generic(Path().apply {
            moveTo(r, 0f); lineTo(w-r, 0f)
            cubicTo(w-.75f*r, 0f, w-.5f*r, 0f, w-.25f*r, .25f*r)
            cubicTo(w, .5f*r, w, .75f*r, w, r)
            lineTo(w, h-r)
            cubicTo(w, h-.75f*r, w, h-.5f*r, w-.25f*r, h-.25f*r)
            cubicTo(w-.5f*r, h, w-.75f*r, h, w-r, h)
            lineTo(r, h)
            cubicTo(.75f*r, h, .5f*r, h, .25f*r, h-.25f*r)
            cubicTo(0f, h-.5f*r, 0f, h-.75f*r, 0f, h-r)
            lineTo(0f, r)
            cubicTo(0f, .75f*r, 0f, .5f*r, .25f*r, .25f*r)
            cubicTo(.5f*r, 0f, .75f*r, 0f, r, 0f)
            close()
        })
    }
}
