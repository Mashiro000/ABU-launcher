package com.limi.tvdesktop.plugins

/** Version of the public script plugin contract implemented by this APK. */
object PluginHostApi {
    const val VERSION = "1.1.0"
    private val rangePattern = Regex(">=([0-9]+)\\.([0-9]+)\\.([0-9]+) <([0-9]+)\\.([0-9]+)\\.([0-9]+)")

    fun supports(range: String): Boolean {
        val match = rangePattern.matchEntire(range) ?: return false
        val current = VERSION.split('.').map(String::toInt)
        val lower = match.groupValues.subList(1, 4).map(String::toInt)
        val upper = match.groupValues.subList(4, 7).map(String::toInt)
        return compare(current, lower) >= 0 && compare(current, upper) < 0
    }

    private fun compare(left: List<Int>, right: List<Int>): Int =
        left.indices.firstNotNullOfOrNull { index -> (left[index] - right[index]).takeIf { it != 0 } } ?: 0
}
