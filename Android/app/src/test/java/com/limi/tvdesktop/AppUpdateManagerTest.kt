package com.limi.tvdesktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.json.JSONArray

class AppUpdateManagerTest {
    @Test
    fun comparesCommonGitHubReleaseTags() {
        assertTrue(AppUpdateManager.isNewer("v0.05", "0.04-beta.1"))
        assertTrue(AppUpdateManager.isNewer("0.04", "0.04-beta.1"))
        assertTrue(AppUpdateManager.isNewer("v0.04-beta.2", "0.04-beta.1"))
        assertTrue(AppUpdateManager.isNewer("v1.2.10", "1.2.9"))
        assertFalse(AppUpdateManager.isNewer("0.04-beta.1", "0.04-beta.1"))
        assertFalse(AppUpdateManager.isNewer("0.03", "0.04-beta.1"))
    }

    @Test
    fun choosesUniversalApkBeforeAbiSpecificAsset() {
        val assets = JSONArray("""[
            {"name":"launcher-arm64-v8a.apk","browser_download_url":"https://github.com/example/arm.apk"},
            {"name":"launcher-universal.apk","browser_download_url":"https://github.com/example/all.apk"}
        ]""")
        assertEquals("https://github.com/example/all.apk",
            AppUpdateManager.selectApkUrl(assets, arrayOf("arm64-v8a")))
    }

    @Test
    fun avoidsWrongAbiWhenOnlySplitApksExist() {
        val assets = JSONArray("""[
            {"name":"launcher-x86.apk","browser_download_url":"https://github.com/example/x86.apk"},
            {"name":"launcher-arm64-v8a.apk","browser_download_url":"https://github.com/example/arm.apk"}
        ]""")
        assertEquals("https://github.com/example/arm.apk",
            AppUpdateManager.selectApkUrl(assets, arrayOf("arm64-v8a")))
    }
}
