package com.limi.tvdesktop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class StaticBlurPolicyInstrumentedTest {
    private fun checkConfiguration(check: (Context) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RenderPerformance.init(context)
        val motion = RenderPerformance.motionMode
        val blur = RenderPerformance.blurMode
        try { check(context) } finally {
            RenderPerformance.setMotionMode(context, motion)
            RenderPerformance.setBlurMode(context, blur)
        }
    }

    @Test fun staticBlurPersistsAcrossAllAnimationModes() = checkConfiguration { context ->
        RenderPerformance.setBlurMode(context, RenderPerformance.BlurMode.STATIC)
        RenderPerformance.MotionMode.entries.forEach {
            RenderPerformance.setMotionMode(context, it)
            assertTrue("$it must retain cached blur", RenderPerformance.staticBlur)
            assertFalse(RenderPerformance.blur31)
        }
    }

    @Test fun reducedRealtimeUsesCachedBlurWithoutGpuSampling() = checkConfiguration { context ->
        RenderPerformance.setBlurMode(context, RenderPerformance.BlurMode.REALTIME)
        RenderPerformance.setMotionMode(context, RenderPerformance.MotionMode.REDUCED)
        assertTrue(RenderPerformance.staticBlur)
        assertFalse(RenderPerformance.blur31)
    }

    @Test fun disablingBlurIsRespected() = checkConfiguration { context ->
        RenderPerformance.setBlurMode(context, RenderPerformance.BlurMode.NONE)
        RenderPerformance.MotionMode.entries.forEach {
            RenderPerformance.setMotionMode(context, it)
            assertFalse(RenderPerformance.staticBlur)
            assertFalse(RenderPerformance.blur31)
        }
    }
}
