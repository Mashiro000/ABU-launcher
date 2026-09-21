package com.limi.tvdesktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot

/** A complete top-left to bottom-right overlay sweep, once per selection. */
internal fun Modifier.focusSweep(selected: Boolean, shape: Shape = Glass): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        progress.snapTo(0f)
        if (selected) progress.animateTo(1f, tween(500, easing = LinearEasing))
    }
    drawWithContent {
        drawContent()
        if (selected && progress.value < 1f) {
            val diagonal = hypot(size.width, size.height).coerceAtLeast(1f)
            val direction = Offset(size.width / diagonal, size.height / diagonal)
            val halfWidth = diagonal * .22f
            // Both ends begin/end outside the complete shape, including the trailing edge.
            val distance = -halfWidth + (diagonal + halfWidth * 2f) * progress.value
            val center = direction * distance
            val mask = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
            clipPath(mask) {
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = .12f),
                        Color.White.copy(alpha = .42f), Color(0x14000000), Color.Transparent),
                    start = center - direction * halfWidth,
                    end = center + direction * halfWidth
                ), blendMode = BlendMode.Overlay)
            }
        }
    }
}

internal fun Modifier.focusSweepOnFocus(shape: Shape = Glass): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.hasFocus }.focusSweep(focused, shape)
}
