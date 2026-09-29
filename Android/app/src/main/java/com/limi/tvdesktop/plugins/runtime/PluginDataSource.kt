package com.limi.tvdesktop.plugins.runtime

import com.limi.tvdesktop.plugins.InstalledPlugin
import org.json.JSONObject
import java.net.URI

data class PluginMediaEntry(val id: String, val title: String, val streamUrl: String?)
data class PluginMediaPage(val items: List<PluginMediaEntry>, val nextCursor: String?)

object PluginDataSource {
    fun parse(value: JSONObject?): PluginMediaPage {
        requireNotNull(value) { "数据源没有返回 value 对象" }
        require(value.optString("error").isBlank()) { value.optString("error") }
        val array = value.optJSONArray("items") ?: throw IllegalArgumentException("数据源缺少 items 数组")
        require(array.length() <= 100) { "单页媒体条目超过 100 个" }
        val items = (0 until array.length()).map { index ->
            val item = array.optJSONObject(index) ?: throw IllegalArgumentException("媒体条目必须是对象")
            val id = item.optString("id")
            val title = item.optString("title")
            require(id.isNotBlank() && id.length <= 200 && title.isNotBlank() && title.length <= 200) { "媒体条目 ID 或标题无效" }
            PluginMediaEntry(id, title, item.optString("streamUrl").takeIf(String::isNotBlank))
        }
        val cursor = if (value.isNull("nextCursor")) null else value.optString("nextCursor").takeIf(String::isNotBlank)
        require(cursor == null || cursor.length <= 200) { "下一页游标过长" }
        return PluginMediaPage(items, cursor)
    }

    fun playableUrl(plugin: InstalledPlugin, url: String?): String? = runCatching {
        if (url == null || plugin.permissions.none { it.id == "network" }) return@runCatching null
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            plugin.networkDomains.none { it.equals(uri.host, true) }) null else url
    }.getOrNull()
}
