package com.tospery.suite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** 列表页面的整体状态：首次加载、空态、全页错误或已有内容。 */
sealed interface SuiteListContentState {
    data object Loading : SuiteListContentState

    data object Empty : SuiteListContentState

    data class Error(
        val error: Throwable,
    ) : SuiteListContentState

    data object Content : SuiteListContentState
}

/**
 * 按 [state] 在首次加载、空态、全页错误与内容之间切换。
 *
 * 不内置任何文案：空态与错误由调用方提供，内容区由调用方自行组织列表，可自由放置头部、筛选栏或吸顶项。
 */
@Composable
fun SuiteListStateLayout(
    state: SuiteListContentState,
    empty: @Composable () -> Unit,
    error: @Composable (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { SuiteListLoadingState(modifier = modifier) },
    content: @Composable () -> Unit,
) {
    when (state) {
        SuiteListContentState.Loading -> loading()
        SuiteListContentState.Empty -> empty()
        is SuiteListContentState.Error -> error(state.error)
        SuiteListContentState.Content -> content()
    }
}

/** 居中的首次加载指示器。 */
@Composable
fun SuiteListLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        SuiteLoadingIndicator()
    }
}
