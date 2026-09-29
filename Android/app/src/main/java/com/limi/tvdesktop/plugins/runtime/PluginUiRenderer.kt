package com.limi.tvdesktop.plugins.runtime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Host-owned widgets preserve TV focus, touch behavior and theme boundaries. */
@Composable
fun PluginUiRenderer(
    node: PluginUiNode,
    onAction: (String) -> Unit,
    onInput: (String, String) -> Unit = { _, _ -> },
    resolveAsset: (String) -> File? = { null },
    modifier: Modifier = Modifier,
) {
    @Composable fun child(item: PluginUiNode, childModifier: Modifier = Modifier.fillMaxWidth()) =
        PluginUiRenderer(item, onAction, onInput, resolveAsset, childModifier)

    when (node.type) {
        "column" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            node.children.forEach { child(it) }
        }
        "row" -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            node.children.forEach { child(it, Modifier.weight(1f)) }
        }
        "card" -> Column(
            modifier.background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { node.children.forEach { child(it) } }
        "list" -> LazyColumn(modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(node.children, key = { index, _ -> index }) { _, item -> child(item) }
        }
        "button" -> Box(
            modifier.background(toneColor(node.tone), RoundedCornerShape(14.dp))
                .clickable(enabled = node.action != null) { node.action?.let(onAction) }
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) { Text(node.text.orEmpty(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        "input" -> {
            var value by remember(node.id, node.value) { mutableStateOf(node.value.orEmpty()) }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; node.id?.let { id -> onInput(id, it) } },
                label = node.text?.let { { Text(it) } },
                placeholder = node.hint?.let { { Text(it) } },
                singleLine = true,
                modifier = modifier,
            )
        }
        "toggle" -> {
            var checked by remember(node.id, node.value) { mutableStateOf(node.value == "true") }
            Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(node.text.orEmpty(), color = Color.White)
                Switch(checked = checked, onCheckedChange = {
                    checked = it
                    node.id?.let { id -> onInput(id, it.toString()) }
                })
            }
        }
        "image" -> {
            val path = node.asset?.let(resolveAsset)
            val bitmap = remember(path) {
                path?.takeIf { it.isFile && it.length() <= 4L * 1024 * 1024 }?.let { file ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    if (bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) BitmapFactory.decodeFile(file.path) else null
                }
            }
            if (bitmap != null) Image(bitmap.asImageBitmap(), node.text, modifier.heightIn(max = 320.dp), contentScale = ContentScale.Fit)
            else Text(node.text ?: "图片不可用", modifier, color = Color(0xFFA5ACB8))
        }
        "progress" -> LinearProgressIndicator(progress = { (node.progress ?: 0f).coerceIn(0f, 1f) }, modifier = modifier)
        "spacer" -> Spacer(modifier.height(12.dp))
        "text" -> Text(node.text.orEmpty(), modifier, color = toneColor(node.tone), fontSize = 18.sp)
    }
}

private fun toneColor(tone: String?): Color = when (tone) {
    "danger" -> Color(0xFFFF6B6B)
    "muted" -> Color(0xFFA5ACB8)
    "accent" -> Color(0xFF8E7CFF)
    else -> Color.White
}
