package com.limi.tvdesktop.plugins

import org.json.JSONArray
import org.json.JSONObject

/** Strict contract for script packages that opt into a versioned host API. */
object PluginScriptManifestValidator {
    private val idPattern = Regex("[a-zA-Z0-9._-]{3,100}")
    private val versionPattern = Regex("[0-9A-Za-z][0-9A-Za-z._+-]{0,63}")
    private val servicePattern = Regex("[a-zA-Z0-9._-]{1,100}")
    private val domainPattern = Regex("[a-zA-Z0-9.-]+")
    private val supportedSurfaces = setOf("home", "settings", "player")
    private val supportedSlots = setOf("home.quickActions", "player.overlay")
    private val supportedPermissions = setOf("storage", "network", "usb", "bluetooth")

    fun validate(manifest: JSONObject) {
        require(manifest.optInt("schemaVersion") == 1) { "schemaVersion: 必须是 1" }
        require(idPattern.matches(manifest.optString("id"))) { "id: 格式无效" }
        require(versionPattern.matches(manifest.optString("version"))) { "version: 格式无效" }
        require(manifest.optString("name").isNotBlank()) { "name: 不能为空" }
        require(manifest.optString("author").isNotBlank()) { "author: 不能为空" }
        require(manifest.optString("kind") in setOf("ui", "data_source", "subtitle", "system")) { "kind: 类型无效" }
        val entry = manifest.optString("entry")
        require(validPath(entry) && entry.endsWith(".js")) { "entry: 必须是包内相对 .js 路径" }
        val hostApi = manifest.optString("hostApi")
        require(hostApi.isNotBlank() && PluginHostApi.supports(hostApi)) { "hostApi: 与当前 ${PluginHostApi.VERSION} 不兼容" }

        strings(manifest, "surfaces").forEach { require(it in supportedSurfaces) { "surfaces: 不支持 $it" } }
        strings(manifest, "slots").forEach { require(it in supportedSlots) { "slots: 不支持 $it" } }
        val permissions = objects(manifest, "permissions")
        permissions.forEach {
            require(it.optString("id") in supportedPermissions && it.optString("title").isNotBlank()) { "permissions: 权限声明无效" }
        }
        val domains = strings(manifest, "networkDomains")
        domains.forEach {
            require(domainPattern.matches(it) && !it.startsWith('.') && !it.contains("..")) { "networkDomains: 域名无效" }
        }
        require(domains.isEmpty() || permissions.any { it.optString("id") == "network" }) { "networkDomains: 需要声明 network 权限" }
        val services = objects(manifest, "services")
        services.forEach {
            require(servicePattern.matches(it.optString("name")) && it.optInt("version") > 0) { "services: 服务声明无效" }
        }
        require(services.map { it.optString("name") }.distinct().size == services.size) { "services: 服务名重复" }
    }

    fun validPath(path: String): Boolean = path.isNotBlank() && !path.startsWith('/') &&
        !path.contains('\\') && !path.contains(':') && !path.contains('\u0000') &&
        path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    private fun strings(manifest: JSONObject, name: String): List<String> {
        val array = array(manifest, name)
        return (0 until array.length()).map { index ->
            require(array.opt(index) is String) { "$name: 必须是字符串数组" }
            array.getString(index)
        }
    }

    private fun objects(manifest: JSONObject, name: String): List<JSONObject> {
        val array = array(manifest, name)
        return (0 until array.length()).map { index ->
            array.optJSONObject(index) ?: throw IllegalArgumentException("$name: 必须是对象数组")
        }
    }

    private fun array(manifest: JSONObject, name: String): JSONArray {
        if (!manifest.has(name)) return JSONArray()
        return manifest.optJSONArray(name) ?: throw IllegalArgumentException("$name: 必须是数组")
    }
}
