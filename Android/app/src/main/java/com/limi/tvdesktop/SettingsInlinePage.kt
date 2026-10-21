package com.limi.tvdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/** A settings child stays inside its parent panel, rather than opening a second Android window. */
@Composable
internal fun SettingsInlinePage(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val progress = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val back = remember { FocusRequester() }
    fun close() {
        if (closing) return
        closing = true
        scope.launch { progress.animateTo(1f, tween(RenderPerformance.duration(140))); onDismiss() }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        back.requestFocus()
        progress.animateTo(0f, tween(RenderPerformance.duration(140)))
    }
    BackHandler { close() }
    Column(Modifier.fillMaxSize().zIndex(10f).graphicsLayer {
        if (RenderPerformance.reducedEffects) alpha = 1f - progress.value
        else translationX = size.width * progress.value
    }
        .settingsVerticalScroll().padding(horizontal = 12.dp, vertical = 28.dp).padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsRowItem("← 返回", null, back, onClick = ::close)
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

@Composable
internal fun SettingsConfirmationPage(onDismissRequest: () -> Unit,
    title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit) {
    SettingsInlinePage(onDismissRequest) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)) { title() }
        text()
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { confirmButton(); dismissButton() }
    }
}
