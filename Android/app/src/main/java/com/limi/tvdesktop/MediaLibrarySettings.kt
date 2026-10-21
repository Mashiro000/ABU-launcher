package com.limi.tvdesktop

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaLibrarySettings(
    onBack: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAccountForManage by remember { mutableStateOf<MediaAccount?>(null) }
    var editingAccount by remember { mutableStateOf<MediaAccount?>(null) }
    val firstFocus = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    val childOpen = showAddDialog || editingAccount != null || selectedAccountForManage != null
    val baseOffset by animateFloatAsState(if (childOpen) -1f else 0f,
        tween(RenderPerformance.duration(140)), label = "media-parent-slide")
    Column(
        Modifier
            .fillMaxSize().graphicsLayer {
                if (RenderPerformance.reducedEffects) alpha = 1f + baseOffset
                else translationX = size.width * baseOffset
            }
            .focusProperties { onEnter = { if (childOpen) cancelFocusChange() } }.focusGroup()
            .settingsVerticalScroll()
            .padding(top = 32.dp, bottom = 68.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "媒体数据源与账号设置",
                    color = White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "配置 Emby、Jellyfin、Plex 与 WebDAV/Alist 服务器",
                    color = Color(0xFFA5ACB8),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(text = "立即同步全部", onClick = {
                    MediaLibraryManager.refresh()
                    Toast.makeText(context, "正在同步所有已启用服务器", Toast.LENGTH_SHORT).show()
                })
                TvButton(
                    text = "+ 添加媒体服务器",
                    isPrimary = true,
                    focusRequester = firstFocus,
                    onClick = { showAddDialog = true }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Section: Accounts List
        SettingsSectionTitle("已绑定的媒体服务器 (${AccountManager.accounts.size})")

        if (AccountManager.accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousCornerShape(16.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂未添加任何媒体服务器，点击上方按钮添加您的第一个账号",
                    color = Color(0xFF9EA3AE),
                    fontSize = 18.sp
                )
            }
        } else {
            AccountManager.accounts.forEach { acc ->
                AccountItemRow(
                    account = acc,
                    onClick = { selectedAccountForManage = acc }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        SettingsSectionTitle("同步与首页")
        LibraryPreferenceRow("启动时同步服务器", "进入桌面后自动刷新所有启用的媒体源", AccountManager.syncOnLaunch.value, AccountManager::setSyncOnLaunch)
        Spacer(Modifier.height(10.dp))
        LibraryPreferenceRow("显示继续观看", "关闭后隐藏首页的继续观看与剧集续播内容", AccountManager.showNextUp.value) {
            AccountManager.setShowNextUp(it)
            MediaLibraryManager.refresh()
        }
        Spacer(Modifier.height(10.dp))
        LibraryPreferenceRow(
            "隐藏已看完内容", "从最新内容中隐藏播放进度达到 90% 的项目", AccountManager.hideWatched.value
        ) {
            AccountManager.setHideWatched(it)
            MediaLibraryManager.refresh()
        }

        Spacer(Modifier.height(32.dp))

        SettingsSectionTitle("播放与服务器兼容")
        EmbyPlaybackPolicyRow()
        Spacer(Modifier.height(10.dp))
        LibraryPreferenceRow("允许局域网 HTTP 服务器", "关闭后新增服务器必须使用 HTTPS 地址", AccountManager.allowInsecureConnections.value, AccountManager::setAllowInsecureConnections)

        Spacer(Modifier.height(32.dp))

        // Section: Display Mode
        SettingsSectionTitle("媒体库展现模式")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ModeOptionCard(
                title = LibraryDisplayMode.AGGREGATED.displayName,
                desc = LibraryDisplayMode.AGGREGATED.description,
                isSelected = AccountManager.displayMode.value == LibraryDisplayMode.AGGREGATED,
                onClick = { AccountManager.setDisplayMode(LibraryDisplayMode.AGGREGATED) }
            )
            ModeOptionCard(
                title = LibraryDisplayMode.ISOLATED.displayName,
                desc = LibraryDisplayMode.ISOLATED.description,
                isSelected = AccountManager.displayMode.value == LibraryDisplayMode.ISOLATED,
                onClick = { AccountManager.setDisplayMode(LibraryDisplayMode.ISOLATED) }
            )
        }

        Spacer(Modifier.height(32.dp))

        // Section: Poster Quality & Cache
        SettingsSectionTitle("海报画质与本地缓存")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PosterQuality.entries.forEach { q ->
                TvOptionChip(
                    text = q.displayName,
                    isSelected = AccountManager.posterQuality.value == q,
                    onClick = { AccountManager.setPosterQuality(q) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本地磁盘海报缓存已占用: ${String.format("%.1f", PosterCacheManager.getCacheSizeMB())} MB",
                color = Color(0xFFA5ACB8),
                fontSize = 16.sp
            )

            TvButton(
                text = "一键清理海报缓存",
                isPrimary = false,
                onClick = {
                    PosterCacheManager.clearCache()
                    Toast.makeText(context, "已清理本地图片缓存", Toast.LENGTH_SHORT).show()
                }
            )
        }

        Spacer(Modifier.height(28.dp))

    }

    // Add Account Dialog
    if (showAddDialog) {
        AddServerDialog(
            initialAccount = null,
            onDismiss = { showAddDialog = false },
            onSaved = { newAcc ->
                AccountManager.addAccount(newAcc)
                MediaLibraryManager.refresh()
                showAddDialog = false
                Toast.makeText(context, "已添加并同步 ${newAcc.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    editingAccount?.let { original ->
        AddServerDialog(
            initialAccount = original,
            onDismiss = { editingAccount = null },
            onSaved = { updated ->
                AccountManager.updateAccount(updated)
                MediaLibraryManager.refresh()
                editingAccount = null
                Toast.makeText(context, "服务器配置已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Account Detail & Actions Dialog
    selectedAccountForManage?.let { acc ->
        AccountManageDialog(
            account = acc,
            onDismiss = { selectedAccountForManage = null },
            onUpdate = { updated ->
                AccountManager.updateAccount(updated)
                MediaLibraryManager.refresh()
                selectedAccountForManage = updated
            },
            onEdit = {
                selectedAccountForManage = null
                editingAccount = acc
            },
            onTest = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) { providerFor(acc).testConnection() }
                    Toast.makeText(
                        context,
                        result.fold({ "连接成功：$it" }, { "连接失败：${it.message}" }),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDelete = {
                AccountManager.removeAccount(acc.id)
                MediaLibraryManager.refresh()
                selectedAccountForManage = null
                Toast.makeText(context, "已移除媒体源", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun LibraryPreferenceRow(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(16.dp))
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(1.5.dp, if (focused) Color.White else Color(0x22FFFFFF), ContinuousCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null) { onToggle(!enabled) }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (focused) Color.Black else White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = if (focused) Color(0xFF5A606D) else Color(0xFF9EA3AE), fontSize = 14.sp)
        }
        Box(
            Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(18.dp))
                .background(if (enabled) Color(0xFF3875F6) else Color(0xFF4B5563)).padding(4.dp),
            contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
        ) { Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White)) }
    }
}

@Composable
private fun EmbyPlaybackPolicyRow() {
    val policy = AccountManager.embyPlaybackPolicy.value
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier.fillMaxWidth().clip(ContinuousCornerShape(16.dp))
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(1.5.dp, if (focused) Color.White else Color(0x22FFFFFF), ContinuousCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null) {
                val values = AccountManager.EmbyPlaybackPolicy.entries
                AccountManager.setEmbyPlaybackPolicy(values[(policy.ordinal + 1) % values.size])
            }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(horizontal = 22.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Emby / Jellyfin 播放策略", color = if (focused) Color.Black else White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(policy.description, color = if (focused) Color(0xFF5A606D) else Color(0xFF9EA3AE), fontSize = 14.sp)
        }
        Text(policy.label, color = if (focused) Color(0xFF245FD2) else Color(0xFF8FB4FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountItemRow(
    account: MediaAccount,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(16.dp))
            .background(
                if (focused) Brush.linearGradient(listOf(Color.White, Color(0xFFEDEDED)))
                else Brush.linearGradient(listOf(Color(0x1EFFFFFF), Color(0x12FFFFFF)))
            )
            .border(
                1.5.dp,
                if (focused) Color.White else Color(0x24FFFFFF),
                ContinuousCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tag Badge for Server Type
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (account.type) {
                        ServerType.EMBY -> Color(0xFF52B54B)
                        ServerType.JELLYFIN -> Color(0xFF9353D3)
                        ServerType.PLEX -> Color(0xFFE5A00D)
                        ServerType.WEBDAV_ALIST -> Color(0xFF0070F3)
                    }
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                account.type.displayName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(18.dp))

        Column(Modifier.weight(1f)) {
            Text(
                account.name,
                color = if (focused) Color(0xFF14161B) else White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                account.serverUrl + if (account.username.isNotBlank()) "  ·  ${account.username}" else "",
                color = if (focused) Color(0xFF5A606D) else Color(0xFF9EA3AE),
                fontSize = 14.sp
            )
        }

        // Status Indicators
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (account.includeInAggregate) {
                Text(
                    "聚合",
                    color = if (focused) Color(0xFF141414) else Color(0xFF60A5FA),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (account.enabled) Color(0xFF22C55E) else Color(0xFFEF4444))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (account.enabled) "已启用" else "已停用",
                color = if (focused) Color(0xFF14161B) else Color(0xFFA5ACB8),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    desc: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(360.dp)
            .clip(ContinuousCornerShape(16.dp))
            .background(
                if (focused) Color.White
                else if (isSelected) Color(0x2E3875F6)
                else Color(0x14FFFFFF)
            )
            .border(
                2.dp,
                if (focused) Color.White else if (isSelected) Color(0xFF3875F6) else Color.Transparent,
                ContinuousCornerShape(16.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = if (focused) Color(0xFF14161B) else White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSelected) {
                Text("✓ 当前", color = if (focused) Color(0xFF141414) else Color(0xFF60A5FA), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            desc,
            color = if (focused) Color(0xFF5A606D) else Color(0xFFA5ACB8),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun TvOptionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White
                else if (isSelected) Color(0xFF3875F6)
                else Color(0x1EFFFFFF)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 20.sp,
            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TvButton(
    text: String,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White
                else if (isPrimary) Color(0xFF3875F6)
                else Color(0x26FFFFFF)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .settingsAutoScroll()
            .focusProperties { canFocus = true }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun AddServerDialog(
    initialAccount: MediaAccount?,
    onDismiss: () -> Unit,
    onSaved: (MediaAccount) -> Unit,
    standalone: Boolean = false
) {
    var selectedType by remember { mutableStateOf(initialAccount?.type ?: ServerType.EMBY) }
    var name by remember { mutableStateOf(initialAccount?.name.orEmpty()) }
    var host by remember { mutableStateOf(initialAccount?.serverUrl.orEmpty()) }
    var username by remember { mutableStateOf(initialAccount?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf(initialAccount?.token.orEmpty()) }
    var ignoreSsl by remember { mutableStateOf(initialAccount?.ignoreSslErrors ?: false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = remember { FocusRequester() }
    if (standalone) {
        BackHandler { onDismiss() }
        LaunchedEffect(Unit) { withFrameNanos { }; close.requestFocus() }
    }
    suspend fun account(): MediaAccount {
        val address = host.trim().trimEnd('/')
        val parsed = android.net.Uri.parse(address)
        require(parsed.scheme in listOf("http", "https") && !parsed.host.isNullOrBlank()) { "请输入完整服务器地址，例如 http://192.168.1.100:8096" }
        require(AccountManager.allowInsecureConnections.value || parsed.scheme != "http") { "当前禁止 HTTP，请使用 HTTPS 或调整媒体库连接设置" }
        var result = MediaAccount(
            id = initialAccount?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { selectedType.displayName }, type = selectedType, serverUrl = address,
            username = username.trim(), token = token.trim(), userId = initialAccount?.userId.orEmpty(),
            enabled = initialAccount?.enabled ?: true, includeInAggregate = initialAccount?.includeInAggregate ?: true,
            ignoreSslErrors = ignoreSsl)
        if (selectedType in listOf(ServerType.EMBY, ServerType.JELLYFIN) && password.isNotBlank()) {
            result = EmbyProvider(result).authenticate(password).getOrThrow()
        }
        return result
    }
    fun submit(save: Boolean) {
        if (busy) return
        busy = true; status = null
        scope.launch {
            try {
                val value = account()
                if (save) onSaved(value)
                else status = "连接成功 · ${providerFor(value).testConnection().getOrThrow()}"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "连接失败，请检查地址和凭据" }
            finally { busy = false }
        }
    }
    val form: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (initialAccount == null) "添加媒体库" else "编辑媒体库", color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("连接你的服务器，电影与剧集将在这里汇集", color = Muted, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (standalone) TvButton("取消", focusRequester = close, onClick = onDismiss)
        }
        Spacer(Modifier.height(24.dp))
        Text("服务器类型", color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        ServerType.entries.chunked(if (standalone) 4 else 2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { type ->
                    Box(Modifier.weight(1f)) { TvOptionChip(type.displayName, selectedType == type) { selectedType = type; status = null } }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(12.dp))
        TvInputField("服务器地址", when (selectedType) {
            ServerType.PLEX -> "http://192.168.1.100:32400"
            ServerType.WEBDAV_ALIST -> "http://192.168.1.100:5244"
            else -> "http://192.168.1.100:8096"
        }, host, { host = it })
        Spacer(Modifier.height(18.dp))
        if (selectedType in listOf(ServerType.EMBY, ServerType.JELLYFIN)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.weight(1f)) { TvInputField("用户名", "服务器账号", username, { username = it }) }
                Box(Modifier.weight(1f)) { TvInputField("密码", "输入密码", password, { password = it }, isPassword = true) }
            }
        } else TvInputField(if (selectedType == ServerType.PLEX) "Plex 访问令牌" else "Alist 令牌（可选）", "Token", token, { token = it }, isPassword = true)
        Spacer(Modifier.height(18.dp))
        TvInputField("媒体库名称（可选）", "默认使用 ${selectedType.displayName}", name, { name = it })
        Spacer(Modifier.height(18.dp))
        SettingsRowItem("允许自签名证书", if (ignoreSsl) "已开启" else "已关闭") { ignoreSsl = !ignoreSsl }
        status?.let { Text(it, color = if (it.startsWith("连接成功")) Color(0xFF71D9AD) else Color(0xFFFFA6A0), fontSize = 18.sp, modifier = Modifier.padding(top = 14.dp)) }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(if (busy) "正在连接…" else "测试连接", onClick = { submit(false) })
            TvButton(if (busy) "请稍候…" else "保存并连接", isPrimary = true, onClick = { submit(true) })
        }
        Spacer(Modifier.height(12.dp))
    }
    if (standalone) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 1000.dp).fillMaxWidth().heightIn(max = maxHeight - 64.dp)
                .clip(ContinuousCornerShape(28.dp)).background(Color(0xFF1A202B))
                .border(1.dp, Color(0x33FFFFFF), ContinuousCornerShape(28.dp))
                .focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
                .settingsVerticalScroll().padding(32.dp), content = form)
        }
    } else SettingsInlinePage(onDismiss, form)
}

@Composable
private fun AccountManageDialog(
    account: MediaAccount,
    onDismiss: () -> Unit,
    onUpdate: (MediaAccount) -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsInlinePage(onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(24.dp))
                .background(Color(0xFF1A1D24))

                .padding(32.dp)
        ) {
            Column {
                Text(
                    account.name,
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${account.type.displayName} · ${account.serverUrl}",
                    color = Color(0xFF9EA3AE),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Toggle Enabled
                ManageOptionRow(
                    label = "编辑地址与凭据",
                    value = "打开",
                    onClick = onEdit
                )

                Spacer(Modifier.height(12.dp))

                ManageOptionRow(
                    label = "测试服务器连接",
                    value = "立即测试",
                    onClick = onTest
                )

                Spacer(Modifier.height(12.dp))

                ManageOptionRow(
                    label = "启用此服务器",
                    value = if (account.enabled) "已开启" else "已停用",
                    onClick = { onUpdate(account.copy(enabled = !account.enabled)) }
                )

                Spacer(Modifier.height(12.dp))

                // Toggle Include In Aggregate
                ManageOptionRow(
                    label = "参与全源聚合海报墙",
                    value = if (account.includeInAggregate) "是" else "否",
                    onClick = { onUpdate(account.copy(includeInAggregate = !account.includeInAggregate)) }
                )

                Spacer(Modifier.height(12.dp))

                // Set as single active
                ManageOptionRow(
                    label = "设为独立模式首选源",
                    value = if (AccountManager.selectedAccountId.value == account.id) "当前首选" else "设为首选",
                    onClick = { AccountManager.selectAccount(account.id) }
                )

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TvButton(
                        text = "删除此服务器",
                        onClick = onDelete
                    )

                    TvButton(
                        text = "完成",
                        isPrimary = true,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

private fun providerFor(account: MediaAccount): MediaSourceProvider = when (account.type) {
    ServerType.EMBY, ServerType.JELLYFIN -> EmbyProvider(account)
    ServerType.PLEX -> PlexProvider(account)
    ServerType.WEBDAV_ALIST -> WebDavAlistProvider(account)
}

@Composable
private fun ManageOptionRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color(0x1AFFFFFF))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusProperties { canFocus = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (focused) Color.Black else White, fontSize = 16.sp)
        Text(value, color = if (focused) Color(0xFF141414) else Color(0xFF60A5FA), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvInputField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column {
        Text(label, color = Color(0xFFA5ACB8), fontSize = 18.sp, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 21.sp),
            cursorBrush = SolidColor(if (focused) Color.Black else Color.White),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth().settingsAutoScroll()
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) Color.White else Color(0x26FFFFFF))
                .border(1.dp, if (focused) Color(0xFF141414) else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = if (focused) Color(0xFF707782) else Color(0xFF9AA1AD), fontSize = 20.sp)
                    innerTextField()
                }
            }
        )
    }
}
