package com.limi.tvdesktop

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

@Composable
internal fun CreditsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsInlinePage(onBack) {
        Text("开源与致谢", color = White, fontSize = 34.sp)
        Text("阿布桌面在开发过程中学习和参考了 CleanYourTV、Aerial 与 Tigris 的安装引导、电视交互及性能优化思路。感谢作者和贡献者的开源工作，为本项目提供了灵感和参考。", color = Muted, fontSize = 20.sp)
        listOf(
            "CleanYourTV" to "https://cleanyour.tv",
            "Aerial" to "https://github.com/pengchujin/aerial-tv",
            "Tigris" to "https://github.com/lesnolie/Tigris"
        ).forEach { (title, url) ->
            SettingsRowItem(title, "查看项目") {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { android.widget.Toast.makeText(context, url, android.widget.Toast.LENGTH_LONG).show() }
            }
        }
        Text("阿布桌面按自身设计风格和功能需求独立实现。项目采用 MIT 许可；第三方代码、字体与素材遵循各自许可证。参考项目与阿布桌面不存在背书关系。", color = Muted, fontSize = 18.sp)
    }
}
