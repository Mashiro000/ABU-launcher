package com.limi.tvdesktop

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared rendering policy. Read observable state at effect/source creation sites. */
internal object RenderPerformance {
    enum class MotionMode(val label: String) { FULL("完整动画"), REDUCED("精简动画"), NONE("无动画") }
    var motionMode by mutableStateOf(MotionMode.FULL)
        private set
    enum class BlurMode(val label: String) { NONE("无模糊"), STATIC("静态模糊"), REALTIME("实时模糊") }
    var lowPerformance by mutableStateOf(false)
        private set
    var blurMode by mutableStateOf(BlurMode.STATIC)
        private set

    val reducedEffects: Boolean
        get() = motionMode != MotionMode.FULL
    // Reduced motion changes the transition, not its speed.
    fun duration(full: Int): Int = durationFor(motionMode, full)
    internal fun durationFor(mode: MotionMode, full: Int): Int =
        if (mode == MotionMode.NONE) 0 else full
    val blur31: Boolean
        get() = blurMode == BlurMode.REALTIME && !reducedEffects && Build.VERSION.SDK_INT >= 31
    val blur33: Boolean
        get() = blurMode == BlurMode.REALTIME && !reducedEffects && Build.VERSION.SDK_INT >= 33
    val staticBlur: Boolean
        get() = usesStaticBlur(motionMode, blurMode, Build.VERSION.SDK_INT)

    internal fun usesStaticBlur(motion: MotionMode, blur: BlurMode, sdk: Int): Boolean =
        blur != BlurMode.NONE && (blur == BlurMode.STATIC || motion != MotionMode.FULL || sdk < 31)

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
        motionMode = runCatching {
            MotionMode.valueOf(prefs.getString("motionMode", null)
                ?: if (prefs.getBoolean("lowPerformance", false)) MotionMode.REDUCED.name else MotionMode.FULL.name)
        }.getOrDefault(MotionMode.FULL)
        lowPerformance = motionMode != MotionMode.FULL
        val defaultMode = BlurMode.STATIC
        blurMode = runCatching {
            val saved = prefs.getString("blurMode", null)
            if (saved != null) BlurMode.valueOf(saved)
            else if (prefs.contains("staticBlurEnabled")) {
                if (prefs.getBoolean("staticBlurEnabled", false)) BlurMode.STATIC else BlurMode.REALTIME
            } else defaultMode
        }.getOrDefault(defaultMode)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        setMotionMode(context, if (enabled) MotionMode.REDUCED else MotionMode.FULL)
    }

    fun setMotionMode(context: Context, mode: MotionMode) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
            .edit().putString("motionMode", mode.name).putBoolean("lowPerformance", mode != MotionMode.FULL).apply()
        motionMode = mode
        lowPerformance = mode != MotionMode.FULL
    }

    fun setBlurMode(context: Context, mode: BlurMode) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
            .edit().putString("blurMode", mode.name).apply()
        blurMode = mode
    }
}
