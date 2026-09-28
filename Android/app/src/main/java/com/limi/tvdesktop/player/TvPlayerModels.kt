package com.limi.tvdesktop.player

import androidx.compose.ui.graphics.Color
import com.limi.tvdesktop.R

/**
 * 画面显示比例模式
 */
enum class VideoResizeMode(val displayName: String) {
    FIT("自适应 (Fit)"),
    ZOOM("填充裁剪 (Zoom)"),
    FILL("拉伸铺满 (Fill)")
}

/**
 * 弹幕显示区域
 */
enum class DanmakuArea(val displayName: String, val heightRatio: Float) {
    FULL("全屏", 1.0f),
    HALF("半屏", 0.5f),
    TOP("顶部", 0.35f),
    BOTTOM("底部", 0.35f)
}

/**
 * 弹幕滚动速度
 */
enum class DanmakuSpeed(val displayName: String, val durationMs: Long) {
    SLOW("慢速", 9000L),
    NORMAL("正常", 6000L),
    FAST("快速", 4000L)
}

/**
 * 弹幕过滤屏蔽类型
 */
enum class DanmakuFilter(val displayName: String) {
    NONE("不屏蔽"),
    COLOR("屏蔽彩色"),
    TOP_BOTTOM("屏蔽顶底"),
    REPEAT("屏蔽重复")
}

/**
 * 弹幕设置状态
 */
data class TvDanmakuSettings(
    val enabled: Boolean = true,
    val displayArea: DanmakuArea = DanmakuArea.FULL,
    val opacity: Float = 0.70f,
    val fontSizePercent: Int = 100, // 60% ~ 140%
    val speed: DanmakuSpeed = DanmakuSpeed.NORMAL,
    val filterType: DanmakuFilter = DanmakuFilter.NONE
)

/**
 * 弹幕数据项
 */
data class TvDanmakuItem(
    val id: String,
    val timeMs: Long,
    val text: String,
    val color: Color = Color.White,
    val isTop: Boolean = false,
    val isBottom: Boolean = false
)

/**
 * 剧集条目
 */
data class TvEpisodeItem(
    val id: String,
    val index: Int, // 1-based
    val title: String, // e.g. "第 1 集"
    val subTitle: String = "",
    val thumbUrl: String = "",
    val thumbResId: Int = 0,
    val streamUrl: String = "",
    val durationMs: Long = 0L,
    val startPositionMs: Long = 0L
)

/**
 * 音轨信息
 */
data class TvAudioTrack(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean
)

/**
 * 字幕信息
 */
data class TvSubtitleTrack(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String,
    val isSelected: Boolean
)

/**
 * 播放器完整信息包 (与具体服务端 API 解耦)
 */
data class TvPlaybackInfo(
    val mediaId: String = "",
    val title: String = "海岸线之外",
    val seasonEpisodeText: String = "S01 E03",
    val metaSubtitle: String = "2024 ｜ 科幻 ｜ 共 8 集",
    val qualityTag: String = "4K HDR",
    val streamUrl: String = "",
    val backdropUrl: String = "",
    val startPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val overview: String = "在极端严寒与海平面上升的交汇点，年轻的勘探队员踏入未知的近海废墟，搜寻失落的人类文明信号与地核脉动的秘密。",
    val playlist: List<TvEpisodeItem> = emptyList(),
    val currentEpisodeIndex: Int = 0,
    val danmakus: List<TvDanmakuItem> = emptyList(),
    val externalSubtitleUrls: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        const val DEFAULT_TEST_VIDEO = "https://vjs.zencdn.net/v/oceans.mp4"
        const val FALLBACK_TEST_VIDEO = "https://media.w3.org/2010/05/sintel/trailer.mp4"

        /**
         * 提供给参考图标准示范的默认数据
         */
        fun createDemo(): TvPlaybackInfo {
            val thumbList = listOf(
                R.drawable.coast,
                R.drawable.iceberg,
                R.drawable.coast_background,
                R.drawable.far_side,
                R.drawable.planet_poster,
                R.drawable.dune,
                R.drawable.earth,
                R.drawable.interstellar
            )

            val episodes = (1..8).map { ep ->
                TvEpisodeItem(
                    id = "ep_$ep",
                    index = ep,
                    title = "第 $ep 集",
                    subTitle = when (ep) {
                        1 -> "远航回声"
                        2 -> "冰原浮标"
                        3 -> "暗礁灯塔"
                        4 -> "深潜信标"
                        5 -> "极光之后"
                        6 -> "裂谷风暴"
                        7 -> "静止之海"
                        else -> "彼岸黎明"
                    },
                    thumbUrl = "",
                    thumbResId = thumbList[(ep - 1) % thumbList.size],
                    streamUrl = DEFAULT_TEST_VIDEO
                )
            }

            val mockDanmakus = listOf(
                TvDanmakuItem("1", 1500L, "开幕雷击！这画质绝了"),
                TvDanmakuItem("2", 3500L, "女主这个眼神杀，太有戏了", Color(0xFF64B5F6)),
                TvDanmakuItem("3", 5500L, "2024年度最佳科幻美学！"),
                TvDanmakuItem("4", 8000L, "背景音乐绝美，求BGM名字！", Color(0xFFFFD54F)),
                TvDanmakuItem("5", 11000L, "前方高能，注意护眼"),
                TvDanmakuItem("6", 14000L, "4K HDR在电视上看真的惊艳", Color(0xFF81C784)),
                TvDanmakuItem("7", 17000L, "这个运镜很有星际穿越的味道"),
                TvDanmakuItem("8", 20000L, "第三集是封神的一集！", Color(0xFFFF8A65)),
                TvDanmakuItem("9", 23000L, "进度条挺住啊！不想这么快看完"),
                TvDanmakuItem("10", 27000L, "细节狂魔，冰霜纹理好真实", Color(0xFFBA68C8)),
                TvDanmakuItem("11", 31000L, "雪山与夕阳的构图太震撼了"),
                TvDanmakuItem("12", 36000L, "阿布播放器这个玻璃UI真舒服，太有质感了", Color(0xFF4DD0E1)),
                TvDanmakuItem("13", 41000L, "打卡！三刷海岸线之外")
            )

            return TvPlaybackInfo(
                mediaId = "demo_coastline",
                title = "海岸线之外",
                seasonEpisodeText = "S01 E03",
                metaSubtitle = "2024 ｜ 科幻 ｜ 共 8 集",
                qualityTag = "4K HDR",
                streamUrl = DEFAULT_TEST_VIDEO,
                startPositionMs = 0L, // 从头流畅起播
                totalDurationMs = 48 * 60 * 1000L + 12 * 1000L,
                overview = "在极端严寒与海平面上升的交汇点，年轻的勘探队员踏入未知的近海废墟，搜寻失落的人类文明信号与地核脉动的秘密。",
                playlist = episodes,
                currentEpisodeIndex = 2, // 第 3 集 (0-indexed 2)
                danmakus = mockDanmakus
            )
        }
    }
}
