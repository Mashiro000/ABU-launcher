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
        val previewRes get() = when (id) {
            "seals" -> R.drawable.aerial_seals
            "greenland" -> R.drawable.aerial_greenland
            "california" -> R.drawable.aerial_california
            else -> R.drawable.aerial_asia_night
        }
        val url get() = "https://sylvan.apple.com/Videos/$filename.mov"
    }
    val entries = listOf(
        Entry("seals", "开普半岛 · 海狮", "海底", "SE_A016_C009_SDR_20190717_SDR_2K_AVC"),
        Entry("greenland", "格陵兰 · 冰峡湾", "自然", "comp_GL_G002_C002_PSNK_v03_SDR_PS_20180925_SDR_2K_AVC"),
        Entry("california", "南加州至下加利福尼亚", "太空", "comp_A114_C001_0305OT_v10_SDR_FINAL_22062018_SDR_2K_AVC"),
        Entry("asia-night", "韩国与日本 · 夜航", "太空", "comp_GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT_v18_SDR_PS_20180907_SDR_2K_AVC")
    )
    fun file(context: Context, entry: Entry) = File(File(context.filesDir, "video-wallpapers").apply { mkdirs() }, "${entry.id}.mov")
    suspend fun download(context: Context, entry: Entry, sourceUrl: String = entry.url, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val output = file(context, entry)
        val partial = File(output.path + ".part")
        val conn = URL(sourceUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
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
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<VideoWallpaperCatalog.Entry?>(null) }
    var task by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("") }
    Text("在线航拍视频", color = White, fontSize = 25.sp)
    Text("Apple Aerial 公共视频源 · H.264 / SDR。按需下载，文件可能较大；离开此页取消下载。视频版权归原权利人所有。", color = Muted, fontSize = 17.sp)
    VideoWallpaperCatalog.entries.chunked(2).forEach { entries ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            entries.forEach { entry ->
                val exists = remember(revision) { VideoWallpaperCatalog.file(context, entry).exists() }
                val thumbnail by produceState<android.graphics.Bitmap?>(null, entry.id) {
                    value = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, entry.previewRes) }
                }
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.weight(1f).onFocusChanged { focused = it.hasFocus }.settingsAutoScroll()
                    .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x33FFFFFF), ContinuousCornerShape(14.dp))
                    .clickable { if (task?.isActive != true) { selected = entry; status = "" } }.padding(10.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF243145)), contentAlignment = Alignment.Center) {
                        if (thumbnail != null) Image(thumbnail!!.asImageBitmap(), entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text("预览图加载中／网络不可用", color = Muted, fontSize = 14.sp)
                    }
                    Text(entry.title, color = White, fontSize = 19.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(if (exists) "✓ 已下载" else entry.category, color = Muted, fontSize = 16.sp)
                }
            }
        }
    }
    selected?.let { entry ->
        val file = VideoWallpaperCatalog.file(context, entry)
        Text(entry.title, color = White, fontSize = 23.sp)
        if (file.exists()) {
            SettingsRowItem("设为视频壁纸", "${file.length() / 1024 / 1024} MB") {
                VideoWallpaperPrefs.save(context, Uri.fromFile(file))
                WallpaperMode.save(context, WallpaperMode.VIDEO)
                status = "已应用"; onApplied()
            }
            SettingsRowItem("删除下载的视频", null) {
                if (VideoWallpaperPrefs.current(context) == Uri.fromFile(file)) {
                    context.getSharedPreferences("desktop", 0).edit().remove("wallpaperVideoUri").apply()
                    WallpaperMode.save(context, WallpaperMode.GRADIENT)
                    onApplied()
                }
                if (file.delete()) { revision++; status = "已删除" } else status = "删除失败"
            }
        } else if (task?.isActive == true) {
            SettingsRowItem("取消下载", status) { task?.cancel() }
        } else {
            SettingsRowItem("下载此视频", "1080p H.264") {
                status = "正在连接…"
                task = scope.launch {
                    try {
                        VideoWallpaperCatalog.download(context, entry) { bytes, total ->
                            scope.launch { status = if (total > 0) "${bytes * 100 / total}%" else "${bytes / 1024 / 1024} MB" }
                        }
                        revision++; status = "下载完成，请选择设为视频壁纸"
                    } catch (e: CancellationException) { status = "已取消"; throw e }
                    catch (e: Exception) { status = e.message ?: "下载失败，请重试" }
                    finally { task = null }
                }
            }
        }
    }
    if (status.isNotEmpty()) Text(status, color = Muted, fontSize = 18.sp)
    Spacer(Modifier.height(12.dp))
}
