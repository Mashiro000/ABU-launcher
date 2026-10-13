package com.limi.tvdesktop

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

@Composable
internal fun SignalSourcePicker(haze: HazeState, staticBlur: ImageBitmap?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("desktop", Context.MODE_PRIVATE) }
    val inputs = remember {
        runCatching {
            context.getSystemService(TvInputManager::class.java)?.tvInputList.orEmpty()
                .filter { it.isPassthroughInput }
                .map { it.id to it.loadLabel(context).toString() }
                .sortedWith(compareBy<Pair<String, String>> { it.second }.thenBy { it.first })
        }.getOrDefault(emptyList())
    }
    var selected by remember { mutableStateOf(prefs.getString("lastSignalSourceId", null)) }
    val initialIndex = remember { inputs.indexOfFirst { it.first == selected }.coerceAtLeast(0) }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
    CenteredSelectionPanel(haze, staticBlur, "信号源选择", onDismiss) {
        if (inputs.isEmpty()) {
            SelectionTile("信号源设置", "此设备未向第三方桌面提供 HDMI 等信号源列表", false,
                Modifier.weight(1f).fillMaxHeight().focusRequester(first),
                icon = { DeviceModeIcon(DesktopPreferences.DeviceMode.TV, it) }) {
                if (runCatching {
                    context.startActivity(Intent("android.settings.TV_INPUT_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isFailure) Toast.makeText(context, "此设备未提供信号源设置入口", Toast.LENGTH_SHORT).show()
            }
        } else {
            LazyRow(Modifier.weight(1f).fillMaxHeight(), state = list,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(inputs, key = { _, input -> input.first }) { index, input ->
                    SelectionTile(input.second, if (selected == input.first) "上次选择" else "点击切换",
                        selected == input.first,
                        Modifier.width(if (inputs.size < 3) ((860 - (inputs.size - 1) * 16) / inputs.size).dp else 276.dp)
                            .fillMaxHeight().then(if (index == initialIndex) Modifier.focusRequester(first) else Modifier),
                        icon = { DeviceModeIcon(DesktopPreferences.DeviceMode.TV, it) }) {
                        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.first))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (runCatching { context.startActivity(intent) }.isSuccess) {
                            selected = input.first
                            prefs.edit().putString("lastSignalSourceId", input.first).apply()
                        } else Toast.makeText(context, "此信号源暂时无法切换", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
