package com.limi.tvdesktop

import org.junit.Assert.*
import org.junit.Test

class FrameStatisticsTest {
    @Test fun idleIsNotReportedAsJank() {
        val summary = FrameStatistics().snapshot()
        assertEquals(0, summary.frames)
        assertEquals(0.0, summary.slowPercent, 0.0)
        assertEquals(0, summary.p95Ms)
    }

    @Test fun respectsRefreshBudgetAndFrozenBoundary() {
        val stats = FrameStatistics()
        stats.add(16.0, 1000.0 / 60)
        stats.add(16.0, 1000.0 / 120)
        stats.add(700.0, 16.67)
        stats.add(701.0, 16.67)
        val result = stats.snapshot()
        assertEquals(4, result.frames)
        assertEquals(3, result.slow)
        assertEquals(1, result.frozen)
        assertEquals(75.0, result.slowPercent, 0.0)
    }

    @Test fun histogramMeasuresP95AndRetainsOutlierMaximum() {
        val stats = FrameStatistics()
        repeat(95) { stats.add(10.2, 16.67, 2.0, 3.0) }
        repeat(5) { stats.add(8000.5, 16.67, 2.0, 3.0) }
        val result = stats.snapshot()
        assertEquals(11, result.p95Ms)
        assertEquals(8000.5, result.maxMs, 0.0)
        assertEquals(2.0, result.layoutMs, 0.0)
        assertEquals(3.0, result.drawMs, 0.0)
    }

    @Test fun lostMetricReportsAreNotDroppedFrames() {
        val stats = FrameStatistics()
        stats.add(5.0, 16.67, lost = 7)
        stats.add(-1.0, 16.67)
        stats.add(Double.NaN, 16.67)
        val result = stats.snapshot()
        assertEquals(1, result.frames)
        assertEquals(0, result.slow)
        assertEquals(7, result.lostReports)
    }
}
