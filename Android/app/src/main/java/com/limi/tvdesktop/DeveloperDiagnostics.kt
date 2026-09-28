package com.limi.tvdesktop

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale

/** Opt-in, foreground-only Window frame telemetry. No forced render loop or network requests. */
internal object DeveloperDiagnostics {
    var overlay by mutableStateOf(false)
        private set
    var pageVisible = false
        set(value) { field = value; reconcile() }
    var live by mutableStateOf(FrameSummary())
        private set
    var memory by mutableStateOf("等待采样")
        private set
    var remaining by mutableStateOf(0)
        private set
    var lastReport by mutableStateOf("")
        private set
    var keySummary by mutableStateOf("按遥控器方向键、确认键测试；返回键正常退出")
        private set
    var status by mutableStateOf("未启动采样")
        private set
    @Volatile var scene = "桌面"
    private var activity: Activity? = null
    private var worker: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var recent = FrameStatistics()
    private var session = FrameStatistics()
    private val scenes = linkedMapOf<String, FrameStatistics>()
    private var capture = false
    private var startedAt = 0L
    private var deadline = 0L
    private var initialMode = ""
    private var lastMemory = 0L
    private var keyCount = 0
    private var maxKeyDelay = 0L
    private var refreshRate = 60f
    private var sceneAtStart = ""

    fun attach(host: Activity) { activity = host; reconcile() }
    fun detach() {
        if (capture) finish("应用进入后台，测试提前结束")
        stopListener()
        activity = null
    }
    fun toggleOverlay() { overlay = !overlay; reconcile() }

    @Suppress("DEPRECATION")
    private fun reconcile() {
        val host = activity ?: return
        if (!overlay && !pageVisible && !capture) { stopListener(); return }
        if (listener != null) return
        refreshRate = host.windowManager.defaultDisplay.refreshRate.takeIf { it > 0f } ?: 60f
        synchronized(lock) { recent = FrameStatistics() }
        val thread = HandlerThread("DesktopFrameMetrics").apply { start() }
        worker = thread
        val callback = Window.OnFrameMetricsAvailableListener { _, frame, lost ->
            // First-draw frames have a different budget and are excluded from steady-state stats.
            if (frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                val budget = if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.DEADLINE) / 1e6 else 0.0
                val ms = frame.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6
                val layout = frame.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) / 1e6
                val draw = frame.getMetric(FrameMetrics.DRAW_DURATION) / 1e6
                val effectiveBudget = budget.takeIf { it > 0 } ?: (1000.0 / refreshRate)
                synchronized(lock) {
                    recent.add(ms, effectiveBudget, layout, draw, lost)
                    if (capture) {
                        session.add(ms, effectiveBudget, layout, draw, lost)
                        scenes.getOrPut(scene) { FrameStatistics() }.add(ms, effectiveBudget, layout, draw, lost)
                    }
                }
            }
        }
        runCatching { host.window.addOnFrameMetricsAvailableListener(callback, Handler(thread.looper)) }
            .onSuccess {
                listener = callback
                status = "采样中 · %.1f Hz".format(Locale.ROOT, refreshRate)
                main.post(tick)
            }.onFailure {
                thread.quitSafely(); worker = null
                status = "当前设备无法提供窗口帧统计"
            }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (listener == null) return
            live = synchronized(lock) { recent.snapshot().also { recent = FrameStatistics() } }
            val now = SystemClock.elapsedRealtime()
            if (now - lastMemory >= 3000) {
                val runtime = Runtime.getRuntime()
                val system = ActivityManager.MemoryInfo()
                (activity?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(system)
                memory = "Java 堆 ${mb(runtime.totalMemory() - runtime.freeMemory())} / ${mb(runtime.maxMemory())} MB" +
                    " · Native 堆 ${mb(Debug.getNativeHeapAllocatedSize())} MB\n系统可用 ${mb(system.availMem)} / ${mb(system.totalMem)} MB · 内存压力 ${if (system.lowMemory) "偏高" else "正常"}"
                lastMemory = now
            }
            if (capture) {
                remaining = ((deadline - now + 999) / 1000).toInt().coerceAtLeast(0)
                if (now >= deadline) finish("30 秒测试完成")
            }
            if (listener != null) main.postDelayed(this, 1000)
        }
    }

    private fun stopListener() {
        main.removeCallbacks(tick)
        listener?.let { callback -> runCatching { activity?.window?.removeOnFrameMetricsAvailableListener(callback) } }
        listener = null
        worker?.quitSafely(); worker = null
    }

    fun start() {
        if (capture) return
        startedAt = SystemClock.elapsedRealtime()
        deadline = startedAt + 30_000
        initialMode = if (RenderPerformance.reducedEffects) "低性能 / 兼容" else "标准"
        sceneAtStart = scene
        synchronized(lock) { session = FrameStatistics(); scenes.clear(); capture = true }
        keyCount = 0; maxKeyDelay = 0
        remaining = 30
        reconcile()
        if (listener == null) finish("设备不支持采样，未完成测试")
    }

    fun finish(reason: String = "手动结束测试") {
        if (!capture) return
        val frameReport = synchronized(lock) {
            capture = false
            session.snapshot().text() + scenes.entries.joinToString("\n\n", prefix = "\n\n分页面：\n") { (name, frames) ->
                "$name\n${frames.snapshot().text()}"
            }
        }
        remaining = 0
        lastReport = "阿布桌面性能诊断\n${java.util.Date()}\n$reason\n" +
            "设备 ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
            "模式 $initialMode · 刷新率 $refreshRate Hz · 时长 ${(SystemClock.elapsedRealtime() - startedAt) / 1000} 秒\n" +
            "开始页面 $sceneAtStart · 悬浮面板 ${if (overlay) "开启" else "关闭"}\n$frameReport\n\n$memory\n" +
            "遥控器按下 $keyCount 次 · 最大分发等待 $maxKeyDelay ms\n" +
            "统计口径：Window FrameMetrics，排除首绘；Android 12+ 使用帧 deadline，其余使用刷新周期。\n" +
            "P95 为向上取整至毫秒的直方图；冻结帧 >700 ms。丢失采样不是丢帧。\n" +
            "仅测本应用窗口，不代表视频解码帧率；静止页面帧数低属正常。布局/绘制耗时不等同 CPU/GPU 利用率。"
        reconcile()
    }

    fun recordKey(event: KeyEvent) {
        if ((!pageVisible && !overlay && !capture) || event.action != KeyEvent.ACTION_DOWN) return
        val wait = (SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0)
        keyCount++; maxKeyDelay = maxOf(maxKeyDelay, wait)
        keySummary = "${KeyEvent.keyCodeToString(event.keyCode)} · 重复 ${event.repeatCount} · 分发等待 $wait ms"
    }

    fun reset() {
        if (capture) finish("重置前结束测试")
        synchronized(lock) { recent = FrameStatistics() }
        live = FrameSummary(); lastReport = ""; keyCount = 0; maxKeyDelay = 0
    }

    fun report(context: Context): String = lastReport.ifBlank {
        "阿布桌面即时诊断\n${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}\n$status\n${live.text()}\n$memory\n$keySummary"
    } + "\n版本 ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}\n"

    fun save(context: Context, report: String): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "diagnostics").let { dir ->
        check(dir.exists() || dir.mkdirs()) { "无法创建报告目录" }
        File(dir, "performance-${System.currentTimeMillis()}.txt").apply { writeText(report) }
    }
    private fun mb(bytes: Long) = bytes / (1024 * 1024)
}
