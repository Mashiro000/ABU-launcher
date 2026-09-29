package com.limi.tvdesktop.plugins.runtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class PluginEvent(val sourcePluginId: String, val topic: String, val payload: JSONObject)

/** Host-mediated events prevent plugins from sharing runtimes or object references. */
object PluginEventBus {
    private val stream = MutableSharedFlow<PluginEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PluginEvent> = stream.asSharedFlow()

    fun publish(sourcePluginId: String, topic: String, payload: JSONObject): Boolean {
        require(topic.matches(Regex("[a-zA-Z0-9._-]{1,100}"))) { "事件名称无效" }
        return stream.tryEmit(PluginEvent(sourcePluginId, topic, payload))
    }
}

data class PluginServiceDescriptor(val ownerPluginId: String, val name: String, val version: Int)

/** Namespaced, versioned service discovery. Invocation still travels through the capability bridge. */
object PluginServiceRegistry {
    private val services = ConcurrentHashMap<String, PluginServiceDescriptor>()

    fun register(ownerPluginId: String, name: String, version: Int) {
        require(version > 0 && name.matches(Regex("[a-zA-Z0-9._-]{1,100}"))) { "服务声明无效" }
        services["$ownerPluginId:$name"] = PluginServiceDescriptor(ownerPluginId, name, version)
    }

    fun resolve(ownerPluginId: String, name: String, minimumVersion: Int): PluginServiceDescriptor? =
        services["$ownerPluginId:$name"]?.takeIf { it.version >= minimumVersion }

    fun unregisterPlugin(pluginId: String) {
        services.entries.removeAll { it.value.ownerPluginId == pluginId }
    }
}
