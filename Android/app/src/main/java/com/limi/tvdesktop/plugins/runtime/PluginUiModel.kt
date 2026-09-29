package com.limi.tvdesktop.plugins.runtime

import org.json.JSONArray
import org.json.JSONObject

data class PluginUiNode(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val action: String? = null,
    val tone: String? = null,
    val children: List<PluginUiNode> = emptyList(),
)

data class PluginRuntimeOutput(
    val ui: PluginUiNode?,
    val capabilities: List<PluginCapabilityRequest>,
)

data class PluginCapabilityRequest(val id: String, val capability: String, val arguments: JSONObject)

object PluginUiParser {
    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 300

    fun parse(json: String): PluginRuntimeOutput {
        val root = JSONObject(json)
        var count = 0
        fun node(value: JSONObject, depth: Int): PluginUiNode {
            require(depth <= MAX_DEPTH && ++count <= MAX_NODES) { "插件页面过于复杂" }
            val childrenJson = value.optJSONArray("children") ?: JSONArray()
            return PluginUiNode(
                type = value.optString("type", "text").lowercase(),
                id = value.optString("id").takeIf { it.isNotBlank() },
                text = value.optString("text").takeIf { it.isNotBlank() },
                action = value.optString("action").takeIf { it.isNotBlank() },
                tone = value.optString("tone").takeIf { it.isNotBlank() },
                children = (0 until childrenJson.length()).mapNotNull { childrenJson.optJSONObject(it)?.let { child -> node(child, depth + 1) } },
            )
        }
        val actions = root.optJSONArray("capabilities") ?: JSONArray()
        return PluginRuntimeOutput(
            ui = root.optJSONObject("ui")?.let { node(it, 0) },
            capabilities = (0 until actions.length()).mapNotNull { index ->
                actions.optJSONObject(index)?.let {
                    PluginCapabilityRequest(it.optString("id", index.toString()), it.optString("capability"), it.optJSONObject("arguments") ?: JSONObject())
                }
            }.filter { it.capability.isNotBlank() },
        )
    }
}
