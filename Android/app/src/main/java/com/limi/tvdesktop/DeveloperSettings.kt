package com.limi.tvdesktop

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun DeveloperSettings(onBack: () -> Unit, returnRequester: FocusRequester?, onTestDesktop: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val back = remember { FocusRequester() }
    var message by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("选择下方设备检查获取详细信息") }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching {
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(pendingExport) }
                }
                "报告已导出"
            }.getOrElse { "导出失败：${it.javaClass.simpleName}" }
        } else message = "已取消导出，可使用保存到本机"
    }
    DisposableEffect(Unit) {
        DeveloperDiagnostics.pageVisible = true
        onDispose { DeveloperDiagnostics.pageVisible = false }
    }
    LaunchedEffect(Unit) { back.requestFocus() }

    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 20.dp, bottom = 60.dp)) {
        SettingsRowItem("← 返回分类", null, back, returnRequester, onBack)
        Spacer(Modifier.height(20.dp))
        Text("电视开发者工具", color = White, fontSize = 34.sp)
        Info("方向键移动，确认键操作，返回键退出。测试时可回到桌面浏览，采样跨页面继续。")
        SettingsSectionTitle("渲染策略")
        Action("低性能模式", if (RenderPerformance.reducedEffects) "已启用" else "关闭", returnRequester) {
            if (DeveloperDiagnostics.remaining > 0) DeveloperDiagnostics.finish("渲染模式改变，测试提前结束")
            RenderPerformance.setEnabled(context, !RenderPerformance.lowPerformance)
        }
        Info("关闭实时与静态模糊、背景采样、扫光和分批入场，减少焦点弹跳；安卓 9 自动使用兼容策略。")
        SettingsSectionTitle("实时性能")
        PerformanceReadout()
        Action("性能悬浮面板", if (DeveloperDiagnostics.overlay) "开启" else "关闭", returnRequester) { DeveloperDiagnostics.toggleOverlay() }
        Info("面板不抢焦点，每秒更新；内存每 3 秒更新。关闭面板并离开工具页后，未进行测试时停止采样。")
        SettingsSectionTitle("30 秒实际操作测试")
        Action(if (DeveloperDiagnostics.remaining > 0) "停止测试" else "开始测试并返回桌面",
            if (DeveloperDiagnostics.remaining > 0) "剩余 ${DeveloperDiagnostics.remaining} 秒" else "手动场景采样", returnRequester) {
            if (DeveloperDiagnostics.remaining > 0) DeveloperDiagnostics.finish() else {
                DeveloperDiagnostics.start()
                if (DeveloperDiagnostics.remaining > 0) onTestDesktop()
            }
        }
        Info("建议固定路线：首页左右切换应用 → 展开应用列表 → 媒体库滚动 → 海报墙 → 详情页。分别以标准 / 低性能模式各测一次，保持同样路线与海报缓存状态。测试自动结束；进入后台会提前结束。")
        if (DeveloperDiagnostics.lastReport.isNotBlank()) ReadableReport(DeveloperDiagnostics.lastReport, returnRequester)
        SettingsSectionTitle("遥控器诊断")
        Info(DeveloperDiagnostics.keySummary)
        Info("显示系统键码、长按重复次数和事件进入应用前的等待时间；不是按键到屏幕显示的完整延迟。")
        SettingsSectionTitle("设备、内存、缓存与网络")
        ReadableReport(details, returnRequester)
        Action("刷新设备检查", if (busy) "检查中" else "设备 / 网络 / 缓存", returnRequester) {
            if (!busy) scope.launch {
                busy = true
                details = runCatching { withContext(Dispatchers.IO) { deviceDetails(context) } }
                    .getOrElse { "检查失败：${it.javaClass.simpleName}" }
                busy = false
            }
        }
        Action("视频解码器能力", if (busy) "检查中" else "AVC / HEVC / AV1", returnRequester) {
            if (!busy) scope.launch {
                busy = true
                details = runCatching { withContext(Dispatchers.Default) {
                    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
                        .flatMap { codec -> codec.supportedTypes.filter { it in setOf("video/avc", "video/hevc", "video/av01") }
                            .map { type -> "$type · ${codec.name}" + if (Build.VERSION.SDK_INT >= 29) {
                                if (codec.isHardwareAccelerated) " · 硬件" else " · 软件 / 其他"
                            } else " · 硬件属性不可用"
                            }
                        }.joinToString("\n").ifBlank { "未发现匹配解码器" }
                } }.getOrElse { "无法读取解码器信息：${it.javaClass.simpleName}" }
                busy = false
            }
        }
        Info("解码器列表是系统声明的能力，不保证所有分辨率或片源都可流畅播放。网络检查只读取连接状态，不发送请求。")
        SettingsSectionTitle("诊断报告")
        if (message.isNotBlank()) Info(message)
        Action("保存报告到本机", "无需文件选择器", returnRequester) {
            val report = DeveloperDiagnostics.report(context) + "\n\n设备检查\n$details"
            scope.launch {
                message = runCatching { withContext(Dispatchers.IO) { "已保存：${DeveloperDiagnostics.save(context, report).absolutePath}" } }
                    .getOrElse { "保存失败：${it.javaClass.simpleName}" }
            }
        }
        Action("导出报告", "选择文件位置", returnRequester) {
            pendingExport = DeveloperDiagnostics.report(context) + "\n\n设备检查\n$details"
            runCatching { export.launch("desktop-performance-${System.currentTimeMillis()}.txt") }
                .onFailure { message = "这台电视没有文件选择器，请使用保存到本机或复制报告" }
        }
        Action("复制报告", "复制到剪贴板", returnRequester) {
            runCatching {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("桌面性能诊断", DeveloperDiagnostics.report(context) + "\n\n$details"))
            }.onSuccess { message = "报告已复制" }.onFailure { message = "剪贴板不可用，请保存到本机" }
        }
        Action("重置统计", "清空本次内存中的结果", returnRequester) { DeveloperDiagnostics.reset(); message = "统计已重置，已保存的文件保留" }
        SettingsSectionTitle("系统工具")
        Action("系统开发者选项", "GPU 绘制 / 过度绘制等", returnRequester) {
            runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                .onFailure { message = "电视未开放系统开发者选项，请在系统设置中查看" }
        }
        Info("超预算按系统帧截止时间判定，旧系统按屏幕刷新周期估算；P95 越低越稳定。窗口帧统计不能直接定位视频解码、网络或 GPU 利用率；静止页面帧数低不等于卡顿。报告仅保存在本机或你选择的位置。")
    }
}

@Composable
private fun PerformanceReadout() {
    Info(DeveloperDiagnostics.status + "\n" + DeveloperDiagnostics.live.text() + "\n" + DeveloperDiagnostics.memory)
}

@Composable
private fun Info(value: String) {
    Text(value, color = Color(0xFFB9C2CF), fontSize = 18.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
}

/** Short focusable blocks let a remote scroll through long reports without skipping them. */
@Composable
private fun ReadableReport(value: String, returnRequester: FocusRequester?) {
    value.lines().chunked(5).forEach { lines ->
        var focused by remember { mutableStateOf(false) }
        Text(lines.joinToString("\n"), color = Color(0xFFB9C2CF), fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                .focusProperties { if (returnRequester != null) left = returnRequester }
                .settingsAutoScroll()
                .onFocusChanged { focused = it.isFocused }.focusable()
                .border(1.dp, if (focused) Color.White else Color.Transparent, ContinuousCornerShape(8.dp))
                .padding(10.dp))
    }
}

@Composable
private fun Action(title: String, value: String, left: FocusRequester?, action: () -> Unit) {
    SettingsRowItem(title, value, leftReturnRequester = left, onClick = action)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun BoxScope.PerformanceOverlay() {
    if (!DeveloperDiagnostics.overlay) return
    Column(Modifier.align(Alignment.TopEnd).padding(12.dp).width(460.dp).zIndex(1000f)
        .background(Color(0xED10151B)).padding(12.dp)) {
        Text("${DeveloperDiagnostics.scene} · ${if (RenderPerformance.reducedEffects) "低性能" else "标准"}" +
            if (DeveloperDiagnostics.remaining > 0) " · 测试 ${DeveloperDiagnostics.remaining}s" else "", color = White, fontSize = 15.sp)
        Text(DeveloperDiagnostics.live.text(), color = Color(0xFF93E5B8), fontSize = 14.sp)
        Text(DeveloperDiagnostics.memory, color = Color.LightGray, fontSize = 13.sp)
    }
}

@Suppress("DEPRECATION")
private fun deviceDetails(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val display = context.resources.displayMetrics
    val network = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = network.getNetworkCapabilities(network.activeNetwork)
    val transport = when {
        capabilities == null -> "未连接"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        else -> "其他连接"
    }
    return "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}" +
        "\nABI ${Build.SUPPORTED_ABIS.joinToString()} · CPU 逻辑核心 ${Runtime.getRuntime().availableProcessors()}" +
        "\n应用显示区域 ${display.widthPixels} × ${display.heightPixels} · 密度 ${display.densityDpi} dpi" +
        "\n系统低内存设备 ${if (activityManager.isLowRamDevice) "是" else "否"} · Java 堆配额 ${activityManager.memoryClass} MB" +
        "\n$transport · 系统互联网验证 ${if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) "通过" else "未通过 / 不可用"}" +
        "\n海报内存缓存 ${PosterCacheManager.memorySizeMB()} MB · 磁盘缓存 %.1f MB".format(Locale.ROOT, PosterCacheManager.getCacheSizeMB()) +
        "\n应用目录可用空间 ${context.filesDir.usableSpace / (1024 * 1024)} MB"
}
