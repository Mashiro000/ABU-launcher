package com.limi.tvdesktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
