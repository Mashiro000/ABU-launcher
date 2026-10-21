package com.limi.tvdesktop

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GradientWallpaperInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun circularPaletteSelectionAppliesAndPersists() {
        val original = GradientWallpapers.current(context)
        val mode = WallpaperMode.current(context)
        try {
            compose.setContent { androidx.compose.foundation.layout.Column { GradientPalettePicker {} } }
            compose.onNodeWithContentDescription("应用海湾渐变壁纸").performClick()
            compose.runOnIdle {
                assertEquals("ocean", GradientWallpapers.current(context).id)
                assertEquals(WallpaperMode.GRADIENT, WallpaperMode.current(context))
            }
            compose.onNodeWithText("柔和渐变 · 海湾").assertIsDisplayed()
        } finally {
            GradientWallpapers.save(context, original)
            WallpaperMode.save(context, mode)
        }
    }

    @Test fun everyPresetRendersOpaqueNonUniformBackground() {
        GradientWallpapers.palettes.forEach { palette ->
            val bitmap = GradientWallpapers.bitmap(palette, 160, 90)
            assertEquals(160, bitmap.width)
            assertEquals(90, bitmap.height)
            assertNotEquals(bitmap.getPixel(0, 0), bitmap.getPixel(159, 89))
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(80, 45)))
            bitmap.recycle()
        }
    }
}
