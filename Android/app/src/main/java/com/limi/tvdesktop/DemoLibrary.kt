package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

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

object DemoLibrary {
    fun bannerIntro(media: DemoMedia): String = when (media.title) {
        "海岸线之外" -> "有些相遇，从海的那一边开始。"
        "暗涌" -> "平静之下，暗涌正在发生。"
        "星辰边境" -> "越过星辰，探索未知的边境。"
        "小满的夏天" -> "在夏日的相遇里，找回温暖与陪伴。"
        "长安月" -> "一轮长安月，照见人间的故事。"
        else -> media.detail
    }
    val watching = listOf(
        DemoMedia("海岸线之外", "第 3 集 / 共 12 集", .64f),
        DemoMedia("暗涌", "第 5 集 / 共 8 集", .52f),
        DemoMedia("星辰边境", "已观看 62%", .62f),
        DemoMedia("小满的夏天", "第 2 集 / 共 10 集", .48f),
        DemoMedia("长安月", "第 6 集 / 共 40 集", .42f),
    )
    val categories = listOf(
        DemoMedia("电影", "1,234 部"),
        DemoMedia("电视剧", "328 部"),
        DemoMedia("动漫", "276 部"),
        DemoMedia("纪录片", "189 部"),
        DemoMedia("家庭视频", "320 部"),
        DemoMedia("音乐", "1,682 首"),
    )
    val favorites = listOf(
        DemoMedia("星际穿越", "2014 · 电影"),
        DemoMedia("繁花", "2023 · 电视剧"),
        DemoMedia("铃芽之旅", "2022 · 动漫"),
        DemoMedia("我们的星球", "2019 · 纪录片"),
        DemoMedia("千与千寻", "2001 · 动漫"),
        DemoMedia("霸王别姬", "1993 · 电影"),
    )
    val recent = listOf(
        DemoMedia("沙丘 第二部", "2024 · 电影"),
        DemoMedia("三体", "2023 · 电视剧"),
        DemoMedia("葬送的芙莉莲", "2023 · 动漫"),
        DemoMedia("地球脉动 III", "2023 · 纪录片"),
        DemoMedia("春色寄情人", "2024 · 电视剧"),
        DemoMedia("机器人之梦", "2023 · 电影"),
    )
    val music = listOf(
        DemoMedia("Moon Music", "Coldplay"),
        DemoMedia("永恒的日常", "RADWIMPS"),
        DemoMedia("11:11", "TAEYEON"),
        DemoMedia("就让世界无童话", "陈奕迅"),
        DemoMedia("The Tortured Poets…", "Taylor Swift"),
        DemoMedia("Forever", "安室奈美惠"),
    )
    val memories = listOf(
        DemoMedia("海边的下午", "2024年8月16日"),
        DemoMedia("和小满的夏天", "2024年7月28日"),
        DemoMedia("家的日落", "2024年5月3日"),
        DemoMedia("生日的烟花", "2024年4月12日"),
    )
}

/** Offline artwork from the original project; replace with media-server artwork later. */
class DemoArtwork(context: Context) {
    private val resources = context.resources
    val background: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.coast_background)
    private val originals = mapOf(
        "海岸线之外" to R.drawable.coast, "暗涌" to R.drawable.undercurrent,
        "星辰边境" to R.drawable.far_side, "小满的夏天" to R.drawable.summer,
        "长安月" to R.drawable.changan, "电影" to R.drawable.reel_v2,
        "电视剧" to R.drawable.television, "动漫" to R.drawable.suzume,
        "纪录片" to R.drawable.iceberg, "家庭视频" to R.drawable.memory_beach,
        "音乐" to R.drawable.vinyl, "星际穿越" to R.drawable.interstellar,
        "繁花" to R.drawable.blossoms, "铃芽之旅" to R.drawable.suzume,
        "我们的星球" to R.drawable.planet_poster, "千与千寻" to R.drawable.spirited,
        "霸王别姬" to R.drawable.farewell, "沙丘 第二部" to R.drawable.dune,
        "三体" to R.drawable.threebody, "葬送的芙莉莲" to R.drawable.frieren_v2,
        "地球脉动 III" to R.drawable.earth, "春色寄情人" to R.drawable.spring,
        "机器人之梦" to R.drawable.robot, "Moon Music" to R.drawable.moon,
        "永恒的日常" to R.drawable.radwimps, "11:11" to R.drawable.taeyeon,
        "就让世界无童话" to R.drawable.eason, "The Tortured Poets…" to R.drawable.taylor,
        "Forever" to R.drawable.forever, "海边的下午" to R.drawable.memory_beach,
        "和小满的夏天" to R.drawable.memory_dog, "家的日落" to R.drawable.memory_sunset,
        "生日的烟花" to R.drawable.memory_sparkler,
    )
    private val cache = java.util.concurrent.ConcurrentHashMap<DemoMedia, ImageBitmap>()
    private val castCache = java.util.concurrent.ConcurrentHashMap<Int, ImageBitmap>()
    fun castImage(resource: Int): ImageBitmap = castCache.getOrPut(resource) {
        BitmapFactory.decodeResource(resources, resource, BitmapFactory.Options().apply { inSampleSize = 2 }).asImageBitmap()
    }
    fun image(media: DemoMedia): ImageBitmap = cache.getOrPut(media) {
        val resId = originals[media.title] ?: R.drawable.reel_v2
        BitmapFactory.decodeResource(resources, resId, BitmapFactory.Options().apply { inSampleSize = 2 }).asImageBitmap()
    }

    /** Android 9-12: three separable box passes on a 1/8-size immutable wallpaper. */
    fun blurredWallpaper(media: DemoMedia? = null): ImageBitmap {
        val source = if (media == null || media.title == "海岸线之外") background else image(media).asAndroidBitmap()
        val small = Bitmap.createScaledBitmap(source, 209, 118, true)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val temp = IntArray(pixels.size)
        repeat(3) {
            pass(pixels, temp, small.width, small.height, true)
            pass(temp, pixels, small.width, small.height, false)
        }
        val result = Bitmap.createBitmap(pixels, small.width, small.height, Bitmap.Config.ARGB_8888)
        small.recycle()
        return result.asImageBitmap()
    }
    private fun pass(src: IntArray, dst: IntArray, width: Int, height: Int, horizontal: Boolean) {
        for (y in 0 until height) for (x in 0 until width) {
            var r = 0; var g = 0; var b = 0
            for (offset in -6..6) {
                val xx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                val yy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                val color = src[yy * width + xx]
                r += (color shr 16) and 255; g += (color shr 8) and 255; b += color and 255
            }
            dst[y * width + x] = (255 shl 24) or ((r / 13) shl 16) or ((g / 13) shl 8) or (b / 13)
        }
    }
}

