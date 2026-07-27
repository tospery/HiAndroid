package com.tospery.suite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tospery.base.error.appErrorMessageOrNull

/**
 * 通用错误状态展示组件。
 *
 * 组件只负责呈现错误状态，不判断错误来源。调用方负责把业务错误映射为文案、
 * 重试动作或其他恢复入口。
 */
@Composable
fun SuiteErrorState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    error: Throwable? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    SuiteStateLayout(
        title = title,
        modifier = modifier,
        description = error?.appErrorMessageOrNull() ?: description,
        descriptionMaxLines = ERROR_DESCRIPTION_MAX_LINES,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        action = {
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                Button(onClick = onActionClick) {
                    Text(text = actionText)
                }
            }
        },
    )
}

private const val ERROR_DESCRIPTION_MAX_LINES = 3
