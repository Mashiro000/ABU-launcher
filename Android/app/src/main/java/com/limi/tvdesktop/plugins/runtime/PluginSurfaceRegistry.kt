package com.limi.tvdesktop.plugins.runtime

import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginTrust

object PluginSurfaceRegistry {
    /** Exactly one full-page owner. Official/verified owners win deterministically. */
    fun owner(surface: String, plugins: List<InstalledPlugin>): InstalledPlugin? = plugins
        .filter { it.enabled && surface in it.surfaces && it.entry != null }
        .sortedWith(compareBy<InstalledPlugin> { trustRank(it.trust) }.thenBy { it.id })
        .firstOrNull()

    /** Multiple cooperating plugins may contribute to a named host slot. */
    fun contributors(slot: String, plugins: List<InstalledPlugin>): List<InstalledPlugin> =
        plugins.filter { it.enabled && slot in it.slots && it.entry != null }.sortedBy { it.id }

    private fun trustRank(trust: PluginTrust) = when (trust) {
        PluginTrust.OFFICIAL -> 0
        PluginTrust.VERIFIED -> 1
        PluginTrust.UNVERIFIED -> 2
    }
}
