package com.limi.tvdesktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AnimationSettings(
    onBack: () -> Unit,
    returnRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LaunchAnim.current(context)) }

    val backRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        backRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
    ) {
        SettingsRowItem(
            label = "← 返回分类",
            value = null,
            focusRequester = backRequester,
            leftReturnRequester = returnRequester,
            onClick = onBack
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "启动动画",
            color = White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            "定制从桌面启动 App 时的视觉缩放与过渡动效",
            color = Color(0xFFA5ACB8),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        AnimationPreview(selected)

        Spacer(Modifier.height(28.dp))
        SettingsSectionTitle("选择动画风格")

        LaunchAnim.entries.forEach { anim ->
            SettingsRadioRow(
                label = anim.label,
                selected = selected == anim,
                leftReturnRequester = returnRequester
            ) {
                LaunchAnim.save(context, anim)
                selected = anim
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** A sample card replays the chosen launch motion when a radio option is selected. */
@Composable
private fun AnimationPreview(anim: LaunchAnim) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(anim) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(if (anim == LaunchAnim.SYSTEM) 240 else 640, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(ContinuousCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF242730), Color(0xFF16181D)))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(190.dp, 108.dp)
                .graphicsLayer {
                    val p = progress.value
                    when (anim) {
                        LaunchAnim.CIRCLE -> {
                            val s = .1f + .9f * p
                            scaleX = s
                            scaleY = s
                            alpha = p
                        }
                        LaunchAnim.ZOOM -> {
                            val s = .5f + .5f * p
                            scaleX = s
                            scaleY = s
                            alpha = p
                        }
                        LaunchAnim.FADE -> alpha = p
                        LaunchAnim.SLIDE_UP -> {
                            translationY = (1f - p) * 200f
                            alpha = p
                        }
                        LaunchAnim.SYSTEM -> alpha = 1f
                    }
                }
        ) {
            SampleCard()
        }
    }
}

@Composable
private fun SampleCard() {
    Box(
        Modifier
            .fillMaxSize()
            .clip(ContinuousCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF0F1F3), Color(0xFFCDD0D6))))
    ) {
        Box(Modifier.padding(16.dp)) {
            Box(Modifier.size(28.dp).clip(ContinuousCornerShape(50.dp)).background(Color(0xFF3875F6)))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(9.dp).clip(Glass).background(Color(0xFF5A606D)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(0.55f).height(9.dp).clip(Glass).background(Color(0xFF8B929E)))
        }
    }
}
