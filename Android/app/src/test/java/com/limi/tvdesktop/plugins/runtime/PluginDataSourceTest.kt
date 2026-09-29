package com.limi.tvdesktop.plugins.runtime

import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginKind
import com.limi.tvdesktop.plugins.PluginPermission
import com.limi.tvdesktop.plugins.PluginTrust
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginDataSourceTest {
    private val plugin = InstalledPlugin(
        "com.example.media", "Media", "1.0.0", "", PluginKind.DATA_SOURCE, "Test", true,
        PluginTrust.UNVERIFIED, 0, listOf(PluginPermission("network", "Network")),
        networkDomains = listOf("media.example.com"),
    )

    @Test fun parsesBoundedPageAndCursor() {
        val page = PluginDataSource.parse(JSONObject("""{"items":[{"id":"a","title":"Movie","streamUrl":"https://media.example.com/a.mp4"}],"nextCursor":"2"}"""))
        assertEquals("Movie", page.items.single().title)
        assertEquals("2", page.nextCursor)
    }

    @Test fun blocksUntrustedPlaybackUrls() {
        assertEquals("https://media.example.com/a.mp4", PluginDataSource.playableUrl(plugin, "https://media.example.com/a.mp4"))
        assertNull(PluginDataSource.playableUrl(plugin, "http://media.example.com/a.mp4"))
        assertNull(PluginDataSource.playableUrl(plugin, "https://other.example.com/a.mp4"))
        assertNull(PluginDataSource.playableUrl(plugin, "https://user:pass@media.example.com/a.mp4"))
    }

    @Test fun rejectsMalformedOrOversizedResults() {
        assertThrows(IllegalArgumentException::class.java) { PluginDataSource.parse(JSONObject("""{"error":"offline"}""")) }
        assertThrows(IllegalArgumentException::class.java) { PluginDataSource.parse(JSONObject("""{"items":[{"id":"","title":"Missing"}]}""")) }
    }
}
