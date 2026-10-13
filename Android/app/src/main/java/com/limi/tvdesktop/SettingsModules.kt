package com.limi.tvdesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class SettingsModule(
    val id: String, val title: String, val subtitle: String, val icon: String,
    val category: SettingsCategory, val pluginId: String? = null,
)

@Composable
internal fun SettingsModuleGrid(
    modules: List<SettingsModule>, requesters: Map<String, FocusRequester>,
    onSelect: (SettingsModule) -> Unit,
    modifier: Modifier = Modifier,
    initialFocusKey: String? = null,
) {
    LaunchedEffect(requesters, initialFocusKey) {
        val requester = requesters[initialFocusKey] ?: modules.firstOrNull()?.let { requesters[it.id] }
        repeat(12) {
            withFrameNanos { }
            if (runCatching { requester?.requestFocus() == true }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("设置", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(8.dp)) {
            items(modules, key = { it.id }) { module ->
                var focused by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().aspectRatio(1f)
                    .focusRequester(requesters.getValue(module.id))
                    .focusProperties { canFocus = true }
                    .onFocusChanged { focused = it.isFocused }
                    .background(if (focused) Color.White else Color(0xF2262626), RoundedCornerShape(18.dp))
                    .clickable { onSelect(module) }
                    .padding(20.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    SettingsModuleIcon(module.category, 48.dp, if (focused) Color(0xFF125ADD) else White)
                    Spacer(Modifier.height(20.dp))
                    Text(module.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                        color = if (focused) Color(0xFF10204B) else White, maxLines = 1)
                    Spacer(Modifier.height(8.dp))
                    Text(module.subtitle, fontSize = 14.sp, maxLines = 2,
                        color = if (focused) Color(0xFF40516D) else Color(0xFFAAAAAA))
                }
            }
        }
    }
}

@Composable
internal fun SettingsIconRail(
    modules: List<SettingsModule>, selected: String, requesters: Map<String, FocusRequester>,
    onFocusChanged: (Boolean) -> Unit, onModuleFocused: (SettingsModule) -> Unit,
    onSelect: (SettingsModule) -> Unit,
) {
    LazyColumn(Modifier.width(92.dp).fillMaxHeight()
        .background(Color.Black.copy(alpha = .2f))
        .onFocusChanged { onFocusChanged(it.hasFocus) }.focusGroup(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items(modules, key = { it.id }) { module ->
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.size(64.dp).focusRequester(requesters.getValue(module.id))
                .focusProperties { canFocus = true }
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onModuleFocused(module) }
                .semantics { contentDescription = module.title; this.selected = module.id == selected }
                .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable { onSelect(module) }, contentAlignment = Alignment.Center) {
                SettingsModuleIcon(module.category, 30.dp, if (focused) Color(0xFF141414) else White)
            }
        }
    }
}
