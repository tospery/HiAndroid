package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 无边框的通用多行文本输入框。
 *
 * 组件保持固定高度，并在右下角预留 [counterContent] 的布局位置；输入状态和计数文案由调用方持有，
 * 使组件不依赖任何业务长度规则或字符串资源。
 */
@Composable
fun SuiteMultilineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    height: Dp = 180.dp,
    minLines: Int = 5,
    maxLines: Int = 7,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    counterContent: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .background(containerColor),
        enabled = enabled,
        minLines = minLines,
        maxLines = maxLines,
        textStyle =
            textStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = 20.dp,
                            top = 16.dp,
                            end = 20.dp,
                            bottom = 12.dp,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 28.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = textStyle,
                        )
                    }
                    innerTextField()
                }
                counterContent?.let { content ->
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        content()
                    }
                }
            }
        },
    )
}
