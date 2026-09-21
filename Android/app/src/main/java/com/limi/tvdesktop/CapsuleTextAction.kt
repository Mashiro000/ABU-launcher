package com.limi.tvdesktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.*

/** Shared navigation-style treatment for text actions: animate the capsule, never the label. */
@Composable
internal fun CapsuleTextAction(
    label: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = LibraryDesign.metadata,
    height: Dp = 44.dp,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val keyboardControl = LocalKeyboardControl.current
    val reveal by animateFloatAsState(if (focused || (hovered && !keyboardControl)) 1f else 0f, tween(300), label = "text-action-capsule")
    Box(modifier.requiredHeight(height).focusSweep(focused || (hovered && !keyboardControl)).onFocusChanged { focused = it.isFocused }
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer {
            alpha = reveal; scaleX = .8f + .2f * reveal; scaleY = .8f + .2f * reveal
        }.clip(Glass).background(Brush.linearGradient(listOf(Color.White, Color(0xFFD0D0D0)))))
        Text(label, Modifier.padding(horizontal = 16.dp), color = lerp(Muted, Color(0xFF161616), reveal), fontSize = fontSize)
    }
}
