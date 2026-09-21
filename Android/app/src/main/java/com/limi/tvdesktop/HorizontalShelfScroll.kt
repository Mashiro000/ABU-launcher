package com.limi.tvdesktop

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Horizontal shelf scrolling.
 *
 * The shared host disables automatic focus relocation (RowFocusNavigation swaps
 * LocalBringIntoViewSpec for one that never scrolls), so every shelf has to animate
 * its own reveal. The reveal runs on a smooth, non-bouncy ease-out curve and starts
 * on the same frame as the focus change, so pressing left/right reads as one gesture
 * instead of a scroll followed by a switch.
 */
internal val ShelfScrollMotion: AnimationSpec<Float> =
    tween(2000, easing = CubicBezierEasing(.16f, 1f, .3f, 1f))

/**
 * Holding the key re-targets before the previous leg has settled. A shorter leg keeps
 * the row moving with the repeat rate instead of queueing behind a full-length tween.
 */
private val ShelfRepeatMotion: AnimationSpec<Float> = tween(110, easing = LinearOutSlowInEasing)
private const val RepeatWindowNanos = 220_000_000L

internal class ShelfScrollController(
    private val state: LazyListState,
    private val scope: CoroutineScope,
    private val itemWidthPx: () -> Float,
    private val gapPx: () -> Float
) {
    private var job: Job? = null
    private var lastMoveNanos = 0L

    /**
     * Moves the shelf selection to [index].
     *
     * [requestFocus] is called synchronously so the card's own focus animation and the
     * shelf movement start on the same frame and share one duration; any unfinished
     * movement is cancelled rather than appended, so rapid keys never fall behind.
     */
    fun select(index: Int, requestFocus: () -> Boolean) {
        val now = System.nanoTime()
        val repeating = now - lastMoveNanos in 1..RepeatWindowNanos
        lastMoveNanos = now
        val focused = requestFocus()
        job?.cancel()
        job = scope.launch {
            val delta = revealDelta(index)
            if (abs(delta) > .5f) {
                if (!focused) {
                    // Target is fully off-screen and not composed yet: focus it the moment
                    // it scrolls into view, so the focus animation overlaps the reveal
                    // scroll instead of waiting for the scroll to settle.
                    launch {
                        snapshotFlow { state.layoutInfo.visibleItemsInfo.any { it.index == index } }
                            .first { it }
                        withFrameNanos { }
                        requestFocus()
                    }
                }
                state.animateScrollBy(delta, if (repeating) ShelfRepeatMotion else ShelfScrollMotion)
            } else if (!focused) {
                // Nothing composed yet (first layout): reveal, then hand focus over.
                state.animateScrollToItem(index)
                withFrameNanos { }
                requestFocus()
            }
        }
    }

    /** Distance that brings item [index] inside the viewport, keeping a 5% overscan margin. */
    private fun revealDelta(index: Int): Float {
        val info = state.layoutInfo
        val anchor = info.visibleItemsInfo.firstOrNull() ?: return 0f
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val width = item?.size?.toFloat() ?: itemWidthPx()
        val start = item?.offset?.toFloat()
            ?: (anchor.offset + (index - anchor.index) * (itemWidthPx() + gapPx()))
        val margin = width * .05f
        val left = info.viewportStartOffset + info.beforeContentPadding.toFloat()
        val right = info.viewportEndOffset - info.afterContentPadding.toFloat()
        return when {
            start - margin < left -> start - margin - left
            start + width + margin > right -> start + width + margin - right
            else -> 0f
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
