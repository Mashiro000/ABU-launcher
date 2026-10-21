package com.limi.tvdesktop

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PinEntryInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun oneFieldShowsOnlyCountAndBackDeletesOneStep() {
        var submitted: String? = null
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF14181F))) {
                PinEntryDialog("setup_lock", {}, { submitted = it }, remember { HazeState() }, null)
            }
        }
        compose.onNodeWithContentDescription("方向密码").performKeyInput {
            pressKey(Key.DirectionUp); pressKey(Key.DirectionDown); pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight)
        }
        compose.onNodeWithText("已输入 4 步").assertIsDisplayed()
        compose.onNodeWithText("UDLR").assertDoesNotExist()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "abu-qa").apply { mkdirs() }
        File(directory, "password.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("方向密码").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("已输入 3 步").assertIsDisplayed()
        compose.onNodeWithContentDescription("方向密码").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("UDLR", submitted) }
    }
}
