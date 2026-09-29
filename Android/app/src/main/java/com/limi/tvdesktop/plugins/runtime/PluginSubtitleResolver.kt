package com.limi.tvdesktop.plugins.runtime

import android.content.Context
import com.limi.tvdesktop.MediaItemInfo
import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginKind
import com.limi.tvdesktop.plugins.PluginManager
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.net.URI

object PluginSubtitleResolver {
    /** Never passes an Emby token, stream URL, account ID or headers to subtitle plugins. */
    suspend fun resolve(
        context: Context,
        media: MediaItemInfo,
        requestConsent: suspend (PluginConsentRequest) -> Boolean,
    ): List<String> {
        val manager = PluginManager.get(context)
        val bridge = PluginCapabilityBridge(context)
        val plugins = manager.installed().filter { it.enabled && it.kind == PluginKind.SUBTITLE && it.entry != null }.take(3)
        val urls = mutableListOf<String>()
        for (plugin in plugins) {
            PluginSandboxClient(context).use { client ->
                val output = try {
                    PluginRuntimeSession(plugin, manager, client, bridge).invokeOutput(
                        "onSubtitle",
                        JSONObject().put("title", media.title).put("durationMs", media.totalDurationMs)
                            .put("seasonNumber", media.seasonNumber).put("episodeNumber", media.episodeNumber),
                        requestConsent = requestConsent,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                val entries = output.value?.optJSONArray("subtitles") ?: continue
                val candidates = (0 until minOf(entries.length(), 10)).mapNotNull { index ->
                    val url = entries.optJSONObject(index)?.optString("url") ?: entries.optString(index)
                    url.takeIf { allowed(plugin, it) }
                }
                if (candidates.isEmpty()) continue
                if (bridge.consentState(plugin.id, "network") != PluginPermissionState.GRANTED) {
                    if (bridge.consentState(plugin.id, "network") == PluginPermissionState.DENIED) continue
                    val declaration = plugin.permissions.firstOrNull { it.id == "network" } ?: continue
                    val granted = requestConsent(PluginConsentRequest("network", declaration.title, declaration.sensitive))
                    bridge.setConsent(plugin.id, "network", granted)
                    if (!granted) continue
                }
                urls += candidates
            }
        }
        return urls.distinct()
    }

    fun allowed(plugin: InstalledPlugin, url: String): Boolean = runCatching {
        if (plugin.permissions.none { it.id == "network" }) return@runCatching false
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.host != null &&
            plugin.networkDomains.any { it.equals(uri.host, true) } &&
            uri.path.substringAfterLast('.').lowercase() in setOf("srt", "vtt")
    }.getOrDefault(false)
}
