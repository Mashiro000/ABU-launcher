package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DemoMedia(
    val title: String,
    val detail: String,
    val progress: Float = 0f,
    val realItem: MediaItemInfo? = null
)

fun MediaItemInfo.toDemoMedia(): DemoMedia = DemoMedia(
    title = title,
    detail = detail,
    progress = progress,
    realItem = this
)

fun MediaCategoryInfo.toDemoMedia(): DemoMedia = DemoMedia(
    title = title,
    detail = countText,
    realItem = MediaItemInfo(
        id = id,
        accountId = accountId,
        serverType = ServerType.EMBY,
        title = title,
        detail = countText,
        posterUrl = thumbUrl,
        collectionType = collectionType,
        primaryImageAspectRatio = primaryImageAspectRatio
    )
)

/** Offline artwork from the original project; replace with media-server artwork later. */
class DemoArtwork(context: Context) {
    private val resources = context.resources
    val background: Bitmap = GradientWallpapers.bitmap(GradientWallpapers.palettes.first())
    private val placeholder = Bitmap.createBitmap(24, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF252B34.toInt()) }.asImageBitmap()
    private val blurCache = object : android.util.LruCache<String, ImageBitmap>(12) {}
    fun image(media: DemoMedia): ImageBitmap = placeholder

    @Composable
    fun rememberImage(media: DemoMedia): ImageBitmap {
        return placeholder
    }

    /** Android 7.1-12: three separable box passes on a 1/8-size immutable wallpaper. */
    fun blurredWallpaper(media: DemoMedia? = null): ImageBitmap {
        val cacheKey = media?.title ?: "__background__"
        blurCache.get(cacheKey)?.let { return it }
        val source = if (media == null || media.title == "海岸线之外") background else image(media).asAndroidBitmap()
        return blurredBitmap(source, cacheKey)
    }

    fun blurredBitmap(source: Bitmap, cacheKey: String): ImageBitmap {
        blurCache.get(cacheKey)?.let { return it }
        val small = Bitmap.createScaledBitmap(source, 209, 118, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val temp = IntArray(pixels.size)
        repeat(3) {
            staticBlurPass(pixels, temp, small.width, small.height, true)
            staticBlurPass(temp, pixels, small.width, small.height, false)
        }
        val result = Bitmap.createBitmap(pixels, small.width, small.height, Bitmap.Config.ARGB_8888)
        small.recycle()
        return result.asImageBitmap().also { blurCache.put(cacheKey, it) }
    }
}
