package com.limi.tvdesktop

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/** Local review setup only; this class is packaged in the test APK, never the app. */
class PreviewSetupInstrumentedTest {
    @Test fun directAppleDownloadProbe(): Unit = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val item = VideoWallpaperCatalog.entries(context).first { it.id == "asia-night" }
        val output = VideoWallpaperCatalog.download(context, item) { bytes, total ->
            android.util.Log.i("ABUVideoProbe", "$bytes / $total")
        }
        org.junit.Assert.assertTrue(output.length() > 1024 * 1024)
    }
    @Test fun showDownloadedVideoForDesktopReview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = VideoWallpaperCatalog.entries(context).first { it.id == "asia-night" }
        val file = VideoWallpaperCatalog.file(context, entry)
        org.junit.Assert.assertTrue(file.exists())
        VideoWallpaperPrefs.save(context, android.net.Uri.fromFile(file))
        context.getSharedPreferences("desktop", 0).edit()
            .putString("blurMode", InstrumentationRegistry.getArguments().getString("blurMode") ?: "STATIC")
            .putString("motionMode", "FULL")
            .putString("wallpaperMode", WallpaperMode.VIDEO.name)
            .putString("startupPage", "HOME").putBoolean("navigationShown", true).remove("navigationHidden").commit()
    }
    @Test fun configureLocalPreviewSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = InstrumentationRegistry.getArguments().getString("previewBaseUrl")
        require(base?.startsWith("http://127.0.0.1:") == true)
        val context = instrumentation.targetContext
        context.getSharedPreferences("desktop", 0).edit().putString("videoPreviewBaseUrl", base).commit()
        NavigationPreferences.reset(context)
        WallpaperMode.save(context, WallpaperMode.GRADIENT)
        context.getSharedPreferences("desktop", 0).edit().putBoolean("navigationShown", true).remove("navigationHidden").putString("wallpaperMode", WallpaperMode.GRADIENT.name).commit()
    }
}
