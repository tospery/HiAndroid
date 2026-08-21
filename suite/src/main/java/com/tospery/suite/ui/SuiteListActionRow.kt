package com.tospery.suite.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SuiteListActionRowContentPadding =
    PaddingValues(horizontal = 20.dp, vertical = 14.dp)

/**
 * 用于退出登录、删除等非业务通用的列表操作行。
 *
 * 默认使用错误色突出破坏性操作；调用方可覆盖颜色，用于普通操作。
 */
@Composable
fun SuiteListActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleColor: Color = MaterialTheme.colorScheme.error,
    contentPadding: PaddingValues = SuiteListActionRowContentPadding,
    showDivider: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    SuiteListRow(
        title = title,
        modifier = modifier,
        onClick = onClick,
        titleStyle = titleStyle,
        titleColor = titleColor,
        titleTextAlign = TextAlign.Center,
        contentPadding = contentPadding,
        containerColor = containerColor,
        showDivider = showDivider,
    )
}
