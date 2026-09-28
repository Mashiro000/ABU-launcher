package com.limi.tvdesktop

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared rendering policy. Read observable state at effect/source creation sites. */
internal object RenderPerformance {
    var lowPerformance by mutableStateOf(false)
        private set
    var staticBlurEnabled by mutableStateOf(true)
        private set

    val reducedEffects: Boolean
        get() = lowPerformance || Build.VERSION.SDK_INT <= 28
    val blur31: Boolean
        get() = !staticBlurEnabled && !reducedEffects && Build.VERSION.SDK_INT >= 31
    val blur33: Boolean
        get() = !staticBlurEnabled && !reducedEffects && Build.VERSION.SDK_INT >= 33
    val staticBlur: Boolean
        get() = staticBlurEnabled

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
        lowPerformance = prefs.getBoolean("lowPerformance", false)
        staticBlurEnabled = prefs.getBoolean("staticBlurEnabled", true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
            .edit().putBoolean("lowPerformance", enabled).apply()
        lowPerformance = enabled
    }

    fun setStaticBlur(context: Context, enabled: Boolean) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
            .edit().putBoolean("staticBlurEnabled", enabled).apply()
        staticBlurEnabled = enabled
    }
}
