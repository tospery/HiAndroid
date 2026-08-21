package com.tospery.suite.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

/**
 * 带单选语义和选中标记的通用列表行。
 *
 * 调用方负责提供标题和可选的前置内容；组件统一处理点击区域、
 * RadioButton 可访问性语义以及选中状态图标。
 */
@Composable
fun SuiteSelectableListRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    SuiteListRow(
        title = title,
        modifier =
            modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        leadingContent = leadingContent,
        trailingContent =
            if (selected) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        containerColor = containerColor,
        showDivider = showDivider,
    )
}
