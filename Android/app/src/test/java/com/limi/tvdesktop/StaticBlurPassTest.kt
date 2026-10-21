package com.limi.tvdesktop

import org.junit.Assert.*
import org.junit.Test

class StaticBlurPassTest {
    @Test fun clampedEdgesPreserveExpectedAverageInBothDirections() {
        val input = intArrayOf(0xff000000.toInt(), 0xff0d0000.toInt(), 0xff1a0000.toInt())
        val expected = intArrayOf(0xff0b0000.toInt(), 0xff0d0000.toInt(), 0xff0f0000.toInt())
        val output = IntArray(3)
        staticBlurPass(input, output, 3, 1, true)
        assertArrayEquals(expected, output)
        staticBlurPass(input, output, 1, 3, false)
        assertArrayEquals(expected, output)
    }

    @Test fun multiRowPassMatchesClampedBoxFilter() {
        val width = 19; val height = 11
        val input = IntArray(width * height) { i -> 0xff000000.toInt() or ((i * 37 and 255) shl 16) or ((i * 11 and 255) shl 8) or (i * 17 and 255) }
        listOf(true, false).forEach { horizontal ->
            val expected = IntArray(input.size) { i ->
                var r = 0; var g = 0; var b = 0
                for (offset in -6..6) {
                    val x = (i % width + if (horizontal) offset else 0).coerceIn(0, width - 1)
                    val y = (i / width + if (horizontal) 0 else offset).coerceIn(0, height - 1)
                    val c = input[y * width + x]
                    r += c shr 16 and 255; g += c shr 8 and 255; b += c and 255
                }
                0xff000000.toInt() or ((r / 13) shl 16) or ((g / 13) shl 8) or (b / 13)
            }
            val output = IntArray(input.size)
            staticBlurPass(input, output, width, height, horizontal)
            assertArrayEquals(expected, output)
        }
    }
}
