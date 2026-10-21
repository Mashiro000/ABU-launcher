package com.limi.tvdesktop

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object NavigationPreferences {
    val defaults = listOf("首页", "媒体库")
    private fun prefs(context: Context) = context.getSharedPreferences("desktop", Context.MODE_PRIVATE)
    fun order(context: Context): List<String> = normalizeNavigation(prefs(context).getString("navigationOrder", "")?.split('|').orEmpty())
    fun visible(context: Context): List<String> {
        val hidden = prefs(context).getStringSet("navigationHidden", emptySet()).orEmpty()
        return order(context).filterNot { it in hidden }.ifEmpty { listOf("首页") }
    }
    fun shown(context: Context) = prefs(context).getBoolean("navigationShown", true)
    fun saveOrder(context: Context, order: List<String>) {
        prefs(context).edit().putString("navigationOrder", normalizeNavigation(order).joinToString("|")).apply()
        DesktopPreferences.notifyChanged()
    }
    fun setShown(context: Context, shown: Boolean) {
        prefs(context).edit().putBoolean("navigationShown", shown).apply(); DesktopPreferences.notifyChanged()
    }
    fun toggle(context: Context, tab: String) {
        val hidden = prefs(context).getStringSet("navigationHidden", emptySet()).orEmpty().toMutableSet()
        if (!hidden.remove(tab)) hidden.add(tab)
        if (defaults.all { it in hidden }) return
        prefs(context).edit().putStringSet("navigationHidden", hidden).apply(); DesktopPreferences.notifyChanged()
    }
    fun reset(context: Context) {
        prefs(context).edit().remove("navigationOrder").remove("navigationHidden").remove("navigationShown").apply()
        DesktopPreferences.notifyChanged()
    }
}

internal fun normalizeNavigation(saved: List<String>): List<String> =
    (saved.filter { it in NavigationPreferences.defaults } + NavigationPreferences.defaults).distinct()

@Composable
internal fun NavigationSettings(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = DesktopPreferences.version
    val order = remember(version) { NavigationPreferences.order(context) }
    val visible = remember(version) { NavigationPreferences.visible(context) }
    SettingsInlinePage(onBack) {
        Text("导航条", color = White, fontSize = 34.sp)
        Text("长按导航项目进入移动，左右调整顺序，确认保存，返回取消。隐藏导航条后，右上角时钟仍可进入设置。", color = Muted, fontSize = 18.sp)
        SettingsRowItem("显示导航条", if (NavigationPreferences.shown(context)) "已显示" else "已隐藏") {
            NavigationPreferences.setShown(context, !NavigationPreferences.shown(context)); onChanged()
        }
        order.forEachIndexed { index, tab ->
            SettingsRowItem("显示 $tab", if (tab in visible) "已显示" else "已隐藏") {
                NavigationPreferences.toggle(context, tab); onChanged()
            }
            if (index > 0) SettingsRowItem("将 $tab 向前移动", "调整顺序") {
                val updated = order.toMutableList().apply { removeAt(index); add(index - 1, tab) }
                NavigationPreferences.saveOrder(context, updated); onChanged()
            }
        }
        SettingsRowItem("恢复默认导航条", null) { NavigationPreferences.reset(context); onChanged() }
        Spacer(Modifier.height(16.dp))
    }
}
