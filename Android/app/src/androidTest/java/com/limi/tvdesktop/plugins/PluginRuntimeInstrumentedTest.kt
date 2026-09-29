package com.limi.tvdesktop.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.limi.tvdesktop.plugins.runtime.PluginCapabilityBridge
import com.limi.tvdesktop.plugins.runtime.PluginRuntimeSession
import com.limi.tvdesktop.plugins.runtime.PluginSandboxClient
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

    private fun packageFile(context: android.content.Context, id: String, source: String, services: String = "[]"): File {
        val file = File(context.cacheDir, "$id.abu-plugin")
        val manifest = """{"schemaVersion":1,"id":"$id","name":"Runtime test","version":"1.0.0","kind":"ui","entry":"dist/index.js","surfaces":["home"],"services":$services}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js")); zip.write(source.toByteArray()); zip.closeEntry()
        }
        return file
    }
}
