package com.tospery.suite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tospery.suite.R

/** 列表末尾追加加载的状态，与具体分页库无关。 */
enum class SuiteLoadMoreState {
    IDLE,
    LOADING,
    ERROR,
}

/**
 * 在列表末尾追加“加载更多”页脚；空闲时不占位。
 *
 * 加载中显示指示器与“正在加载”，失败时显示重试按钮。
 */
fun LazyListScope.suiteLoadMoreFooter(
    state: SuiteLoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = SUITE_LOAD_MORE_FOOTER_KEY,
) {
    if (state == SuiteLoadMoreState.IDLE) return
    item(key = key) {
        SuiteLoadMoreFooter(
            state = state,
            onRetry = onRetry,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SuiteLoadMoreFooter(
    state: SuiteLoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            SuiteLoadMoreState.LOADING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SuiteLoadingIndicator()
                    Text(
                        text = stringResource(R.string.suite_load_more_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            SuiteLoadMoreState.ERROR -> {
                TextButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                    )
                    Text(text = stringResource(R.string.suite_load_more_retry))
                }
            }

            SuiteLoadMoreState.IDLE -> Unit
        }
    }
}

private const val SUITE_LOAD_MORE_FOOTER_KEY = "suite_load_more_footer"
