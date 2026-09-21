package com.limi.tvdesktop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object DesktopPreferences {
    private const val PREFS = "desktop"

    var version by mutableIntStateOf(0)
        private set

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
}
