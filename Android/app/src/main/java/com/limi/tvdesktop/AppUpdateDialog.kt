package com.limi.tvdesktop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

@Composable
internal fun AppUpdateDialog(release: AppRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
    }
    var showNotes by remember(release.version) { mutableStateOf(false) }
    val moreFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = { if (showNotes) showNotes = false else onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler {
            if (showNotes) showNotes = false else onDismiss()
        }
        Box(
            Modifier.fillMaxSize().background(Color(0xB8000000)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(min = 560.dp, max = 820.dp)
                    .fillMaxWidth(.72f)
                    .background(Color(0xFF171A21), RoundedCornerShape(26.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                    .padding(34.dp),
            ) {
                Text("发现新版本", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$currentVersion  →  ${release.version}",
                    color = Color(0xFF8FB4FF),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(release.title, color = Color(0xFFB8BEC9), fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))

                AnimatedVisibility(showNotes) {
                    Column(Modifier.padding(top = 22.dp)) {
                        Text("更新内容", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(
                            release.notes,
                            color = Color(0xFFD2D5DB),
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 10.dp).heightIn(max = 250.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
                ) {
                    UpdateDialogButton("稍后", onClick = onDismiss)
                    UpdateDialogButton(
                        if (showNotes) "收起内容" else "查看更多",
                        modifier = Modifier.focusRequester(moreFocus),
                        onClick = { showNotes = !showNotes },
                    )
                    UpdateDialogButton("前往下载", emphasized = true) {
                        AppUpdateManager.openReleasePage(context, release)
                    }
                }
            }
        }
        LaunchedEffect(release.version) {
            delay(120)
            runCatching { moreFocus.requestFocus() }
        }
    }
}

@Composable
private fun UpdateDialogButton(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val background = when {
        active -> Color.White
        emphasized -> Color(0xFF3875F6)
        else -> Color(0x20FFFFFF)
    }
    Text(
        label,
        color = if (active) Color(0xFF111318) else Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .focusable(interactionSource = interaction)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(background, RoundedCornerShape(13.dp))
            .border(1.dp, if (active) Color.White else Color(0x2FFFFFFF), RoundedCornerShape(13.dp))
            .padding(horizontal = 22.dp, vertical = 13.dp),
    )
}
