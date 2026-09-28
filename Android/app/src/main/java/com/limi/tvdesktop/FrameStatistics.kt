package com.limi.tvdesktop

import kotlin.math.ceil

/** Fixed-size histogram: diagnostics must not grow with session duration. */
internal class FrameStatistics {
    private val buckets = IntArray(5002)
    private var count = 0
    private var slow = 0
    private var frozen = 0
    private var dropped = 0
    private var total = 0.0
    private var maximum = 0.0
    private var layout = 0.0
    private var draw = 0.0

    fun add(ms: Double, budgetMs: Double, layoutMs: Double = 0.0, drawMs: Double = 0.0, lost: Int = 0) {
        dropped += lost.coerceAtLeast(0)
        if (!ms.isFinite() || ms < 0 || budgetMs <= 0) return
        count++
        total += ms
        maximum = maxOf(maximum, ms)
        layout += layoutMs.coerceAtLeast(0.0)
        draw += drawMs.coerceAtLeast(0.0)
        if (ms > budgetMs) slow++
        if (ms > 700) frozen++
        buckets[ceil(ms).toInt().coerceIn(0, buckets.lastIndex)]++
    }

    fun snapshot(): FrameSummary {
        val target = ceil(count * .95).toInt()
        var seen = 0
        val p95 = if (count == 0) 0 else buckets.indices.first { index ->
            seen += buckets[index]
            seen >= target
        }
        return FrameSummary(count, slow, frozen, dropped, if (count == 0) 0.0 else total / count,
            maximum, p95, if (count == 0) 0.0 else layout / count, if (count == 0) 0.0 else draw / count)
    }
}

internal data class FrameSummary(
    val frames: Int = 0, val slow: Int = 0, val frozen: Int = 0, val lostReports: Int = 0,
    val averageMs: Double = 0.0, val maxMs: Double = 0.0, val p95Ms: Int = 0,
    val layoutMs: Double = 0.0, val drawMs: Double = 0.0
) {
    val slowPercent: Double get() = if (frames == 0) 0.0 else slow * 100.0 / frames
    fun text(): String = if (frames == 0) "尚无绘制帧（静止页面无需持续绘制）" else
        "帧数 $frames · 超预算 %.1f%% · 冻结帧 $frozen\n平均 %.1f ms · P95 ${if (p95Ms > 5000) ">5000" else p95Ms} ms · 最慢 %.1f ms\n布局 %.1f ms · 绘制 %.1f ms · 丢失采样 $lostReports"
            .format(java.util.Locale.ROOT, slowPercent, averageMs, maxMs, layoutMs, drawMs)
}
