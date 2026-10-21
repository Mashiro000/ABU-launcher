package com.limi.tvdesktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsRowLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortStatusLeavesRoomForSecondaryPageLabels() {
        val labels = listOf("显示 Dock 应用内容", "恢复默认导航条", "锁定当前 Bing 壁纸")
        compose.setContent {
            Column(Modifier.width(500.dp)) {
                labels.forEach { SettingsRowItem(it, "已开启", onClick = {}) }
            }
        }
        labels.forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(label, 1, layouts.single().lineCount)
            assertFalse(label, layouts.single().hasVisualOverflow)
        }
    }
}
