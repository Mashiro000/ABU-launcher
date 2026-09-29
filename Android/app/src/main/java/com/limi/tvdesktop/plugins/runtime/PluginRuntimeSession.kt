package com.limi.tvdesktop.plugins.runtime

import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PluginConsentRequest(val permission: String, val title: String, val sensitive: Boolean)

class PluginRuntimeSession(
    private val plugin: InstalledPlugin,
    private val manager: PluginManager,
    private val sandbox: PluginSandboxClient,
    private val bridge: PluginCapabilityBridge,
) {
    suspend fun invoke(
        method: String,
        input: JSONObject,
        requestConsent: suspend (PluginConsentRequest) -> Boolean,
    ): PluginUiNode? {
        val source = manager.readEntryScript(plugin.id) ?: throw IllegalStateException("插件入口不可用")
        var output = execute(source, method, input)
        repeat(MAX_CAPABILITY_ROUNDS) {
            if (output.capabilities.isEmpty()) return output.ui
            val results = JSONArray()
            for (request in output.capabilities) {
                var result = withContext(Dispatchers.IO) { bridge.execute(plugin, request) }
                if (result is CapabilityResult.NeedsConsent) {
                    val declaration = plugin.permissions.firstOrNull { it.id == result.permission }
                    val granted = requestConsent(
                        PluginConsentRequest(result.permission, declaration?.title ?: result.permission, declaration?.sensitive == true)
                    )
                    bridge.setConsent(plugin.id, result.permission, granted)
                    result = if (granted) withContext(Dispatchers.IO) { bridge.execute(plugin, request) }
                    else CapabilityResult.Rejected("用户拒绝授权")
                }
                results.put(JSONObject().put("id", request.id).apply {
                    when (result) {
                        is CapabilityResult.Success -> put("ok", true).put("value", result.value)
                        is CapabilityResult.Rejected -> put("ok", false).put("error", result.reason)
                        is CapabilityResult.NeedsConsent -> put("ok", false).put("error", "permission unresolved")
                    }
                })
            }
            output = execute(source, "onCapabilities", JSONObject().put("results", results))
        }
        throw IllegalStateException("插件能力调用轮次过多")
    }

    private suspend fun execute(source: String, method: String, input: JSONObject): PluginRuntimeOutput {
        val json = sandbox.execute(plugin.id, source, method, input.toString()).getOrThrow()
        return PluginUiParser.parse(json)
    }

    companion object { private const val MAX_CAPABILITY_ROUNDS = 4 }
}
