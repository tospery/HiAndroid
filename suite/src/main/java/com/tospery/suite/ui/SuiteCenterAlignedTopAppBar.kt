package com.tospery.suite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private const val TOP_APP_BAR_DIVIDER_ALPHA = 0.85f

/**
 * 带底部分割线的通用居中顶部导航栏。
 *
 * 标题、导航图标和操作区由调用方提供，以便业务模块继续负责文案与交互语义；
 * 组件统一导航栏背景和底部分割线样式；相邻内容需要与导航栏连续显示时可关闭分割线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuiteCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    showDivider: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier) {
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                ),
        )
        if (showDivider) {
            OnePixelHorizontalDivider(
                color =
                    MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = TOP_APP_BAR_DIVIDER_ALPHA,
                    ),
            )
        }
    }
}
