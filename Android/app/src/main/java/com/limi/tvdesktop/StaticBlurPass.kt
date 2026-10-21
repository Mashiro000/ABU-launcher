package com.limi.tvdesktop

/** Sliding-window box pass: each pixel enters/leaves once instead of being sampled 13 times. */
internal fun staticBlurPass(src: IntArray, dst: IntArray, width: Int, height: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    if (length == 0) return
    for (line in 0 until lines) {
        val base = if (horizontal) line * width else line
        val stride = if (horizontal) 1 else width
        var r = 0; var g = 0; var b = 0
        for (offset in -6..6) {
            val color = src[base + offset.coerceIn(0, length - 1) * stride]
            r += (color shr 16) and 255; g += (color shr 8) and 255; b += color and 255
        }
        for (position in 0 until length) {
            dst[base + position * stride] = (255 shl 24) or ((r / 13) shl 16) or ((g / 13) shl 8) or (b / 13)
            val leaving = src[base + (position - 6).coerceIn(0, length - 1) * stride]
            val entering = src[base + (position + 7).coerceIn(0, length - 1) * stride]
            r += ((entering shr 16) and 255) - ((leaving shr 16) and 255)
            g += ((entering shr 8) and 255) - ((leaving shr 8) and 255)
            b += (entering and 255) - (leaving and 255)
        }
    }
}
