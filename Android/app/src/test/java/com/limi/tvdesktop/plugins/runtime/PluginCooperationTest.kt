package com.limi.tvdesktop.plugins.runtime

import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginKind
import com.limi.tvdesktop.plugins.PluginTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginCooperationTest {
    @Test fun choosesSingleTrustedOwnerAndMultipleSlotContributors() {
        val unverified = plugin("z.third", PluginTrust.UNVERIFIED)
        val official = plugin("a.official", PluginTrust.OFFICIAL)
        val verified = plugin("b.verified", PluginTrust.VERIFIED)
        assertEquals("a.official", PluginSurfaceRegistry.owner("home", listOf(unverified, verified, official))?.id)
        assertEquals(listOf("a.official", "b.verified", "z.third"), PluginSurfaceRegistry.contributors("home.quickActions", listOf(unverified, verified, official)).map { it.id })
    }

    @Test fun serviceRegistryUsesOwnerNamespaceAndVersion() {
        val owner = "test.owner"
        PluginServiceRegistry.register(owner, "library", 2)
        assertEquals(2, PluginServiceRegistry.resolve(owner, "library", 1)?.version)
        assertNull(PluginServiceRegistry.resolve(owner, "library", 3))
        PluginServiceRegistry.unregisterPlugin(owner)
        assertNull(PluginServiceRegistry.resolve(owner, "library", 1))
    }

    private fun plugin(id: String, trust: PluginTrust) = InstalledPlugin(
        id, id, "1", "", PluginKind.UI, "test", true, trust, 1, emptyList(),
        entry = "dist/index.js", surfaces = listOf("home"), slots = listOf("home.quickActions"),
    )
}
