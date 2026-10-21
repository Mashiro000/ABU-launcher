package com.limi.tvdesktop

import org.junit.Assert.*
import org.junit.Test

class RenderPerformanceTest {
    @Test fun reducedMotionPreservesDurationAndNoneSkipsIt() {
        for (duration in listOf(220, 460, 500, 700)) {
            assertEquals(duration, RenderPerformance.durationFor(RenderPerformance.MotionMode.FULL, duration))
            assertEquals(duration, RenderPerformance.durationFor(RenderPerformance.MotionMode.REDUCED, duration))
            assertEquals(0, RenderPerformance.durationFor(RenderPerformance.MotionMode.NONE, duration))
        }
    }
    @Test fun staticBlurSurvivesEveryAnimationChoiceOnAndroidNine() {
        RenderPerformance.MotionMode.entries.forEach {
            assertTrue(RenderPerformance.usesStaticBlur(it, RenderPerformance.BlurMode.STATIC, 28))
        }
    }

    @Test fun realtimeRequestFallsBackOnAndroidNine() {
        assertTrue(RenderPerformance.usesStaticBlur(RenderPerformance.MotionMode.FULL, RenderPerformance.BlurMode.REALTIME, 28))
    }

    @Test fun reducedAnimationsKeepCheapBlurEvenOnNewAndroid() {
        assertTrue(RenderPerformance.usesStaticBlur(RenderPerformance.MotionMode.REDUCED, RenderPerformance.BlurMode.REALTIME, 35))
        assertTrue(RenderPerformance.usesStaticBlur(RenderPerformance.MotionMode.NONE, RenderPerformance.BlurMode.REALTIME, 35))
    }

    @Test fun explicitlyDisabledBlurStaysDisabled() {
        RenderPerformance.MotionMode.entries.forEach {
            assertFalse(RenderPerformance.usesStaticBlur(it, RenderPerformance.BlurMode.NONE, 28))
        }
    }

    @Test fun fullRealtimeOnSupportedDeviceDoesNotDoubleRenderStaticBlur() {
        assertFalse(RenderPerformance.usesStaticBlur(RenderPerformance.MotionMode.FULL, RenderPerformance.BlurMode.REALTIME, 35))
    }
}
