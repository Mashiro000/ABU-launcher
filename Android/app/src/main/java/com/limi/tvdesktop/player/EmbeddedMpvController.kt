package com.limi.tvdesktop.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib
import com.limi.tvdesktop.plugins.MpvPluginRuntime

/** Thin libmpv adapter. UI and input remain owned by the app. */
class EmbeddedMpvController(context: Context) {
    private data class PendingSubtitle(val url: String, val select: Boolean)

    private val mpv = run {
        MpvPluginRuntime.ensureLoaded(context.applicationContext)
        requireNotNull(MPVLib.create(context.applicationContext)) { "无法创建 libmpv 实例" }
    }
    private var loadedUrl: String? = null
    private var pendingPositionMs = 0L
    private var surfaceAttached = false
    private var fileLoaded = false
    private val pendingSubtitles = mutableListOf<PendingSubtitle>()
    private val httpProxy = MpvHttpProxy()

    private val eventObserver = object : MPVLib.EventObserver {
        override fun event(eventId: Int) {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                    fileLoaded = false
                    Log.i(TAG, "MPV 开始加载媒体")
                }
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    fileLoaded = true
                    Log.i(TAG, "MPV 媒体加载完成")
                    applyPendingPlaybackState()
                }
                MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                    fileLoaded = false
                    Log.w(TAG, "MPV 媒体播放结束或加载失败")
                }
            }
        }

        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Double) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit
    }

    private val logObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            val message = text.trim()
            if (message.isNotEmpty()) Log.d(TAG, "$prefix: $message")
        }
    }

    init {
        mpv.setOptionString("config", "no")
        mpv.setOptionString("osc", "no")
        mpv.setOptionString("input-default-bindings", "no")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        // auto-safe can use MediaCodec when supported and fall back to FFmpeg software
        // decoding. Forcing mediacodec-copy made MPV fail on x86 emulators and on codecs
        // unsupported by the device -- precisely the files MPV is intended to rescue.
        mpv.setOptionString("hwdec", "auto-safe")
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("network-timeout", "15")
        mpv.setOptionString("tls-verify", "no")
        mpv.setOptionString("ytdl", "no")
        mpv.setOptionString("user-agent", "Mozilla/5.0 (Linux; Android TV) mpv")
        mpv.init()
        mpv.addObserver(eventObserver)
        mpv.addLogObserver(logObserver)
    }

    fun createSurfaceView(context: Context): SurfaceView = SurfaceView(context).apply {
        isFocusable = false
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mpv.attachSurface(holder.surface)
                surfaceAttached = true
                loadedUrl?.let { loadNow(it, pendingPositionMs) }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceAttached = false
                mpv.detachSurface()
            }
        })
    }

    fun load(url: String, positionMs: Long = 0L) {
        loadedUrl = url
        pendingPositionMs = positionMs
        if (surfaceAttached) loadNow(url, positionMs)
    }

    private fun loadNow(url: String, positionMs: Long) {
        fileLoaded = false
        val playableUrl = if (url.startsWith("https://", ignoreCase = true)) httpProxy.urlFor(url) else url
        mpv.command(arrayOf("loadfile", playableUrl, "replace"))
        // Seeking and adding subtitles before FILE_LOADED are ignored by libmpv on a
        // number of network protocols. They are applied from the event callback instead.
    }

    private fun applyPendingPlaybackState() {
        if (pendingPositionMs > 0L) {
            mpv.command(arrayOf("seek", (pendingPositionMs / 1000.0).toString(), "absolute", "exact"))
            pendingPositionMs = 0L
        }
        pendingSubtitles.forEach { subtitle ->
            mpv.command(arrayOf("sub-add", subtitle.url, if (subtitle.select) "select" else "auto"))
        }
        pendingSubtitles.clear()
        mpv.setPropertyBoolean("pause", false)
    }

    fun play() = mpv.setPropertyBoolean("pause", false)
    fun pause() = mpv.setPropertyBoolean("pause", true)
    fun seekTo(positionMs: Long) = mpv.command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute", "exact"))
    fun setSpeed(speed: Float) = mpv.setPropertyDouble("speed", speed.toDouble())
    fun setHardwareDecoding(enabled: Boolean) = mpv.setPropertyString("hwdec", if (enabled) "auto-safe" else "no")
    fun setPreserveAssStyles(enabled: Boolean) = mpv.setPropertyString("sub-ass-override", if (enabled) "no" else "force")
    fun setSubtitleDelay(seconds: Double) = mpv.setPropertyDouble("sub-delay", seconds)
    fun setAudioDelay(seconds: Double) = mpv.setPropertyDouble("audio-delay", seconds)
    fun setAspect(mode: VideoResizeMode) = mpv.setPropertyString(
        "video-unscaled",
        if (mode == VideoResizeMode.FILL) "downscale-big" else "no"
    )

    fun addSubtitle(url: String, select: Boolean) {
        if (fileLoaded) {
            mpv.command(arrayOf("sub-add", url, if (select) "select" else "auto"))
        } else if (pendingSubtitles.none { it.url == url }) {
            pendingSubtitles += PendingSubtitle(url, select)
        }
    }

    val positionMs: Long get() = ((mpv.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
    val durationMs: Long get() = ((mpv.getPropertyDouble("duration") ?: 0.0) * 1000).toLong()
    val isPlaying: Boolean get() = fileLoaded && mpv.getPropertyBoolean("pause") != true
    val isBuffering: Boolean get() = !fileLoaded || mpv.getPropertyBoolean("paused-for-cache") == true
    val speed: Float get() = (mpv.getPropertyDouble("speed") ?: 1.0).toFloat()
    val cacheDurationMs: Long get() = ((mpv.getPropertyDouble("demuxer-cache-duration") ?: 0.0) * 1000).toLong()

    fun destroy() {
        runCatching { mpv.removeLogObserver(logObserver) }
        runCatching { mpv.removeObserver(eventObserver) }
        runCatching { mpv.command(arrayOf("stop")) }
        runCatching { mpv.detachSurface() }
        runCatching { mpv.destroy() }
        httpProxy.close()
    }

    private companion object {
        const val TAG = "EmbeddedMpv"
    }
}
