package com.limi.tvdesktop.plugins.runtime

import android.content.Context
import android.os.Build
import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

sealed interface CapabilityResult {
    data class Success(val value: JSONObject) : CapabilityResult
    data class NeedsConsent(val permission: String) : CapabilityResult
    data class Rejected(val reason: String) : CapabilityResult
}

/** The only route from plugin code to host services. No Context enters the JS process. */
class PluginCapabilityBridge(context: Context) {
    private val app = context.applicationContext
    private val permissions = PluginPermissionStore(app)
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).followRedirects(false).build()

    fun execute(plugin: InstalledPlugin, request: PluginCapabilityRequest): CapabilityResult {
        val declared = plugin.permissions.map { it.id }.toSet()
        val requiredPermission = when (request.capability) {
            "device.info" -> null
            "storage.get", "storage.set" -> "storage"
            "network.fetch" -> "network"
            "usb.list" -> "usb"
            "bluetooth.list" -> "bluetooth"
            "events.publish", "services.register", "services.resolve" -> null
            else -> return CapabilityResult.Rejected("未知能力：${request.capability}")
        }
        if (requiredPermission != null) {
            if (requiredPermission !in declared) return CapabilityResult.Rejected("插件未声明 $requiredPermission 权限")
            when (permissions.get(plugin.id, requiredPermission)) {
                PluginPermissionState.ASK -> return CapabilityResult.NeedsConsent(requiredPermission)
                PluginPermissionState.DENIED -> return CapabilityResult.Rejected("用户已拒绝 $requiredPermission 权限")
                PluginPermissionState.GRANTED -> Unit
            }
        }
        return runCatching {
            when (request.capability) {
                "device.info" -> CapabilityResult.Success(JSONObject().put("sdk", Build.VERSION.SDK_INT).put("model", Build.MODEL))
                "storage.get" -> CapabilityResult.Success(JSONObject().put("value", storage(plugin.id).getString(request.arguments.getString("key"), null)))
                "storage.set" -> {
                    storage(plugin.id).edit().putString(request.arguments.getString("key"), request.arguments.optString("value")).apply()
                    CapabilityResult.Success(JSONObject().put("ok", true))
                }
                "network.fetch" -> fetch(plugin, request.arguments)
                "usb.list", "bluetooth.list" -> CapabilityResult.Rejected("此主程序版本尚未开放该设备能力")
                "events.publish" -> {
                    val ok = PluginEventBus.publish(plugin.id, request.arguments.getString("topic"), request.arguments.optJSONObject("payload") ?: JSONObject())
                    CapabilityResult.Success(JSONObject().put("published", ok))
                }
                "services.register" -> {
                    val name = request.arguments.getString("name")
                    val version = request.arguments.optInt("version", 1)
                    require(plugin.services.any { it.name == name && it.version == version }) { "服务未在清单中声明" }
                    PluginServiceRegistry.register(plugin.id, name, version)
                    CapabilityResult.Success(JSONObject().put("ok", true))
                }
                "services.resolve" -> {
                    val descriptor = PluginServiceRegistry.resolve(request.arguments.getString("owner"), request.arguments.getString("name"), request.arguments.optInt("minimumVersion", 1))
                        ?.takeIf { found -> PluginManager.get(app).installed().any { it.id == found.ownerPluginId && it.enabled } }
                    CapabilityResult.Success(JSONObject().apply {
                        put("found", descriptor != null)
                        descriptor?.let { put("owner", it.ownerPluginId).put("name", it.name).put("version", it.version) }
                    })
                }
                else -> CapabilityResult.Rejected("不支持")
            }
        }.getOrElse { CapabilityResult.Rejected(it.message ?: "能力调用失败") }
    }

    fun setConsent(pluginId: String, permission: String, granted: Boolean) =
        permissions.set(pluginId, permission, if (granted) PluginPermissionState.GRANTED else PluginPermissionState.DENIED)

    private fun storage(pluginId: String) = app.getSharedPreferences("plugin_data_$pluginId", Context.MODE_PRIVATE)

    private fun fetch(plugin: InstalledPlugin, args: JSONObject): CapabilityResult {
        val url = args.getString("url")
        val uri = URI(url)
        require(uri.scheme == "https") { "只允许 HTTPS" }
        require(plugin.networkDomains.any { it.equals(uri.host, true) }) { "域名不在插件白名单" }
        http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val output = ByteArrayOutputStream()
            response.body?.byteStream()?.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 512 * 1024) { "响应过大" }
                    output.write(buffer, 0, count)
                }
            }
            return CapabilityResult.Success(JSONObject().put("status", response.code).put("body", output.toString(Charsets.UTF_8.name())))
        }
    }
}
