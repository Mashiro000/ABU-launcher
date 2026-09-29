package com.limi.tvdesktop.plugins.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renders a deliberately small, host-owned widget vocabulary. */
@Composable
fun PluginUiRenderer(node: PluginUiNode, onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    when (node.type) {
        "column" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            node.children.forEach { PluginUiRenderer(it, onAction, Modifier.fillMaxWidth()) }
        }
        "row" -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            node.children.forEach { PluginUiRenderer(it, onAction, Modifier.weight(1f)) }
        }
        "card" -> Column(
            modifier.background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { node.children.forEach { PluginUiRenderer(it, onAction, Modifier.fillMaxWidth()) } }
        "button" -> Box(
            modifier.background(toneColor(node.tone), RoundedCornerShape(14.dp))
                .clickable(enabled = node.action != null) { node.action?.let(onAction) }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) { Text(node.text.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        "spacer" -> Spacer(modifier.height(12.dp))
        else -> Text(node.text.orEmpty(), modifier, color = toneColor(node.tone), fontSize = 18.sp)
    }
}

private fun toneColor(tone: String?): Color = when (tone) {
    "danger" -> Color(0xFFFF6B6B)
    "muted" -> Color(0xFFA5ACB8)
    "accent" -> Color(0xFF8E7CFF)
    else -> Color.White
}
