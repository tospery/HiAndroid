package com.tospery.suite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 带向右导航指示器的通用列表行。
 *
 * 适用于点击整行后导航或执行进一步操作的设置、资料等列表场景。
 */
@Composable
fun SuiteNavigationListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    SuiteListRow(
        title = title,
        modifier = modifier,
        onClick = onClick,
        leadingContent = leadingContent,
        supportingText = supportingText,
        trailingText = trailingText,
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        containerColor = containerColor,
        showDivider = showDivider,
    )
}
