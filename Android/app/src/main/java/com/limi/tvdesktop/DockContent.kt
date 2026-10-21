package com.limi.tvdesktop

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class DockProgram(val id: String, val packageName: String, val title: String,
    val description: String, val imageUri: String, val intentUri: String, val source: String)

internal object DockContent {
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    fun enabled(context: Context) = context.getSharedPreferences("desktop", 0).getBoolean("dockContentEnabled", false)
    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("desktop", 0).edit().putBoolean("dockContentEnabled", value).apply()
        DesktopPreferences.notifyChanged()
    }
    fun hasProvider(context: Context) = Build.VERSION.SDK_INT >= 26 && context.packageManager.resolveContentProvider("android.media.tv", 0) != null
    fun canRead(context: Context) = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    private data class Cached(val at: Long, val programs: List<DockProgram>)
    private val cache = object : android.util.LruCache<String, Cached>(8) {}

    suspend fun load(context: Context, packageName: String): List<DockProgram> = withContext(Dispatchers.IO) {
        if (!hasProvider(context) || !canRead(context)) return@withContext emptyList()
        cache.get(packageName)?.takeIf { android.os.SystemClock.elapsedRealtime() - it.at < 60_000 }?.let { return@withContext it.programs }
        val output = mutableListOf<DockProgram>()
        for ((path, source) in listOf("preview_program" to "应用推荐", "watch_next_program" to "接着探索")) {
            // Public TvContract URI contract; no extra dependency or signature-only EPG permission.
            val uri = Uri.parse("content://android.media.tv/$path").buildUpon().appendQueryParameter("package", packageName).build()
            try {
                context.contentResolver.query(uri, arrayOf("_id", "package_name", "title", "short_description", "poster_art_uri", "intent_uri", "browsable"), null, null, null)?.use { cursor ->
                    var scanned = 0
                    while (cursor.moveToNext() && scanned++ < 100 && output.size < 12) {
                        readProgram(cursor, path, source, packageName)?.let { output.add(it) }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Missing/locked vendor provider: preserve wallpaper and normal launch. */ }
        }
        output.distinctBy { it.intentUri }.take(8).also { cache.put(packageName, Cached(android.os.SystemClock.elapsedRealtime(), it)) }
    }

    internal fun readProgram(cursor: Cursor, path: String, source: String, owner: String): DockProgram? {
        fun text(column: String): String = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
        if (text("package_name") != owner || text("title").isBlank() || text("intent_uri").isBlank()) return null
        if (text("browsable") == "0") return null
        if (safeIntent(text("intent_uri"), owner) == null) return null
        return DockProgram("$path:${text("_id")}", owner, text("title"), text("short_description"), text("poster_art_uri"), text("intent_uri"), source)
    }

    internal fun safeIntent(uri: String, owner: String): Intent? = runCatching {
        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        require(intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_MAIN)
        require(intent.component == null || intent.component?.packageName == owner)
        intent.selector = null
        intent.component = intent.component?.takeIf { it.packageName == owner }
        intent.setPackage(owner)
        intent.clipData = null
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intent
    }.getOrNull()

    fun open(context: Context, program: DockProgram) {
        val intent = safeIntent(program.intentUri, program.packageName) ?: return
        runCatching { context.startActivity(intent) }.onFailure {
            android.widget.Toast.makeText(context, "此内容暂时无法打开，请进入应用查看", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun rememberDockImage(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = null
        if (!uri.isNullOrBlank()) value = withContext(Dispatchers.IO) {
            if (uri.startsWith("https://") || uri.startsWith("http://")) PosterCacheManager.loadPoster(uri)
            else if (uri.startsWith("content://") || uri.startsWith("android.resource://")) runCatching {
                val source = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 720) sample *= 2
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap() }
            }.getOrNull() else null
        }
    }
    return image
}

@Composable
internal fun DockContentShelf(programs: List<DockProgram>, selected: DockProgram, onSelect: (DockProgram) -> Unit,
    first: FocusRequester, returnToDock: FocusRequester) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 80.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(selected.source, color = Muted, fontSize = 19.sp)
        Text(selected.title, color = White, fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (selected.description.isNotBlank()) Text(selected.description, color = Muted, fontSize = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GlassButton("打开内容", Modifier.width(210.dp).height(62.dp).focusRequester(first).focusProperties { down = returnToDock }) { DockContent.open(context, selected) }
            GlassButton("打开应用", Modifier.width(210.dp).height(62.dp).focusProperties { down = returnToDock }) {
                val intent = context.packageManager.getLeanbackLaunchIntentForPackage(selected.packageName) ?: context.packageManager.getLaunchIntentForPackage(selected.packageName)
                if (intent != null) runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            itemsIndexed(programs, key = { _, item -> item.id }) { _, item ->
                val image = rememberDockImage(item.imageUri)
                FocusCard(Modifier.width(216.dp).height(122.dp).focusProperties { down = returnToDock }
                    .onFocusChanged { if (it.isFocused) onSelect(item) }, showBorder = true,
                    onClick = { DockContent.open(context, item) }) {
                    if (image != null) Image(image, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)))
                    Text(item.title, color = White, fontSize = 17.sp, maxLines = 2, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
internal fun DockContentSettings(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val version = DesktopPreferences.version
    val enabled = remember(version) { DockContent.enabled(context) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        DockContent.setEnabled(context, granted); onChanged()
    }
    SettingsInlinePage(onBack) {
        Text("Dock 内容预览", color = White, fontSize = 34.sp)
        Text("聚焦 Dock 应用时，显示它向 Android TV 提供的推荐、内容和入口。支持直接打开内容或进入应用。关闭后始终保留你选择的壁纸。", color = Muted, fontSize = 19.sp)
        SettingsRowItem("显示 Dock 应用内容", if (enabled) "已开启" else "已关闭") {
            if (!enabled && DockContent.hasProvider(context) && !DockContent.canRead(context)) permission.launch(DockContent.PERMISSION)
            else { DockContent.setEnabled(context, !enabled); onChanged() }
        }
        Text(when {
            !DockContent.hasProvider(context) -> "此设备没有可用的 Android TV 内容频道，开启后仍保留壁纸。"
            !DockContent.canRead(context) -> "需要允许读取电视内容列表。拒绝授权不影响桌面和壁纸。"
            else -> "只显示应用实际公开的内容；没有数据或固件不允许读取时保留壁纸。部分国内应用不提供此接口。"
        }, color = Muted, fontSize = 18.sp)
    }
}
