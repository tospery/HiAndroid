package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * App 级阻塞式 HUD。
 *
 * [showContentContainer] 为 false 时，加载内容直接绘制在 scrim 上，不创建 Surface、圆角或阴影。
 * [scrimColor] 可设为 [Color.Transparent]，用于只保留加载内容与阻塞交互的场景。
 */
@Composable
fun AppHud(
    visible: Boolean,
    modifier: Modifier = Modifier,
    message: String? = null,
    showContentContainer: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.24f),
) {
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor),
        contentAlignment = Alignment.Center,
    ) {
        if (showContentContainer) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 112.dp, max = 220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                AppHudContent(
                    message = message,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                )
            }
        } else {
            AppHudContent(
                message = message,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        }
    }
}

@Composable
private fun AppHudContent(
    message: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SuiteLoadingIndicator(
            modifier = Modifier.size(36.dp),
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
