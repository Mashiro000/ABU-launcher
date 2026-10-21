package com.limi.tvdesktop

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WallpaperSourceInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun capture(name: String) {
        val folder = File(context.cacheDir, "abu-qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun videoCatalogAndSecondarySettingsFitThePanel() {
        var page by mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Color(0xFF161B24)).padding(24.dp)) {
                    Box(Modifier.width(560.dp).fillMaxHeight()) {
                        when (page) {
                            0 -> SettingsInlinePage({}) { OnlineVideoWallpapers {} }
                            1 -> DockContentSettings({}, {})
                            2 -> NavigationSettings({}, {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("在线航拍视频").assertIsDisplayed()
        capture("video-catalog")
        compose.onNodeWithText("开普半岛 · 海狮").performClick()
        compose.onNodeWithText("下载此视频").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { page = 1 }
        compose.onNodeWithText("显示 Dock 应用内容").assertIsDisplayed()
        capture("dock-content-settings")
        compose.runOnIdle { page = 2 }
        compose.onNodeWithText("显示导航条").assertIsDisplayed()
        capture("navigation-settings")
    }
    @Test fun videoDownloadValidatesAndDecodesTheActualFile(): Unit = runBlocking {
        val source = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("videoTestUrl")
        org.junit.Assume.assumeTrue("Requires explicit videoTestUrl fixture", source != null)
        val entry = VideoWallpaperCatalog.Entry("test-download", "下载验证", "测试", "fixture")
        val output = VideoWallpaperCatalog.download(context, entry, source!!) { _, _ -> }
        try {
            assertTrue(output.length() > 1024 * 1024)
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(output.path)
                val frame = retriever.getFrameAtTime(0)
                assertNotNull("Downloaded H.264 video must decode", frame)
                frame?.recycle()
            } finally { retriever.release() }
        } finally { output.delete() }
    }
    @Test fun lockingSkipsDailyNetworkAndUnlockResetsTheCheckDay() = runBlocking {
        val prefs = context.getSharedPreferences("desktop", 0)
        val previous = prefs.getBoolean("bingAutoUpdate", true)
        val file = BingWallpaper.fileFor(context, "19990101")
        file.writeBytes(byteArrayOf(1))
        try {
            BingWallpaper.setAutoUpdate(context, false)
            assertFalse(BingWallpaper.ensureDaily(context))
            prefs.edit().putString("bingCheckedDay", "20000101").commit()
            BingWallpaper.setAutoUpdate(context, true)
            assertNull(prefs.getString("bingCheckedDay", null))
        } finally { file.delete(); BingWallpaper.setAutoUpdate(context, previous) }
    }
}
