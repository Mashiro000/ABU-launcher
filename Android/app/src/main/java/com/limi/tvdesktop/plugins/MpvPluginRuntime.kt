package com.limi.tvdesktop.plugins

import android.content.Context
import android.os.Build
import java.io.File

object MpvPluginRuntime {
    const val PLUGIN_ID = "com.limi.player.mpv"

    @Volatile private var loaded = false

    fun isInstalled(context: Context): Boolean = resolveNativeDirectory(context) != null

    fun isEnabled(context: Context): Boolean = isInstalled(context) &&
        context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
            .getBoolean("enabled.$PLUGIN_ID", false)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            check(isEnabled(context)) { "MPV 播放器插件尚未启用" }
            val nativeDir = resolveNativeDirectory(context)
                ?: throw MpvPluginMissingException("尚未安装 MPV 播放器插件")

            // Dependencies are loaded explicitly because downloaded native libraries are not in
            // ApplicationInfo.nativeLibraryDir. MPVLib's later loadLibrary calls become no-ops.
            LOAD_ORDER.filter { File(nativeDir, "lib$it.so").isFile }.forEach { name ->
                val library = File(nativeDir, "lib$name.so")
                System.load(library.absolutePath)
            }
            loaded = true
        }
    }

    fun currentAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS }

    private fun resolveNativeDirectory(context: Context): File? {
        val abi = currentAbi() ?: return null
        val pluginRoot = File(context.filesDir, "plugins/$PLUGIN_ID")
        val current = File(pluginRoot, "current").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        if (current.isBlank()) return null
        val nativeDir = File(pluginRoot, "$current/lib/$abi")
        return nativeDir.takeIf { dir ->
            dir.isDirectory && REQUIRED_LIBRARIES.all { File(dir, "lib$it.so").isFile }
        }
    }

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    private val LOAD_ORDER = listOf(
        "c++_shared",
        "avutil",
        "swresample",
        "swscale",
        "avcodec",
        "avformat",
        "avfilter",
        "avdevice",
        "mediakitandroidhelper",
        "mpv",
        "player",
    )
    private val REQUIRED_LIBRARIES = listOf("mpv", "player")
}

class MpvPluginMissingException(message: String) : IllegalStateException(message)
