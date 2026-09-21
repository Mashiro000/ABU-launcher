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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    val firstFocus = remember { FocusRequester() }

    BackHandler {
        onBack()
    }

    LaunchedEffect(Unit) {
        firstFocus.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 48.dp)
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

            TvButton(
                text = "+ 添加媒体服务器",
                isPrimary = true,
                focusRequester = firstFocus,
                onClick = { showAddDialog = true }
            )
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

        // Section: Demo Data Toggle
        SettingsSectionTitle("未绑定时体验演示")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousCornerShape(16.dp))
                .background(Color(0x14FFFFFF))
                .clickable {
                    AccountManager.setShowDemoWhenEmpty(!AccountManager.showDemoWhenEmpty.value)
                }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "在无网络账号时显示示例电影海报",
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "若关闭，未绑定媒体源时将显示纯净的“添加媒体库”引导界面",
                    color = Color(0xFF9EA3AE),
                    fontSize = 14.sp
                )
            }
            Text(
                if (AccountManager.showDemoWhenEmpty.value) "已开启" else "已隐藏",
                color = if (AccountManager.showDemoWhenEmpty.value) Color(0xFF22C55E) else Color(0xFF9EA3AE),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // Add Account Dialog
    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onSaved = { newAcc ->
                AccountManager.addAccount(newAcc)
                MediaLibraryManager.refresh()
                showAddDialog = false
                Toast.makeText(context, "已添加并同步 ${newAcc.name}", Toast.LENGTH_SHORT).show()
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
                selectedAccountForManage = null
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
            .focusable(interactionSource = interaction)
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
                    color = if (focused) Color(0xFF3875F6) else Color(0xFF60A5FA),
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
            .focusable(interactionSource = interaction)
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
                Text("✓ 当前", color = if (focused) Color(0xFF3875F6) else Color(0xFF60A5FA), fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (focused) Color.White
                else if (isSelected) Color(0xFF3875F6)
                else Color(0x1EFFFFFF)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 16.sp,
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
            .focusable(interactionSource = interaction)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onSaved: (MediaAccount) -> Unit
) {
    var selectedType by remember { mutableStateOf(ServerType.EMBY) }
    var name by remember { mutableStateOf("我的 Emby") }
    var host by remember { mutableStateOf("http://192.168.1.100:8096") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var ignoreSsl by remember { mutableStateOf(true) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .clip(ContinuousCornerShape(24.dp))
                .background(Color(0xFF1A1D24))
                .border(1.5.dp, Color(0x40FFFFFF), ContinuousCornerShape(24.dp))
                .padding(36.dp)
        ) {
            Column {
                Text(
                    "添加媒体服务器",
                    color = White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                // Protocol selector
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServerType.entries.forEach { type ->
                        TvOptionChip(
                            text = type.displayName,
                            isSelected = selectedType == type,
                            onClick = {
                                selectedType = type
                                name = "我的 ${type.displayName}"
                                host = when (type) {
                                    ServerType.EMBY, ServerType.JELLYFIN -> "http://192.168.1.100:8096"
                                    ServerType.PLEX -> "http://192.168.1.100:32400"
                                    ServerType.WEBDAV_ALIST -> "http://192.168.1.100:5244"
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                TvInputField(label = "名称", value = name, onValueChange = { name = it })
                Spacer(Modifier.height(12.dp))
                TvInputField(label = "服务器地址 (URL)", value = host, onValueChange = { host = it })
                Spacer(Modifier.height(12.dp))

                if (selectedType == ServerType.PLEX) {
                    TvInputField(label = "X-Plex-Token", value = token, onValueChange = { token = it })
                } else if (selectedType == ServerType.WEBDAV_ALIST) {
                    TvInputField(label = "Alist Token (若无需密码可留空)", value = token, onValueChange = { token = it })
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) {
                            TvInputField(label = "用户名", value = username, onValueChange = { username = it })
                        }
                        Box(Modifier.weight(1f)) {
                            TvInputField(label = "密码", value = password, isPassword = true, onValueChange = { password = it })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // SSL bypass checkbox
                Row(
                    modifier = Modifier.clickable { ignoreSsl = !ignoreSsl },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (ignoreSsl) "☑" else "☐", color = Color(0xFF3875F6), fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("忽略 SSL 自签名证书校验 (内网 HTTPS 推荐开启)", color = Color(0xFFA5ACB8), fontSize = 14.sp)
                }

                testStatus?.let { status ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        status,
                        color = if (status.startsWith("成功")) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TvButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(12.dp))

                    TvButton(
                        text = if (testing) "测试中..." else "测试连接",
                        onClick = {
                            testing = true
                            testStatus = null
                            scope.launch {
                                val tempAcc = MediaAccount(
                                    name = name,
                                    type = selectedType,
                                    serverUrl = host,
                                    username = username,
                                    token = token,
                                    ignoreSslErrors = ignoreSsl
                                )
                                val provider = when (selectedType) {
                                    ServerType.EMBY, ServerType.JELLYFIN -> {
                                        val p = EmbyProvider(tempAcc)
                                        if (password.isNotBlank()) {
                                            p.authenticate(password)
                                        }
                                        p
                                    }
                                    ServerType.PLEX -> PlexProvider(tempAcc)
                                    ServerType.WEBDAV_ALIST -> WebDavAlistProvider(tempAcc)
                                }
                                val res = provider.testConnection()
                                withContext(Dispatchers.Main) {
                                    testing = false
                                    testStatus = if (res.isSuccess) "成功: ${res.getOrNull()}" else "失败: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    )

                    Spacer(Modifier.width(12.dp))

                    TvButton(
                        text = "保存并启用",
                        isPrimary = true,
                        onClick = {
                            scope.launch {
                                var finalAcc = MediaAccount(
                                    name = name,
                                    type = selectedType,
                                    serverUrl = host,
                                    username = username,
                                    token = token,
                                    ignoreSslErrors = ignoreSsl
                                )
                                if ((selectedType == ServerType.EMBY || selectedType == ServerType.JELLYFIN) && password.isNotBlank()) {
                                    val p = EmbyProvider(finalAcc)
                                    val authRes = p.authenticate(password)
                                    if (authRes.isSuccess) {
                                        finalAcc = authRes.getOrThrow()
                                    }
                                }
                                onSaved(finalAcc)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountManageDialog(
    account: MediaAccount,
    onDismiss: () -> Unit,
    onUpdate: (MediaAccount) -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .clip(ContinuousCornerShape(24.dp))
                .background(Color(0xFF1A1D24))
                .border(1.5.dp, Color(0x40FFFFFF), ContinuousCornerShape(24.dp))
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
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (focused) Color.Black else White, fontSize = 16.sp)
        Text(value, color = if (focused) Color(0xFF3875F6) else Color(0xFF60A5FA), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TvInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column {
        Text(label, color = Color(0xFFA5ACB8), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(if (focused) Color.Black else Color.White),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) Color.White else Color(0x26FFFFFF))
                .border(1.dp, if (focused) Color(0xFF3875F6) else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}
