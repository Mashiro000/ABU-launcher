package com.limi.tvdesktop.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.limi.tvdesktop.plugins.runtime.PluginCapabilityBridge
import com.limi.tvdesktop.plugins.runtime.PluginRuntimeSession
import com.limi.tvdesktop.plugins.runtime.PluginSandboxClient
import com.limi.tvdesktop.plugins.runtime.PluginCapabilityRequest
import com.limi.tvdesktop.plugins.runtime.CapabilityResult
import com.limi.tvdesktop.plugins.runtime.PluginDataSource
import com.limi.tvdesktop.plugins.runtime.PluginSubtitleResolver
import com.limi.tvdesktop.playbackMediaItem
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class PluginRuntimeInstrumentedTest {
    @Test fun subtitlePluginResultIsValidatedAndAttachedToNativePlayer() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "test.subtitle.${System.nanoTime()}"
        val file = packageFile(context, id, """
            globalThis.ABUPlugin = {
              render() { return {}; },
              onSubtitle(input) { return {value:{subtitles:[
                {url:'https://sub.example.com/caption.vtt'},
                {url:'https://evil.example.com/caption.srt'}
              ]}}; }
            };
        """.trimIndent(), kind = "subtitle", extra = """"permissions":[{"id":"network","title":"Network"}],"networkDomains":["sub.example.com"],""")
        val manager = PluginManager.get(context)
        try {
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val plugin = manager.installed().single { it.id == id }
            val output = PluginSandboxClient(context).use { client ->
                PluginRuntimeSession(plugin, manager, client, PluginCapabilityBridge(context)).invokeOutput(
                    "onSubtitle", JSONObject().put("title", "Test"), requestConsent = { false }
                ).value
            }
            val urls = output!!.getJSONArray("subtitles")
            assertTrue(PluginSubtitleResolver.allowed(plugin, urls.getJSONObject(0).getString("url")))
            assertTrue(!PluginSubtitleResolver.allowed(plugin, urls.getJSONObject(1).getString("url")))
            val item = playbackMediaItem("https://media.example.com/a.mp4", listOf("https://sub.example.com/caption.vtt"))
            assertTrue(item.localConfiguration!!.subtitleConfigurations.single().mimeType == "text/vtt")
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    } }
    @Test fun dataSourcePluginReturnsPagedMediaThroughSandbox() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "test.media.${System.nanoTime()}"
        val source = """
            globalThis.ABUPlugin = {
              render() { return {}; },
              onDataSource(input) {
                const page = Number(input.cursor || 0);
                return {value:{items:[{id:String(page),title:'Page '+page,streamUrl:'https://media.example.com/a.mp4'}],nextCursor:page<1?String(page+1):null}};
              }
            };
        """.trimIndent()
        val file = packageFile(context, id, source, kind = "data_source")
        val manager = PluginManager.get(context)
        try {
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val plugin = manager.installed().single { it.id == id }
            PluginSandboxClient(context).use { client ->
                val session = PluginRuntimeSession(plugin, manager, client, PluginCapabilityBridge(context))
                val first = PluginDataSource.parse(session.invokeOutput("onDataSource", JSONObject().put("cursor", JSONObject.NULL), requestConsent = { false }).value)
                val second = PluginDataSource.parse(session.invokeOutput("onDataSource", JSONObject().put("cursor", first.nextCursor), requestConsent = { false }).value)
                assertTrue(first.items.single().title == "Page 0" && second.items.single().title == "Page 1")
                assertTrue(second.nextCursor == null)
            }
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    } }
    @Test fun deviceEnumerationRequiresPluginAndAndroidConsent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val plugin = InstalledPlugin(
            id = "test.devices.${System.nanoTime()}", name = "Devices", version = "1.0.0", description = "",
            kind = PluginKind.SYSTEM, author = "test", enabled = true, trust = PluginTrust.UNVERIFIED,
            installedBytes = 0, permissions = listOf(PluginPermission("usb", "USB"), PluginPermission("bluetooth", "Bluetooth")),
        )
        val bridge = PluginCapabilityBridge(context)
        val usb = PluginCapabilityRequest("usb", "usb.list", JSONObject())
        val bluetooth = PluginCapabilityRequest("bt", "bluetooth.list", JSONObject())
        assertTrue(bridge.execute(plugin, usb) is CapabilityResult.NeedsConsent)
        bridge.setConsent(plugin.id, "usb", true)
        bridge.setConsent(plugin.id, "bluetooth", true)
        assertTrue(bridge.execute(plugin, usb) is CapabilityResult.Success)
        val permission = Manifest.permission.BLUETOOTH_CONNECT
        val hadPermission = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val result = bridge.execute(plugin, bluetooth)
        if (hadPermission) assertTrue(result is CapabilityResult.Success && result.value.has("devices"))
        else assertTrue(result is CapabilityResult.NeedsAndroidPermission)
        context.getSharedPreferences("plugin_permissions", android.content.Context.MODE_PRIVATE).edit()
            .remove("${plugin.id}.usb").remove("${plugin.id}.bluetooth").apply()
    }
    @Test fun isolatedJavascriptRendersHandlesActionAndCallsCapability() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = "test.runtime.${System.nanoTime()}"
        val source = """
            globalThis.ABUPlugin = {
              render(input) { return {ui:{type:'text',text:'surface='+input.surface}}; },
              onAction(input) { return {capabilities:[{id:'info',capability:'device.info',arguments:{}}]}; },
              onCapabilities(input) { return {ui:{type:'text',text:input.results[0].ok?'capability-ok':'capability-failed'}}; }
            };
        """.trimIndent()
        val file = packageFile(context, id, source)
        val manager = PluginManager.get(context)
        try {
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val plugin = manager.installed().single { it.id == id }
            PluginSandboxClient(context).use { client ->
                val session = PluginRuntimeSession(plugin, manager, client, PluginCapabilityBridge(context))
                val initial = session.invoke("render", JSONObject().put("surface", "home")) { false }
                assertTrue(initial?.text == "surface=home")
                val afterAction = session.invoke("onAction", JSONObject().put("action", "device")) { false }
                assertTrue(afterAction?.text == "capability-ok")
            }
        } finally {
            manager.uninstall(id)
            file.delete()
        }
    } }

    @Test fun onePluginCanCallAnotherPluginsService() { runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime()
        val providerId = "test.provider.$suffix"
        val callerId = "test.caller.$suffix"
        val providerFile = packageFile(context, providerId, """
            globalThis.ABUPlugin = {
              render() { return {capabilities:[{id:'register',capability:'services.register',arguments:{name:'math',version:2}}]}; },
              onCapabilities() { return {ui:{type:'text',text:'ready'}}; },
              onService(input) { return {value:{answer:input.arguments.number+1,caller:input.callerPluginId}}; }
            };
        """.trimIndent(), """[{"name":"math","version":2}]""")
        val callerFile = packageFile(context, callerId, """
            globalThis.ABUPlugin = {
              render() { return {ui:{type:'text',text:'caller'}}; },
              onAction() { return {capabilities:[{id:'call',capability:'services.call',arguments:{owner:'$providerId',name:'math',minimumVersion:2,method:'increment',arguments:{number:41}}}]}; },
              onCapabilities(input) { return {ui:{type:'text',text:input.results[0].ok?String(input.results[0].value.answer):input.results[0].error}}; }
            };
        """.trimIndent())
        val manager = PluginManager.get(context)
        try {
            manager.installPackage(providerFile)
            manager.installPackage(callerFile)
            manager.setEnabled(providerId, true)
            manager.setEnabled(callerId, true)
            PluginSandboxClient(context).use { client ->
                val bridge = PluginCapabilityBridge(context)
                val provider = PluginRuntimeSession(manager.installed().single { it.id == providerId }, manager, client, bridge)
                val caller = PluginRuntimeSession(manager.installed().single { it.id == callerId }, manager, client, bridge)
                assertTrue(caller.invoke("onAction", JSONObject()) { false }?.text == "42")
                assertTrue(provider.invoke("render", JSONObject()) { false }?.text == "ready")
                manager.setEnabled(providerId, false)
                assertTrue(caller.invoke("onAction", JSONObject()) { false }?.text?.contains("不可用") == true)
            }
        } finally {
            manager.uninstall(providerId)
            manager.uninstall(callerId)
            providerFile.delete()
            callerFile.delete()
        }
    } }

    private fun packageFile(context: android.content.Context, id: String, source: String, services: String = "[]", kind: String = "ui", extra: String = ""): File {
        val file = File(context.cacheDir, "$id.abu-plugin")
        val manifest = """{"schemaVersion":1,"id":"$id","name":"Runtime test","version":"1.0.0","kind":"$kind","entry":"dist/index.js","surfaces":["home"],${extra}"services":$services}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js")); zip.write(source.toByteArray()); zip.closeEntry()
        }
        return file
    }
}
