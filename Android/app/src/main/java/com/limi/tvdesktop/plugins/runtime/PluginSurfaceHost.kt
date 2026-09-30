package com.limi.tvdesktop.plugins.runtime

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.withFrameNanos
import org.json.JSONObject
import java.io.File

private data class PluginRoute(val name: String, val params: JSONObject = JSONObject())

@Composable
fun PluginSurfaceHost(plugin: InstalledPlugin, surface: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { PluginManager.get(context) }
    val client = remember { PluginSandboxClient(context) }
    val bridge = remember { PluginCapabilityBridge(context) }
    val session = remember(plugin.id, plugin.version) { PluginRuntimeSession(plugin, manager, client, bridge) }
    val scope = rememberCoroutineScope()
    var node by remember(plugin.id) { mutableStateOf<PluginUiNode?>(null) }
    var error by remember(plugin.id) { mutableStateOf<String?>(null) }
    var routes by remember(plugin.id, surface) { mutableStateOf(listOf(PluginRoute("root"))) }
    val values = remember(plugin.id, surface) { mutableStateMapOf<String, String>() }
    val focusByDepth = remember(plugin.id, surface) { mutableStateMapOf<Int, String>() }
    val focusRequesters = remember(plugin.id, surface) { mutableMapOf<String, FocusRequester>() }
    var consent by remember { mutableStateOf<Pair<PluginConsentRequest, CompletableDeferred<Boolean>>?>(null) }
    var systemPermissionAnswer by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val systemPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemPermissionAnswer?.complete(granted)
        systemPermissionAnswer = null
    }
    DisposableEffect(client) { onDispose(client::close) }

    suspend fun invoke(method: String, input: JSONObject) {
        try {
            val output = session.invokeOutput(method, input, requestConsent = { request ->
                val answer = CompletableDeferred<Boolean>()
                consent = request to answer
                try { answer.await() } finally { consent = null }
            }, requestAndroidPermission = { permission ->
                val answer = CompletableDeferred<Boolean>()
                systemPermissionAnswer = answer
                systemPermissionLauncher.launch(permission)
                try { answer.await() } finally { systemPermissionAnswer = null }
            })
            output.ui?.let { node = it }
            error = null
            val navigation = output.navigation
            val next = when {
                method == "render" -> null
                navigation?.push != null && routes.size < 16 -> routes + PluginRoute(navigation.push, navigation.params ?: JSONObject())
                navigation?.pop == true && routes.size > 1 -> routes.dropLast(1)
                else -> null
            }
            if (next != null) {
                routes = next
                node = null
                focusRequesters.clear()
                invoke("render", renderInput(surface, routes.last()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = "插件运行失败：${failure.message}"
        }
    }

    LaunchedEffect(plugin.id, plugin.version, surface) {
        invoke("render", renderInput(surface, routes.last()))
    }
    BackHandler(enabled = routes.size > 1) {
        routes = routes.dropLast(1)
        node = null
        focusRequesters.clear()
        scope.launch { invoke("render", renderInput(surface, routes.last())) }
    }
    LaunchedEffect(routes, node) {
        if (node != null) {
            withFrameNanos { }
            val key = focusByDepth[routes.lastIndex] ?: focusRequesters.keys.firstOrNull()
            key?.let { focusRequesters[it]?.requestFocus() }
        }
    }
    LaunchedEffect(plugin.id, plugin.version) {
        PluginEventBus.events.filter { it.sourcePluginId != plugin.id }.collect { event ->
            invoke("onEvent", JSONObject().put("sourcePluginId", event.sourcePluginId).put("topic", event.topic).put("payload", event.payload))
        }
    }
    Box(modifier.fillMaxSize().padding(42.dp), contentAlignment = Alignment.Center) {
        when {
            node != null -> PluginUiRenderer(
                node!!,
                onAction = { action ->
                    scope.launch {
                        invoke("onAction", JSONObject().put("surface", surface).put("route", routes.last().name)
                            .put("params", routes.last().params)
                            .put("action", action).put("values", JSONObject(values.toMap())))
                    }
                },
                onInput = { id, value -> values[id] = value },
                currentValues = values,
                focusRequesters = focusRequesters,
                onFocused = { key -> focusByDepth[routes.lastIndex] = key },
                resolveAsset = { asset ->
                    val root = manager.currentVersionDir(plugin.id)
                    val file = root?.let { File(it, asset) }
                    file?.takeIf { asset.startsWith("assets/") && !asset.contains("..") &&
                        it.canonicalPath.startsWith(File(root, "assets").canonicalPath + File.separator) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            error != null -> Text(error!!, color = Color(0xFFFF8A80), fontSize = 20.sp)
            else -> Text("正在加载 ${plugin.name}…", color = Color(0xFFA5ACB8), fontSize = 20.sp)
        }
    }
    consent?.let { (request, answer) ->
        AlertDialog(
            onDismissRequest = { if (!answer.isCompleted) answer.complete(false) },
            title = { Text("插件请求权限") },
            text = { Text("${plugin.name} 请求“${request.title}”${if (request.sensitive) "。这是敏感权限，仅在你信任此插件时允许。" else "。"}") },
            confirmButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(true) }) { Text("允许") } },
            dismissButton = { TextButton(onClick = { if (!answer.isCompleted) answer.complete(false) }) { Text("拒绝") } },
        )
    }
}

private fun renderInput(surface: String, route: PluginRoute): JSONObject = JSONObject()
    .put("surface", surface).put("route", route.name).put("params", route.params)
