package com.limi.tvdesktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Filled silhouettes share a 24-unit viewport and a centred 20-unit optical envelope. */
private object SettingsModuleIcons {
    val paths = mapOf(
        SettingsCategory.DESKTOP to "M4 3 H20 Q22 3 22 5 V16 Q22 18 20 18 H14 V20 H18 V22 H6 V20 H10 V18 H4 Q2 18 2 16 V5 Q2 3 4 3 Z M5 6 V15 H19 V6 Z",
        SettingsCategory.STARTUP to "M4 2 H20 Q22 2 22 4 V20 Q22 22 20 22 H4 Q2 22 2 20 V4 Q2 2 4 2 Z M10 5 V13 H6 L12 19 L18 13 H14 V5 Z",
        SettingsCategory.APPLICATIONS to "M3 2 H10 Q11 2 11 3 V10 Q11 11 10 11 H3 Q2 11 2 10 V3 Q2 2 3 2 Z M14 2 H21 Q22 2 22 3 V10 Q22 11 21 11 H14 Q13 11 13 10 V3 Q13 2 14 2 Z M3 13 H10 Q11 13 11 14 V21 Q11 22 10 22 H3 Q2 22 2 21 V14 Q2 13 3 13 Z M14 13 H21 Q22 13 22 14 V21 Q22 22 21 22 H14 Q13 22 13 21 V14 Q13 13 14 13 Z",
        SettingsCategory.PERFORMANCE to "M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 Z M7 17 L10 11 L18 6 L14 14 Z M11 5 V7 H13 V5 Z M5 11 V13 H7 V11 Z M17 11 V13 H19 V11 Z",
        SettingsCategory.PLAYER to "M12 2 A10 10 0 1 1 12 22 A10 10 0 1 1 12 2 Z M9 6 V18 L18 12 Z",
        SettingsCategory.LIBRARY to "M4 3 H10 L13 6 H20 Q22 6 22 8 V19 Q22 21 20 21 H4 Q2 21 2 19 V5 Q2 3 4 3 Z M6 10 V12 H18 V10 Z M6 15 V17 H15 V15 Z",
        SettingsCategory.GENERAL to "M10 2 H14 L15 5 L18 4 L21 7 L20 10 L23 11 V14 L20 15 L21 18 L18 21 L15 20 L14 23 H10 L9 20 L6 21 L3 18 L4 15 L1 14 V11 L4 10 L3 7 L6 4 L9 5 Z M12 8 A4.5 4.5 0 1 0 12 17 A4.5 4.5 0 1 0 12 8 Z",
        SettingsCategory.PLUGINS to "M4 4 H9 V3 A3 3 0 0 1 15 3 V4 H20 V9 H21 A3 3 0 0 1 21 15 H20 V20 H15 V19 A3 3 0 0 0 9 19 V20 H4 V15 H3 A3 3 0 0 1 3 9 H4 Z",
    ).mapValues { (_, data) -> PathParser().parsePathString(data).toPath().apply { fillType = PathFillType.EvenOdd } }
}

@Composable
internal fun SettingsModuleIcon(category: SettingsCategory, size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        // Dense gear/puzzle silhouettes are optically reduced to match the other icons.
        val opticalScale = when (category) {
            SettingsCategory.GENERAL -> .9f
            SettingsCategory.PLUGINS -> .83f
            else -> 1f
        }
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            scale(opticalScale, pivot = Offset(12f, 12f)) { drawPath(SettingsModuleIcons.paths.getValue(category), color) }
        }
    }
}
