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
    ): PluginUiNode? = invokeOutput(method, input, requestConsent).ui

    suspend fun invokeOutput(
        method: String,
        input: JSONObject,
        requestConsent: suspend (PluginConsentRequest) -> Boolean,
        allowServiceCalls: Boolean = true,
        requestAndroidPermission: suspend (String) -> Boolean = { false },
    ): PluginRuntimeOutput {
        val source = manager.readEntryScript(plugin.id) ?: throw IllegalStateException("插件入口不可用")
        var output = execute(source, method, input)
        val navigation = output.navigation
        repeat(MAX_CAPABILITY_ROUNDS) {
            if (output.capabilities.isEmpty()) return output.copy(navigation = output.navigation ?: navigation)
            val results = JSONArray()
            for (request in output.capabilities) {
                var result = if (request.capability == "services.call") {
                    if (allowServiceCalls) callService(request, requestConsent, requestAndroidPermission)
                    else CapabilityResult.Rejected("服务调用不能嵌套")
                } else withContext(Dispatchers.IO) { bridge.execute(plugin, request) }
                if (result is CapabilityResult.NeedsConsent) {
                    val declaration = plugin.permissions.firstOrNull { it.id == result.permission }
                    val granted = requestConsent(
                        PluginConsentRequest(result.permission, declaration?.title ?: result.permission, declaration?.sensitive == true)
                    )
                    bridge.setConsent(plugin.id, result.permission, granted)
                    result = if (granted) withContext(Dispatchers.IO) { bridge.execute(plugin, request) }
                    else CapabilityResult.Rejected("用户拒绝授权")
                }
                if (result is CapabilityResult.NeedsAndroidPermission) {
                    val granted = requestAndroidPermission(result.permission)
                    result = if (granted) withContext(Dispatchers.IO) { bridge.execute(plugin, request) }
                    else CapabilityResult.Rejected("用户拒绝系统设备权限")
                }
                results.put(JSONObject().put("id", request.id).apply {
                    when (result) {
                        is CapabilityResult.Success -> put("ok", true).put("value", result.value)
                        is CapabilityResult.Rejected -> put("ok", false).put("error", result.reason)
                        is CapabilityResult.NeedsConsent -> put("ok", false).put("error", "permission unresolved")
                        is CapabilityResult.NeedsAndroidPermission -> put("ok", false).put("error", "Android permission unresolved")
                    }
                })
            }
            output = execute(source, "onCapabilities", JSONObject().put("results", results))
        }
        throw IllegalStateException("插件能力调用轮次过多")
    }

    private suspend fun callService(
        request: PluginCapabilityRequest,
        requestConsent: suspend (PluginConsentRequest) -> Boolean,
        requestAndroidPermission: suspend (String) -> Boolean,
    ): CapabilityResult = runCatching {
        val owner = request.arguments.getString("owner")
        val name = request.arguments.getString("name")
        val minimumVersion = request.arguments.optInt("minimumVersion", 1)
        val method = request.arguments.getString("method")
        require(method.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "服务方法无效" }
        require(owner != plugin.id) { "不能调用自身服务" }
        val descriptor = PluginServiceRegistry.resolve(owner, name, minimumVersion)
            ?: return@runCatching CapabilityResult.Rejected("服务不可用或版本不兼容")
        val provider = manager.installed().firstOrNull { it.id == owner && it.enabled && it.entry != null }
            ?: return@runCatching CapabilityResult.Rejected("服务提供方未启用")
        require(descriptor.ownerPluginId == provider.id) { "服务提供方不匹配" }
        val providerSession = PluginRuntimeSession(provider, manager, sandbox, bridge)
        val input = JSONObject().put("callerPluginId", plugin.id).put("service", name)
            .put("method", method).put("arguments", request.arguments.optJSONObject("arguments") ?: JSONObject())
        val output = providerSession.invokeOutput("onService", input, requestConsent, allowServiceCalls = false,
            requestAndroidPermission = requestAndroidPermission)
        CapabilityResult.Success(output.value ?: JSONObject())
    }.getOrElse { CapabilityResult.Rejected(it.message ?: "服务调用失败") }

    private suspend fun execute(source: String, method: String, input: JSONObject): PluginRuntimeOutput {
        val json = sandbox.execute(plugin.id, source, method, input.toString()).getOrThrow()
        return PluginUiParser.parse(json)
    }

    companion object { private const val MAX_CAPABILITY_ROUNDS = 4 }
}
