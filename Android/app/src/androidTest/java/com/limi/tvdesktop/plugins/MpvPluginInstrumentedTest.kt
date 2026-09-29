package com.limi.tvdesktop.plugins

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import dev.jdtech.mpv.MPVLib

@RunWith(AndroidJUnit4::class)
class MpvPluginInstrumentedTest {
    @Test fun installAndLoadNativeMpvPlugin() {
        val sourcePath = InstrumentationRegistry.getArguments().getString("mpvPluginPath")
        assumeTrue("mpvPluginPath not supplied", !sourcePath.isNullOrBlank())
        val source = File(sourcePath!!)
        assumeTrue("MPV package not visible", source.isFile)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = PluginManager.get(context)
        runCatching { manager.uninstall(MpvPluginRuntime.PLUGIN_ID) }
        manager.installPackage(source)
        manager.setEnabled(MpvPluginRuntime.PLUGIN_ID, true)
        assertTrue(MpvPluginRuntime.isInstalled(context))
        MpvPluginRuntime.ensureLoaded(context)
        assertTrue(MpvPluginRuntime.isEnabled(context))
        val instance = requireNotNull(MPVLib.create(context))
        instance.setOptionString("config", "no")
        instance.setOptionString("vo", "null")
        instance.init()
        instance.destroy()
    }
}
