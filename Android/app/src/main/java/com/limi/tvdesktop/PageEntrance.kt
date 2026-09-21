package com.limi.tvdesktop

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
/** Shared focus motion: quick response followed by a soft, non-bouncing settle. */
internal fun focusMotion() = tween<Float>(240, easing = CubicBezierEasing(.2f, .8f, .2f, 1f))
/** A small spring on selection; deselection settles without another bounce. */
internal fun focusScaleMotion(selected: Boolean): FiniteAnimationSpec<Float> =
    if (selected) spring(dampingRatio = .75f, stiffness = 450f, visibilityThreshold = .001f)
    else focusMotion()
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
                // All components share elapsed time; their 380ms animations overlap.
                while (timeline.elapsed < maxOf(2000f, timeline.lastIndex * 35f + 380f)) {
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
        val fraction = ((timeline.elapsed - index * 35f) / 380f).coerceIn(0f, 1f)
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
