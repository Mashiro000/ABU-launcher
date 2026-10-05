package com.limi.tvdesktop

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val EntranceEasing = CubicBezierEasing(.16f, 1f, .3f, 1f)
internal val PageTransitionEasing = CubicBezierEasing(.16f, 1f, .3f, 1f)
internal const val PAGE_TRANSITION_MS = 700
internal const val OVERLAY_TRANSITION_MS = 700
internal const val SELECTION_TRANSITION_MS = 500
internal fun pageMotion() = tween<Float>(PAGE_TRANSITION_MS, easing = PageTransitionEasing)
internal fun overlayMotion() = tween<Float>(OVERLAY_TRANSITION_MS, easing = PageTransitionEasing)
/** Every focus/option state uses the same 500ms rhythm in both directions. */
internal fun focusMotion() = tween<Float>(SELECTION_TRANSITION_MS, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))
internal fun focusScaleMotion(selected: Boolean): FiniteAnimationSpec<Float> = focusMotion()
private class EntranceRegistry(var visited: MutableState<List<String>>)
private class EntranceTimeline {
    var elapsed by mutableFloatStateOf(0f)
    var lastIndex = 0
}
private val LocalRegistry = compositionLocalOf<EntranceRegistry> { error("AppEntranceHost required") }
private val LocalTimeline = compositionLocalOf<EntranceTimeline?> { null }

/** One registry per app navigation session; survives recreation and page returns. */
@Composable
internal fun AppEntranceHost(content: @Composable () -> Unit) {
    val visited = rememberSaveable { mutableStateOf(emptyList<String>()) }
    val registry = remember { EntranceRegistry(visited) }
    CompositionLocalProvider(LocalRegistry provides registry, content = content)
}

/** One shared clock, so lazy composition, refresh and recomposition cannot replay items. */
@Composable
internal fun PageEntranceScope(pageKey: String, enabled: Boolean = true, replayOnOpen: Boolean = false, content: @Composable () -> Unit) {
    if (RenderPerformance.reducedEffects) {
        CompositionLocalProvider(LocalTimeline provides null, content = content)
        return
    }
    val registry = LocalRegistry.current
    val timeline = remember(pageKey) {
        EntranceTimeline().apply {
            if (!replayOnOpen && pageKey in registry.visited.value) elapsed = Float.POSITIVE_INFINITY
        }
    }
    LaunchedEffect(pageKey, enabled) {
        if (enabled && timeline.elapsed != Float.POSITIVE_INFINITY) {
            if (!replayOnOpen && pageKey in registry.visited.value) {
                timeline.elapsed = Float.POSITIVE_INFINITY
            } else {
                if (pageKey !in registry.visited.value) registry.visited.value = registry.visited.value + pageKey
                val start = SystemClock.uptimeMillis()
                // All components share one 700ms page transition timeline.
                while (timeline.elapsed < timeline.lastIndex * 35f + PAGE_TRANSITION_MS) {
                    withFrameNanos { }
                    timeline.elapsed = (SystemClock.uptimeMillis() - start).toFloat()
                }
                timeline.elapsed = Float.POSITIVE_INFINITY
            }
        }
    }
    CompositionLocalProvider(LocalTimeline provides timeline, content = content)
}

/** Apply to the complete component/card, never its individual internal labels. */
@Composable
internal fun Modifier.staggeredEntrance(index: Int): Modifier {
    val timeline = LocalTimeline.current ?: return this
    timeline.lastIndex = maxOf(timeline.lastIndex, index)
    val distance = with(LocalDensity.current) { 20.dp.toPx() }
    return graphicsLayer {
        val fraction = ((timeline.elapsed - index * 35f) / PAGE_TRANSITION_MS).coerceIn(0f, 1f)
        val reveal = EntranceEasing.transform(fraction)
        alpha = reveal
        translationY = distance * (1f - reveal)
    }
}

@Composable
internal fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.staggeredEntrance(index), content = content)
}
