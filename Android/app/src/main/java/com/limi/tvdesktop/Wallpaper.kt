package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Wallpaper source, persisted in the "desktop" prefs under "wallpaperMode". */
enum class WallpaperMode(val label: String) {
    FOLLOW_CONTENT("跟随内容"),
    BING("Bing 每日壁纸"),
    LOCAL("本地壁纸"),
    GRADIENT("渐变色"),
    VIDEO("视频壁纸");

    companion object {
        fun current(context: Context): WallpaperMode {
            val name = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
                .getString("wallpaperMode", GRADIENT.name) ?: GRADIENT.name
            return runCatching { valueOf(name) }.getOrDefault(GRADIENT)
        }
        fun save(context: Context, mode: WallpaperMode) {
            context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
                .edit().putString("wallpaperMode", mode.name).apply()
        }
    }
}

/** Bundled local wallpapers shipped in drawable-nodpi, plus a user file-entry override. */
object LocalWallpapers {
    data class Entry(val name: String, val res: Int)

    val bundled = emptyList<Entry>()

    private const val PREFS = "desktop"
    private const val KEY_RES = "wallpaperLocalRes"
    private const val KEY_URI = "wallpaperLocalUri"

    fun selectedRes(context: Context): Int? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_RES, -1).takeIf { it >= 0 }

    fun selectedUri(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URI, null)?.let(Uri::parse)

    fun saveRes(context: Context, res: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_RES, res).remove(KEY_URI).apply()
    }

    fun saveUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URI, uri.toString()).remove(KEY_RES).apply()
    }

    fun bitmap(context: Context): ImageBitmap? {
        selectedRes(context)?.let { res ->
            return runCatching { BitmapFactory.decodeResource(context.resources, res,
                BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull()
        }
        val uri = selectedUri(context) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap() }
        }.getOrNull()
    }
}

/** Bing daily wallpapers: fetch today's image, cache it locally, and list everything downloaded. */
object BingWallpaper {
    private const val API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"
    data class Daily(val url: String, val date: String)

    suspend fun fetchToday(): Daily? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val img = JSONObject(text).getJSONArray("images").getJSONObject(0)
            Daily("https://www.bing.com" + img.getString("url"), img.getString("startdate"))
        }.getOrNull()
    }

    private fun dir(context: Context) = File(context.filesDir, "bing-wallpapers").apply { mkdirs() }

    fun fileFor(context: Context, date: String) = File(dir(context), "$date.jpg")

    fun downloaded(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".jpg") }
            ?.sortedByDescending { it.nameWithoutExtension } ?: emptyList()

    suspend fun download(context: Context, daily: Daily): File? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(daily.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val out = fileFor(context, daily.date)
            val partial = File(out.parentFile, out.name + ".part")
            try {
                conn.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(partial.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid wallpaper image" }
                check(partial.renameTo(out))
                downloaded(context).filterNot { it.nameWithoutExtension == selectedDate(context) }.drop(12).forEach { it.delete() }
                out
            } finally { conn.disconnect(); partial.delete() }

        }.getOrNull()
    }

    fun bitmap(file: File): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }).asImageBitmap()
    }.getOrNull()

    var revision by mutableIntStateOf(0)
        private set
    private val updateMutex = kotlinx.coroutines.sync.Mutex()
    private fun prefs(context: Context) = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
    fun autoUpdate(context: Context) = prefs(context).getBoolean("bingAutoUpdate", true)
    fun setAutoUpdate(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("bingAutoUpdate", enabled).apply()
        if (enabled) prefs(context).edit().remove("bingCheckedDay").apply()
    }
    /** Checks on entry/resume and while the desktop is running; failures are retried later. */
    suspend fun ensureDaily(context: Context): Boolean {
        updateMutex.lock()
        try {
            val missing = downloaded(context).isEmpty()
            if (!missing && !autoUpdate(context)) return false
            val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
            if (!missing && prefs(context).getString("bingCheckedDay", null) == today) return false
            val daily = fetchToday() ?: return false
            if (!fileFor(context, daily.date).exists() && download(context, daily) == null) return false
            saveDate(context, daily.date)
            prefs(context).edit().putString("bingCheckedDay", today).apply()
            return true
        } finally { updateMutex.unlock() }
    }

    private const val KEY_DATE = "wallpaperBingDate"

    fun selectedDate(context: Context): String? =
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE).getString(KEY_DATE, null)

    fun saveDate(context: Context, date: String) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE).edit().putString(KEY_DATE, date).apply()
        revision++
    }

    /** Selected downloaded Bing image (defaults to the most recent one). */
    fun currentBitmap(context: Context): ImageBitmap? {
        val preferred = selectedDate(context)?.let { fileFor(context, it) }?.takeIf { it.exists() }
        return (preferred ?: downloaded(context).firstOrNull())?.let { bitmap(it) }
    }
}

/** Video wallpaper selection, persisted as a content Uri under "wallpaperVideoUri". */
object VideoWallpaperPrefs {
    private const val PREFS = "desktop"
    private const val KEY = "wallpaperVideoUri"

    fun current(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.let(Uri::parse)

    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, uri.toString()).apply()
    }
}

/** Full-screen looping, muted video surface used as the launcher background. */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun VideoWallpaperSurface(uri: Uri, modifier: Modifier = Modifier, active: Boolean = true) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = false
            prepare()
        }
    }
    val lifecycle = (context as? LifecycleOwner)?.lifecycle
    var failed by remember(uri) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle, active) {
        fun updatePlayback() { player.playWhenReady = active && !failed && lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false }
        val observer = LifecycleEventObserver { _, _ -> updatePlayback() }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true; player.playWhenReady = false }
        }
        player.addListener(listener)
        lifecycle?.addObserver(observer)
        updatePlayback()
        onDispose { lifecycle?.removeObserver(observer); player.removeListener(listener); player.pause() }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    if (failed) return

    AndroidView(
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.wallpaper_player_view, null, false) as PlayerView).apply {
                isFocusable = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier,
        update = { it.player = player }
    )
}
