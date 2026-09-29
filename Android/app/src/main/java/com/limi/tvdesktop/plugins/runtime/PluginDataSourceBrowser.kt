package com.limi.tvdesktop.plugins.runtime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.json.JSONObject

/** A native, bounded browser for declared data_source plugins. The plugin never receives account tokens. */
@Composable
fun PluginDataSourceBrowser(
    plugin: InstalledPlugin,
    sources: List<InstalledPlugin>,
    onSelectSource: (String) -> Unit,
    onClose: () -> Unit,
    onPlay: (id: String, title: String, streamUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { PluginManager.get(context) }
    val client = remember(plugin.id) { PluginSandboxClient(context) }
    val bridge = remember { PluginCapabilityBridge(context) }
    val session = remember(plugin.id, plugin.version) { PluginRuntimeSession(plugin, manager, client, bridge) }
    val scope = rememberCoroutineScope()
    val entries = remember(plugin.id) { mutableStateListOf<PluginMediaEntry>() }
    var query by remember(plugin.id) { mutableStateOf("") }
    var nextCursor by remember(plugin.id) { mutableStateOf<String?>(null) }
    var loading by remember(plugin.id) { mutableStateOf(false) }
    var error by remember(plugin.id) { mutableStateOf<String?>(null) }
    var consent by remember(plugin.id) { mutableStateOf<Pair<PluginConsentRequest, CompletableDeferred<Boolean>>?>(null) }
    DisposableEffect(client) { onDispose(client::close) }
    BackHandler(onBack = onClose)

    suspend fun load(cursor: String?, search: String) {
        if (loading) return
        loading = true
        runCatching {
            val output = session.invokeOutput(
                "onDataSource",
                JSONObject().put("operation", "list").put("query", search).put("cursor", cursor ?: JSONObject.NULL),
                requestConsent = { request ->
                    val answer = CompletableDeferred<Boolean>()
                    consent = request to answer
                    answer.await().also { consent = null }
                },
            )
            PluginDataSource.parse(output.value)
        }.fold(onSuccess = { page ->
            if (cursor == null) entries.clear()
            entries.addAll(page.items)
            nextCursor = page.nextCursor
            error = null
        }, onFailure = { error = it.message ?: "加载失败" })
        loading = false
    }

    LaunchedEffect(plugin.id, plugin.version) { load(null, query) }
    Column(Modifier.fillMaxSize().background(Color(0xFF090B10)).padding(36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("插件媒体源 · ${plugin.name}", color = Color.White, fontSize = 27.sp)
            TextButton(onClick = onClose) { Text("返回媒体库") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sources, key = InstalledPlugin::id) { source ->
                TextButton(onClick = { onSelectSource(source.id) }) { Text(source.name) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索") }, singleLine = true)
            TextButton(onClick = { scope.launch { load(null, query) } }) { Text("查询") }
        }
        if (loading) CircularProgressIndicator()
        error?.let { Text("加载失败：$it", color = Color(0xFFFF8A80)) }
        if (!loading && entries.isEmpty() && error == null) Text("此数据源没有返回内容", color = Color.LightGray)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries, key = { index, _ -> index }) { _, item ->
                TextButton(onClick = {
                    scope.launch {
                        val url = PluginDataSource.playableUrl(plugin, item.streamUrl)
                        if (url == null) {
                            error = "播放地址必须是已声明域名的 HTTPS 地址，并且插件需声明 network 权限"
                            return@launch
                        }
                        when (bridge.consentState(plugin.id, "network")) {
                            PluginPermissionState.DENIED -> { error = "用户已拒绝网络权限"; return@launch }
                            PluginPermissionState.ASK -> {
                                val declaration = plugin.permissions.first { it.id == "network" }
                                val answer = CompletableDeferred<Boolean>()
                                consent = PluginConsentRequest("network", declaration.title, declaration.sensitive) to answer
                                val granted = try { answer.await() } finally { consent = null }
                                bridge.setConsent(plugin.id, "network", granted)
                                if (!granted) { error = "用户已拒绝网络权限"; return@launch }
                            }
                            PluginPermissionState.GRANTED -> Unit
                        }
                        onPlay(item.id, item.title, url)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(item.title, color = Color.White) }
            }
            if (nextCursor != null) item {
                TextButton(onClick = { scope.launch { load(nextCursor, query) } }) { Text("加载更多") }
            }
        }
    }
    consent?.let { (request, answer) ->
        AlertDialog(
            onDismissRequest = { if (!answer.isCompleted) answer.complete(false) },
            title = { Text("插件请求权限") },
            text = { Text("${plugin.name} 请求“${request.title}”。${if (request.sensitive) "这是敏感权限，请确认信任此数据源。" else ""}") },
            confirmButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(true) }) { Text("允许") } },
            dismissButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(false) }) { Text("拒绝") } },
        )
    }
}
