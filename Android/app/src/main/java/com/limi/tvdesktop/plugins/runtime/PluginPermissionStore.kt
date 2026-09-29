package com.limi.tvdesktop.plugins.runtime

import android.content.Context

enum class PluginPermissionState { ASK, GRANTED, DENIED }

class PluginPermissionStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)

    fun get(pluginId: String, permission: String): PluginPermissionState = runCatching {
        PluginPermissionState.valueOf(prefs.getString("$pluginId.$permission", null) ?: return PluginPermissionState.ASK)
    }.getOrDefault(PluginPermissionState.ASK)

    fun set(pluginId: String, permission: String, state: PluginPermissionState) {
        prefs.edit().putString("$pluginId.$permission", state.name).apply()
    }

    fun clear(pluginId: String) {
        prefs.edit().also { editor -> prefs.all.keys.filter { it.startsWith("$pluginId.") }.forEach(editor::remove) }.apply()
    }
}
