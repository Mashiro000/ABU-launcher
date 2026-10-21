package com.limi.tvdesktop

import androidx.compose.runtime.staticCompositionLocalOf

/** Static glass stays transparent over video; realtime glass samples the embedded texture. */
internal val LocalTransparentVideoBackdrop = staticCompositionLocalOf { false }

internal fun useTransparentVideoBackdrop(mode: WallpaperMode, home: Boolean, hasVideo: Boolean, showingAppArtwork: Boolean, realtimeBlur: Boolean = false): Boolean =
    mode == WallpaperMode.VIDEO && home && hasVideo && !showingAppArtwork && !realtimeBlur
