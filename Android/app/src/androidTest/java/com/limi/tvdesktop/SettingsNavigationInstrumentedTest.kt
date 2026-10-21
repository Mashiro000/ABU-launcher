package com.limi.tvdesktop

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.limi.tvdesktop.plugins.PluginManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsNavigationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun showSettings() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                AppEntranceHost { SettingsPage(onClose = {}, onWallpaperChanged = {}) }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun remoteVisitsEverySettingWithoutSkippingAcrossSections() {
        showSettings()
        compose.onNodeWithText("桌面与外观").performClick()
        val rows = listOf("玻璃与模糊", "壁纸", "导航条", "Dock 内容预览", "图标大小", "时钟样式", "时间格式", "日期显示",
            "时间显示秒数", "Dock 布局", "应用圆角", "启动默认页", "屏幕保护", "开机自启动", "强制横屏",
            "应用列表背景模糊", "网格密度", "文件夹页码样式")
        rows.forEachIndexed { index, label ->
            compose.onNodeWithText(label).assertIsFocused()
            if (index < rows.lastIndex) compose.onNodeWithText(label).performKeyInput { pressKey(Key.DirectionDown) }
        }
        rows.asReversed().drop(1).forEach { label ->
            compose.onNodeWithText(rows[rows.indexOf(label) + 1]).performKeyInput { pressKey(Key.DirectionUp) }
            compose.onNodeWithText(label).assertIsFocused()
        }
    }

    @Test fun modulesCollapseToIconsAndAllMotionChoicesPersist() {
        val original = RenderPerformance.motionMode
        try {
            showSettings()
            compose.onNodeWithText("关闭").assertDoesNotExist()
            compose.onNodeWithText("应用与文件夹").assertIsDisplayed()
            compose.onNodeWithText("性能与动画").performClick()
            compose.onNodeWithText("应用与文件夹").assertDoesNotExist()
            compose.onNodeWithContentDescription("应用与文件夹").assertIsDisplayed()
            compose.onNodeWithText("低性能模式").performClick()
            listOf("完整动画", "精简动画", "无动画").forEach { compose.onAllNodesWithText(it).onLast().assertIsDisplayed() }
            compose.onNodeWithText("精简动画").performClick()
            compose.runOnIdle {
                RenderPerformance.init(context)
                assertEquals(RenderPerformance.MotionMode.REDUCED, RenderPerformance.motionMode)
                assertFalse(RenderPerformance.blur31)
                assertEquals(700, RenderPerformance.duration(700))
            }
            compose.onNodeWithText("无动画").performClick()
            compose.runOnIdle {
                RenderPerformance.init(context)
                assertEquals(RenderPerformance.MotionMode.NONE, RenderPerformance.motionMode)
                assertEquals(0, RenderPerformance.duration(700))
            }
            compose.onNodeWithText("插件设置").performClick()
            compose.onNodeWithText("插件管理").assertIsDisplayed()
        } finally {
            compose.runOnIdle { RenderPerformance.setMotionMode(context, original) }
        }
    }

    @Test fun clockAndAppListBlurChoicesPersist() {
        val originalFormat = DesktopPreferences.TimeFormat.current(context)
        val originalDate = DesktopPreferences.DateDisplay.current(context)
        val originalSeconds = DesktopPreferences.ClockDisplay.showSeconds(context)
        val originalBlur = DesktopPreferences.GlassTuning.appListBlurEnabled(context)
        try {
            showSettings()
            compose.onNodeWithText("桌面与外观").performClick()
            compose.onNodeWithText("时间格式").performScrollTo().performClick()
            compose.onAllNodesWithText("12 小时制").onLast().performClick()
            compose.onNodeWithText("← 返回分类").performClick()
            compose.onNodeWithText("日期显示").performScrollTo().performClick()
            compose.onAllNodesWithText("隐藏日期").onLast().performClick()
            compose.onNodeWithText("← 返回分类").performClick()
            compose.onNodeWithText("时间显示秒数").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(DesktopPreferences.TimeFormat.HOUR_12, DesktopPreferences.TimeFormat.current(context))
                assertEquals(DesktopPreferences.DateDisplay.OFF, DesktopPreferences.DateDisplay.current(context))
                assertEquals(!originalSeconds, DesktopPreferences.ClockDisplay.showSeconds(context))
                assertEquals("" to "", DesktopPreferences.ClockDisplay.dateLines(System.currentTimeMillis(), DesktopPreferences.DateDisplay.OFF))
            }
            compose.onNodeWithContentDescription("应用与文件夹").performClick()
            compose.onNodeWithText("应用列表背景模糊").performClick()
            compose.runOnIdle {
                assertEquals(!originalBlur, DesktopPreferences.GlassTuning.appListBlurEnabled(context))
                assertEquals(!originalBlur, context.getSharedPreferences("desktop", Context.MODE_PRIVATE).getBoolean("appListBlurEnabled", originalBlur))
            }
        } finally {
            compose.runOnIdle {
                DesktopPreferences.TimeFormat.save(context, originalFormat)
                DesktopPreferences.DateDisplay.save(context, originalDate)
                DesktopPreferences.ClockDisplay.setShowSeconds(context, originalSeconds)
                DesktopPreferences.GlassTuning.setAppListBlurEnabled(context, originalBlur)
            }
        }
    }

    @Test fun installedPluginHasModuleAndOpensSettingsInsidePanel() {
        val manager = PluginManager.get(context)
        val id = "test.settings.modules." + System.nanoTime()
        val file = File(context.cacheDir, "$id.abu-plugin")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"schemaVersion":1,"id":"$id","name":"设置测试插件","version":"1.0.0","kind":"ui","entry":"dist/index.js"}""".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js"))
            zip.write("globalThis.ABUPlugin={render(){return {ui:{type:'text',text:'设置插件内容'}}}}".toByteArray())
            zip.closeEntry()
        }
        try {
            manager.installPackage(file)
            showSettings()
            compose.onNodeWithText("插件设置").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("设置测试插件").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("设置测试插件").performClick()
            compose.onNodeWithText("启用插件").assertIsDisplayed()
            compose.onNodeWithContentDescription("设置测试插件").assertIsDisplayed()
            compose.onNodeWithText("暂无已安装插件").assertDoesNotExist()
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun remoteCanOpenModuleAndReturnToModuleGrid() {
        showSettings()
        compose.onNodeWithText("桌面与外观").assertIsFocused()
        compose.onNodeWithText("桌面与外观").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("启动与行为").assertIsFocused()
        compose.onNodeWithText("启动与行为").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("启动默认页").assertIsFocused()
        compose.onNodeWithText("启动默认页").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithContentDescription("启动与行为").assertIsFocused()
        compose.onNodeWithContentDescription("启动与行为").performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("应用与文件夹").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun continuousScrollUpdatesRailAndRailJumpsToSection() {
        showSettings()
        compose.onNodeWithText("桌面与外观").performClick()
        // Position the startup section at the top, rather than merely revealing it at the bottom.
        compose.onNodeWithTag("continuous-settings-list").performScrollToIndex(12)
        compose.onNodeWithContentDescription("启动与行为").assertIsSelected()
        compose.onNodeWithContentDescription("性能与动画").performClick()
        compose.onNodeWithText("低性能模式").assertIsDisplayed().assertIsFocused()
        compose.onNodeWithContentDescription("性能与动画").assertIsSelected()
        compose.onNodeWithText("低性能模式").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("隐藏应用管理").assertIsFocused()
        compose.onNodeWithContentDescription("应用与文件夹").assertIsSelected()
    }
}
