package com.limi.tvdesktop

import org.junit.Assert.*
import org.junit.Test

class DesktopMovePolicyTest {
    @Test fun dockCannotCrossIntoGrid() {
        for (index in 0..5) {
            assertNull(desktopMoveTarget(index, 25, 5, MoveDirection.DOWN))
            assertNull(desktopMoveTarget(index, 25, 5, MoveDirection.UP))
        }
        assertEquals(4, desktopMoveTarget(5, 25, 5, MoveDirection.LEFT))
        assertNull(desktopMoveTarget(5, 25, 5, MoveDirection.RIGHT))
    }
    @Test fun gridKeepsColumnAndDoesNotWrapOrEnterDock() {
        assertNull(desktopMoveTarget(6, 18, 5, MoveDirection.UP))
        assertNull(desktopMoveTarget(6, 18, 5, MoveDirection.LEFT))
        assertNull(desktopMoveTarget(10, 18, 5, MoveDirection.RIGHT))
        assertEquals(11, desktopMoveTarget(6, 18, 5, MoveDirection.DOWN))
        assertEquals(6, desktopMoveTarget(11, 18, 5, MoveDirection.UP))
        assertNull(desktopMoveTarget(13, 18, 5, MoveDirection.DOWN))
    }
    @Test fun partialDockAndInvalidPositionsStayWithinBounds() {
        assertNull(desktopMoveTarget(2, 3, 5, MoveDirection.RIGHT))
        assertNull(desktopMoveTarget(-1, 3, 5, MoveDirection.RIGHT))
        assertNull(desktopMoveTarget(0, 0, 5, MoveDirection.RIGHT))
        assertNull(desktopMoveTarget(8, 20, 0, MoveDirection.DOWN))
    }
    @Test fun navigationMigrationRemovesUnknownAndDuplicateItems() {
        assertEquals(listOf("媒体库", "首页"), normalizeNavigation(listOf("媒体库", "应用", "媒体库")))
        assertEquals(listOf("首页", "媒体库"), normalizeNavigation(emptyList()))
    }
}
