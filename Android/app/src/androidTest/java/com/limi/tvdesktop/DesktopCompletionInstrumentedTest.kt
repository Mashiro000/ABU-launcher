package com.limi.tvdesktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File

/** Runs the production TvDesktop composition in an isolated test activity; MainActivity has a separate launch smoke check. */
class DesktopCompletionInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun showDesktop() {
        compose.activityRule.scenario.onActivity { activity ->
            RenderPerformance.init(activity)
            NavigationPreferences.reset(activity)
            androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("previewBaseUrl")?.let {
                activity.getSharedPreferences("desktop", 0).edit().putString("videoPreviewBaseUrl", it).commit()
            }
            AccountManager.init(activity)
            PosterCacheManager.init(activity)
            MediaLibraryManager.init(activity)
            ScreenSaverState.dismiss()
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        }
        compose.setContent {
            val input = androidx.compose.ui.platform.LocalInputModeManager.current
            androidx.compose.runtime.LaunchedEffect(Unit) { input.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            MaterialTheme(colorScheme = darkColorScheme()) { AppEntranceHost { TvDesktop() } }
        }
    }

    private fun screenshot(name: String) {
        val directory = File(compose.activity.cacheDir, "abu-qa").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun emptyLibraryOpensAddDialogDirectlyAndCanReturn() {
        compose.onNodeWithText("媒体库").performClick()
        compose.onNodeWithText("媒体库").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("媒体库").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("添加媒体库").assertIsFocused().assertIsDisplayed()
        screenshot("media-library")
        compose.onNodeWithText("添加媒体库").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("服务器地址").assertIsDisplayed()
        screenshot("add-library")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("添加媒体库").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithText("海岸线之外").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun dockMoveKeepsItsRegionAndCommitsTheSelectedApp() {
        compose.onNodeWithText("首页").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("dock-slot-0").fetchSemanticsNodes().isNotEmpty() }
        val before = AppHomeFeatures.homeOrder(compose.activity)
        assertTrue(before.size >= 6)
        screenshot("home")
        compose.onNodeWithTag("dock-slot-0").performTouchInput { longClick() }
        compose.onNodeWithText("移动", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("dock-slot-0").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "移动中"))
        compose.onNodeWithTag("dock-slot-0").performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle {
            val after = AppHomeFeatures.homeOrder(compose.activity)
            assertEquals(before[0], after[1])
            assertEquals(before[1], after[0])
            assertEquals(before.drop(6), after.drop(6))
            AppHomeFeatures.saveHomeOrder(compose.activity, before)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun gridEditKeepsGeometryAndCannotMoveIntoDock() {
        compose.onNodeWithText("首页").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("dock-slot-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dock-slot-0").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithTag("dock-slot-0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("grid-slot-0").assertIsDisplayed()
        screenshot("applications")
        val before = AppHomeFeatures.homeOrder(compose.activity)
        compose.onNodeWithTag("grid-slot-0").performTouchInput { longClick() }
        compose.onNodeWithText("移动", useUnmergedTree = true).performClick()
        val first = compose.onNodeWithTag("grid-slot-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("grid-slot-1").fetchSemanticsNode().boundsInRoot
        // The focused mover intentionally grows by 18 dp on each side.
        assertTrue(first.width >= second.width && first.width <= second.width * 1.25f)
        assertTrue(second.left > first.right)
        screenshot("move-app")
        compose.onNodeWithTag("grid-slot-0").performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle {
            val after = AppHomeFeatures.homeOrder(compose.activity)
            assertEquals(before.take(6), after.take(6))
            assertEquals(before[6], after[7])
            assertEquals(before[7], after[6])
            AppHomeFeatures.saveHomeOrder(compose.activity, before)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun navigationLongPressReordersAndPersists() {
        val before = NavigationPreferences.order(compose.activity)
        try {
            compose.onNodeWithText("首页").performClick()
            compose.onNodeWithText("首页").performKeyInput {
                keyDown(Key.DirectionCenter)
                advanceEventTime(650)
                keyUp(Key.DirectionCenter)
            }
            compose.onNodeWithText("‹ 首页 ›").assertIsDisplayed().performKeyInput {
                pressKey(if (before.indexOf("首页") == 0) Key.DirectionRight else Key.DirectionLeft)
                pressKey(Key.Enter)
            }
            compose.runOnIdle { assertEquals(before.reversed(), NavigationPreferences.order(compose.activity)) }
        } finally {
            compose.runOnIdle { NavigationPreferences.saveOrder(compose.activity, before) }
        }
    }

    @Test fun hiddenNavigationKeepsControlCenterAccessible() {
        try {
            compose.runOnIdle { NavigationPreferences.setShown(compose.activity, false) }
            compose.onNodeWithText("首页").assertDoesNotExist()
            compose.onNodeWithContentDescription("打开快捷控制中心").assertIsDisplayed().performClick()
            compose.onNodeWithText("设置").assertIsDisplayed()
        } finally {
            compose.runOnIdle { NavigationPreferences.setShown(compose.activity, true) }
        }
    }

    @Test fun captureSettingsAndGradientWallpaper() {
        val original = WallpaperMode.current(compose.activity)
        try {
            compose.onNodeWithText("首页").performClick()
            compose.onNodeWithContentDescription("打开快捷控制中心").performClick()
            compose.onNodeWithText("设置").performClick()
            compose.onNodeWithText("桌面与外观").assertIsDisplayed()
            screenshot("settings")
            compose.onNodeWithText("桌面与外观").performClick()
            compose.onNodeWithText("壁纸").performScrollTo().performClick()
            compose.onNodeWithText("渐变色").performScrollTo().performClick()
            compose.onNodeWithContentDescription("应用海湾渐变壁纸").performScrollTo().performClick()
            compose.onNodeWithContentDescription("应用海湾渐变壁纸").assertIsFocused()
            compose.waitUntil(5_000) {
                val card = compose.onNodeWithContentDescription("应用海湾渐变壁纸").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                card.bottom <= root.bottom - 12f
            }
            screenshot("gradient-picker")
            compose.onNodeWithText("视频壁纸").performScrollTo().performClick()
            compose.onNodeWithText("浏览并下载在线视频壁纸").performScrollTo().performClick()
            compose.onNodeWithText("在线航拍视频").assertIsDisplayed()
            screenshot("video-wallpaper")
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("渐变色").performScrollTo().performClick()
            repeat(5) {
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
            compose.onNodeWithTag("dock-slot-0").assertIsDisplayed()
            screenshot("gradient-home")
        } finally {
            WallpaperMode.save(compose.activity, original)
        }
    }
}
