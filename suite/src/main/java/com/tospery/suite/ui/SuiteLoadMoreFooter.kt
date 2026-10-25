package com.tospery.suite.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tospery.suite.R
import kotlin.math.abs
import kotlin.math.min

/** 列表末尾追加加载的状态，与具体分页库无关。 */
enum class SuiteLoadMoreState {
    IDLE,
    LOADING,
    ERROR,
}

/**
 * 在列表末尾追加“加载更多”页脚；空闲时不占位。
 *
 * 加载中显示紧凑的三点动效，失败时在相同位置显示重试操作。
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
        modifier = modifier.defaultMinSize(minHeight = LOAD_MORE_FOOTER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            SuiteLoadMoreState.LOADING -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    SuiteLoadMoreDots()
                    Text(
                        text = stringResource(R.string.suite_load_more_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SuiteLoadMoreState.ERROR -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.suite_load_more_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = onRetry,
                        colors =
                            ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    ) {
                        Text(text = stringResource(R.string.suite_load_more_retry))
                    }
                }
            }

            SuiteLoadMoreState.IDLE -> Unit
        }
    }
}

@Composable
private fun SuiteLoadMoreDots() {
    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "suite_load_more_dots")
    val phase =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = DOT_COUNT.toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = DOT_CYCLE_MILLIS, easing = LinearEasing),
                ),
            label = "suite_load_more_phase",
        )

    Canvas(modifier = Modifier.size(width = 42.dp, height = 20.dp)) {
        repeat(DOT_COUNT) { index ->
            val linearDistance = abs(phase.value - index)
            val circularDistance = min(linearDistance, DOT_COUNT - linearDistance)
            val emphasis = (1f - circularDistance / DOT_PULSE_WIDTH).coerceIn(0f, 1f)
            val center = Offset(x = (7 + index * 14).dp.toPx(), y = size.height / 2f)
            if (emphasis > 0f) {
                drawCircle(
                    color = primary.copy(alpha = 0.1f * emphasis),
                    radius = (5 + 3 * emphasis).dp.toPx(),
                    center = center,
                )
            }
            drawCircle(
                color = lerp(primary.copy(alpha = 0.25f), primary, emphasis),
                radius = (3 + 2 * emphasis).dp.toPx(),
                center = center,
            )
        }
    }
}

private const val SUITE_LOAD_MORE_FOOTER_KEY = "suite_load_more_footer"
private val LOAD_MORE_FOOTER_HEIGHT = 52.dp
private const val DOT_COUNT = 3
private const val DOT_CYCLE_MILLIS = 840
private const val DOT_PULSE_WIDTH = 0.75f
