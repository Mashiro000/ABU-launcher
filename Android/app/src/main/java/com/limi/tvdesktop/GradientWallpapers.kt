package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object GradientWallpapers {
    data class Palette(val id: String, val label: String, val start: Int, val end: Int, val glow: Int)
    val palettes = listOf(
        Palette("dusk", "暮光", 0xFF252B64.toInt(), 0xFF954B83.toInt(), 0xFFF4AA8C.toInt()),
        Palette("ocean", "海湾", 0xFF091C45.toInt(), 0xFF156E98.toInt(), 0xFF67DDC4.toInt()),
        Palette("aurora", "极光", 0xFF102C39.toInt(), 0xFF285E55.toInt(), 0xFF8BD4A5.toInt()),
        Palette("ember", "余晖", 0xFF5A224B.toInt(), 0xFFBC5853.toInt(), 0xFFFFCB91.toInt()),
        Palette("iris", "鸢尾", 0xFF241C58.toInt(), 0xFF6255A1.toInt(), 0xFFB6A5F0.toInt()),
        Palette("slate", "雾蓝", 0xFF101C2C.toInt(), 0xFF3B526B.toInt(), 0xFF9CB4C7.toInt())
    )
    fun current(context: Context): Palette {
        val id = context.getSharedPreferences("desktop", Context.MODE_PRIVATE).getString("gradientPalette", "dusk")
        return palettes.firstOrNull { it.id == id } ?: palettes.first()
    }
    fun save(context: Context, palette: Palette) {
        context.getSharedPreferences("desktop", Context.MODE_PRIVATE).edit().putString("gradientPalette", palette.id).apply()
    }
    /** Generated once on a worker thread; no packaged images, no continuous GPU animation. */
    fun bitmap(palette: Palette, width: Int = 960, height: Int = 540): Bitmap {
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), palette.start, palette.end, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = RadialGradient(width * .78f, height * .18f, width * .85f,
            intArrayOf((palette.glow and 0x00FFFFFF) or 0xD0000000.toInt(), palette.glow and 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = RadialGradient(width * .18f, height * 1.15f, width * .8f,
            intArrayOf(0xA0081028.toInt(), 0x00081028), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return image
    }
}

@Composable
internal fun GradientPalettePicker(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf(GradientWallpapers.current(context)) }
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, selected.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { GradientWallpapers.bitmap(selected, 640, 360).asImageBitmap() }
    }
    preview?.let { androidx.compose.foundation.Image(it, selected.label, Modifier.fillMaxWidth().height(190.dp).clip(ContinuousCornerShape(20.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
    Spacer(Modifier.height(20.dp))
    Text("柔和渐变 · ${selected.label}", color = White, fontSize = 23.sp)
    Text("静态渐变保留细腻层次，适合低性能电视。选择颜色即可应用。", color = Muted, fontSize = 17.sp)
    Spacer(Modifier.height(18.dp))
    GradientWallpapers.palettes.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            row.forEach { palette ->
                var focused by remember { mutableStateOf(false) }
                val active = selected.id == palette.id
                val requester = remember { androidx.compose.ui.focus.FocusRequester() }
                Column(Modifier.weight(1f).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(76.dp).then(Modifier.focusRequester(requester)).semantics { contentDescription = "应用${palette.label}渐变壁纸" }.onFocusChanged { focused = it.hasFocus }.settingsAutoScroll()
                        .border(if (focused) 3.dp else 2.dp, if (focused || active) Color.White else Color.Transparent, CircleShape)
                        .padding(6.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(palette.glow), Color(palette.end), Color(palette.start))))
                        .clickable(onClickLabel = "应用${palette.label}渐变壁纸") {
                            requester.requestFocus()
                            selected = palette
                            GradientWallpapers.save(context, palette)
                            WallpaperMode.save(context, WallpaperMode.GRADIENT)
                            onChanged()
                        }, contentAlignment = Alignment.Center) {
                        if (active) Text("✓", color = Color.White, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(palette.label, color = if (focused || active) White else Muted, fontSize = 18.sp)
                }
            }
        }
    }
}
