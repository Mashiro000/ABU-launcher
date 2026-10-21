package com.limi.tvdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsChoiceDetail(page: String, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val config = remember(page, revision, DesktopPreferences.version) {
        when (page) {
            "time_format" -> Triple("时间格式", DesktopPreferences.TimeFormat.entries.map { it.label }, DesktopPreferences.TimeFormat.current(context).label)
            "date_display" -> Triple("日期显示", DesktopPreferences.DateDisplay.entries.map { it.label }, DesktopPreferences.DateDisplay.current(context).label)
            "motion" -> Triple("低性能模式", RenderPerformance.MotionMode.entries.map { it.label }, RenderPerformance.motionMode.label)
            "blur" -> Triple("模糊效果", RenderPerformance.BlurMode.entries.map { it.label }, RenderPerformance.blurMode.label)
            "corners" -> Triple("应用圆角", DesktopPreferences.AppCornerStyle.entries.map { it.label }, DesktopPreferences.AppCornerStyle.current(context).label)
            else -> Triple("开机自启动", listOf("关闭", "开启"), if (DesktopPreferences.AutoStart.isEnabled(context)) "开启" else "关闭")
        }
    }
    fun choose(index: Int) {
        when (page) {
            "time_format" -> DesktopPreferences.TimeFormat.save(context, DesktopPreferences.TimeFormat.entries[index])
            "date_display" -> DesktopPreferences.DateDisplay.save(context, DesktopPreferences.DateDisplay.entries[index])
            "motion" -> {
                if (DeveloperDiagnostics.remaining > 0) DeveloperDiagnostics.finish("渲染模式改变，测试提前结束")
                RenderPerformance.setMotionMode(context, RenderPerformance.MotionMode.entries[index])
            }
            "blur" -> RenderPerformance.setBlurMode(context, RenderPerformance.BlurMode.entries[index])
            "corners" -> DesktopPreferences.AppCornerStyle.save(context, DesktopPreferences.AppCornerStyle.entries[index])
            else -> DesktopPreferences.AutoStart.setEnabled(context, index == 1)
        }
        revision++
        onChanged()
    }
    val back = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { withFrameNanos { }; back.requestFocus() }
    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp)) {
        SettingsRowItem("← 返回分类", null, back, onClick = onBack)
        Spacer(Modifier.height(24.dp))
        Text(config.first, color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        if (page == "motion") Text("完整动画：缩放与位移；精简动画：淡入淡出，保持过渡时长；无动画：直接切换。",
            color = Muted, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        if (page == "autostart") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("开机自启动", color = White, fontSize = 22.sp)
                Switch(checked = config.third == "开启", onCheckedChange = { choose(if (it) 1 else 0) },
                    modifier = Modifier.semantics { contentDescription = "开机自启动开关" })
            }
            Text("开机后尝试启动阿布桌面；部分系统需在系统自启动管理中放行，设为默认桌面可获得更稳定的启动行为。",
                color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp))
        } else config.second.forEachIndexed { index, label ->
            SettingsRadioRow(label, config.third == label) { choose(index) }
            Spacer(Modifier.height(8.dp))
        }
        if (page == "blur") {
            Text("老电视与投影仪建议使用静态模糊。精简／无动画仍保留静态模糊；不支持实时模糊的系统会使用静态模糊。",
                color = Muted, fontSize = 18.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }
}
