package com.limi.tvdesktop.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import dev.jdtech.mpv.MPVLib

/** Thin libmpv adapter. UI and input remain owned by the app. */
class EmbeddedMpvController(context: Context) {
    private val mpv = requireNotNull(MPVLib.create(context.applicationContext)) { "无法创建 libmpv 实例" }
    private var loadedUrl: String? = null
    private var pendingPositionMs = 0L
    private var surfaceAttached = false

    init {
        mpv.setOptionString("config", "no")
        mpv.setOptionString("osc", "no")
        mpv.setOptionString("input-default-bindings", "no")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("hwdec", "mediacodec-copy")
        mpv.setOptionString("keep-open", "yes")
        mpv.init()
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
        mpv.command(arrayOf("loadfile", url, "replace"))
        if (positionMs > 0L) mpv.command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute", "exact"))
    }

    fun play() = mpv.setPropertyBoolean("pause", false)
    fun pause() = mpv.setPropertyBoolean("pause", true)
    fun seekTo(positionMs: Long) = mpv.command(arrayOf("seek", (positionMs / 1000.0).toString(), "absolute", "exact"))
    fun setSpeed(speed: Float) = mpv.setPropertyDouble("speed", speed.toDouble())
    fun setHardwareDecoding(enabled: Boolean) = mpv.setPropertyString("hwdec", if (enabled) "mediacodec-copy" else "no")
    fun setPreserveAssStyles(enabled: Boolean) = mpv.setPropertyString("sub-ass-override", if (enabled) "no" else "force")
    fun setSubtitleDelay(seconds: Double) = mpv.setPropertyDouble("sub-delay", seconds)
    fun setAudioDelay(seconds: Double) = mpv.setPropertyDouble("audio-delay", seconds)
    fun setAspect(mode: VideoResizeMode) = mpv.setPropertyString(
        "video-unscaled",
        if (mode == VideoResizeMode.FILL) "downscale-big" else "no"
    )

    fun addSubtitle(url: String, select: Boolean) {
        mpv.command(arrayOf("sub-add", url, if (select) "select" else "auto"))
    }

    val positionMs: Long get() = ((mpv.getPropertyDouble("time-pos") ?: 0.0) * 1000).toLong()
    val durationMs: Long get() = ((mpv.getPropertyDouble("duration") ?: 0.0) * 1000).toLong()
    val isPlaying: Boolean get() = mpv.getPropertyBoolean("pause") != true
    val speed: Float get() = (mpv.getPropertyDouble("speed") ?: 1.0).toFloat()
    val cacheDurationMs: Long get() = ((mpv.getPropertyDouble("demuxer-cache-duration") ?: 0.0) * 1000).toLong()

    fun destroy() {
        runCatching { mpv.command(arrayOf("stop")) }
        runCatching { mpv.detachSurface() }
        runCatching { mpv.destroy() }
    }
}
