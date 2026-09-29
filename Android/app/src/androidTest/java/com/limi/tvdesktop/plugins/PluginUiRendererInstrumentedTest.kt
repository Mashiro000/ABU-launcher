package com.limi.tvdesktop.plugins

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.focus.FocusRequester
import com.limi.tvdesktop.plugins.runtime.PluginUiNode
import com.limi.tvdesktop.plugins.runtime.PluginUiRenderer
import com.limi.tvdesktop.plugins.runtime.PluginSurfaceHost
import com.limi.tvdesktop.plugins.runtime.PluginDataSourceBrowser
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PluginUiRendererInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun touchInputAndButtonUseHostWidgets() {
        val values = mutableMapOf<String, String>()
        var action: String? = null
        compose.setContent {
            PluginUiRenderer(
                PluginUiNode("column", children = listOf(
                    PluginUiNode("input", id = "name", text = "Name"),
                    PluginUiNode("button", text = "Save", action = "save"),
                )),
                onAction = { action = it },
                onInput = { key, value -> values[key] = value },
            )
        }
        compose.onNodeWithText("Name").performTextInput("ABU")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals("ABU", values["name"])
            assertEquals("save", action)
        }
    }

    @Test fun remoteConfirmActivatesFocusedButton() {
        var count = 0
        val requesters = mutableMapOf<String, FocusRequester>()
        compose.setContent {
            PluginUiRenderer(PluginUiNode("button", text = "Play", action = "play"), onAction = { count++ }, focusRequesters = requesters)
        }
        compose.runOnIdle { requesters.getValue("root").requestFocus() }
        compose.onNode(hasClickAction()).assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun hostNavigationPassesParametersAndRestoresFocus() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "test.ui.${System.nanoTime()}"
        val file = File(context.cacheDir, "$id.abu-plugin")
        val manifest = """{"schemaVersion":1,"id":"$id","name":"UI test","version":"1.0.0","author":"test","kind":"ui","entry":"dist/index.js","hostApi":">=1.1.0 <2.0.0","surfaces":["home"]}"""
        val source = """
            globalThis.ABUPlugin = {
              render(input) {
                return {ui:input.route==='detail'
                  ? {type:'button',id:'back',text:'Inside:'+input.params.item,action:'back'}
                  : {type:'button',id:'open',text:'Open',action:'open'}};
              },
              onAction(input) {
                return input.action==='open'
                  ? {navigation:{push:'detail',params:{item:'42'}}}
                  : {navigation:{pop:true}};
              }
            };
        """.trimIndent()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js")); zip.write(source.toByteArray()); zip.closeEntry()
        }
        val manager = PluginManager.get(context)
        try {
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val plugin = manager.installed().single { it.id == id }
            compose.setContent { PluginSurfaceHost(plugin, "home") }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open").assertIsFocused().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Inside:42").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Inside:42").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open").assertIsFocused()
            compose.onNodeWithText("Open").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Inside:42").fetchSemanticsNodes().isNotEmpty() }
            Espresso.pressBack()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Open").assertIsFocused()
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    }

    @Test fun dataSourceBrowserHandsTrustedMediaToPlayer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "test.browser.${System.nanoTime()}"
        val file = File(context.cacheDir, "$id.abu-plugin")
        val manifest = """{"schemaVersion":1,"id":"$id","name":"Browser test","version":"1.0.0","author":"test","kind":"data_source","entry":"dist/index.js","hostApi":">=1.1.0 <2.0.0","permissions":[{"id":"network","title":"Network"}],"networkDomains":["media.example.com"]}"""
        val source = "globalThis.ABUPlugin={render(){return{};},onDataSource(){return{value:{items:[{id:'one',title:'Sample Movie',streamUrl:'https://media.example.com/movie.mp4'}]}};}};"
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js")); zip.write(source.toByteArray()); zip.closeEntry()
        }
        val manager = PluginManager.get(context)
        var played: String? = null
        try {
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val plugin = manager.installed().single { it.id == id }
            compose.setContent {
                PluginDataSourceBrowser(plugin, listOf(plugin), onSelectSource = {}, onClose = {}, onPlay = { _, _, url -> played = url })
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Sample Movie").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Sample Movie").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("允许").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("允许").performClick()
            compose.waitUntil(10_000) { played != null }
            compose.runOnIdle { assertEquals("https://media.example.com/movie.mp4", played) }
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    }
}
