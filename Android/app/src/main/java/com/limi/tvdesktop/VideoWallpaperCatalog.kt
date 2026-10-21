package com.limi.tvdesktop

import android.content.Context
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Small link-only selection from Apple's public Aerial catalog; videos are never bundled. */
internal object VideoWallpaperCatalog {
    data class Entry(val id: String, val title: String, val category: String, val filename: String) {
        val url get() = "https://sylvan.apple.com/Videos/$filename.mov"
    }
    @Volatile private var catalog: List<Entry>? = null
    fun entries(context: Context): List<Entry> = catalog ?: synchronized(this) {
        catalog ?: org.json.JSONArray(context.assets.open("video-wallpapers.json").bufferedReader().use { it.readText() }).let { array ->
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                Entry(it.getString("id"), it.getString("title"), it.getString("category"), it.getString("filename"))
            } }
        }.also { catalog = it }
    }
    private val thumbnailSlots = kotlinx.coroutines.sync.Semaphore(2)
    suspend fun thumbnail(context: Context, entry: Entry): androidx.compose.ui.graphics.ImageBitmap? = withContext(Dispatchers.IO) {
        thumbnailSlots.acquire()
        try {
            val dir = File(context.cacheDir, "video-previews").apply { mkdirs() }
            val cached = File(dir, "${entry.id}.jpg")
            if (cached.exists()) BitmapFactory.decodeFile(cached.path)?.let { return@withContext it.asImageBitmap() }
            val base = if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) context.getSharedPreferences("desktop", 0).getString("videoPreviewBaseUrl", null) else null
            val url = (base ?: "https://raw.githubusercontent.com/Mashiro000/ABU-launcher/main/docs/video-previews").trimEnd('/') + "/${entry.id}.jpg"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000; connection.readTimeout = 8_000
            try {
                check(connection.responseCode == 200)
                val bytes = connection.inputStream.use { input ->
                    val result = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(result.size() + count <= 512 * 1024)
                        result.write(buffer, 0, count)
                    }
                    result.toByteArray()
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                check(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                cached.writeBytes(bytes)
                bitmap.asImageBitmap()
            } finally { connection.disconnect() }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        finally { thumbnailSlots.release() }
    }
    fun file(context: Context, entry: Entry) = File(File(context.filesDir, "video-wallpapers").apply { mkdirs() }, "${entry.id}.mov")
    suspend fun download(context: Context, entry: Entry, sourceUrl: String = entry.url, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val output = file(context, entry)
        val partial = File(output.path + ".part")
        val conn = URL(sourceUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        try {
            check(conn.responseCode == 200) { "视频源暂时不可用（${conn.responseCode}）" }
            val total = conn.contentLengthLong
            val limit = 1024L * 1024 * 1024
            check(total <= limit) { "视频超过 1 GB，请选择其他视频" }
            check(output.parentFile!!.usableSpace > maxOf(total, 64L * 1024 * 1024) + 32L * 1024 * 1024) { "存储空间不足" }
            var received = 0L
            var reported = 0L
            conn.inputStream.use { input -> partial.outputStream().use { stream ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    check(received <= limit) { "视频超过 1 GB" }
                    stream.write(buffer, 0, count)
                    if (received - reported >= 512 * 1024) { progress(received, total); reported = received }
                }
            } }
            check(received > 0 && (total < 0 || received == total)) { "下载不完整，请重试" }
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(partial.path)
                check(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "下载内容不是有效视频" }
            } finally { metadata.release() }
            currentCoroutineContext().ensureActive()
            check(partial.renameTo(output)) { "保存失败" }
            output
        } finally { conn.disconnect(); partial.delete() }
    }
}

@Composable
internal fun OnlineVideoWallpapers(onApplied: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { VideoWallpaperCatalog.entries(context) }
    var filter by remember { mutableStateOf("全部") }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<VideoWallpaperCatalog.Entry?>(null) }
    var task by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("") }
    Text("在线航拍视频", color = White, fontSize = 28.sp)
    Text("Apple Aerial · ${catalog.size} 部。视频按需下载，缩略图联网获取并缓存。视频版权归原权利人所有。", color = Muted, fontSize = 17.sp)
    val filtered = remember(filter, revision) { catalog.filter { filter == "全部" || it.category == filter || (filter == "已下载" && VideoWallpaperCatalog.file(context, it).exists()) } }
    val pages = ((filtered.size + 7) / 8).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pages - 1)
    val current = selected
    if (current == null) {
        listOf("全部", "城市", "自然", "海底", "太空", "已下载").chunked(3).forEach { filters ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                filters.forEach { label ->
                    var focused by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f).semantics { contentDescription = "筛选${label}视频" }.onFocusChanged { focused = it.hasFocus }.settingsAutoScroll()
                        .background(if (focused) Color.White else if (filter == label) Color(0x664F78B4) else Color(0x1AFFFFFF), ContinuousCornerShape(12.dp))
                        .clickable { filter = label; page = 0 }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (focused) Color(0xFF14161B) else White, fontSize = 19.sp)
                    }
                }
            }
        }
        Text("${filter} · ${filtered.size} 部 · 第 ${currentPage + 1} / $pages 页", color = Muted, fontSize = 17.sp)
        if (filtered.isEmpty()) Text("暂无已下载视频，选择一个画面开始下载。", color = Muted, fontSize = 18.sp)
        filtered.drop(currentPage * 8).take(8).chunked(2).forEach { entries ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                entries.forEach { entry -> key(entry.id) {
                    val exists = remember(revision) { VideoWallpaperCatalog.file(context, entry).exists() }
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.weight(1f).onFocusChanged { focused = it.hasFocus }.settingsAutoScroll()
                        .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x33FFFFFF), ContinuousCornerShape(14.dp))
                        .clickable { selected = entry; status = "" }.padding(10.dp)) {
                        VideoPreview(entry)
                        Text(entry.title, color = White, fontSize = 19.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 50.dp).padding(top = 8.dp))
                        Text(if (exists) "✓ 已下载" else entry.category, color = Muted, fontSize = 16.sp)
                    }
                } }
                if (entries.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (currentPage > 0) Box(Modifier.weight(1f)) { SettingsRowItem("上一页", null) { page = currentPage - 1 } }
            if (currentPage < pages - 1) Box(Modifier.weight(1f)) { SettingsRowItem("下一页", null) { page = currentPage + 1 } }
        }
    } else {
        SettingsRowItem("← 返回视频目录", current.category) { if (task?.isActive != true) selected = null }
        VideoPreview(current)
        Text(current.title, color = White, fontSize = 24.sp)
        val file = VideoWallpaperCatalog.file(context, current)
        if (file.exists()) {
            SettingsRowItem("设为视频壁纸", "${file.length() / 1024 / 1024} MB") {
                VideoWallpaperPrefs.save(context, Uri.fromFile(file)); WallpaperMode.save(context, WallpaperMode.VIDEO)
                status = "已应用"; onApplied()
            }
            SettingsRowItem("删除下载的视频", null) {
                if (VideoWallpaperPrefs.current(context) == Uri.fromFile(file)) {
                    context.getSharedPreferences("desktop", 0).edit().remove("wallpaperVideoUri").apply()
                    WallpaperMode.save(context, WallpaperMode.GRADIENT); onApplied()
                }
                if (file.delete()) { revision++; status = "已删除" } else status = "删除失败"
            }
        } else if (task?.isActive == true) {
            SettingsRowItem("取消下载", status) { task?.cancel() }
        } else {
            Text("1080p H.264 / SDR · 文件通常为数百 MB。离开此页会取消未完成的下载。", color = Muted, fontSize = 17.sp)
            SettingsRowItem("下载此视频", "按需下载") {
                status = "正在连接…"
                task = scope.launch {
                    try {
                        VideoWallpaperCatalog.download(context, current) { bytes, total ->
                            scope.launch { status = if (total > 0) "${bytes * 100 / total}%" else "${bytes / 1024 / 1024} MB" }
                        }
                        revision++; status = "下载完成，请选择设为视频壁纸"
                    } catch (e: CancellationException) { status = "已取消"; throw e }
                    catch (e: Exception) {
                        android.util.Log.w("ABUWallpaper", "Video download failed: ${current.id}", e)
                        status = when (e) {
                            is javax.net.ssl.SSLException -> "视频源证书验证失败，请检查系统时间或更换网络后重试"
                            is java.net.SocketTimeoutException -> "连接超时，请检查网络后重试"
                            is java.net.UnknownHostException -> "无法解析视频源地址，请检查网络或 DNS"
                            else -> e.message ?: "下载失败，请重试"
                        }
                    }
                    finally { task = null }
                }
            }
        }
    }
    if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 18.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun VideoPreview(entry: VideoWallpaperCatalog.Entry) {
    val context = LocalContext.current
    val result by produceState<Pair<Boolean, androidx.compose.ui.graphics.ImageBitmap?>>(false to null, entry.id) {
        value = true to VideoWallpaperCatalog.thumbnail(context, entry)
    }
    val thumbnail = result.second
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF243145)), contentAlignment = Alignment.Center) {
        if (thumbnail != null) Image(thumbnail!!, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (result.first) "预览图暂不可用" else "联网加载预览图…", color = Muted, fontSize = 14.sp)
    }
}
