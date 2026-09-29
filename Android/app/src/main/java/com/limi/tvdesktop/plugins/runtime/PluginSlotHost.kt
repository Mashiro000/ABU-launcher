package com.limi.tvdesktop.plugins.runtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.limi.tvdesktop.plugins.PluginManager

@Composable
fun PluginSlotHost(slot: String, modifier: Modifier = Modifier) {
    val plugins = PluginSurfaceRegistry.contributors(slot, PluginManager.get(LocalContext.current).installed())
    if (plugins.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        plugins.forEach { plugin -> PluginSurfaceHost(plugin, "slot:$slot", Modifier.fillMaxWidth()) }
    }
}
