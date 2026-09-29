package com.limi.tvdesktop

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.limi.tvdesktop.plugins.MpvPluginRuntime
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.limi.tvdesktop.player.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "TvPlayer"

/**
 * Activity-level key bridge for the player. Compose focus is unreliable once the AndroidView
 * (video surface) is in the tree, so "any key brings the controls back" is handled here instead
 * of relying on a focused Compose node. Null whenever the player is closed.
 */
object PlayerKeyBridge {
    var onKey: ((android.view.KeyEvent) -> Boolean)? = null
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    title: String,
    streamUrl: String,
    startPositionMs: Long = 0L,
    playbackInfo: TvPlaybackInfo? = null,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onOpenExternal: (Long) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mediaRoutingKey = playbackInfo?.mediaId?.takeIf { it.isNotBlank() } ?: streamUrl
    var activeEngine by remember(mediaRoutingKey) {
        val configured = DesktopPreferences.PlaybackEngine.current(context)
        val remembered = PlayerEngineHistory.preferred(context, mediaRoutingKey)
        mutableStateOf(
            if (MpvPluginRuntime.isEnabled(context) &&
                (configured == DesktopPreferences.PlaybackEngine.MPV ||
                    configured == DesktopPreferences.PlaybackEngine.AUTO && remembered == PlayerEngineHistory.Engine.MPV))
                DesktopPreferences.PlaybackEngine.MPV else DesktopPreferences.PlaybackEngine.INTERNAL
        )
    }
    val useMpv = activeEngine == DesktopPreferences.PlaybackEngine.MPV

    // 整合数据源
    val currentMediaInfo = remember(playbackInfo, title, streamUrl) {
        val base = playbackInfo ?: TvPlaybackInfo.createDemo()
        base.copy(
            title = title.ifBlank { base.title },
            streamUrl = streamUrl.ifBlank { base.streamUrl },
            startPositionMs = startPositionMs
        )
    }

    var currentEpisodeIndex by remember { mutableIntStateOf(currentMediaInfo.currentEpisodeIndex) }
    var episodesList by remember { mutableStateOf(currentMediaInfo.playlist) }
    var danmakuSettings by remember { mutableStateOf(TvDanmakuSettings()) }
    var runtimeDanmakus by remember(currentMediaInfo.mediaId) { mutableStateOf(currentMediaInfo.danmakus) }
    var currentResizeMode by remember { mutableStateOf(VideoResizeMode.FIT) }

    // 播放器运行状态
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(currentMediaInfo.totalDurationMs) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFallbackTried by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var bufferingSince by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var networkSpeedText by remember { mutableStateOf("0 KB/s") }
    var hasVideoTrack by remember { mutableStateOf(false) }
    var engineSuccessRecorded by remember(activeEngine, mediaRoutingKey) { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var speedOverlayText by remember { mutableStateOf<String?>(null) }
    var hideSpeedOverlayJob by remember { mutableStateOf<Job?>(null) }
    var longPressDetectJob by remember { mutableStateOf<Job?>(null) }

    var isFastForwarding3x by remember { mutableStateOf(false) }
    var isFastRewinding3x by remember { mutableStateOf(false) }

    fun showTemporaryToast(text: String, durationMs: Long = 1000L) {
        hideSpeedOverlayJob?.cancel()
        speedOverlayText = text
        hideSpeedOverlayJob = coroutineScope.launch {
            delay(durationMs)
            if (!isFastForwarding3x && !isFastRewinding3x) {
                speedOverlayText = null
            }
        }
    }

    // UI 显隐与抽屉状态
    var showControls by remember { mutableStateOf(true) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }
    var showTrackSettings by remember { mutableStateOf(false) }
    var showMoreSettings by remember { mutableStateOf(false) }
    var mpvHardwareDecoding by remember { mutableStateOf(true) }
    var preserveAssStyles by remember { mutableStateOf(true) }
    var subtitleDelaySeconds by remember { mutableDoubleStateOf(0.0) }
    var controlInteractionTick by remember { mutableIntStateOf(0) }

    // 轨道信息
    var audioTracks by remember { mutableStateOf<List<TvAudioTrack>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TvSubtitleTrack>>(emptyList()) }

    // 焦点管理
    val playPauseFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // 实时磨砂玻璃状态 HazeState (支持硬件级背景模糊)
    val playerHaze = remember { HazeState() }

    // 画中画支持检测
    val isPipSupported = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    // 确定当前起播的 URL
    val activePlayUrl = remember(episodesList, currentEpisodeIndex, currentMediaInfo) {
        val target = if (episodesList.isNotEmpty() && currentEpisodeIndex in episodesList.indices) {
            episodesList[currentEpisodeIndex].streamUrl.ifBlank { currentMediaInfo.streamUrl }
        } else {
            currentMediaInfo.streamUrl
        }
        target
    }
    val healthMonitor = remember(activePlayUrl, useMpv) { PlaybackHealthMonitor() }

    LaunchedEffect(activePlayUrl) {
        if (activePlayUrl.isBlank()) errorMessage = "该条目没有可用的播放地址"
    }

    val mpvController = remember(useMpv) {
        if (useMpv) runCatching { EmbeddedMpvController(context) }.getOrNull() else null
    }
    LaunchedEffect(useMpv, mpvController) {
        if (useMpv && mpvController == null) {
            activeEngine = DesktopPreferences.PlaybackEngine.INTERNAL
            errorMessage = "尚未安装可用的 MPV 插件，请先在设置 → 插件中安装"
        }
    }
    DisposableEffect(mpvController) {
        onDispose { mpvController?.destroy() }
    }

    // 初始化 AndroidX Media3 ExoPlayer 内核
    val exoPlayer = remember {
        Log.i(TAG, "初始化 ExoPlayer，起播 URL: $activePlayUrl, startPositionMs: $startPositionMs")
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android TV; ExoPlayer)")

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                if (activePlayUrl.isBlank()) {
                    Log.e(TAG, "没有可用的播放地址，跳过 prepare")
                } else if (!useMpv) {
                    setMediaItem(MediaItem.fromUri(activePlayUrl))
                    // 仅当 startPosition 合理且未超长时 seek
                    if (startPositionMs > 0 && (currentMediaInfo.totalDurationMs == 0L || startPositionMs < currentMediaInfo.totalDurationMs)) {
                        seekTo(startPositionMs)
                    }
                    prepare()
                    playWhenReady = true
                    play()
                    Log.i(TAG, "ExoPlayer prepare() 与 play() 已调用, url=$activePlayUrl")
                }
            }
    }

    LaunchedEffect(useMpv, activePlayUrl) {
        if (activePlayUrl.isBlank()) return@LaunchedEffect
        if (useMpv) {
            mpvController?.load(activePlayUrl, currentMediaInfo.startPositionMs)
            currentMediaInfo.externalSubtitleUrls.forEachIndexed { index, url ->
                mpvController?.addSubtitle(url, index == 0)
            }
        } else if (exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() != activePlayUrl) {
            exoPlayer.setMediaItem(MediaItem.fromUri(activePlayUrl), currentMediaInfo.startPositionMs)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun enginePosition(): Long = if (useMpv) mpvController?.positionMs ?: 0L else exoPlayer.currentPosition
    fun engineDuration(): Long = if (useMpv) mpvController?.durationMs ?: 0L else exoPlayer.duration
    fun engineIsPlaying(): Boolean = if (useMpv) mpvController?.isPlaying == true else exoPlayer.isPlaying
    fun enginePlay() { if (useMpv) mpvController?.play() else exoPlayer.play() }
    fun enginePause() { if (useMpv) mpvController?.pause() else exoPlayer.pause() }
    fun engineSeekTo(positionMs: Long) { if (useMpv) mpvController?.seekTo(positionMs) else exoPlayer.seekTo(positionMs) }
    fun engineSetSpeed(speed: Float) { if (useMpv) mpvController?.setSpeed(speed) else exoPlayer.setPlaybackSpeed(speed) }
    fun engineSpeed(): Float = if (useMpv) mpvController?.speed ?: 1f else exoPlayer.playbackParameters.speed

    // 监听 ExoPlayer 真实播放状态
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.i(TAG, "onIsPlayingChanged: $playing, currentPos: ${exoPlayer.currentPosition}, dur: ${exoPlayer.duration}")
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                val stateName = when (state) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN($state)"
                }
                Log.i(TAG, "onPlaybackStateChanged: $stateName, duration: ${exoPlayer.duration}, buffered: ${exoPlayer.bufferedPosition}")
                when (state) {
                    Player.STATE_BUFFERING -> {
                        if (!isBuffering) bufferingSince = System.currentTimeMillis()
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                        errorMessage = null
                        isBuffering = false
                    }
                    Player.STATE_ENDED -> isBuffering = false
                    else -> {}
                }
            }

            override fun onRenderedFirstFrame() {
                healthMonitor.onFirstFrame()
                if (!engineSuccessRecorded) {
                    PlayerEngineHistory.recordSuccess(context, mediaRoutingKey, PlayerEngineHistory.Engine.MEDIA3)
                    engineSuccessRecorded = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                Log.i(TAG, "onTracksChanged: groups = ${tracks.groups.size}")
                val audios = mutableListOf<TvAudioTrack>()
                val subtitles = mutableListOf<TvSubtitleTrack>()
                hasVideoTrack = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }

                tracks.groups.forEachIndexed { groupIdx, group ->
                    for (trackIdx in 0 until group.length) {
                        val format = group.getTrackFormat(trackIdx)
                        val isSelected = group.isTrackSelected(trackIdx)
                        val lang = format.language ?: "und"
                        val label = format.label ?: format.id ?: "轨道 $trackIdx"

                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            val mime = format.sampleMimeType?.substringAfterLast('/') ?: "audio"
                            val channels = format.channelCount
                            audios.add(
                                TvAudioTrack(
                                    id = "$groupIdx-$trackIdx",
                                    groupIndex = groupIdx,
                                    trackIndex = trackIdx,
                                    name = "$label ($mime ${channels}ch)",
                                    language = lang,
                                    isSelected = isSelected
                                )
                            )
                        } else if (group.type == C.TRACK_TYPE_TEXT) {
                            subtitles.add(
                                TvSubtitleTrack(
                                    id = "$groupIdx-$trackIdx",
                                    groupIndex = groupIdx,
                                    trackIndex = trackIdx,
                                    name = "$label ($lang)",
                                    language = lang,
                                    isSelected = isSelected
                                )
                            )
                        }
                    }
                }
                // 只显示播放器实际发现的轨道；“关闭字幕”是真实的禁用操作。
                // 旧实现会伪造中/英/日轨道，用户点击后看似选中但播放器毫无变化。
                subtitles.add(
                    0,
                    TvSubtitleTrack(
                        id = "off",
                        groupIndex = -1,
                        trackIndex = -1,
                        name = "关闭字幕",
                        language = "none",
                        isSelected = subtitles.none { it.isSelected }
                    )
                )
                audioTracks = audios
                subtitleTracks = subtitles
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "ExoPlayer 播放异常 errorCode: ${error.errorCodeName}, 详情: ${error.message}", error)
                isBuffering = false
                val reason = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "网络连接失败：无法连接到媒体服务器"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "服务器返回错误：播放地址失效或没有播放权限"
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "本机无法解码该视频编码（多为 HEVC/H.265）：请用「调用外部播放器」，或在服务器端开启转码"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "播放地址不存在（文件可能已被删除或移动）"
                    else -> error.message ?: "未知播放错误"
                }
                errorMessage = reason + "〔" + error.errorCodeName + "〕"
                Log.e(TAG, "播放失败，已停止（不切换演示视频）")
                val decoderFailed = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                if (decoderFailed && !isFallbackTried && MpvPluginRuntime.isEnabled(context)) {
                    isFallbackTried = true
                    PlayerEngineHistory.recordFailure(context, mediaRoutingKey, PlayerEngineHistory.Engine.MEDIA3)
                    errorMessage = "内置解码器不支持该视频，正在切换到 MPV 插件…"
                    coroutineScope.launch {
                        delay(350)
                        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                        activeEngine = DesktopPreferences.PlaybackEngine.MPV
                        errorMessage = null
                    }
                }
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            Log.i(TAG, "释放 ExoPlayer: pos=$pos, dur=$dur")
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            onProgressUpdate(pos, dur)
        }
    }


    // 实时位置与缓冲轮询 (250ms 刷新)
    LaunchedEffect(useMpv, activePlayUrl) {
        while (true) {
            currentPosition = enginePosition().coerceAtLeast(0L)
            bufferedPosition = if (useMpv) currentPosition + (mpvController?.cacheDurationMs ?: 0L)
                else exoPlayer.bufferedPosition.coerceAtLeast(0L)
            if (engineDuration() > 0) {
                duration = engineDuration()
            }
            if (useMpv) isPlaying = engineIsPlaying()
            if (useMpv && currentPosition >= 1_000L && !engineSuccessRecorded) {
                PlayerEngineHistory.recordSuccess(context, mediaRoutingKey, PlayerEngineHistory.Engine.MPV)
                engineSuccessRecorded = true
            } else if (!useMpv && !isFallbackTried) {
                when (healthMonitor.sample(
                    positionMs = currentPosition,
                    bufferedPositionMs = bufferedPosition,
                    isBuffering = isBuffering,
                    hasVideo = hasVideoTrack,
                )) {
                    PlaybackHealthMonitor.Decision.RETRY_MEDIA3 -> {
                        Log.w(TAG, "播放健康评分请求重试 Media3")
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    PlaybackHealthMonitor.Decision.SWITCH_ENGINE -> {
                        if (MpvPluginRuntime.isEnabled(context)) {
                            Log.w(TAG, "播放健康评分判定 Media3 异常，切换 MPV")
                            isFallbackTried = true
                            PlayerEngineHistory.recordFailure(context, mediaRoutingKey, PlayerEngineHistory.Engine.MEDIA3)
                            exoPlayer.pause()
                            activeEngine = DesktopPreferences.PlaybackEngine.MPV
                            errorMessage = null
                        }
                    }
                    PlaybackHealthMonitor.Decision.WAITING_FOR_NETWORK -> {
                        // Buffer is empty: this is a slow network, not a decoder failure.
                    }
                    PlaybackHealthMonitor.Decision.HEALTHY -> Unit
                }
            }
            delay(250)
        }
    }

    // 实时瞬时下载网速采样 (缓冲期间每 500ms 采样一次并格式化显示)
    LaunchedEffect(isBuffering) {
        if (!isBuffering) {
            networkSpeedText = "0 KB/s"
            return@LaunchedEffect
        }
        var lastRx = TrafficStats.getUidRxBytes(Process.myUid())
        if (lastRx < 0) lastRx = TrafficStats.getTotalRxBytes()
        var lastTime = SystemClock.elapsedRealtime()

        while (isBuffering) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            var curRx = TrafficStats.getUidRxBytes(Process.myUid())
            if (curRx < 0) curRx = TrafficStats.getTotalRxBytes()
            val bytesDiff = (curRx - lastRx).coerceAtLeast(0L)
            val timeDiffSec = (now - lastTime).coerceAtLeast(1L) / 1000f
            if (timeDiffSec > 0f) {
                val speedBytes = bytesDiff / timeDiffSec
                networkSpeedText = when {
                    speedBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", speedBytes / (1024 * 1024))
                    speedBytes >= 1024 -> String.format(Locale.US, "%.0f KB/s", speedBytes / 1024)
                    else -> String.format(Locale.US, "%.0f B/s", speedBytes)
                }
            }
            lastRx = curRx
            lastTime = now
        }
    }

    // 控制器自动隐藏逻辑：播放中且无抽屉展开时，4.5 秒后自动收起
    LaunchedEffect(showControls, isPlaying, showPlaylist, showDanmakuSettings, showMediaInfo, showTrackSettings, showMoreSettings, controlInteractionTick) {
        val anyDrawerOpen = showPlaylist || showDanmakuSettings || showMediaInfo || showTrackSettings || showMoreSettings
        if (showControls && isPlaying && !anyDrawerOpen) {
            delay(4500)
            showControls = false
        }
    }

    // AnimatedVisibility 首帧尚未把子节点放进焦点树。跨帧重试，确保 OK
    // 唤出控制条后焦点落到播放按钮，而不是留在全屏输入层。
    LaunchedEffect(showControls) {
        repeat(3) {
            withFrameNanos { }
            val focused = runCatching {
                if (showControls) playPauseFocusRequester.requestFocus() else rootFocusRequester.requestFocus()
            }.isSuccess
            if (focused) return@LaunchedEffect
        }
    }

    // 动态时钟 (顶部右侧 20:36)
    var currentTimeString by remember { mutableStateOf("20:36") }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = timeFormat.format(Date())
            delay(20000)
        }
    }

    // 3 倍速快退的平滑步进循环
    LaunchedEffect(isFastRewinding3x) {
        if (!isFastRewinding3x) return@LaunchedEffect
        while (isFastRewinding3x) {
            val target = (enginePosition() - 300L).coerceAtLeast(0L)
            engineSeekTo(target)
            currentPosition = target
            if (target <= 0L) break
            delay(100)
        }
    }

    // 遥控器与键盘事件核心桥接：未打开控制栏时，OK/回车打开控制，左右键短按 5s 步进，长按 3 倍速
    val showControlsState = rememberUpdatedState(showControls)
    DisposableEffect(Unit) {
        PlayerKeyBridge.onKey = { event ->
            val isControlsOpen = showControlsState.value
            val isDown = event.action == android.view.KeyEvent.ACTION_DOWN
            val isUp = event.action == android.view.KeyEvent.ACTION_UP

            if (isControlsOpen && isDown) controlInteractionTick++

            if (!isControlsOpen) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (isDown) {
                            showControls = true
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_MENU -> {
                        if (isDown) {
                            showControls = true
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isDown) {
                            if (event.repeatCount == 0) {
                                longPressDetectJob?.cancel()
                                longPressDetectJob = coroutineScope.launch {
                                    delay(280)
                                    isFastForwarding3x = true
                                    hideSpeedOverlayJob?.cancel()
                                    speedOverlayText = "3.0X 快进中 ▶▶▶"
                                    engineSetSpeed(3.0f)
                                }
                            } else if (event.repeatCount > 0 && !isFastForwarding3x) {
                                isFastForwarding3x = true
                                longPressDetectJob?.cancel()
                                hideSpeedOverlayJob?.cancel()
                                speedOverlayText = "3.0X 快进中 ▶▶▶"
                                engineSetSpeed(3.0f)
                            }
                        } else if (isUp) {
                            longPressDetectJob?.cancel()
                            if (isFastForwarding3x) {
                                isFastForwarding3x = false
                                engineSetSpeed(1.0f)
                                speedOverlayText = null
                            } else {
                                val target = (enginePosition() + 5000L).coerceAtMost(duration)
                                engineSeekTo(target)
                                currentPosition = target
                                showTemporaryToast("快进 +5s")
                            }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isDown) {
                            if (event.repeatCount == 0) {
                                longPressDetectJob?.cancel()
                                longPressDetectJob = coroutineScope.launch {
                                    delay(280)
                                    isFastRewinding3x = true
                                    hideSpeedOverlayJob?.cancel()
                                    speedOverlayText = "3.0X 快退中 ◀◀◀"
                                }
                            } else if (event.repeatCount > 0 && !isFastRewinding3x) {
                                isFastRewinding3x = true
                                longPressDetectJob?.cancel()
                                hideSpeedOverlayJob?.cancel()
                                speedOverlayText = "3.0X 快退中 ◀◀◀"
                            }
                        } else if (isUp) {
                            longPressDetectJob?.cancel()
                            if (isFastRewinding3x) {
                                isFastRewinding3x = false
                                speedOverlayText = null
                                enginePlay()
                            } else {
                                val target = (enginePosition() - 5000L).coerceAtLeast(0L)
                                engineSeekTo(target)
                                currentPosition = target
                                showTemporaryToast("快退 -5s")
                            }
                        }
                        true
                    }
                    else -> false
                }
            } else {
                // 方向键与确认键交给 Compose。媒体键、键盘空格及手柄肩键在
                // Activity 层统一处理，避免不同电视/手柄映射差异。
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_SPACE,
                    android.view.KeyEvent.KEYCODE_BUTTON_START -> {
                        if (isDown && event.repeatCount == 0) {
                            if (engineIsPlaying()) enginePause() else enginePlay()
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        if (isDown) enginePlay()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        if (isDown) enginePause()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                        if (isDown && event.repeatCount == 0) {
                            val target = (enginePosition() - 10_000L).coerceAtLeast(0L)
                            engineSeekTo(target)
                            currentPosition = target
                            showTemporaryToast("快退 -10s")
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                        if (isDown && event.repeatCount == 0) {
                            val target = (enginePosition() + 10_000L).coerceAtMost(duration)
                            engineSeekTo(target)
                            currentPosition = target
                            showTemporaryToast("快进 +10s")
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        onDispose {
            PlayerKeyBridge.onKey = null
            engineSetSpeed(1.0f)
        }
    }

    // 返回键逐层关闭：抽屉 -> 控制条 -> 播放器，避免误退出。
    BackHandler {
        when {
            showPlaylist || showDanmakuSettings || showMediaInfo || showTrackSettings || showMoreSettings -> {
                showPlaylist = false
                showDanmakuSettings = false
                showMediaInfo = false
                showTrackSettings = false
                showMoreSettings = false
            }
            showControls -> showControls = false
            else -> onClose()
        }
    }

    // 切换剧集
    fun playEpisode(index: Int) {
        if (index in episodesList.indices) {
            currentEpisodeIndex = index
            val ep = episodesList[index]
            val url = ep.streamUrl
            if (url.isBlank()) {
                errorMessage = "该集没有可用的播放地址"
                return
            }
            Log.i(TAG, "切换播放第 ${ep.index} 集: $url")
            if (useMpv) mpvController?.load(url, ep.startPositionMs) else {
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // ==========================================
        // 1. 最底层：视频与海报背景 (放入 HazeSource 供模糊采样)
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (RenderPerformance.blur31) Modifier.hazeSource(playerHaze) else Modifier
                )
        ) {
            // 真实背景图（媒体自身的 backdrop）。没有就保持纯黑，绝不用内置演示图冒充视频。
            val backdropUrl = currentMediaInfo.backdropUrl
            val backdrop = if (backdropUrl.isNotBlank()) rememberPosterImage(backdropUrl) else null
            if (backdrop != null) {
                Image(backdrop, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B)))
            }

            // 真实视频播放渲染层 (通过 XML 布局绑定 TextureView，确保在 View 树内可被 Haze 采样模糊)
            AndroidView(
                factory = { ctx ->
                    if (useMpv) {
                        return@AndroidView requireNotNull(mpvController).createSurfaceView(ctx).apply {
                            keepScreenOn = true
                        }
                    }
                    val view = LayoutInflater.from(ctx).inflate(R.layout.tv_player_view, null, false) as PlayerView
                    view.apply {
                        this.player = exoPlayer
                        useController = false
                        keepScreenOn = true
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                )
                            )
                            setFractionalTextSize(0.052f)
                            setBottomPaddingFraction(0.08f)
                        }
                        resizeMode = when (currentResizeMode) {
                            VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    }
                    Log.i(TAG, "PlayerView 成功绑定 ExoPlayer")
                    view
                },
                update = { view ->
                    if (view is PlayerView) {
                        if (view.player != exoPlayer) view.player = exoPlayer
                        view.resizeMode = when (currentResizeMode) {
                            VideoResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            VideoResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            VideoResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                    } else if (useMpv) {
                        mpvController?.setAspect(currentResizeMode)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ==========================================
        // 1.5 全屏输入层 (在视频之上、所有面板之下)
        // 保证：控制器收起后遥控器任意键 / 点击屏幕都能重新唤出控件，并始终持有焦点。
        // ==========================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = true }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && !showControls) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter,
                            Key.DirectionUp, Key.DirectionDown -> {
                                showControls = true
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        )

        // ==========================================
        // 2. 弹幕渲染层 (Danmaku Overlay，时间轴同步)
        // ==========================================
        DanmakuOverlay(
            currentPositionMs = currentPosition,
            isPlaying = isPlaying,
            danmakuList = runtimeDanmakus,
            settings = danmakuSettings
        )

        // ==========================================
        // 3. UI 浮起时的轻微压暗遮罩 (不大幅模糊视频本身)
        // ==========================================
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
            )
        }

        // ==========================================
        // 4. 顶部媒体信息与极简状态区 (严格按参考图加大字体)
        // ==========================================
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = screenWidth * 0.038f,
                        vertical = screenHeight * 0.045f
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 顶部左侧：大标题媒体信息 (无任何软件品牌名)
                Column {
                    Text(
                        text = "${currentMediaInfo.title}  ${currentMediaInfo.seasonEpisodeText}",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = currentMediaInfo.metaSubtitle,
                        color = Color(0xCCFFFFFF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // 顶部右侧：极简状态区 [💬] [ılı] 4K HDR 20:36
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val statusCapsuleBg = Color(0x661A1D2D)
                    val canBlur = RenderPerformance.blur31

                    // 弹幕状态胶囊
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .then(
                                if (canBlur) {
                                    Modifier.hazeEffect(playerHaze) {
                                        blurRadius = 20.dp
                                        tints = listOf(HazeTint(statusCapsuleBg))
                                        noiseFactor = 0f
                                    inputScale = HazeInputScale.Fixed(.4f)
                                    }
                                } else {
                                    Modifier.background(statusCapsuleBg)
                                }
                            )
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            drawTvIcon(
                                TvIconType.DANMAKU,
                                if (danmakuSettings.enabled) Color.White else Color(0x66FFFFFF)
                            )
                        }
                    }

                    // 音轨状态胶囊
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .then(
                                if (canBlur) {
                                    Modifier.hazeEffect(playerHaze) {
                                        blurRadius = 20.dp
                                        tints = listOf(HazeTint(statusCapsuleBg))
                                        noiseFactor = 0f
                                    inputScale = HazeInputScale.Fixed(.4f)
                                    }
                                } else {
                                    Modifier.background(statusCapsuleBg)
                                }
                            )
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            drawTvIcon(TvIconType.AUDIO_TRACK, Color.White)
                        }
                    }

                    // 4K HDR
                    Text(
                        text = currentMediaInfo.qualityTag,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(Modifier.width(4.dp))

                    // 当前系统时间
                    Text(
                        text = currentTimeString,
                        color = Color(0xEEFFFFFF),
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ==========================================
        // 5. 左侧：播放列表抽屉 (占宽约 19.5%，高约 61%，背景模糊磨砂玻璃)
        // ==========================================
        AnimatedVisibility(
            visible = showControls && showPlaylist,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = screenWidth * 0.038f,
                    top = screenHeight * 0.13f
                )
        ) {
            EpisodePlaylistDrawer(
                episodes = episodesList,
                currentEpisodeIndex = currentEpisodeIndex,
                onSelectEpisode = { idx -> playEpisode(idx) },
                hazeState = playerHaze,
                panelWidth = screenWidth * 0.195f,
                panelHeight = screenHeight * 0.61f
            )
        }

        // ==========================================
        // 6. 右侧：弹幕设置抽屉 (占宽约 22.5%，高约 54%，背景模糊磨砂玻璃)
        // ==========================================
        AnimatedVisibility(
            visible = showControls && showDanmakuSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = screenWidth * 0.038f,
                    top = screenHeight * 0.14f
                )
        ) {
            DanmakuSettingsDrawer(
                settings = danmakuSettings,
                onSettingsChanged = { danmakuSettings = it },
                onSendDanmaku = {
                    val newDanmaku = TvDanmakuItem(
                        id = UUID.randomUUID().toString(),
                        timeMs = currentPosition + 400L,
                        text = "前方高能！这画质太棒了",
                        color = Color(0xFF64B5F6)
                    )
                    runtimeDanmakus = runtimeDanmakus + newDanmaku
                    showTemporaryToast("弹幕已发送")
                },
                hazeState = playerHaze,
                panelWidth = screenWidth * 0.225f,
                panelHeight = screenHeight * 0.54f
            )
        }

        // ==========================================
        // 7. 附加抽屉：媒体详情 / 音轨字幕
        // ==========================================
        AnimatedVisibility(
            visible = showControls && showMediaInfo,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = screenWidth * 0.038f,
                    top = screenHeight * 0.13f
                )
        ) {
            MediaInfoDrawer(playbackInfo = currentMediaInfo, hazeState = playerHaze)
        }

        AnimatedVisibility(
            visible = showControls && showTrackSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = screenWidth * 0.038f,
                    top = screenHeight * 0.14f
                )
        ) {
            AudioSubtitleDrawer(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                onSelectAudioTrack = { idx ->
                    val track = audioTracks.getOrNull(idx)
                    val group = track?.let { exoPlayer.currentTracks.groups.getOrNull(it.groupIndex) }
                    if (track != null && group != null && track.trackIndex in 0 until group.length) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex)))
                            .build()
                    }
                },
                onSelectSubtitleTrack = { idx ->
                    val track = subtitleTracks.getOrNull(idx)
                    if (track?.groupIndex == -1) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    } else {
                        val group = track?.let { exoPlayer.currentTracks.groups.getOrNull(it.groupIndex) }
                        if (track != null && group != null && track.trackIndex in 0 until group.length) {
                            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex)))
                                .build()
                        }
                    }
                },
                hazeState = playerHaze
            )
        }

        AnimatedVisibility(
            visible = showControls && showMoreSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopStart).padding(start = screenWidth * 0.038f, top = screenHeight * 0.13f)
        ) {
            PlayerMoreDrawer(
                isMpv = useMpv,
                speed = engineSpeed(),
                hardwareDecoding = mpvHardwareDecoding,
                preserveAssStyles = preserveAssStyles,
                subtitleDelaySeconds = subtitleDelaySeconds,
                onCycleSpeed = {
                    val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                    engineSetSpeed(speeds.firstOrNull { it > engineSpeed() + 0.01f } ?: 1.0f)
                },
                onToggleHardwareDecoding = {
                    mpvHardwareDecoding = !mpvHardwareDecoding
                    mpvController?.setHardwareDecoding(mpvHardwareDecoding)
                },
                onToggleAssStyles = {
                    preserveAssStyles = !preserveAssStyles
                    mpvController?.setPreserveAssStyles(preserveAssStyles)
                },
                onSubtitleDelay = {
                    subtitleDelaySeconds = it.coerceIn(-10.0, 10.0)
                    mpvController?.setSubtitleDelay(subtitleDelaySeconds)
                },
                hazeState = playerHaze
            )
        }

        // ==========================================
        // 8. 播放错误提示浮层 (居中，带清晰排查信息)
        // ==========================================
        // ==========================================
        // 8. 加载状态与实时网速浮层 (居中，纯净无边框磨砂玻璃 + 实时网速 + 精确进度)
        // ==========================================
        if (errorMessage == null && isBuffering) {
            val bufferedPct = if (duration > 0) ((bufferedPosition * 100f / duration).toInt()).coerceIn(0, 100) else 0
            val bufferedSec = bufferedPosition / 1000
            val totalSec = duration / 1000
            val loadingDetail = if (duration > 0)
                "已缓冲 $bufferedSec / $totalSec 秒 ($bufferedPct%)"
            else "正在连接媒体服务器并预加载…"

            val cardBg = Color(0x66131622) // 40% 不透明深灰黑
            val canBlur = RenderPerformance.blur31

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(ContinuousCornerShape(26.dp))
                    .then(
                        if (canBlur) {
                            Modifier.hazeEffect(playerHaze) {
                                blurRadius = 36.dp
                                tints = listOf(HazeTint(cardBg))
                                noiseFactor = 0f
                            inputScale = HazeInputScale.Fixed(.4f)
                            }
                        } else {
                            Modifier.background(cardBg)
                        }
                    )
                    .padding(horizontal = 46.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF4A89FF),
                    strokeWidth = 5.dp,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(Modifier.height(18.dp))

                // 醒目的实时下载网速
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "正在缓冲",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = networkSpeedText,
                        color = Color(0xFF6FA5FF),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 缓冲进度描述
                Text(
                    text = loadingDetail,
                    color = Color(0xCCFFFFFF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )

                // 如果有总时长，展示一条平滑缓冲进度条
                if (duration > 0) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x38FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (bufferedPct / 100f).coerceIn(0f, 1f))
                                .background(Color(0xFF4A89FF))
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = activePlayUrl.substringAfter("://").substringBefore("/"),
                    color = Color(0x88FFFFFF),
                    fontSize = 14.sp
                )
            }
        }

        if (errorMessage != null) {
            val errorBg = Color(0x661E1E26)
            val canBlur = RenderPerformance.blur31
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(ContinuousCornerShape(26.dp))
                    .then(
                        if (canBlur) {
                            Modifier.hazeEffect(playerHaze) {
                                blurRadius = 36.dp
                                tints = listOf(HazeTint(errorBg))
                                noiseFactor = 0f
                            inputScale = HazeInputScale.Fixed(.4f)
                            }
                        } else {
                            Modifier.background(errorBg)
                        }
                    )
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放遇到问题", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(errorMessage ?: "", color = Color(0xFFFF8A80), fontSize = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(30.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvCapsuleSelector(
                        text = "重试",
                        hazeState = playerHaze,
                        onClick = {
                            errorMessage = null
                            isBuffering = true
                            bufferingSince = System.currentTimeMillis()
                            if (activePlayUrl.isNotBlank()) {
                                if (useMpv) mpvController?.load(activePlayUrl, currentPosition) else {
                                    exoPlayer.setMediaItem(MediaItem.fromUri(activePlayUrl))
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            }
                        }
                    )
                    TvCapsuleSelector(
                        text = if (useMpv) "已使用 mpv 内核" else "切换到 mpv 内核",
                        hazeState = playerHaze,
                        onClick = {
                            if (!useMpv && MpvPluginRuntime.isEnabled(context)) {
                                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                                exoPlayer.pause()
                                activeEngine = DesktopPreferences.PlaybackEngine.MPV
                                errorMessage = null
                            } else if (!MpvPluginRuntime.isEnabled(context)) {
                                errorMessage = "MPV 插件尚未安装或启用，请前往设置 → 插件"
                            }
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("按返回键退出播放器", color = Color(0x99FFFFFF), fontSize = 16.sp)
            }
        }

        // ==========================================
        // 8.5 快进快退 / 3倍速播放 屏幕中央轻量磨砂提示浮层
        // ==========================================
        AnimatedVisibility(
            visible = speedOverlayText != null,
            enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.88f),
            exit = fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = screenHeight * 0.08f)
        ) {
            val toastBg = Color(0x66131622)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(ContinuousCornerShape(22.dp))
                    .then(
                        if (RenderPerformance.blur31) {
                            Modifier.hazeEffect(playerHaze) {
                                blurRadius = 30.dp
                                tints = listOf(HazeTint(toastBg))
                                noiseFactor = 0f
                            inputScale = HazeInputScale.Fixed(.4f)
                            }
                        } else {
                            Modifier.background(toastBg)
                        }
                    )
                    .padding(horizontal = 32.dp, vertical = 18.dp)
            ) {
                Text(
                    text = speedOverlayText ?: "",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // ==========================================
        // 9. 底部核心大型悬浮玻璃控制器 (宽 84%，高 18%，大圆角 36dp，实时背景模糊)
        // ==========================================
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = screenHeight * 0.045f)
        ) {
            TvGlassCard(
                cornerRadius = 36.dp,
                hazeState = playerHaze,
                modifier = Modifier
                    .width(screenWidth * 0.84f)
                    .height(screenHeight * 0.18f)
                    .focusGroup()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 18.dp, bottom = 18.dp, start = 24.dp, end = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 上半部分：粗度显著放大的时间轴与进度条
                    TvTimelineScrubber(
                        currentPositionMs = currentPosition,
                        durationMs = duration,
                        bufferedPositionMs = bufferedPosition,
                        onSeek = { targetMs ->
                            Log.i(TAG, "用户 Seek 至: $targetMs ms")
                            engineSeekTo(targetMs)
                            currentPosition = targetMs
                        }
                    )

                    // 下半部分：功能按钮行 (左侧功能 + 中间主控 + 右侧功能)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧功能按钮组：[••• 更多]  [ⓘ 媒体信息]  [≡ 播放列表]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvCircleIconButton(
                                iconType = TvIconType.MORE,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = "更多设置",
                                hazeState = playerHaze,
                                onClick = {
                                    showMoreSettings = !showMoreSettings
                                    if (showMoreSettings) {
                                        showPlaylist = false
                                        showMediaInfo = false
                                        showDanmakuSettings = false
                                        showTrackSettings = false
                                    }
                                }
                            )
                            TvCircleIconButton(
                                iconType = TvIconType.INFO,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = "媒体信息",
                                hazeState = playerHaze,
                                onClick = {
                                    showMediaInfo = !showMediaInfo
                                    if (showMediaInfo) {
                                        showMoreSettings = false
                                        showPlaylist = false
                                        showDanmakuSettings = false
                                        showTrackSettings = false
                                    }
                                }
                            )
                            TvCircleIconButton(
                                iconType = TvIconType.PLAYLIST,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = "播放列表",
                                hazeState = playerHaze,
                                onClick = {
                                    showPlaylist = !showPlaylist
                                    if (showPlaylist) {
                                        showMoreSettings = false
                                        showMediaInfo = false
                                        showDanmakuSettings = false
                                        showTrackSettings = false
                                    }
                                }
                            )
                        }

                        // 中间主播放控制组：[↺10] [⏮] [ ▶/❚❚ 超大主播放键 (80dp) ] [⏭] [10↻]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 快退 10 秒
                            TvCircleIconButton(
                                iconType = TvIconType.REWIND_10,
                                size = 64.dp,
                                iconSize = 32.dp,
                                tooltip = "快退10秒",
                                hazeState = playerHaze,
                                onClick = {
                                    val target = (enginePosition() - 10_000L).coerceAtLeast(0L)
                                    engineSeekTo(target)
                                    currentPosition = target
                                }
                            )

                            // 上一集
                            TvCircleIconButton(
                                iconType = TvIconType.PREVIOUS_EPISODE,
                                size = 64.dp,
                                iconSize = 32.dp,
                                tooltip = "上一集",
                                hazeState = playerHaze,
                                onClick = {
                                    if (currentEpisodeIndex > 0) {
                                        playEpisode(currentEpisodeIndex - 1)
                                    }
                                }
                            )

                            // 主播放 / 暂停按钮 (直径 80dp，普通按钮的 1.45 倍，发光微光高亮，默认聚焦)
                            TvCircleIconButton(
                                iconType = if (isPlaying) TvIconType.PAUSE else TvIconType.PLAY,
                                size = 88.dp,
                                iconSize = 42.dp,
                                isPrimary = true,
                                tooltip = if (isPlaying) "暂停" else "播放",
                                hazeState = playerHaze,
                                modifier = Modifier.focusRequester(playPauseFocusRequester),
                                onClick = {
                                    if (engineIsPlaying()) {
                                        Log.i(TAG, "暂停播放")
                                        enginePause()
                                    } else {
                                        Log.i(TAG, "继续播放")
                                        enginePlay()
                                    }
                                }
                            )

                            // 下一集
                            TvCircleIconButton(
                                iconType = TvIconType.NEXT_EPISODE,
                                size = 64.dp,
                                iconSize = 32.dp,
                                tooltip = "下一集",
                                hazeState = playerHaze,
                                onClick = {
                                    if (currentEpisodeIndex < episodesList.lastIndex) {
                                        playEpisode(currentEpisodeIndex + 1)
                                    }
                                }
                            )

                            // 快进 10 秒
                            TvCircleIconButton(
                                iconType = TvIconType.FORWARD_10,
                                size = 64.dp,
                                iconSize = 32.dp,
                                tooltip = "快进10秒",
                                hazeState = playerHaze,
                                onClick = {
                                    val target = (enginePosition() + 10_000L).coerceAtMost(duration)
                                    engineSeekTo(target)
                                    currentPosition = target
                                }
                            )
                        }

                        // 右侧功能按钮组：[💬 弹幕]  [ılı 音轨/字幕]  [⧉ 小窗]  [⛶ 比例]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 弹幕设置
                            TvCircleIconButton(
                                iconType = TvIconType.DANMAKU,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = "弹幕设置",
                                hazeState = playerHaze,
                                onClick = {
                                    showDanmakuSettings = !showDanmakuSettings
                                    if (showDanmakuSettings) {
                                        showMoreSettings = false
                                        showPlaylist = false
                                        showMediaInfo = false
                                        showTrackSettings = false
                                    }
                                }
                            )

                            // 音轨与字幕
                            TvCircleIconButton(
                                iconType = TvIconType.AUDIO_TRACK,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = "音轨与字幕",
                                hazeState = playerHaze,
                                onClick = {
                                    showTrackSettings = !showTrackSettings
                                    if (showTrackSettings) {
                                        showMoreSettings = false
                                        showPlaylist = false
                                        showMediaInfo = false
                                        showDanmakuSettings = false
                                    }
                                }
                            )

                            // 小窗播放 (Picture-in-Picture)
                            if (isPipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                TvCircleIconButton(
                                    iconType = TvIconType.PIP,
                                    size = 62.dp,
                                    iconSize = 30.dp,
                                    tooltip = "画中画",
                                    hazeState = playerHaze,
                                    onClick = {
                                        showControls = false
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity?.enterPictureInPictureMode(params)
                                    }
                                )
                            }

                            // 全屏 / 画面比例切换 (FIT -> ZOOM -> FILL)
                            TvCircleIconButton(
                                iconType = TvIconType.ASPECT_RATIO,
                                size = 62.dp,
                                iconSize = 30.dp,
                                tooltip = currentResizeMode.displayName,
                                hazeState = playerHaze,
                                onClick = {
                                    currentResizeMode = when (currentResizeMode) {
                                        VideoResizeMode.FIT -> VideoResizeMode.ZOOM
                                        VideoResizeMode.ZOOM -> VideoResizeMode.FILL
                                        VideoResizeMode.FILL -> VideoResizeMode.FIT
                                    }
                                    Log.i(TAG, "切换画面显示比例为: ${currentResizeMode.displayName}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 调起外部播放器工具方法
 */
fun createMpvIntent(info: TvPlaybackInfo, positionMs: Long): Intent =
    Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(info.streamUrl), "video/any")
        setPackage("is.xyz.mpv")
        putExtra("title", info.title)
        putExtra("position", positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        if (info.externalSubtitleUrls.isNotEmpty()) {
            val subtitleUris = info.externalSubtitleUrls.map(Uri::parse).toTypedArray()
            putExtra("subs", subtitleUris)
            putExtra("subs.enable", subtitleUris)
        }
    }
