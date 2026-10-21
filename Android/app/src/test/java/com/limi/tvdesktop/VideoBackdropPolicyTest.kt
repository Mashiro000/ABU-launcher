package com.limi.tvdesktop

import org.junit.Assert.*
import org.junit.Test

class VideoBackdropPolicyTest {
    @Test fun videoOnHomeUsesTransparentGlass() {
        assertTrue(useTransparentVideoBackdrop(WallpaperMode.VIDEO, true, true, false))
    }
    @Test fun realtimeVideoKeepsLiveGlass() {
        assertFalse(useTransparentVideoBackdrop(WallpaperMode.VIDEO, true, true, false, true))
    }
    @Test fun missingVideoAndOtherPagesKeepNormalBackdrop() {
        assertFalse(useTransparentVideoBackdrop(WallpaperMode.VIDEO, true, false, false))
        assertFalse(useTransparentVideoBackdrop(WallpaperMode.VIDEO, false, true, false))
    }
    @Test fun stillWallpapersAndAppArtworkKeepTheirOwnBlur() {
        WallpaperMode.entries.filterNot { it == WallpaperMode.VIDEO }.forEach {
            assertFalse(useTransparentVideoBackdrop(it, true, true, false))
        }
        assertFalse(useTransparentVideoBackdrop(WallpaperMode.VIDEO, true, true, true))
    }
}
