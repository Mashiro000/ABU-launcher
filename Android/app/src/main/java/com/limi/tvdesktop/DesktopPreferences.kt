package com.limi.tvdesktop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object DesktopPreferences {
    private const val PREFS = "desktop"

    var version by mutableIntStateOf(0)
        private set

    object GlassTuning {
        const val NAV_BLUR_DEFAULT = 24f
        const val NAV_OPACITY_DEFAULT = .20f
        const val DOCK_BLUR_DEFAULT = 28f
        const val DOCK_OPACITY_DEFAULT = .20f
        const val APP_LIST_BLUR_DEFAULT = 50f
        const val APP_LIST_OPACITY_DEFAULT = .30f

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun navBlur(context: Context) = prefs(context).getFloat("glassNavBlur", NAV_BLUR_DEFAULT)
        fun navOpacity(context: Context) = prefs(context).getFloat("glassNavOpacity", NAV_OPACITY_DEFAULT)
        fun dockBlur(context: Context) = prefs(context).getFloat("glassDockBlur", DOCK_BLUR_DEFAULT)
        fun dockOpacity(context: Context) = prefs(context).getFloat("glassDockOpacity", DOCK_OPACITY_DEFAULT)
        fun appListBlur(context: Context) = prefs(context).getFloat("glassAppListBlur", APP_LIST_BLUR_DEFAULT)
        fun appListOpacity(context: Context) = prefs(context).getFloat("glassAppListOpacity", APP_LIST_OPACITY_DEFAULT)

        fun save(context: Context, key: String, value: Float) {
            prefs(context).edit().putFloat(key, value).apply()
            version++
        }
        fun reset(context: Context, key: String) {
            prefs(context).edit().remove(key).apply()
            version++
        }
        fun resetNav(context: Context) {
            prefs(context).edit().remove("glassNavBlur").remove("glassNavOpacity").apply(); version++
        }
        fun resetDock(context: Context) {
            prefs(context).edit().remove("glassDockBlur").remove("glassDockOpacity").apply(); version++
        }
        fun resetAppList(context: Context) {
            prefs(context).edit().remove("glassAppListBlur").remove("glassAppListOpacity").apply(); version++
        }
    }

    enum class PlaybackEngine(val label: String, val desc: String) {
        AUTO("自动选择（推荐）", "优先内置播放器，解码失败时切换 mpv"),
        INTERNAL("内置播放器", "统一界面与最低操作延迟"),
        MPV("mpv", "直接使用 mpv，适合 HEVC 与 ASS 特效字幕");

        companion object {
            fun current(context: Context): PlaybackEngine {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("playbackEngine", AUTO.name) ?: AUTO.name
                return runCatching { valueOf(name) }.getOrDefault(AUTO)
            }

            fun save(context: Context, engine: PlaybackEngine) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("playbackEngine", engine.name).apply()
                version++
            }
        }
    }

    // 1. 时钟样式
    enum class ClockStyle(val label: String, val desc: String) {
        STANDARD("标准数字时钟", "显示时分秒与完整农历公历"),
        MINIMAL("极简时钟", "大字号时:分，清爽无秒针"),
        SPLIT("双排分体时钟", "左侧大时间，右侧规整日期农历"),
        OFF("隐藏时钟", "极简纯净，展示全景壁纸");

        companion object {
            fun current(context: Context): ClockStyle {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("clockStyle", STANDARD.name) ?: STANDARD.name
                return runCatching { valueOf(name) }.getOrDefault(STANDARD)
            }

            fun save(context: Context, style: ClockStyle) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("clockStyle", style.name).apply()
                version++
            }
        }
    }

    // 2. 图标大小
    enum class IconScale(val label: String, val desc: String, val scale: Float) {
        COMPACT("紧凑 (88%)", "缩小尺寸，精致轻量", 0.88f),
        STANDARD("标准 (100%)", "默认推荐，舒适平衡", 1.0f),
        LARGE("大图标 (112%)", "放大卡片，远距视线更清晰", 1.12f);

        companion object {
            fun current(context: Context): IconScale {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("iconScale", STANDARD.name) ?: STANDARD.name
                return runCatching { valueOf(name) }.getOrDefault(STANDARD)
            }

            fun save(context: Context, scale: IconScale) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("iconScale", scale.name).apply()
                version++
            }
        }
    }

    // 3. Dock 布局样式
    enum class DockStyle(val label: String, val desc: String) {
        GLASS("沉浸毛玻璃底座", "高强度磨砂半透明质感底托"),
        FLOATING("悬浮无底座", "去除非必要的底壳，纯净悬浮"),
        TRANSLUCENT("深黑微透底座", "纯色深黑半透明，边缘分明");

        companion object {
            fun current(context: Context): DockStyle {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("dockStyle", GLASS.name) ?: GLASS.name
                return runCatching { valueOf(name) }.getOrDefault(GLASS)
            }

            fun save(context: Context, style: DockStyle) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("dockStyle", style.name).apply()
                version++
            }
        }
    }

    // 4. 网格密度 (列数)
    enum class GridDensity(val label: String, val desc: String, val columns: Int) {
        COLUMNS_5("5 列 (宽屏大卡)", "更大图标与宽松留白", 5),
        COLUMNS_6("6 列 (标准)", "经典标准 6 列布局", 6),
        COLUMNS_7("7 列 (高密度)", "更多卡片，高密度容纳", 7);

        companion object {
            fun current(context: Context): GridDensity {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("gridDensity", COLUMNS_6.name) ?: COLUMNS_6.name
                return runCatching { valueOf(name) }.getOrDefault(COLUMNS_6)
            }

            fun save(context: Context, density: GridDensity) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("gridDensity", density.name).apply()
                version++
            }
        }
    }

    enum class AppSort(val label: String, val desc: String) {
        CUSTOM("自定义排列", "使用桌面编辑保存的手动顺序"),
        SMART("智能排序", "综合桌面启动频率、最近使用与新安装应用"),
        INSTALL_NEWEST("安装时间 · 最新优先", "最近安装的应用排在前面"),
        INSTALL_OLDEST("安装时间 · 最早优先", "最早安装的应用排在前面"),
        ALPHABETICAL("字母排列", "按应用名称的拼音或字母顺序排列");

        companion object {
            fun current(context: Context): AppSort {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("appSort", CUSTOM.name) ?: CUSTOM.name
                return runCatching { valueOf(name) }.getOrDefault(CUSTOM)
            }

            fun save(context: Context, sort: AppSort) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("appSort", sort.name).apply()
                version++
            }
        }
    }

    // 5. 屏幕保护超时
    enum class ScreenSaverTimeout(val label: String, val minutes: Int) {
        OFF("关闭", -1),
        MIN_2("2 分钟", 2),
        MIN_5("5 分钟 (推荐)", 5),
        MIN_10("10 分钟", 10),
        MIN_15("15 分钟", 15);

        companion object {
            fun current(context: Context): ScreenSaverTimeout {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("screensaverTimeout", MIN_5.name) ?: MIN_5.name
                return runCatching { valueOf(name) }.getOrDefault(MIN_5)
            }

            fun save(context: Context, timeout: ScreenSaverTimeout) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("screensaverTimeout", timeout.name).apply()
                version++
            }
        }
    }

    // 6. 开机自启动
    object AutoStart {
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("autoStartOnBoot", true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("autoStartOnBoot", enabled).apply()
            version++
        }
    }

    /** Keep the desktop usable when sideloaded on a phone whose current orientation is portrait. */
    object ForceLandscape {
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("forceLandscape", true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("forceLandscape", enabled).apply()
            version++
        }
    }

    // 8. 记住上次停留页面
    object LastTab {
        fun get(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lastTab", "媒体库") ?: "媒体库"

        fun set(context: Context, tab: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("lastTab", tab).apply()
        }
    }

    // 9. 启动默认页
    enum class StartupPage(val label: String, val pageName: String?, val desc: String) {
        HOME("首页", "首页", "每次启动回到桌面首页"),
        LIBRARY("媒体库", "媒体库", "每次启动进入媒体库"),
        LIVE("直播", "直播", "每次启动进入直播"),
        GAMES("游戏", "游戏", "每次启动进入游戏"),
        LAST("记住上次", null, "跟随上次停留的页面");

        companion object {
            fun current(context: Context): StartupPage {
                val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("startupPage", HOME.name) ?: HOME.name
                return runCatching { valueOf(name) }.getOrDefault(HOME)
            }

            fun save(context: Context, page: StartupPage) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString("startupPage", page.name).apply()
                version++
            }
        }
    }
}
