package com.limi.tvdesktop.plugins

import android.content.Context

class PluginCrashGuard(private val context: Context) {
    private val prefs = context.getSharedPreferences("plugin_crash_guard", Context.MODE_PRIVATE)
    private val managerPrefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)

    /** Call before plugin UI starts. Returns the plugin automatically disabled after three failed starts. */
    fun beginStartup(): String? {
        val previousPending = prefs.getBoolean("startup_pending", false)
        val pluginId = managerPrefs.getString("last_enabled_plugin", null)
        val failures = if (previousPending && pluginId != null) prefs.getInt("failures.$pluginId", 0) + 1 else 0
        var disabled: String? = null
        if (pluginId != null && failures >= 3) {
            PluginManager.get(context).setEnabled(pluginId, false)
            prefs.edit().putInt("failures.$pluginId", 0).apply()
            disabled = pluginId
        } else if (pluginId != null && previousPending) {
            prefs.edit().putInt("failures.$pluginId", failures).apply()
        }
        prefs.edit().putBoolean("startup_pending", true).apply()
        return disabled
    }

    fun markStable() {
        prefs.edit().putBoolean("startup_pending", false).apply()
    }
}
