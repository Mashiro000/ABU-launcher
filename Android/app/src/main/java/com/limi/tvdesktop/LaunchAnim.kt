package com.limi.tvdesktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.ui.geometry.Rect

/** Launch-transition choices, persisted in the "desktop" prefs under "launchAnim". */
enum class LaunchAnim(val label: String) {
    SYSTEM("系统"),
    CIRCLE("圆形"),
    ZOOM("缩放"),
    FADE("淡入"),
    SLIDE_UP("上滑");

    companion object {
        fun current(context: Context): LaunchAnim {
            val name = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
                .getString("launchAnim", CIRCLE.name) ?: CIRCLE.name
            return runCatching { valueOf(name) }.getOrDefault(CIRCLE)
        }
        fun save(context: Context, anim: LaunchAnim) {
            context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
                .edit().putString("launchAnim", anim.name).apply()
        }
    }
}

/**
 * Launch the resolved activity with the user-selected transition (defaults to a circle reveal).
 * The transition itself is rendered by the system on the launched task's window; we only choose
 * which ActivityOptions to hand over — the same mechanism stock Launcher3 / Nova use.
 */
internal fun launchAppWithClipReveal(context: Context, view: View, bounds: Rect? = null, intent: Intent) {
    val targetView = (context as? android.app.Activity)?.window?.decorView ?: view
    val screenW = targetView.width.takeIf { it > 0 } ?: 1920
    val screenH = targetView.height.takeIf { it > 0 } ?: 1080
    val centerX = bounds?.center?.x?.toInt() ?: (screenW / 2)
    val centerY = bounds?.center?.y?.toInt() ?: (screenH / 2)

    val bundle = runCatching {
        when (LaunchAnim.current(context)) {
            LaunchAnim.SYSTEM -> null
            LaunchAnim.CIRCLE -> {
                // Launcher3-style: reveal from the icon centre with a tight square.
                val side = (bounds?.width?.coerceAtMost(bounds.height)?.toInt() ?: 0).coerceAtLeast(1)
                ActivityOptions.makeClipRevealAnimation(targetView,
                    (centerX - side / 2).coerceIn(0, screenW),
                    (centerY - side / 2).coerceIn(0, screenH),
                    side, side).toBundle()
            }
            LaunchAnim.ZOOM ->
                ActivityOptions.makeScaleUpAnimation(targetView, centerX, centerY,
                    bounds?.width?.toInt() ?: 0, bounds?.height?.toInt() ?: 0).toBundle()
            LaunchAnim.FADE ->
                ActivityOptions.makeCustomAnimation(context, R.anim.launch_fade_in, 0).toBundle()
            LaunchAnim.SLIDE_UP ->
                ActivityOptions.makeCustomAnimation(context, R.anim.launch_slide_up, 0).toBundle()
        }
    }.recoverCatching {
        ActivityOptions.makeScaleUpAnimation(targetView, centerX, centerY,
            bounds?.width?.toInt() ?: 0, bounds?.height?.toInt() ?: 0).toBundle()
    }.getOrNull()

    runCatching {
        if (bundle != null) context.startActivity(intent, bundle) else context.startActivity(intent)
    }.onFailure {
        runCatching { context.startActivity(intent) }
    }
}
