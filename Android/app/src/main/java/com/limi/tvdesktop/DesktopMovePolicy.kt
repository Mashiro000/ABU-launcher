package com.limi.tvdesktop

internal enum class MoveDirection { LEFT, RIGHT, UP, DOWN }

/** Dock and grid have separate ordering domains. Horizontal movement never wraps a row. */
internal fun desktopMoveTarget(index: Int, total: Int, columns: Int, direction: MoveDirection): Int? {
    if (index !in 0 until total || columns < 1) return null
    val dockSize = minOf(6, total)
    if (index < dockSize) return when (direction) {
        MoveDirection.LEFT -> (index - 1).takeIf { it >= 0 }
        MoveDirection.RIGHT -> (index + 1).takeIf { it < dockSize }
        else -> null
    }
    val relative = index - dockSize
    return when (direction) {
        MoveDirection.LEFT -> (index - 1).takeIf { relative % columns > 0 }
        MoveDirection.RIGHT -> (index + 1).takeIf { relative % columns < columns - 1 && it < total }
        MoveDirection.UP -> (index - columns).takeIf { it >= dockSize }
        MoveDirection.DOWN -> (index + columns).takeIf { it < total }
    }
}
