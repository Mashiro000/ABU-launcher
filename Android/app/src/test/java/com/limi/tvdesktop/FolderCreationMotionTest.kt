package com.limi.tvdesktop

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.*
import org.junit.Assert.*
import org.junit.Test

class FolderCreationMotionTest {
    @Test fun fullMotionDoesNotCollapseInTheFirstHundredMilliseconds() {
        val animation = TargetBasedAnimation(folderCreationMotion(RenderPerformance.MotionMode.FULL),
            Float.VectorConverter, 0f, 1f)
        fun at(ms: Long) = animation.getValueFromNanos(ms * 1_000_000)
        assertTrue(at(100) < .4f)
        assertEquals(.72f, at(300), .001f)
        assertEquals(1f, at(600), .001f)
        assertEquals(600_000_000L, animation.durationNanos)
        var previous = 0f
        for (ms in 0L..600L step 10) {
            val value = at(ms)
            assertTrue(value in 0f..1f && value >= previous)
            previous = value
        }
    }

    @Test fun reducedMotionIsAnEvenFadeWithTheSameDuration() {
        val animation = TargetBasedAnimation(folderCreationMotion(RenderPerformance.MotionMode.REDUCED),
            Float.VectorConverter, 0f, 1f)
        assertEquals(.5f, animation.getValueFromNanos(300_000_000), .001f)
        assertEquals(600_000_000L, animation.durationNanos)
    }
    @Test fun startsAtActualHighlightedIconBounds() {
        val source = Rect(80f, 90f, 320f, 250f)
        assertEquals(source, folderCreationRect(source, Rect(90f, 100f, 310f, 240f), 0f, 1f))
    }

    @Test fun endsExactlyAtFirstFolderCellAcrossDensities() {
        for (density in listOf(1f, 1.5f, 2f)) {
            val card = Rect(100f, 200f, 100f + 222f * density, 200f + 132f * density)
            val result = folderCreationRect(card, card, 1f, density)
            assertEquals(100f + 24f * density, result.left, .001f)
            assertEquals(200f + 24f * density, result.top, .001f)
            assertEquals(160f / 3f * density, result.width, .001f)
            assertEquals(70f / 3f * density, result.height, .001f)
        }
    }

    @Test fun controlCenterOvershootKeepsThumbnailInsideFolder() {
        val card = Rect(100f, 200f, 322f, 332f)
        val r = folderCreationRect(card, card, 1.025f, 1f)
        assertTrue(r.width > 0f && r.height > 0f)
        assertTrue(r.left >= card.left && r.top >= card.top && r.right <= card.right && r.bottom <= card.bottom)
    }

    @Test fun focusExpansionIsPreservedAtTheHandoff() {
        val base = Rect(100f, 200f, 322f, 332f)
        val visual = Rect(90f, 190f, 332f, 342f)
        val r = folderCreationRect(visual, base, 1f, 1f)
        assertEquals(90f + 24f * 242f / 222f, r.left, .001f)
        assertEquals(190f + 24f * 152f / 132f, r.top, .001f)
        assertEquals(160f / 3f * 242f / 222f, r.width, .001f)
        assertEquals(70f / 3f * 152f / 132f, r.height, .001f)
    }
}
