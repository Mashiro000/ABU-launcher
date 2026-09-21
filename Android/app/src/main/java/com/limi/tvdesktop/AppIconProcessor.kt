package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal enum class IconMode { ADAPTIVE, MONOCHROME, TRANSPARENT_LOGO, EXTRACTED_FOREGROUND, LEGACY }

internal data class ProcessedAppIcon(
    val foreground: Bitmap,
    val backgroundColor: Int,
    val mode: IconMode,
    val confidence: Float,
    val scale: Float,
)

/** Background-only icon analysis. Call from an IO dispatcher. */
internal object AppIconProcessor {
    private const val SIZE = 256
    private const val CACHE_VERSION = 4
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
            val raw = render(source)
            val normalized = normalize(raw, target = .82f, preserveFullShape = true)
            ProcessedAppIcon(normalized.bitmap, safeBackground(dominantColor(raw), averageLuma(raw)),
                IconMode.LEGACY, 0f, normalized.scale)
        }.getOrElse {
            val fallback = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            ProcessedAppIcon(fallback, Color.rgb(38, 42, 48), IconMode.LEGACY, 0f, 1f)
        }
    }

    private fun processAdaptive(icon: AdaptiveIconDrawable): ProcessedAppIcon {
        val bgDrawable = icon.background
        val fgDrawable = icon.foreground
        val background = render(bgDrawable)
        val renderedForeground = render(fgDrawable)
        val foregroundBrand = dominantChromaticColor(renderedForeground)
        val nativeBackground = if (bgDrawable != null) dominantColor(background) else (foregroundBrand ?: Color.rgb(38, 42, 48))
        val backgroundColor = if (isNeutral(nativeBackground) && foregroundBrand != null) foregroundBrand else nativeBackground
        val mono = if (Build.VERSION.SDK_INT >= 33) runCatching { icon.monochrome }.getOrNull() else null
        val layer = mono ?: fgDrawable
        val raw = if (mono != null) tintMonochrome(render(layer), contrastingColor(backgroundColor)) else renderedForeground
        val normalized = normalize(raw, target = if (mono != null) .58f else .64f, preserveFullShape = false)
        return ProcessedAppIcon(normalized.bitmap, backgroundColor,
            if (mono != null) IconMode.MONOCHROME else IconMode.ADAPTIVE, 1f, normalized.scale)
    }

    private fun isNeutral(color: Int): Boolean {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        return hsv[1] < .14f || hsv[2] < .10f || hsv[2] > .94f
    }

    private fun contrastingColor(background: Int): Int {
        val luma = (.2126f * Color.red(background) + .7152f * Color.green(background) + .0722f * Color.blue(background)) / 255f
        return if (luma > .62f) Color.rgb(22, 22, 24) else Color.WHITE
    }

    private fun tintMonochrome(source: Bitmap, color: Int): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val alpha = Color.alpha(source.getPixel(x, y))
            out.setPixel(x, y, Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)))
        }
        return out
    }

    private fun processLegacy(drawable: Drawable): ProcessedAppIcon {
        val raw = render(drawable)
        val transparentConfidence = transparentBorderConfidence(raw)
        if (transparentConfidence >= .70f) {
            val normalized = normalize(raw, target = .62f, preserveFullShape = false)
            val logoColor = dominantColor(normalized.bitmap, ignoreTransparent = true)
            return ProcessedAppIcon(normalized.bitmap, safeBackground(logoColor, averageLuma(normalized.bitmap)),
                IconMode.TRANSPARENT_LOGO, transparentConfidence, normalized.scale)
        }

        val border = analyzeBorder(raw)
        if (border.confidence >= .70f) {
            val extracted = removeBackgroundSoft(raw, border.color)
            val edgeQuality = edgeQuality(extracted)
            val confidence = border.confidence * edgeQuality
            if (confidence >= .70f) {
                val normalized = normalize(extracted, target = .62f, preserveFullShape = false)
                return ProcessedAppIcon(normalized.bitmap, border.color,
                    IconMode.EXTRACTED_FOREGROUND, confidence, normalized.scale)
            }
        }

        // Complex artwork is kept intact and only optically normalized.
        val normalized = normalize(raw, target = .82f, preserveFullShape = true)
        return ProcessedAppIcon(normalized.bitmap, safeBackground(dominantColor(raw), averageLuma(raw)),
            IconMode.LEGACY, max(transparentConfidence, border.confidence), normalized.scale)
    }

    private data class Normalized(val bitmap: Bitmap, val scale: Float)

    private fun normalize(source: Bitmap, target: Float, preserveFullShape: Boolean): Normalized {
        val bounds = alphaBounds(source, if (preserveFullShape) 4 else 12) ?: Rect(0, 0, source.width, source.height)
        val area = bounds.width().toFloat() * bounds.height() / (source.width * source.height)
        val adjustment = when {
            area < .15f -> 1.14f
            area < .30f -> 1.07f
            area > .72f -> .94f
            else -> 1f
        }
        val desired = (SIZE * target * adjustment).coerceIn(SIZE * .48f, SIZE * .88f)
        val scale = desired / max(bounds.width(), bounds.height()).coerceAtLeast(1)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val dstW = bounds.width() * scale
        val dstH = bounds.height() * scale
        Canvas(out).drawBitmap(source, bounds,
            android.graphics.RectF((SIZE - dstW) / 2, (SIZE - dstH) / 2, (SIZE + dstW) / 2, (SIZE + dstH) / 2), null)
        return Normalized(out, scale)
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

    private fun alphaBounds(bitmap: Bitmap, threshold: Int): Rect? {
        var l = bitmap.width; var t = bitmap.height; var r = -1; var b = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > threshold) { l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y) }
        }
        return if (r >= l && b >= t) Rect(l, t, r + 1, b + 1) else null
    }

    private fun transparentBorderConfidence(bitmap: Bitmap): Float {
        var transparent = 0; var sampled = 0
        val band = (SIZE * .12f).toInt()
        for (y in 0 until SIZE step 2) for (x in 0 until SIZE step 2) if (x < band || y < band || x >= SIZE-band || y >= SIZE-band) {
            sampled++; if (Color.alpha(bitmap.getPixel(x, y)) < 24) transparent++
        }
        return (transparent.toFloat() / sampled).coerceIn(0f, 1f)
    }

    private data class BorderResult(val color: Int, val confidence: Float)
    private fun analyzeBorder(bitmap: Bitmap): BorderResult {
        val samples = ArrayList<Int>(); val band = (SIZE * .10f).toInt()
        for (y in 0 until SIZE step 3) for (x in 0 until SIZE step 3) if (x < band || y < band || x >= SIZE-band || y >= SIZE-band) samples += bitmap.getPixel(x, y)
        val seed = samples.groupBy { quantize(it) }.maxByOrNull { it.value.size }?.value?.let { average(it) } ?: Color.DKGRAY
        val confidence = samples.count { colorDistance(it, seed) < 30f }.toFloat() / samples.size
        return BorderResult(seed, confidence)
    }

    private fun removeBackgroundSoft(source: Bitmap, background: Int): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val pixel = source.getPixel(x, y); val d = colorDistance(pixel, background)
            val factor = ((d - 18f) / 42f).coerceIn(0f, 1f)
            out.setPixel(x, y, Color.argb((Color.alpha(pixel) * factor).toInt(), Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
        }
        return out
    }

    private fun edgeQuality(bitmap: Bitmap): Float {
        var partial = 0; var visible = 0
        for (y in 0 until SIZE step 2) for (x in 0 until SIZE step 2) {
            val a = Color.alpha(bitmap.getPixel(x, y)); if (a > 0) visible++; if (a in 16..239) partial++
        }
        if (visible == 0) return 0f
        val ratio = partial.toFloat() / visible
        return (1f - abs(ratio - .12f)).coerceIn(.65f, 1f)
    }

    private fun dominantColor(bitmap: Bitmap, ignoreTransparent: Boolean = false): Int {
        val buckets = HashMap<Int, MutableList<Int>>()
        for (y in 0 until SIZE step 8) for (x in 0 until SIZE step 8) {
            val p = bitmap.getPixel(x, y); if (ignoreTransparent && Color.alpha(p) < 80) continue
            buckets.getOrPut(quantize(p)) { ArrayList() }.add(p)
        }
        return buckets.maxByOrNull { it.value.size }?.value?.let(::average) ?: Color.rgb(38, 42, 48)
    }

    private fun dominantChromaticColor(bitmap: Bitmap): Int? {
        val buckets = HashMap<Int, MutableList<Int>>()
        val hsv = FloatArray(3)
        for (y in 0 until SIZE step 5) for (x in 0 until SIZE step 5) {
            val p = bitmap.getPixel(x, y)
            if (Color.alpha(p) < 80) continue
            Color.colorToHSV(p, hsv)
            if (hsv[1] < .28f || hsv[2] < .18f) continue
            buckets.getOrPut(quantize(p)) { ArrayList() }.add(p)
        }
        val winner = buckets.maxByOrNull { it.value.size }?.value ?: return null
        // Reject tiny colored details such as notification dots or decorative specks.
        return if (winner.size >= 10) average(winner) else null
    }

    private fun quantize(c: Int) = (Color.red(c) / 24 shl 16) or (Color.green(c) / 24 shl 8) or (Color.blue(c) / 24)
    private fun average(colors: List<Int>): Int = Color.rgb(colors.sumOf { Color.red(it) } / colors.size,
        colors.sumOf { Color.green(it) } / colors.size, colors.sumOf { Color.blue(it) } / colors.size)
    private fun colorDistance(a: Int, b: Int): Float {
        val ah = FloatArray(3); val bh = FloatArray(3); Color.colorToHSV(a, ah); Color.colorToHSV(b, bh)
        val hd = min(abs(ah[0] - bh[0]), 360f - abs(ah[0] - bh[0])) / 180f
        return sqrt(hd*hd*1600f + (ah[1]-bh[1])*(ah[1]-bh[1])*1600f + (ah[2]-bh[2])*(ah[2]-bh[2])*900f)
    }

    private fun averageLuma(bitmap: Bitmap): Float {
        var total = 0f; var count = 0
        for (y in 0 until SIZE step 8) for (x in 0 until SIZE step 8) {
            val p = bitmap.getPixel(x, y); if (Color.alpha(p) > 40) { total += (.2126f*Color.red(p)+.7152f*Color.green(p)+.0722f*Color.blue(p))/255f; count++ }
        }
        return if (count == 0) .5f else total / count
    }

    private fun safeBackground(color: Int, foregroundLuma: Float): Int {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * .86f).coerceIn(.16f, .78f)
        hsv[2] = if (foregroundLuma > .58f) hsv[2].coerceIn(.18f, .42f) else hsv[2].coerceIn(.58f, .78f)
        return Color.HSVToColor(hsv)
    }

    private fun cacheFile(context: Context, key: String) = File(context.cacheDir, "dock-icons/${MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }}.png")
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
