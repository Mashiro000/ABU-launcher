package com.limi.tvdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsUpdateDetail(onBack: () -> Unit) {
    val context = LocalContext.current
    val back = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { withFrameNanos { }; back.requestFocus() }
    val state = AppUpdateManager.state
    val release = when (state) {
        is UpdateCheckState.Available -> state.release
        is UpdateCheckState.Downloading -> state.release
        is UpdateCheckState.Ready -> state.release
        else -> null
    }
    Column(Modifier.fillMaxSize().settingsVerticalScroll().padding(top = 28.dp, bottom = 68.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsRowItem("← 返回分类", null, back, onClick = onBack)
        Text("检查更新", color = White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("当前版本：${context.packageManager.getPackageInfo(context.packageName, 0).versionName}", color = Muted, fontSize = 18.sp)
        Text(when (state) {
            UpdateCheckState.Checking -> "正在检查更新…"
            is UpdateCheckState.Failed -> state.message
            is UpdateCheckState.UpToDate -> "已是最新版本"
            is UpdateCheckState.Downloading -> "正在下载新版本…"
            is UpdateCheckState.Ready -> "新版本已下载"
            is UpdateCheckState.Available -> "发现 ${state.release.version}"
            else -> "从 GitHub Releases 获取版本信息"
        }, color = White, fontSize = 20.sp)
        if (release != null) {
            Text(release.title, color = White, fontSize = 24.sp)
            Text(release.notes, color = Muted, fontSize = 18.sp)
            if (state is UpdateCheckState.Ready) SettingsRowItem("安装已下载版本", null, onClick = { AppUpdateManager.openDownloadedApk(context) })
            else if (state !is UpdateCheckState.Downloading) SettingsRowItem("下载新版本", null, onClick = {
                if (release.apkUrl != null) AppUpdateManager.startDownload(context, release, openAfterDownload = false)
                else AppUpdateManager.openReleasePage(context, release)
            })
        }
    }
}
