package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import java.io.File
import java.security.MessageDigest

internal enum class IconMode { ADAPTIVE, LEGACY }

internal data class ProcessedAppIcon(
    val foreground: Bitmap,
    val backgroundColor: Int,
    val mode: IconMode,
    val confidence: Float,
    val scale: Float,
)

/** Persistent launcher icon preparation. Call from an IO dispatcher. */
internal object AppIconProcessor {
    private const val SIZE = 256
    private const val CACHE_VERSION = 5
    private val memory = object : LruCache<String, ProcessedAppIcon>(8 * 1024) {
        override fun sizeOf(key: String, value: ProcessedAppIcon) = value.foreground.byteCount / 1024
    }

    fun process(context: Context, packageName: String, source: Drawable): ProcessedAppIcon {
        return runCatching {
            val info = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()
            val version = if (info != null) {
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            } else 0L
            val updateTime = info?.lastUpdateTime ?: 0L
            val key = "v$CACHE_VERSION-$packageName-$version-$updateTime"
            synchronized(memory) { memory.get(key)?.let { return it } }
            readDisk(context, key)?.let { synchronized(memory) { memory.put(key, it) }; return it }

            val result = if (Build.VERSION.SDK_INT >= 26 && source is AdaptiveIconDrawable) {
                processAdaptive(source)
            } else processLegacy(source)
            synchronized(memory) { memory.put(key, result) }
            writeDisk(context, key, result)
            result
        }.getOrElse {
            preview(source)
        }
    }

    fun preview(source: Drawable): ProcessedAppIcon {
        return runCatching {
            processLegacy(source)
        }.getOrElse {
            val fallback = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            ProcessedAppIcon(fallback, Color.rgb(38, 42, 48), IconMode.LEGACY, 0f, 1f)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun processAdaptive(icon: AdaptiveIconDrawable): ProcessedAppIcon {
        val bgDrawable = icon.background
        val fgDrawable = icon.foreground
        val background = render(bgDrawable)
        val renderedForeground = render(fgDrawable)
        return ProcessedAppIcon(renderedForeground, bottomRowColor(background),
            IconMode.ADAPTIVE, 1f, 1f)
    }

    private fun processLegacy(drawable: Drawable): ProcessedAppIcon {
        val raw = render(drawable)
        return ProcessedAppIcon(raw, bottomRowColor(raw), IconMode.LEGACY, 1f, 1f)
    }

    /** Use the icon's lowest non-transparent pixel row as its fallback card colour. */
    private fun bottomRowColor(bitmap: Bitmap): Int {
        for (y in bitmap.height - 1 downTo 0) {
            var red = 0L; var green = 0L; var blue = 0L; var weight = 0L
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel).toLong()
                if (alpha == 0L) continue
                red += Color.red(pixel) * alpha
                green += Color.green(pixel) * alpha
                blue += Color.blue(pixel) * alpha
                weight += alpha
            }
            if (weight > 0L) return Color.rgb((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
        }
        return Color.rgb(38, 42, 48)
    }

    private fun render(drawable: Drawable?): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        if (drawable == null) return out
        runCatching {
            val old = drawable.bounds
            drawable.setBounds(0, 0, SIZE, SIZE)
            drawable.draw(Canvas(out))
            drawable.bounds = old
        }
        return out
    }

    private fun cacheFile(context: Context, key: String) = File(context.filesDir, "launcher-icons/${MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }}.png")
    private fun writeDisk(context: Context, key: String, value: ProcessedAppIcon) = runCatching {
        val file = cacheFile(context, key); file.parentFile?.mkdirs(); file.outputStream().use { value.foreground.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(file.path + ".meta").writeText("${value.backgroundColor},${value.mode.name},${value.confidence},${value.scale}")
    }
    private fun readDisk(context: Context, key: String): ProcessedAppIcon? = runCatching {
        val file = cacheFile(context, key); val parts = File(file.path + ".meta").readText().split(',')
        ProcessedAppIcon(android.graphics.BitmapFactory.decodeFile(file.path) ?: return null, parts[0].toInt(),
            IconMode.valueOf(parts[1]), parts[2].toFloat(), parts[3].toFloat())
    }.getOrNull()
}
