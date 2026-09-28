package com.limi.tvdesktop.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

/**
 * 左侧：播放列表抽屉面板 (严格按参考图尺寸：宽约 19%，高约 61%，大圆角，磨砂玻璃)
 */
@Composable
fun EpisodePlaylistDrawer(
    episodes: List<TvEpisodeItem>,
    currentEpisodeIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    panelWidth: Dp = 290.dp,
    panelHeight: Dp = 480.dp
) {
    TvGlassCard(
        cornerRadius = 28.dp,
        hazeState = hazeState,
        modifier = modifier
            .width(panelWidth)
            .height(panelHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp, bottom = 20.dp, start = 18.dp, end = 18.dp)
        ) {
            // 标题栏：[≡] 播放列表 (大字号 20sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 18.dp)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawTvIcon(TvIconType.PLAYLIST, Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "播放列表",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // 剧集滚动列表
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentEpisodeIndex - 1).coerceAtLeast(0)
            )

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(episodes) { index, episode ->
                    val isCurrent = index == currentEpisodeIndex
                    EpisodeListItem(
                        episode = episode,
                        isCurrentPlaying = isCurrent,
                        onClick = { onSelectEpisode(index) }
                    )
                }
            }
        }
    }
}

/**
 * 剧集单项组件 (大幅度 16:9 缩略图 + 集数名称 + TV 焦点与正在播放蓝灰微光胶囊高亮)
 */
@Composable
private fun EpisodeListItem(
    episode: TvEpisodeItem,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(140),
        label = "ep_scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isCurrentPlaying -> Color(0xAA4C74B8)
            isCurrentPlaying -> Color(0x8C33527F) // 参考图当前集：深蓝灰高亮微光胶囊
            isFocused -> Color(0x4DFFFFFF)
            else -> Color(0x18FFFFFF)
        },
        animationSpec = tween(140),
        label = "ep_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFFB0CEFF)
            isCurrentPlaying -> Color(0x996DA0ED)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "ep_border"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .height(72.dp) // TV 大屏增加高度
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // 正在播放小图标
        if (isCurrentPlaying) {
            Canvas(modifier = Modifier.size(16.dp)) {
                drawTvIcon(TvIconType.PLAY, Color.White)
            }
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(4.dp))
        }

        // 16:9 缩略图 (宽 84dp, 高 50dp)
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF222633)),
            contentAlignment = Alignment.Center
        ) {
            if (episode.thumbResId != 0) {
                Image(
                    painter = painterResource(id = episode.thumbResId),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "${episode.index}",
                    color = Color(0x80FFFFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // 集数标题与副标题
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = episode.title,
                color = if (isCurrentPlaying || isFocused) Color.White else Color(0xEEFFFFFF),
                fontSize = 17.sp,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.subTitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = episode.subTitle,
                    color = if (isCurrentPlaying) Color(0xD9C6DDFF) else Color(0x99FFFFFF),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 右侧：弹幕设置抽屉面板 (严格按参考图尺寸：宽约 22%，高约 53%，大圆角，磨砂玻璃)
 */
@Composable
fun DanmakuSettingsDrawer(
    settings: TvDanmakuSettings,
    onSettingsChanged: (TvDanmakuSettings) -> Unit,
    onSendDanmaku: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    panelWidth: Dp = 340.dp,
    panelHeight: Dp = 440.dp
) {
    TvGlassCard(
        cornerRadius = 28.dp,
        hazeState = hazeState,
        modifier = modifier
            .width(panelWidth)
            .height(panelHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部标题：[💬] 弹幕设置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawTvIcon(TvIconType.DANMAKU, Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "弹幕设置",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            // 1. 开启弹幕 (Switch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开启弹幕", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                TvSwitch(
                    checked = settings.enabled,
                    onCheckedChange = { onSettingsChanged(settings.copy(enabled = it)) }
                )
            }

            // 2. 显示区域 (Capsule Selector)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("显示区域", color = Color(0xEEFFFFFF), fontSize = 17.sp)
                val areaText = "${settings.displayArea.displayName} >"
                TvCapsuleSelector(
                    text = areaText,
                    onClick = {
                        val next = when (settings.displayArea) {
                            DanmakuArea.FULL -> DanmakuArea.HALF
                            DanmakuArea.HALF -> DanmakuArea.TOP
                            DanmakuArea.TOP -> DanmakuArea.BOTTOM
                            DanmakuArea.BOTTOM -> DanmakuArea.FULL
                        }
                        onSettingsChanged(settings.copy(displayArea = next))
                    }
                )
            }

            // 3. 不透明度 (Slider 70%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("不透明度", color = Color(0xEEFFFFFF), fontSize = 17.sp)
                TvSlider(
                    value = settings.opacity,
                    valueText = "${(settings.opacity * 100).toInt()}%",
                    onValueChange = { onSettingsChanged(settings.copy(opacity = it)) }
                )
            }

            // 4. 字体大小 (Slider 100%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("字体大小", color = Color(0xEEFFFFFF), fontSize = 17.sp)
                val fontRatio = ((settings.fontSizePercent - 60) / 80f).coerceIn(0f, 1f)
                TvSlider(
                    value = fontRatio,
                    valueText = "${settings.fontSizePercent}%",
                    onValueChange = {
                        val percent = (60 + (it * 80)).toInt()
                        onSettingsChanged(settings.copy(fontSizePercent = percent))
                    }
                )
            }

            // 5. 移动速度 (Slider 正常)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("移动速度", color = Color(0xEEFFFFFF), fontSize = 17.sp)
                val speedRatio = when (settings.speed) {
                    DanmakuSpeed.SLOW -> 0.2f
                    DanmakuSpeed.NORMAL -> 0.5f
                    DanmakuSpeed.FAST -> 0.9f
                }
                TvSlider(
                    value = speedRatio,
                    valueText = settings.speed.displayName,
                    onValueChange = {
                        val newSpeed = when {
                            it < 0.35f -> DanmakuSpeed.SLOW
                            it < 0.75f -> DanmakuSpeed.NORMAL
                            else -> DanmakuSpeed.FAST
                        }
                        onSettingsChanged(settings.copy(speed = newSpeed))
                    }
                )
            }

            // 6. 屏蔽类型 (Capsule Selector)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("屏蔽类型", color = Color(0xEEFFFFFF), fontSize = 17.sp)
                val filterText = "${settings.filterType.displayName} >"
                TvCapsuleSelector(
                    text = filterText,
                    onClick = {
                        val next = when (settings.filterType) {
                            DanmakuFilter.NONE -> DanmakuFilter.COLOR
                            DanmakuFilter.COLOR -> DanmakuFilter.TOP_BOTTOM
                            DanmakuFilter.TOP_BOTTOM -> DanmakuFilter.REPEAT
                            DanmakuFilter.REPEAT -> DanmakuFilter.NONE
                        }
                        onSettingsChanged(settings.copy(filterType = next))
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // 底部按钮：发送弹幕
            SendDanmakuButton(onClick = onSendDanmaku)
        }
    }
}

/**
 * 底部发送弹幕按钮
 */
@Composable
private fun SendDanmakuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(140),
        label = "send_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0x664870B0) else Color(0x33FFFFFF))
            .border(
                1.5.dp,
                if (isFocused) Color(0xFFB0CEFF) else Color(0x28FFFFFF),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                drawTvIcon(TvIconType.SEND_DANMAKU, Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "发送弹幕",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * 轻量级侧边媒体详情面板 (点击 ⓘ 展开，不跳转完整页面)
 */
@Composable
fun MediaInfoDrawer(
    playbackInfo: TvPlaybackInfo,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    TvGlassCard(
        cornerRadius = 28.dp,
        hazeState = hazeState,
        modifier = modifier
            .width(380.dp)
            .height(480.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawTvIcon(TvIconType.INFO, Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "媒体详情",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "${playbackInfo.title}  ${playbackInfo.seasonEpisodeText}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = playbackInfo.metaSubtitle,
                color = Color(0xCCFFFFFF),
                fontSize = 15.sp
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x384E7BCC))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = playbackInfo.qualityTag,
                    color = Color(0xFFD2E3FC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "简介",
                color = Color(0x99FFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = playbackInfo.overview,
                color = Color(0xEEFFFFFF),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            // 底部技术规格概览
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("解码格式", color = Color(0x99FFFFFF), fontSize = 14.sp)
                    Text("HEVC Main10 / H.264", color = Color.White, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("色彩空间", color = Color(0x99FFFFFF), fontSize = 14.sp)
                    Text("BT.2020 / HDR10", color = Color.White, fontSize = 14.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("主音频", color = Color(0x99FFFFFF), fontSize = 14.sp)
                    Text("EAC3 5.1 / AAC", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * 音轨与字幕切换抽屉面板
 */
@Composable
fun AudioSubtitleDrawer(
    audioTracks: List<TvAudioTrack>,
    subtitleTracks: List<TvSubtitleTrack>,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    TvGlassCard(
        cornerRadius = 28.dp,
        hazeState = hazeState,
        modifier = modifier
            .width(360.dp)
            .height(480.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawTvIcon(TvIconType.AUDIO_TRACK, Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "音轨与字幕",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text("音轨选择", color = Color(0x99FFFFFF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
            ) {
                if (audioTracks.isEmpty()) {
                    item {
                        Text("当前媒体没有可切换音轨", color = Color(0x99FFFFFF), fontSize = 14.sp)
                    }
                }
                itemsIndexed(audioTracks) { index, track ->
                    TrackSelectionItem(
                        name = track.name,
                        isSelected = track.isSelected,
                        onClick = { onSelectAudioTrack(index) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text("字幕选择", color = Color(0x99FFFFFF), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (subtitleTracks.isEmpty()) {
                    item {
                        Text("当前媒体没有可用字幕", color = Color(0x99FFFFFF), fontSize = 14.sp)
                    }
                }
                itemsIndexed(subtitleTracks) { index, track ->
                    TrackSelectionItem(
                        name = track.name,
                        isSelected = track.isSelected,
                        onClick = { onSelectSubtitleTrack(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerMoreDrawer(
    isMpv: Boolean,
    speed: Float,
    hardwareDecoding: Boolean,
    preserveAssStyles: Boolean,
    subtitleDelaySeconds: Double,
    onCycleSpeed: () -> Unit,
    onToggleHardwareDecoding: () -> Unit,
    onToggleAssStyles: () -> Unit,
    onSubtitleDelay: (Double) -> Unit,
    hazeState: HazeState? = null
) {
    TvGlassCard(cornerRadius = 28.dp, hazeState = hazeState, modifier = Modifier.width(390.dp).height(500.dp)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("更多播放设置", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isMpv) "当前内核：libmpv（内嵌）" else "当前内核：Media3 ExoPlayer",
                color = Color(0xFF78AFFF), fontSize = 14.sp
            )
            TrackSelectionItem("播放速度  ${speed}x", false, onCycleSpeed)
            if (isMpv) {
                TrackSelectionItem("硬件解码  ${if (hardwareDecoding) "开启" else "关闭"}", hardwareDecoding, onToggleHardwareDecoding)
                TrackSelectionItem("保留 ASS 原始特效  ${if (preserveAssStyles) "开启" else "关闭"}", preserveAssStyles, onToggleAssStyles)
                TrackSelectionItem("字幕提前 0.1 秒", false) { onSubtitleDelay(subtitleDelaySeconds - 0.1) }
                TrackSelectionItem("字幕延后 0.1 秒", false) { onSubtitleDelay(subtitleDelaySeconds + 0.1) }
                TrackSelectionItem("字幕延迟归零  ${"%.1f".format(subtitleDelaySeconds)}s", subtitleDelaySeconds == 0.0) { onSubtitleDelay(0.0) }
                Text("mpv 专属设置只在当前内核支持时显示。", color = Color(0x99FFFFFF), fontSize = 13.sp)
            } else {
                Text("切换到 mpv 后，这里会显示硬解、ASS 特效和字幕延迟等专属功能。", color = Color(0x99FFFFFF), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun TrackSelectionItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isFocused -> Color(0x554E7BCC)
                    isSelected -> Color(0x334E7BCC)
                    else -> Color(0x1AFFFFFF)
                }
            )
            .border(
                1.dp,
                if (isFocused) Color(0xFFB0CEFF) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isSelected || isFocused) Color.White else Color(0xDDFFFFFF),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF70A8FF))
            )
        }
    }
}
