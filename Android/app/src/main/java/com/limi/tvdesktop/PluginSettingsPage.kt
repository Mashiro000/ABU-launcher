package com.limi.tvdesktop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.limi.tvdesktop.plugins.InstalledPlugin
import com.limi.tvdesktop.plugins.PluginManager
import com.limi.tvdesktop.plugins.PluginTrust
import com.limi.tvdesktop.plugins.PluginRepositoryClient
import com.limi.tvdesktop.plugins.PluginSignatureVerifier
import com.limi.tvdesktop.plugins.PluginVerification
import com.limi.tvdesktop.plugins.RemotePlugin
import com.limi.tvdesktop.plugins.MpvPluginRuntime
import com.limi.tvdesktop.plugins.runtime.PluginSandboxClient
import com.limi.tvdesktop.plugins.runtime.PluginUiParser
import com.limi.tvdesktop.plugins.runtime.PluginUiNode
import com.limi.tvdesktop.plugins.runtime.PluginUiRenderer
import com.limi.tvdesktop.plugins.runtime.PluginPermissionState
import com.limi.tvdesktop.plugins.runtime.PluginPermissionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PluginSettingsPage(onBack: () -> Unit, returnRequester: FocusRequester?) {
    val context = LocalContext.current
    val manager = remember { PluginManager.get(context) }
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var remotePlugins by remember { androidx.compose.runtime.mutableStateOf<List<RemotePlugin>>(emptyList()) }
    var pendingInstall by remember { androidx.compose.runtime.mutableStateOf<RemotePlugin?>(null) }
    var selectedPlugin by remember { androidx.compose.runtime.mutableStateOf<InstalledPlugin?>(null) }
    var repositoryEditor by remember { androidx.compose.runtime.mutableStateOf<com.limi.tvdesktop.plugins.PluginRepository?>(null) }
    var addingRepository by remember { androidx.compose.runtime.mutableStateOf(false) }
    val plugins = remember(refresh) { manager.installed() }
    val backRequester = remember { FocusRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val repositoryClient = remember { PluginRepositoryClient(context) }

    fun refreshRepositories() {
        scope.launch {
            status = "正在检查插件仓库…"
            val found = mutableListOf<RemotePlugin>()
            val errors = mutableListOf<String>()
            manager.repositories().filter { it.enabled }.forEach { repository ->
                repositoryClient.fetch(repository).fold(
                    onSuccess = { found += it },
                    onFailure = { errors += "${repository.name}: ${it.message}" }
                )
            }
            remotePlugins = found.distinctBy { "${it.id}@${it.version}" }
            status = when {
                found.isNotEmpty() -> "发现 ${found.size} 个可用插件"
                errors.isNotEmpty() -> "仓库检查失败：${errors.joinToString("；")}" 
                else -> "仓库中暂无插件"
            }
        }
    }

    fun installRemote(plugin: RemotePlugin) {
        val abi = MpvPluginRuntime.currentAbi().orEmpty()
        val asset = plugin.assets.firstOrNull { it.abi == abi || it.abi == "universal" }
        if (asset == null) {
            status = "没有适用于本机 $abi 的插件包"
            return
        }
        scope.launch {
            status = "正在下载 ${plugin.name}…"
            repositoryClient.download(plugin, asset) { downloaded, total ->
                if (total > 0) status = "正在下载 ${plugin.name} ${(downloaded * 100 / total).coerceIn(0, 100)}%"
            }.fold(
                onSuccess = { file ->
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            manager.installPackage(
                                file,
                                expectedSha256 = asset.sha256,
                                verification = PluginVerification(plugin.publicKeyBase64, asset.signatureBase64, plugin.official),
                            )
                        }.also { file.delete() }
                    }
                    status = result.fold(
                        onSuccess = { "已安装 ${it.name} v${it.version}，请检查权限后启用" },
                        onFailure = { "安装失败：${it.message}" }
                    )
                    refresh++
                },
                onFailure = { status = "下载失败：${it.message}" }
            )
        }
    }

    LaunchedEffect(Unit) {
        backRequester.requestFocus()
        refreshRepositories()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = "正在校验并安装插件…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "plugin-import-${System.nanoTime()}.abu-plugin")
                    try {
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "无法读取插件文件" }
                            temp.outputStream().use { input.copyTo(it) }
                        }
                        manager.installPackage(temp)
                    } finally {
                        temp.delete()
                    }
                }
            }
            status = result.fold(
                onSuccess = { "已安装 ${it.name} v${it.version}，请确认权限后手动启用" },
                onFailure = { "安装失败：${it.message ?: "插件包无效"}" }
            )
            refresh++
        }
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 20.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PluginActionRow(
                title = "← 返回设置",
                subtitle = null,
                modifier = Modifier.focusRequester(backRequester),
                onClick = onBack
            )
            Spacer(Modifier.height(20.dp))
            Text("插件", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "插件默认关闭；敏感能力会在首次使用时再次征求授权",
                color = Color(0xFFA5ACB8),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
            if (status != null) Text(
                status!!,
                color = if (status!!.startsWith("安装失败")) Color(0xFFFF8A80) else Color(0xFF9BE7A5),
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (plugins.isEmpty()) {
            item {
                PluginActionRow(
                    title = "暂无已安装插件",
                    subtitle = "可从插件仓库安装，或导入本地 .abu-plugin 文件",
                    onClick = {}
                )
            }
        } else {
            items(plugins, key = { it.id }) { plugin ->
                InstalledPluginRow(plugin, onOpen = { selectedPlugin = plugin }, onToggle = {
                    manager.setEnabled(plugin.id, !plugin.enabled)
                    refresh++
                })
            }
        }

        item {
            SettingsSectionTitle("插件来源")
            PluginActionRow(
                title = "检查插件仓库",
                subtitle = "获取更新与适用于 ${MpvPluginRuntime.currentAbi() ?: "当前设备"} 的插件",
                onClick = ::refreshRepositories
            )
            Spacer(Modifier.height(10.dp))
            manager.repositories().forEach { repo ->
                PluginActionRow(
                    title = repo.name,
                    subtitle = "${if (repo.official) "官方" else "第三方"} · ${if (repo.enabled) "已启用" else "已停用"} · ${repo.indexUrl}",
                    onClick = { if (!repo.official) repositoryEditor = repo }
                )
                Spacer(Modifier.height(10.dp))
            }
            PluginActionRow(
                title = "添加第三方仓库",
                subtitle = "第三方插件可能读取数据或改变整个界面，添加前请核对发布者指纹",
                accent = Color(0xFFFFB86B),
                onClick = { addingRepository = true }
            )
            if (remotePlugins.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SettingsSectionTitle("可安装插件")
                remotePlugins.forEach { plugin ->
                    PluginActionRow(
                        title = "${plugin.name}  v${plugin.version}",
                        subtitle = "${if (plugin.official) "官方" else "第三方"} · ${plugin.author} · ${plugin.description}",
                        onClick = { pendingInstall = plugin }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            PluginActionRow(
                title = "从本地导入插件",
                subtitle = "支持离线安装；安装前校验文件哈希与发布者签名",
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "安全模式：启动应用时长按返回键，可临时停用全部第三方插件。连续三次启动失败时，最近启用的插件将被自动关闭。",
                color = Color(0xFF8B929E),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }

    pendingInstall?.let { plugin ->
        val sensitive = plugin.permissions.filter { it.sensitive }
        AlertDialog(
            onDismissRequest = { pendingInstall = null },
            title = { Text(if (plugin.official) "安装官方插件" else "安装第三方插件") },
            text = {
                Column {
                    Text("${plugin.name} v${plugin.version}\n发布者：${plugin.author}")
                    Spacer(Modifier.height(10.dp))
                    Text("发布者指纹：${PluginSignatureVerifier.fingerprint(plugin.publicKeyBase64)}", fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(if (plugin.permissions.isEmpty()) "无需额外权限" else "权限：${plugin.permissions.joinToString { it.title }}")
                    if (sensitive.isNotEmpty()) Text("敏感权限会在首次使用时再次询问。", color = Color(0xFFFFB86B))
                    if (!plugin.official) Text("第三方插件可能改变应用行为，请仅安装可信来源。", color = Color(0xFFFF8A80))
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingInstall = null; installRemote(plugin) }) { Text("下载并安装") }
            },
            dismissButton = {
                TextButton(onClick = { pendingInstall = null }) { Text("取消") }
            }
        )
    }

    selectedPlugin?.let { plugin ->
        PluginDetailDialog(
            plugin,
            manager,
            onChanged = {
                refresh++
                selectedPlugin = manager.installed().firstOrNull { it.id == plugin.id }
            },
            onDismiss = { selectedPlugin = null },
        )
    }
    if (addingRepository) RepositoryAddDialog(
        onDismiss = { addingRepository = false },
        onAdd = { name, url ->
            runCatching { manager.addRepository(name, url) }.fold(
                onSuccess = { addingRepository = false; refresh++; status = "已添加第三方仓库" },
                onFailure = { status = "添加失败：${it.message}" },
            )
        },
    )
    repositoryEditor?.let { repository ->
        AlertDialog(
            onDismissRequest = { repositoryEditor = null },
            title = { Text(repository.name) },
            text = { Text(repository.indexUrl) },
            confirmButton = {
                TextButton(onClick = {
                    manager.setRepositoryEnabled(repository.indexUrl, !repository.enabled)
                    repositoryEditor = null; refresh++
                }) { Text(if (repository.enabled) "停用仓库" else "启用仓库") }
            },
            dismissButton = {
                TextButton(onClick = {
                    manager.removeRepository(repository.indexUrl)
                    repositoryEditor = null; refresh++
                }) { Text("删除", color = Color(0xFFFF8A80)) }
            },
        )
    }
}

@Composable
private fun RepositoryAddDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    var url by remember { androidx.compose.runtime.mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加第三方仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("第三方仓库中的插件可能改变界面或访问获准的数据，请仅添加可信来源。", color = Color(0xFFFFB86B))
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("仓库名称") })
                OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("HTTPS 索引地址") })
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onAdd(name, url.trim()) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun InstalledPluginRow(plugin: InstalledPlugin, onOpen: () -> Unit, onToggle: () -> Unit) {
    val trust = when (plugin.trust) {
        PluginTrust.OFFICIAL -> "官方"
        PluginTrust.VERIFIED -> "签名已验证"
        PluginTrust.UNVERIFIED -> "未验证发布者"
    }
    PluginActionRow(
        title = plugin.name,
        subtitle = "${plugin.kind.name.lowercase()} · v${plugin.version} · $trust · ${formatBytes(plugin.installedBytes)}",
        trailing = {
            Switch(checked = plugin.enabled, onCheckedChange = { onToggle() })
        },
        onClick = onOpen
    )
}

@Composable
private fun PluginDetailDialog(
    plugin: InstalledPlugin,
    manager: PluginManager,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val client = remember { PluginSandboxClient(context) }
    val permissionStore = remember { PluginPermissionStore(context) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var preview by remember { androidx.compose.runtime.mutableStateOf<PluginUiNode?>(null) }
    var previewStatus by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var confirmUninstall by remember { androidx.compose.runtime.mutableStateOf(false) }
    var operationStatus by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    DisposableEffect(client) { onDispose { client.close() } }
    LaunchedEffect(plugin.id, plugin.version) {
        val source = runCatching { manager.readEntryScript(plugin.id) }.getOrNull()
        if (source == null) {
            previewStatus = "此插件不提供界面入口"
        } else {
            previewStatus = "正在隔离进程中加载界面…"
            client.execute(plugin.id, source, "render", "{\"surface\":\"settings\"}").fold(
                onSuccess = { json ->
                    runCatching { PluginUiParser.parse(json).ui }.fold(
                        onSuccess = { preview = it; previewStatus = if (it == null) "插件未返回界面" else null },
                        onFailure = { previewStatus = "界面数据无效：${it.message}" },
                    )
                },
                onFailure = { previewStatus = "插件运行失败：${it.message}" },
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${plugin.name}  v${plugin.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(plugin.description.ifBlank { "暂无说明" })
                Text("作者：${plugin.author} · ${plugin.trust.name.lowercase()}", fontSize = 13.sp)
                if (plugin.permissions.isEmpty()) Text("权限：无", fontSize = 13.sp) else {
                    Text("权限", fontWeight = FontWeight.SemiBold)
                    plugin.permissions.forEach { permission ->
                        val state = remember(permissionRevision, plugin.id, permission.id) { permissionStore.get(plugin.id, permission.id) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(permission.title, fontSize = 14.sp)
                                Text("${permission.id}${if (permission.sensitive) " · 敏感权限" else ""} · ${state.name.lowercase()}", fontSize = 12.sp, color = if (permission.sensitive) Color(0xFFFFB86B) else Color(0xFFA5ACB8))
                            }
                            TextButton(onClick = {
                                val next = when (state) {
                                    PluginPermissionState.ASK -> PluginPermissionState.GRANTED
                                    PluginPermissionState.GRANTED -> PluginPermissionState.DENIED
                                    PluginPermissionState.DENIED -> PluginPermissionState.ASK
                                }
                                permissionStore.set(plugin.id, permission.id, next)
                                permissionRevision++
                            }) { Text(when (state) { PluginPermissionState.ASK -> "每次询问"; PluginPermissionState.GRANTED -> "已允许"; PluginPermissionState.DENIED -> "已拒绝" }) }
                        }
                    }
                }
                if (plugin.surfaces.isNotEmpty()) Text("可替换页面：${plugin.surfaces.joinToString()}", fontSize = 13.sp)
                if (plugin.slots.isNotEmpty()) Text("扩展槽位：${plugin.slots.joinToString()}", fontSize = 13.sp)
                if (plugin.availableVersions.size > 1) {
                    Text("已保留版本", fontWeight = FontWeight.SemiBold)
                    plugin.availableVersions.forEach { version ->
                        TextButton(
                            enabled = version != plugin.version,
                            onClick = {
                                runCatching { manager.activateVersion(plugin.id, version) }.fold(
                                    onSuccess = { operationStatus = "已切换到 v$version"; onChanged() },
                                    onFailure = { operationStatus = "切换失败：${it.message}" },
                                )
                            },
                        ) { Text(if (version == plugin.version) "v$version（当前）" else "切换到 v$version") }
                    }
                }
                operationStatus?.let { Text(it, color = Color(0xFFA5ACB8), fontSize = 13.sp) }
                previewStatus?.let { Text(it, color = Color(0xFFA5ACB8), fontSize = 13.sp) }
                preview?.let { PluginUiRenderer(it, onAction = { previewStatus = "已触发操作：$it" }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = { confirmUninstall = true }) { Text("卸载", color = Color(0xFFFF8A80)) } },
    )
    if (confirmUninstall) AlertDialog(
        onDismissRequest = { confirmUninstall = false },
        title = { Text("卸载 ${plugin.name}？") },
        text = { Text("将删除插件的全部版本、权限记录和插件私有数据，此操作不可撤销。") },
        confirmButton = {
            TextButton(onClick = {
                manager.uninstall(plugin.id)
                confirmUninstall = false
                onChanged()
                onDismiss()
            }) { Text("确认卸载", color = Color(0xFFFF6B6B)) }
        },
        dismissButton = { TextButton(onClick = { confirmUninstall = false }) { Text("取消") } },
    )
}

@Composable
private fun PluginActionRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF9B8CFF),
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier.fillMaxWidth()
            .background(if (focused) Color(0x26FFFFFF) else Color(0x0FFFFFFF), RoundedCornerShape(18.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) accent else Color(0x20FFFFFF), RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Color(0xFFA5ACB8), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        trailing?.invoke()
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
