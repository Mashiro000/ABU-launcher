package com.limi.tvdesktop.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.limi.tvdesktop.plugins.runtime.PluginPermissionState
import com.limi.tvdesktop.plugins.runtime.PluginPermissionStore
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class PluginManagerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val manager = PluginManager.get(context)

    @Test fun installRetainRollbackAndUninstall() {
        val id = "test.stage1.${System.nanoTime()}"
        try {
            (1..4).forEach { version -> manager.installPackage(packageFile(id, "$version.0.0")) }
            var installed = manager.installed().single { it.id == id }
            assertEquals("4.0.0", installed.version)
            assertEquals(3, installed.availableVersions.size)
            assertFalse("1.0.0" in installed.availableVersions)

            installed = manager.activateVersion(id, "2.0.0")
            assertEquals("2.0.0", installed.version)
            manager.setEnabled(id, true)
            assertTrue(manager.installed().single { it.id == id }.enabled)

            manager.uninstall(id)
            assertFalse(manager.installed().any { it.id == id })
        } finally {
            runCatching { manager.uninstall(id) }
        }
    }

    @Test fun rejectsZipPathTraversal() {
        val file = File(context.cacheDir, "bad-${System.nanoTime()}.abu-plugin")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        try {
            assertThrows(Exception::class.java) { manager.installPackage(file) }
        } finally {
            file.delete()
        }
    }

    @Test fun repositoryAndPermissionStateRoundTrip() {
        val url = "https://plugins.example.test/${System.nanoTime()}/index.json"
        manager.addRepository("Test repository", url)
        assertTrue(manager.repositories().any { it.indexUrl == url && it.enabled })
        manager.setRepositoryEnabled(url, false)
        assertFalse(manager.repositories().single { it.indexUrl == url }.enabled)
        manager.removeRepository(url)
        assertFalse(manager.repositories().any { it.indexUrl == url })

        val store = PluginPermissionStore(context)
        val pluginId = "test.permission.${System.nanoTime()}"
        assertEquals(PluginPermissionState.ASK, store.get(pluginId, "network"))
        store.set(pluginId, "network", PluginPermissionState.GRANTED)
        assertEquals(PluginPermissionState.GRANTED, store.get(pluginId, "network"))
        store.clear(pluginId)
        assertEquals(PluginPermissionState.ASK, store.get(pluginId, "network"))
    }

    @Test fun crashGuardDisablesMostRecentlyEnabledPluginAfterThreeFailedStarts() {
        val id = "test.crash.${System.nanoTime()}"
        val file = packageFile(id, "1.0.0")
        val guardPrefs = context.getSharedPreferences("plugin_crash_guard", android.content.Context.MODE_PRIVATE)
        try {
            guardPrefs.edit().clear().commit()
            manager.installPackage(file)
            manager.setEnabled(id, true)
            val guard = PluginCrashGuard(context)
            guard.beginStartup()
            guard.beginStartup()
            guard.beginStartup()
            assertEquals(id, guard.beginStartup())
            assertFalse(manager.installed().single { it.id == id }.enabled)
        } finally {
            guardPrefs.edit().clear().apply()
            runCatching { manager.uninstall(id) }
            file.delete()
        }
    }

    @Test fun repositoryParsingDownloadAndSignatureVerification() { runBlocking {
        val packageBytes = "signed plugin package".toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(packageBytes).joinToString("") { "%02x".format(it) }
        val publicKey = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        val signature = Base64.encodeToString(ByteArray(64), Base64.NO_WRAP)
        val index = """{"schemaVersion":1,"plugins":[{"id":"test.remote.plugin","name":"Remote","version":"1.0.0","kind":"ui","publicKey":"$publicKey","assets":[{"abi":"universal","url":"https://assets.example.test/plugin.abu-plugin","size":${packageBytes.size},"sha256":"$hash","signature":"$signature"}]}]}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val content = if (chain.request().url.host == "repo.example.test") index.toByteArray() else packageBytes
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(content.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        val client = PluginRepositoryClient(context, http)
        val plugin = client.fetch(PluginRepository("Test", "https://repo.example.test/index.json")).getOrThrow().single()
        assertEquals("test.remote.plugin", plugin.id)
        val downloaded = client.download(plugin, plugin.assets.single()).getOrThrow()
        assertEquals(hash, manager.sha256(downloaded))
        downloaded.delete()

        val signed = File(context.cacheDir, "signed-${System.nanoTime()}").apply { writeBytes(byteArrayOf(0x72)) }
        val rfcPublic = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val rfcSignature = hex("92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00")
        assertTrue(PluginSignatureVerifier.verify(signed, Base64.encodeToString(rfcPublic, Base64.NO_WRAP), Base64.encodeToString(rfcSignature, Base64.NO_WRAP)))
        signed.delete()
    } }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun packageFile(id: String, version: String): File {
        val file = File(context.cacheDir, "$id-$version.abu-plugin")
        val manifest = """{"schemaVersion":1,"id":"$id","name":"Test","version":"$version","kind":"ui","entry":"dist/index.js"}"""
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("dist/index.js")); zip.write("globalThis.ABUPlugin={render(){return {ui:{type:'text',text:'ok'}}}}".toByteArray()); zip.closeEntry()
        }
        return file
    }
}
