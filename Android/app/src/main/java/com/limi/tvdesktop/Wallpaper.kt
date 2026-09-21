package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    VIDEO("视频壁纸");

    companion object {
        fun current(context: Context): WallpaperMode {
            val name = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
                .getString("wallpaperMode", FOLLOW_CONTENT.name) ?: FOLLOW_CONTENT.name
            return runCatching { valueOf(name) }.getOrDefault(FOLLOW_CONTENT)
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

    val bundled = listOf(
        Entry("海岸", R.drawable.coast), Entry("沙丘", R.drawable.dune),
        Entry("地球", R.drawable.earth), Entry("星际穿越", R.drawable.interstellar),
        Entry("星球", R.drawable.planet), Entry("月球", R.drawable.moon),
        Entry("芙莉莲", R.drawable.frieren_v2), Entry("机器人", R.drawable.robot),
        Entry("春色", R.drawable.spring), Entry("繁花", R.drawable.blossoms),
        Entry("冰山", R.drawable.iceberg), Entry("远界", R.drawable.far_side),
        Entry("夏日", R.drawable.summer), Entry("长安", R.drawable.changan),
        Entry("海边", R.drawable.memory_beach), Entry("日落", R.drawable.memory_sunset),
    )

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
            return BitmapFactory.decodeResource(context.resources, res,
                BitmapFactory.Options().apply { inSampleSize = 2 }).asImageBitmap()
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
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            out
        }.getOrNull()
    }

    fun bitmap(file: File): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 }).asImageBitmap()
    }.getOrNull()

    private const val KEY_DATE = "wallpaperBingDate"

    fun selectedDate(context: Context): String? =
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE).getString(KEY_DATE, null)

    fun saveDate(context: Context, date: String) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE).edit().putString(KEY_DATE, date).apply()
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
internal fun VideoWallpaperSurface(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier
    )
}
