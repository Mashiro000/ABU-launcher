package com.limi.tvdesktop

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlin.math.abs

internal data class FocusRowSpec(val id: String, val lazyIndex: Int, val centerOnMove: Boolean = true, val centerOffsetDp: Float? = null)

/**
 * Vertical page scroll: a smooth, non-bouncy ease-out curve matching the horizontal
 * shelf glide, so up/down section moves feel as fluid as left/right card moves.
 */
internal val RowScrollMotion: AnimationSpec<Float> =
    tween(2000, easing = CubicBezierEasing(.16f, 1f, .3f, 1f))

/**
 * LazyListState.animateScrollToItem hardcodes a critically-damped spring and exposes no
 * animation spec. Reproduce its distance calculation here and run it on [RowScrollMotion],
 * so section jumps share the same lightly-damped glide.
 */
internal suspend fun LazyListState.animateScrollToItemSpring(index: Int, scrollOffset: Int = 0) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return
    val firstIndex = firstVisibleItemIndex
    val firstOffset = firstVisibleItemScrollOffset
    val distance = if (index in visible.first().index..visible.last().index) {
        (visible.firstOrNull { it.index == index }?.offset ?: 0).toFloat() + scrollOffset
    } else {
        val average = visible.map { it.size }.average().toFloat()
        (average * (index - firstIndex)) - firstOffset + scrollOffset
    }
    animateScrollBy(distance, RowScrollMotion)
}

private class RowFocusNode(val row: String, val requester: FocusRequester) {
    var preferredEntry = false
    var coordinates: LayoutCoordinates? = null
    fun centerX(): Float? = coordinates?.takeIf { it.isAttached }?.let {
        it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)).x
    }
    fun centerY(): Float? = coordinates?.takeIf { it.isAttached }?.let {
        it.localToRoot(Offset(it.size.width / 2f, it.size.height / 2f)).y
    }
}
private class RowFocusNavigator(val scope: CoroutineScope) {
    val nodes = mutableListOf<RowFocusNode>()
    val firstReveal = mutableMapOf<String, suspend () -> Unit>()
    var active: RowFocusNode? = null
    var rows: List<FocusRowSpec> = emptyList()
    var reveal: suspend (FocusRowSpec) -> Unit = {}
    var edge: suspend (Int) -> Unit = {}
    var center: suspend (RowFocusNode) -> Unit = {}
    var revealTop: suspend () -> Unit = {}
    private var job: Job? = null
    private var pendingRow: String? = null
    private var anchorX = 0f

    fun move(direction: Int): Boolean {
        val source = active ?: return false
        val sourceRow = pendingRow ?: source.row
        val index = rows.indexOfFirst { it.id == sourceRow }
        if (index < 0) return false
        if (pendingRow == null) anchorX = source.centerX() ?: anchorX
        val next = index + direction
        job?.cancel()
        if (next !in rows.indices) {
            pendingRow = null
            if (sourceRow != "header") job = scope.launch { edge(direction) }
            return true
        }
        pendingRow = rows[next].id
        val chooseFirst = direction > 0 && sourceRow.endsWith(":actions")
        job = scope.launch {
            try {
                var candidateRow = next
                while (candidateRow in rows.indices) {
                    val spec = rows[candidateRow]
                    val neededReveal = nodes.none { it.row == spec.id && it.centerX() != null }
                    if (neededReveal) {
                        // Start the reveal scroll and hand focus over the moment the row
                        // composes, so focus lands during the scroll instead of after it
                        // settles (mirrors the horizontal shelf).
                        launch { reveal(spec) }
                        var attempts = 0
                        while (nodes.none { it.row == spec.id && it.centerX() != null } && attempts < 300) {
                            withFrameNanos { }
                            attempts++
                        }
                    }
                    if (chooseFirst) {
                        firstReveal[spec.id]?.invoke()
                        withFrameNanos { }
                    }
                    val candidates = nodes.filter { it.row == spec.id && it.centerX() != null }
                    val candidate = if (spec.id == "header") candidates.firstOrNull { it.preferredEntry }
                        ?: candidates.minByOrNull { abs(it.centerX()!! - anchorX) }
                        else if (chooseFirst) candidates.minByOrNull { it.centerX()!! }
                        else candidates.minByOrNull { abs(it.centerX()!! - anchorX) }
                    if (candidate != null && candidate.requester.requestFocus()) {
                        android.util.Log.d("TvRowFocus", "${source.row} -> ${spec.id}; x=$anchorX -> ${candidate.centerX()}")
                        active = candidate
                        // Focus has arrived; rapid subsequent keys must start from this row.
                        pendingRow = null
                        if (spec.id != "header" && spec.id != "back") {
                            repeat(2) { withFrameNanos { } }
                            if (spec.centerOnMove) {
                                if (!neededReveal || spec.centerOffsetDp == null) center(candidate)
                            } else revealTop()
                        }
                        break
                    }
                    // A filtered empty shelf has no selectable row to visit.
                    candidateRow += direction
                }
            } finally {
                if (isActive) pendingRow = null
            }
        }
        return true
    }
}
@Composable internal fun RegisterRowStart(id: String?, reveal: suspend () -> Unit) {
    val navigator = LocalRowNavigator.current
    val currentReveal by rememberUpdatedState(reveal)
    DisposableEffect(navigator, id) {
        if (id != null) navigator?.firstReveal?.set(id) { currentReveal() }
        onDispose { if (id != null) navigator?.firstReveal?.remove(id) }
    }
}
private val LocalRowNavigator = staticCompositionLocalOf<RowFocusNavigator?> { null }
private val LocalRowId = staticCompositionLocalOf<String?> { null }

@Composable internal fun FocusRow(id: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRowId provides id, content = content)
}

/** Capture vertical keys before Compose's geometric search or old per-control shortcuts. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun FocusRowsHost(
    list: LazyListState, rows: List<FocusRowSpec>, enabled: Boolean,
    onDirection: () -> Unit = {}, content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navigator = remember(list) { RowFocusNavigator(scope) }
    var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    navigator.rows = rows
    navigator.reveal = { spec ->
        val height = viewportCoordinates?.size?.height ?: list.layoutInfo.viewportEndOffset
        val header = with(density) { 128.dp.toPx() }
        val middle = header + (height - header - with(density) { 24.dp.toPx() }) / 2f
        val offset = spec.centerOffsetDp?.let { with(density) { it.dp.toPx() } - middle }
            ?: -header
        // Go straight to the row's final center, avoiding a second reverse adjustment.
        list.animateScrollToItemSpring(spec.lazyIndex.coerceAtLeast(0), offset.toInt())
    }
    navigator.edge = { direction -> list.animateScrollBy(with(density) { (240.dp * direction).toPx() }, RowScrollMotion) }
    navigator.revealTop = { list.animateScrollToItemSpring(0) }
    navigator.center = { node ->
        val viewport = viewportCoordinates?.takeIf { it.isAttached }
        val y = node.centerY()
        if (viewport != null && y != null) {
            val top = viewport.localToRoot(Offset.Zero).y
            val header = with(density) { 128.dp.toPx() }.coerceAtMost(viewport.size.height.toFloat())
            val bottom = with(density) { 24.dp.toPx() }
            val target = top + header + (viewport.size.height - header - bottom) / 2f
            // LazyList clamps at actual start/end; no extra spacer is introduced.
            list.animateScrollBy(y - target, RowScrollMotion)
            android.util.Log.d("TvRowFocus", "center ${node.row}: y=${node.centerY()}, target=$target, start=${!list.canScrollBackward}, end=${!list.canScrollForward}")
        }
    }
    // Explicit navigation owns scrolling. Automatic focus relocation would otherwise
    // race the center animation or move a settled row again during a left/right change.
    val relocation = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }
    CompositionLocalProvider(LocalRowNavigator provides navigator, LocalRowId provides null,
        LocalBringIntoViewSpec provides relocation) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { viewportCoordinates = it }.onPreviewKeyEvent {
            if (enabled && (it.key == Key.DirectionDown || it.key == Key.DirectionUp)) {
                if (it.type == KeyEventType.KeyDown) {
                    onDirection()
                    navigator.move(if (it.key == Key.DirectionDown) 1 else -1)
                } else navigator.active != null
            } else false
        }) { content() }
    }
}

internal fun Modifier.rowFocusTarget(requester: FocusRequester? = null, row: String? = null, preferredEntry: Boolean = false): Modifier = composed {
    val navigator = LocalRowNavigator.current
    val id = row ?: LocalRowId.current
    if (navigator == null || id == null) return@composed this
    val ownRequester = remember { FocusRequester() }
    val visibility = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val node = remember(navigator, id, requester) { RowFocusNode(id, requester ?: ownRequester) }
    node.preferredEntry = preferredEntry
    LaunchedEffect(focused, size) {
        if (focused && id != "header" && id != "back" && size.height > 0) {
            withFrameNanos { }
            // Reserve the fixed 112dp backdrop plus 16dp spacing, including focus overscan.
            visibility.bringIntoView(Rect(
                -size.width * .05f,
                -size.height * .05f - with(density) { 128.dp.toPx() },
                size.width * 1.05f,
                size.height * 1.05f + with(density) { 24.dp.toPx() }
            ))
        }
    }
    DisposableEffect(navigator, node) {
        navigator.nodes.add(node)
        onDispose { navigator.nodes.remove(node) }
    }
    this.bringIntoViewRequester(visibility).onSizeChanged { size = it }
        .focusRequester(node.requester).onGloballyPositioned { node.coordinates = it }
        .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) navigator.active = node }
}

internal val LibraryFocusRows = listOf(
    FocusRowSpec("header", 0, false), FocusRowSpec("play", 0, false), FocusRowSpec("watching", 0, false),
    FocusRowSpec("categories", 1, centerOffsetDp = 96f + 36f + LibraryDesign.titleGap.value + 366f / 2f), FocusRowSpec("我的收藏:actions", 2),
    FocusRowSpec("favoriteFilters", 2), FocusRowSpec("favorites", 2),
    FocusRowSpec("最近添加:actions", 3), FocusRowSpec("最近添加:cards", 3),
    FocusRowSpec("最近听过:actions", 4), FocusRowSpec("最近听过:cards", 4),
    FocusRowSpec("最近的回忆:actions", 5), FocusRowSpec("memories", 5)
)
