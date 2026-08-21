package com.tospery.suite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 无业务的列表分组容器。
 *
 * 组与组之间、以及页面顶部的间距由页面布局决定，避免把页面视觉规则固定到通用组件中。
 */
@Composable
fun SuiteListSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    SuiteListSection(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

/** 可在特殊语义场景中覆盖默认的内容行容器色。 */
@Composable
fun SuiteListSection(
    modifier: Modifier = Modifier,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(containerColor),
        content = content,
    )
}
