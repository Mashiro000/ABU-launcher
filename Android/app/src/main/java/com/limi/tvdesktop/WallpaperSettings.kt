package com.limi.tvdesktop

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun WallpaperSettings(
    onBack: () -> Unit,
    onWallpaperChanged: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(WallpaperMode.current(context)) }
    var onlineVideos by remember { mutableStateOf(false) }
    var bingAuto by remember { mutableStateOf(BingWallpaper.autoUpdate(context)) }
    var downloading by remember { mutableStateOf(false) }
    var bingFailed by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(BingWallpaper.downloaded(context)) }
    var selectedDate by remember { mutableStateOf(BingWallpaper.selectedDate(context)) }
    var localRes by remember { mutableStateOf(LocalWallpapers.selectedRes(context)) }
    var localUri by remember { mutableStateOf(LocalWallpapers.selectedUri(context)) }
    var videoUri by remember { mutableStateOf(VideoWallpaperPrefs.current(context)) }

    val backRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            LocalWallpapers.saveUri(context, uri)
            WallpaperMode.save(context, WallpaperMode.LOCAL)
            mode = WallpaperMode.LOCAL
            localRes = null
            localUri = uri
            onWallpaperChanged()
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            VideoWallpaperPrefs.save(context, uri)
            WallpaperMode.save(context, WallpaperMode.VIDEO)
            mode = WallpaperMode.VIDEO
            videoUri = uri
            onWallpaperChanged()
        }
    }

    fun applyMode(m: WallpaperMode) {
        WallpaperMode.save(context, m)
        mode = m
        onWallpaperChanged()
    }

    LaunchedEffect(mode) {
        if (mode == WallpaperMode.BING) {
            downloading = true
            try { BingWallpaper.ensureDaily(context) }
            finally {
                downloading = false
                downloaded = BingWallpaper.downloaded(context)
                selectedDate = BingWallpaper.selectedDate(context)
                bingFailed = downloaded.isEmpty()
                onWallpaperChanged()
            }
        }
    }
    if (onlineVideos) {
        SettingsInlinePage({ onlineVideos = false }) {
            OnlineVideoWallpapers {
                videoUri = VideoWallpaperPrefs.current(context)
                mode = WallpaperMode.current(context)
                onWallpaperChanged()
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .settingsVerticalScroll()
            .padding(top = 28.dp, bottom = 68.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "壁纸设置",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "选择应用在主屏幕的背景呈现样式",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        SettingsSectionTitle("壁纸来源")
        WallpaperMode.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { source ->
                    WallpaperSourceCard(source, selected = mode == source, modifier = Modifier.weight(1f)) { applyMode(source) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Text("选择来源后，在下方预览并选择壁纸。完整图片与视频按需读取，内置少量小尺寸视频预览图。", color = Muted, fontSize = 17.sp)

        Spacer(Modifier.height(28.dp))

        when (mode) {
            WallpaperMode.GRADIENT -> GradientPalettePicker(onWallpaperChanged)
            WallpaperMode.FOLLOW_CONTENT -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(ContinuousCornerShape(18.dp))
                        .background(Color(0x1AFFFFFF))
                        .padding(24.dp)
                ) {
                    Text(
                        "首页将跟随推荐影视内容的海报自动切换壁纸；\n媒体库页面则始终沉浸跟随当前播放与浏览的封面。",
                        color = Color(0xFFC5CBD6),
                        fontSize = 19.sp,
                        lineHeight = 28.sp
                    )
                }
            }

            WallpaperMode.BING -> {
                SettingsSectionTitle("今日必应壁纸")
                SettingsRowItem("锁定当前 Bing 壁纸", if (bingAuto) "未锁定 · 每日更新" else "已锁定") {
                    bingAuto = !bingAuto
                    BingWallpaper.setAutoUpdate(context, bingAuto)
                    if (bingAuto) scope.launch {
                        downloading = true
                        try { BingWallpaper.ensureDaily(context) }
                        finally {
                            downloading = false
                            downloaded = BingWallpaper.downloaded(context)
                            selectedDate = BingWallpaper.selectedDate(context)
                            onWallpaperChanged()
                        }
                    }
                }
                Text(if (bingAuto) "每天使用桌面时自动检查更新，断网后稍后重试。" else "保留当前图片。关闭锁定可恢复每日更新；选择历史图片也会自动锁定。", color = Muted, fontSize = 17.sp)
                SettingsRowItem(
                    label = if (downloading) "正在下载必应超清壁纸…" else if (bingAuto) "检查并应用今日 Bing 壁纸" else "下载今日图片（保留当前）",
                    value = if (downloading) "下载中" else "每日更新",
                    leftReturnRequester = returnRequester
                ) {
                    if (!downloading) scope.launch {
                        downloading = true
                        bingFailed = false
                        val daily = BingWallpaper.fetchToday()
                        if (daily != null) {
                            if (BingWallpaper.download(context, daily) != null) {
                                if (bingAuto || downloaded.isEmpty()) {
                                    BingWallpaper.saveDate(context, daily.date)
                                    selectedDate = daily.date
                                }
                                downloaded = BingWallpaper.downloaded(context)
                                onWallpaperChanged()
                            } else bingFailed = true
                        } else bingFailed = true
                        downloading = false
                    }
                }
                if (bingFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text("下载失败，请检查网络连接。", color = Color(0xFFEF5350), fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.height(24.dp))
                if (downloaded.isNotEmpty()) {
                    SettingsSectionTitle("已下载壁纸历史")
                    DownloadedRow(downloaded, selectedDate, returnRequester) { date ->
                        BingWallpaper.setAutoUpdate(context, false)
                        bingAuto = false
                        BingWallpaper.saveDate(context, date)
                        selectedDate = date
                        onWallpaperChanged()
                    }
                } else {
                    Text("暂无下载记录，点击上方按钮即可一键获取今日美图。", color = Color(0xFF8B929E), fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }

            WallpaperMode.LOCAL -> {
                SettingsSectionTitle("本地图片")
                LocalWallpapers.bundled.chunked(4).forEach { rowEntries ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowEntries.forEach { entry ->
                            val bmp by produceState<ImageBitmap?>(null, entry.res) {
                                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BitmapFactory.decodeResource(
                                    context.resources,
                                    entry.res,
                                    BitmapFactory.Options().apply { inSampleSize = 2 }
                                ).asImageBitmap() }
                            }
                            if (bmp != null) WallpaperThumb(
                                bitmap = bmp!!,
                                label = entry.name,
                                selected = localRes == entry.res && localUri == null,
                                modifier = Modifier.weight(1f),
                                leftReturnRequester = returnRequester
                            ) {
                                LocalWallpapers.saveRes(context, entry.res)
                                localRes = entry.res
                                localUri = null
                                WallpaperMode.save(context, WallpaperMode.LOCAL)
                                mode = WallpaperMode.LOCAL
                                onWallpaperChanged()
                            }
                        }
                        repeat(4 - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.height(12.dp))
                SettingsSectionTitle("自定义壁纸")
                SettingsRowItem(
                    label = "从电视本地存储添加图片…",
                    value = if (localUri != null) "已生效本地图片" else "浏览文件",
                    leftReturnRequester = returnRequester
                ) {
                    imagePicker.launch(arrayOf("image/*"))
                }
            }

            WallpaperMode.VIDEO -> {
                SettingsSectionTitle("动态视频壁纸")
                SettingsRowItem("浏览并下载在线视频壁纸", "航拍 · 海底 · 太空") { onlineVideos = true }
                Spacer(Modifier.height(12.dp))
                SettingsRowItem(
                    label = if (videoUri == null) "选择本地视频文件作为壁纸…" else "更换当前壁纸视频…",
                    value = if (videoUri != null) "已配置" else "未设置",
                    leftReturnRequester = returnRequester
                ) {
                    videoPicker.launch(arrayOf("video/*"))
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(ContinuousCornerShape(16.dp))
                        .background(Color(0x18FFFFFF))
                        .padding(20.dp)
                ) {
                    Text(
                        if (videoUri == null)
                            "尚未选择视频。设置后桌面将静音、无缝循环播放该动态视频，尽享生机勃勃的客厅巨幕。"
                        else
                            "视频静音循环；离开桌面、待机或打开应用列表时暂停。文件无法读取时显示默认背景。低性能电视建议使用 1080p H.264 视频。",
                        color = Color(0xFFC5CBD6),
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperSourceCard(source: WallpaperMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = ContinuousCornerShape(18.dp)
    val colors = when (source) {
        WallpaperMode.FOLLOW_CONTENT -> listOf(Color(0xFF34455D), Color(0xFF1E2633))
        WallpaperMode.BING -> listOf(Color(0xFF285E59), Color(0xFF183B42))
        WallpaperMode.LOCAL -> listOf(Color(0xFF5A436A), Color(0xFF30293D))
        WallpaperMode.GRADIENT -> listOf(Color(0xFF735581), Color(0xFF334775))
        WallpaperMode.VIDEO -> listOf(Color(0xFF66513A), Color(0xFF372D29))
    }
    Column(modifier.height(124.dp).onFocusChanged { focused = it.hasFocus }.settingsAutoScroll()
        .clip(shape).background(Brush.linearGradient(colors))
        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = .15f), shape)
        .clickable(onClick = onClick).padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(source.label, color = White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text(if (selected) "✓ 当前来源" else "选择来源 ›", color = Muted, fontSize = 16.sp)
    }
}

@Composable
private fun DownloadedRow(
    files: List<File>,
    selectedDate: String?,
    leftReturnRequester: FocusRequester?,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(files) { file ->
            val date = file.nameWithoutExtension
            val bmp by produceState<ImageBitmap?>(null, file) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { BingWallpaper.bitmap(file) } }
            if (bmp != null) {
                WallpaperThumb(
                    bitmap = bmp!!,
                    label = date,
                    selected = date == selectedDate,
                    modifier = Modifier.width(260.dp),
                    leftReturnRequester = leftReturnRequester
                ) {
                    onSelect(date)
                }
            }
        }
    }
}

@Composable
private fun WallpaperThumb(
    bitmap: ImageBitmap,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    leftReturnRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered

    val scale by animateFloatAsState(if (active) 1.05f else 1f, focusMotion(), label = "thumb-scale")
    val shape = ContinuousCornerShape(16.dp)

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (active) 5f else 0f)
            .focusProperties {
                if (leftReturnRequester != null) left = leftReturnRequester
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent {
                if (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyDown) {
                        onClick()
                    }
                    true
                } else false
            }
            .settingsAutoScroll()
            .focusable(interactionSource = interaction)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .clip(shape)
            .border(
                width = if (active) 3.dp else if (selected) 2.dp else 1.dp,
                color = if (active) Color.White else if (selected) Color(0xCCFFFFFF) else Color(0x25FFFFFF),
                shape = shape
            )
            .focusSweep(active, shape)
    ) {
        Image(
            bitmap,
            label,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Bottom label gradient overlay
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC000000))
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                label,
                color = if (active) White else Color(0xDDFFFFFF),
                fontSize = 16.sp,
                fontWeight = if (active || selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }

        if (selected) {
            Box(
                Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(ContinuousCornerShape(50.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color(0xFF14161B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
